"""
Wrapper class around ESM-IF1 (ESM Inverse Folding), the structure-conditioned
zero-shot variant-effect model from facebookresearch/esm
(https://github.com/facebookresearch/esm/blob/main/examples/inverse_folding/README.md).
"""
import os
from typing import Any, Self, Sequence

import numpy as np

from evedesign.model import (
    BaseModel,
    Scorer,
    MutationScorer,
    ConditionalMutationScorer,
    assign_scores_to_instances,
)
from evedesign.structure import Structure
from evedesign.system import System, SystemInstance
from evedesign.types import DeviceType, StatusCallback
from evedesign.utils import ensure_sequence, status_done, status_start

try:
    import biotite.structure as struc

    if not hasattr(struc, "filter_backbone"):
        struc.filter_backbone = struc.filter_peptide_backbone

    import torch
    import torch.nn.functional as F

    import esm
    import esm.inverse_folding.util as if_util
    import esm.inverse_folding.multichain_util as if_multichain_util

    import argparse
    torch.serialization.add_safe_globals([argparse.Namespace])

    IMPORT_AVAILABLE = True
except ImportError:
    IMPORT_AVAILABLE = False

# The only ESM-IF1 checkpoint
CHECKPOINT_NAME = "esm_if1_gvp4_t16_142M_UR50"

_TARGET_CHAIN_ID = "__target__"


def _score_sequence_on_device(
    model,
    alphabet,
    coords: np.ndarray,
    seq: str,
    device,
    coord_mask_source: np.ndarray | None = None,
) -> tuple[float, float]:
    """
    Re-implementation of `esm.inverse_folding.util.get_sequence_loss` +
    `score_sequence` (published `fair-esm==2.0.0` wheel's `get_sequence_loss` 
    never passes a `device=` argument to `CoordBatchConverter.__call__`)

    Parameters
    ----------
    model, alphabet
        The loaded GVPTransformerModel and its Alphabet.
    coords
        (L, 3, 3) N/CA/C coordinate array to condition the encoder on
    seq
        Target sequence string to score
    device
        Device to run the forward pass on
    coord_mask_source
        Array to compute `ll_withcoord`'s coordinate-coverage mask from -
        defaults to `coords`

    Returns
    -------
    (ll_fullseq, ll_withcoord) - average log-likelihood over all target
    positions, and over only coordinate-covered positions, respectively.
    """
    if coord_mask_source is None:
        coord_mask_source = coords

    batch_converter = if_util.CoordBatchConverter(alphabet)
    batch = [(coords, None, seq)]
    coords_t, confidence, strs, tokens, padding_mask = batch_converter(batch, device=device)

    prev_output_tokens = tokens[:, :-1]
    target = tokens[:, 1:]
    target_padding_mask = (target == alphabet.padding_idx)
    logits, _ = model.forward(coords_t, padding_mask, confidence, prev_output_tokens)
    loss = F.cross_entropy(logits, target, reduction="none")
    loss = loss[0].detach().cpu().numpy()
    target_padding_mask_np = target_padding_mask[0].cpu().numpy()

    ll_fullseq = -np.sum(loss * ~target_padding_mask_np) / np.sum(~target_padding_mask_np)
    coord_mask = np.all(np.isfinite(coord_mask_source), axis=(-1, -2))
    ll_withcoord = -np.sum(loss * coord_mask) / np.sum(coord_mask)
    return float(ll_fullseq), float(ll_withcoord)


def _extract_target_chain_coords(
    chain: Structure,
    first_index: int,
    length: int,
    rep: str,
) -> np.ndarray:
    """
    Extract N/CA/C backbone coordinates for the target chain, reindexed to a
    full (length, 3, 3) array aligned 1:1 with the entity's rep sequence
    (position i <-> coords[i]), with np.nan rows for any position the
    structure has no coordinates for.

    Parameters
    ----------
    chain
        Single-chain Structure to extract from (see Structure.chains()).
    first_index
        Entity's first_index (see evedesign.system.Entity)
    length
        Full entity rep length (the output array's first dimension)
    rep
        Full entity rep sequence string (used only to validate the
        structure's own residue identities agree with it

    Returns
    -------
    (length, 3, 3) float32 array of N/CA/C coordinates, np.nan where the
    structure has no coordinate for that entity position
    """
    positions = list(range(first_index, first_index + length))
    # Raises ValueError if the structure is multi-chain/has insertion codes,
    # defines a position outside [first_index, first_index+length), or
    # disagrees with rep at any position it does cover
    chain.represents(positions=positions, sequence=list(rep), allow_missing=True, raise_invalid=True)

    bb_mask = struc.filter_backbone(chain.atom_array)
    bb_atoms = chain.atom_array[bb_mask]
    if len(bb_atoms) == 0:
        raise ValueError("Structure chain has no peptide backbone (N/CA/C) atoms")

    res_ids, _ = struc.get_residues(bb_atoms)
    coords, seq = if_util.extract_coords_from_structure(bb_atoms)

    full_coords = np.full((length, 3, 3), np.nan, dtype=np.float32)
    for i, res_id in enumerate(res_ids):
        pos = int(res_id) - first_index
        if 0 <= pos < length:
            full_coords[pos] = coords[i]
    return full_coords


def _relabel_chain_ids(chains: Sequence[Structure]) -> Structure:
    """
    Deep-copy and relabel a list of single-chain Structures to unique
    single-letter chain ids, then concatenate into one Structure
    """
    def chain_label(i: int) -> str:
        if i < 26:
            return chr(65 + i)
        return chr(65 + (i - 26) // 26) + chr(65 + (i - 26) % 26)

    relabeled = []
    for i, chain in enumerate(chains):
        copy = chain.copy()
        copy.atom_array.chain_id[:] = chain_label(i)
        relabeled.append(copy)

    return Structure.concat(relabeled)


class ESMIF1(BaseModel, Scorer, MutationScorer, ConditionalMutationScorer):
    """
    Wrapper around ESM-IF1's whole-sequence, structure-conditioned
    log-likelihood scoring.
    """
    available = IMPORT_AVAILABLE
    name: str = "ESM-IF1"
    citations: list[str] = [
        # Learning inverse folding from millions of predicted structures
        "10.1101/2022.04.10.487779",
    ]

    requires_target: bool = True
    requires_fixed_length: bool = True
    handles_deletions: bool = False
    handles_insertions: bool = False
    requires_gpu: bool = False
    supports_gpu: bool = True
    supports_gpu_parallel: bool = False
    supports_cpu_parallel: bool = False

    required_entity_attributes: list[str] | None = ["structures"]
    optional_entity_attributes: list[str] | None = None

    def __init__(
        self,
        checkpoint_path: str | os.PathLike | None = None,
        structure_name: str | None = None,
        device: DeviceType = "cpu",
    ):
        """
        Instantiate an ESM-IF1 model.

        Parameters
        ----------
        checkpoint_path
            Path to a local `esm_if1_gvp4_t16_142M_UR50.pt`
        structure_name
            Key into the entity's `structures` mapping to select which
            structure to use, if more than one is present. If None (default)
            and there is exactly one structure, that one is used
        device
            Device to load the model onto and run scoring on.
        """
        if not self.available:
            raise ImportError(
                "torch/fair-esm/torch_geometric dependencies could not be "
                "imported. Install them to use this model."
            )

        self.checkpoint_path = checkpoint_path
        self.structure_name = structure_name
        self.device = device

        self._system: System | None = None
        self._model = None
        self._alphabet = None
        self._coords: np.ndarray | None = None
        self._coords_dict: dict[str, np.ndarray] | None = None
        self._multichain: bool = False
        self._scoring_coords: np.ndarray | None = None
        self._coord_mask_source: np.ndarray | None = None

    @property
    def system(self) -> System | None:
        return self._system

    @property
    def ready(self) -> bool:
        return self._system is not None and (self._coords is not None or self._coords_dict is not None)

    @classmethod
    def can_model(cls, system: System, data: Any = None) -> tuple[bool, str]:
        if data is not None:
            return False, "Model does not support a data parameter (must be None)"

        if len(system) != 1 or system[0].type != "protein":
            return False, "Can only handle a single-component protein system"

        target = system[0]
        if not target.defined_sequence():
            return False, "Entity must have a defined rep sequence"

        if not target.structures:
            return False, "Must provide a structure (entity.structures) for model inference"

        return True, ""

    def _select_structure_chains(self, target) -> list[Structure]:
        """
        Select which structure to use from the entity's structures mapping
        """
        structures = target.structures
        if self.structure_name is not None:
            if self.structure_name not in structures:
                raise ValueError(
                    f"structure_name={self.structure_name!r} not found in entity "
                    f"structures, available keys: {list(structures.keys())}"
                )
            structure = structures[self.structure_name]
        else:
            if len(structures) != 1:
                raise ValueError(
                    "Entity has more than one structure; set structure_name to "
                    f"select one of: {list(structures.keys())}"
                )
            structure = next(iter(structures.values()))

        chains = list(ensure_sequence(structure))
        for chain in chains:
            if len(chain.chains()) != 1:
                raise ValueError(
                    f"Expected each structure chain entry to be single-chain, got chains={chain.chains()}"
                )
        return chains

    def _load_model(self):
        if self.checkpoint_path is not None:
            model, alphabet = esm.pretrained.load_model_and_alphabet_local(str(self.checkpoint_path))
        else:
            model, alphabet = esm.pretrained.esm_if1_gvp4_t16_142M_UR50()
        model = model.to(self.device).eval()
        return model, alphabet

    def build(
        self,
        system: System,
        data: None = None,
        status_callback: StatusCallback | None = None,
    ) -> Self:
        self.can_model_or_raise(system, data)

        status_start(status_callback, "Loading ESM-IF1 and extracting structure coordinates")

        self._system = system
        target = system[0]

        self._coords = None
        self._coords_dict = None

        rep = "".join(target.rep)
        length = len(rep)
        first_index = target.first_index

        chains = self._select_structure_chains(target)

        self._model, self._alphabet = self._load_model()

        if len(chains) == 1:
            self._multichain = False
            self._coords = _extract_target_chain_coords(chains[0], first_index, length, rep)
            # Single-chain: the encoder is conditioned on exactly the target coords
            self._scoring_coords = self._coords
            self._coord_mask_source = self._coords
        else:
            # First chain is the target
            self._multichain = True
            target_coords = _extract_target_chain_coords(chains[0], first_index, length, rep)

            context_complex = _relabel_chain_ids(chains[1:])
            context_bb_mask = struc.filter_backbone(context_complex.atom_array)
            context_coords, _ = if_multichain_util.extract_coords_from_complex(
                context_complex.atom_array[context_bb_mask]
            )
            self._coords_dict = {**context_coords, _TARGET_CHAIN_ID: target_coords}
            # Precompute the concatenated (target + padding + context chains)
            self._scoring_coords = if_multichain_util._concatenate_coords(
                self._coords_dict, _TARGET_CHAIN_ID
            )
            self._coord_mask_source = target_coords

        status_done(status_callback, "ESM-IF1 model finished building")

        return self

    def score(
        self,
        instances: Sequence[SystemInstance],
        status_callback: StatusCallback | None = None,
    ) -> list[SystemInstance]:
        """
        Score full sequences as ESM-IF1's whole-sequence average
        log-likelihood conditioned on the fixed structure built in build().
        """
        self.ready_or_raise()
        self._validate_instances(instances)

        if len(instances) == 0:
            return []

        status_start(status_callback, "Scoring sequences")

        scores = []
        with torch.no_grad():
            for idx, instance in enumerate(instances):
                seq = "".join(str(s) for s in instance[0].rep)

                ll_fullseq, _ = _score_sequence_on_device(
                    self._model, self._alphabet, self._scoring_coords, seq,
                    self.device, coord_mask_source=self._coord_mask_source,
                )
                scores.append(float(ll_fullseq))

                if status_callback and (idx + 1) % 25 == 0:
                    status_callback(
                        "running", 100.0 * (idx + 1) / len(instances),
                        f"Scored {idx + 1}/{len(instances)}",
                    )

        status_done(status_callback, "Scoring complete")

        return assign_scores_to_instances(instances, np.asarray(scores, dtype=float))
