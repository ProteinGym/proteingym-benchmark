"""
Wrapper class around PottsMPNN (https://github.com/KeatingLab/PottsMPNN)
"""
import os
from pathlib import Path
from types import SimpleNamespace
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
from evedesign.system import Entity, System, SystemInstance
from evedesign.types import StatusCallback
from evedesign.utils import ensure_sequence, status_done, status_start

try:
    import torch

    from .pottsmpnn import etab_utils
    from .pottsmpnn.potts_mpnn_utils import PottsMPNN as _PottsMPNN
    from .pottsmpnn.run_utils import get_etab

    IMPORT_AVAILABLE = True
except ImportError:
    IMPORT_AVAILABLE = False

DATA_DIR = Path(os.environ.get("POTTSMPNN_DATA_DIR", "/opt/program/data"))

# parse_PDB_biounits' residue alphabet: index 20 ("GAP") marks missing or
# non-standard residues and maps to "-"
_ALPHA_1 = list("ARNDCQEGHILKMFPSTWYV-")
_ALPHA_3 = ["ALA", "ARG", "ASN", "ASP", "CYS", "GLN", "GLU", "GLY", "HIS", "ILE",
            "LEU", "LYS", "MET", "PHE", "PRO", "SER", "THR", "TRP", "TYR", "VAL", "GAP"]
_AA_3_TO_1 = {three: one for one, three in zip(_ALPHA_1, _ALPHA_3)}
_BACKBONE = ("N", "CA", "C", "O")


def _structure_to_pdb_dict(chain: "Structure", chain_letter: str = "A", name: str = "target") -> dict:
    """
    Build the upstream parse_PDB dict for a single chain in memory.

    Residues span the lowest to highest residue number; missing backbone
    atoms are NaN, missing or non-standard residues "-", MSE read as MET.

    Parameters
    ----------
    chain
        Single-chain structure
    chain_letter
        Chain identifier used in the dict keys
    name
        Value of the "name" entry

    Returns
    -------
    dict
        Same layout as data_utils.parse_PDB(path)[0]
    """
    aa = chain.atom_array
    res_names = aa.res_name.copy()
    is_mse = aa.hetero & (res_names == "MSE")
    res_names[is_mse] = "MET"
    keep = ~aa.hetero | is_mse
    aa, res_names = aa[keep], res_names[keep]

    res_ids = aa.res_id.astype(int)
    lo, hi = int(res_ids.min()), int(res_ids.max())
    length = hi - lo + 1
    coords = np.full((length, len(_BACKBONE), 3), np.nan)
    seq = ["-"] * length
    seen = set()
    for i in range(len(aa)):
        pos = res_ids[i] - lo
        if pos not in seen:
            # the first record of a residue sets its identity, as in parse_PDB_biounits
            seq[pos] = _AA_3_TO_1.get(res_names[i], "-")
            seen.add(pos)
        atom = aa.atom_name[i]
        if atom in _BACKBONE:
            j = _BACKBONE.index(atom)
            if np.isnan(coords[pos, j, 0]):
                coords[pos, j] = aa.coord[i]

    seq = "".join(seq)
    return {
        f"seq_chain_{chain_letter}": seq,
        f"coords_chain_{chain_letter}": {
            f"{atom}_chain_{chain_letter}": coords[:, j, :].tolist() for j, atom in enumerate(_BACKBONE)
        },
        "name": name,
        "num_of_chains": 1,
        "seq": seq,
        "chain_order": [chain_letter],
    }


class PottsMPNN(BaseModel, Scorer, MutationScorer, ConditionalMutationScorer):
    """
    Wrapper around PottsMPNN Potts-energy scoring (score = -ddG).
    """
    available = IMPORT_AVAILABLE
    name: str = "PottsMPNN"
    citations: list[str] = [
        # Beyond native sequence recovery: Improved modeling of the
        # sequence-energy landscape of protein structures
        "10.1073/pnas.2535494123",
    ]

    requires_target: bool = True
    # energies are evaluated on the fixed structure's residue graph
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
        max_tokens: int = 20000,
        device: str | None = None,
    ):
        """
        Instantiate a PottsMPNN model.

        Parameters
        ----------
        checkpoint_path
            PottsMPNN checkpoint; defaults to
            $POTTSMPNN_DATA_DIR/vanilla_model_weights/pottsmpnn_msa_20.pt.
            Paths containing "msa" use a 22-letter vocabulary, others 21
        structure_name
            Key into entity.structures if the entity has more than one
        max_tokens
            Token budget per energy batch (upstream inference.max_tokens)
        device
            Device to run on; defaults to cuda if available, else cpu
        """
        if not self.available:
            raise ImportError("torch could not be imported. Install it to use this model.")

        self.checkpoint_path = str(checkpoint_path or DATA_DIR / "vanilla_model_weights" / "pottsmpnn_msa_20.pt")
        self.vocab = 22 if "msa" in self.checkpoint_path else 21
        self.structure_name = structure_name
        self.max_tokens = max_tokens
        self.device = device if device is not None else (
            "cuda" if torch.cuda.is_available() else "cpu"
        )

        self._system: System | None = None
        self._pdb_dict: dict | None = None
        # float64 energy table [1, L, k, h, h], neighbour indices, wt sequence
        self._etab = None
        self._E_idx = None
        self._wt_seq: str | None = None
        # [start, end) of target positions (0-based) covered by the structure
        self._structure_range: tuple[int, int] | None = None

    @property
    def system(self) -> System | None:
        return self._system

    @property
    def ready(self) -> bool:
        return self._system is not None and self._etab is not None

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

    def _select_target_chain(self, target: Entity) -> Structure:
        """
        Select the target chain from the entity's structures.

        Parameters
        ----------
        target
            Target entity

        Returns
        -------
        Structure
            First chain of the selected structure
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

        # first chain is the target
        chain = list(ensure_sequence(structure))[0]
        if len(chain.chains()) != 1:
            raise ValueError(f"Expected a single-chain structure, got chains={chain.chains()}")
        return chain

    def _load_model(self):
        """
        Load the checkpoint into a PottsMPNN module.

        Returns
        -------
        PottsMPNN
            Model in eval mode on self.device
        """
        # hyperparameters as in upstream energy_prediction.py
        checkpoint = torch.load(self.checkpoint_path, map_location="cpu", weights_only=False)
        model = _PottsMPNN(
            ca_only=False, num_letters=self.vocab, vocab=self.vocab, node_features=128, edge_features=128,
            hidden_dim=128, potts_dim=400, num_encoder_layers=3, num_decoder_layers=3, k_neighbors=48,
            augment_eps=0.0,
        )
        missing, unexpected = model.load_state_dict(checkpoint["model_state_dict"], strict=False)
        if missing or unexpected:
            raise RuntimeError(
                f"Checkpoint {self.checkpoint_path} does not match PottsMPNN(vocab={self.vocab}): "
                f"missing={missing[:5]} unexpected={unexpected[:5]}"
            )
        model.eval()
        for param in model.parameters():
            param.requires_grad = False
        return model.to(self.device)

    def _cfg(self):
        """
        Build the subset of upstream's config read by get_etab/score_seqs.

        Returns
        -------
        SimpleNamespace
            Config object
        """
        return SimpleNamespace(
            dev=self.device,
            model=SimpleNamespace(vocab=self.vocab),
            inference=SimpleNamespace(ddG=True, mean_norm=False, max_tokens=self.max_tokens, filter=False),
        )

    def build(
        self,
        system: System,
        data: None = None,
        status_callback: StatusCallback | None = None,
    ) -> Self:
        self.can_model_or_raise(system, data)
        status_start(status_callback, "Building PottsMPNN model")

        self._system = system
        target = system[0]
        rep = "".join(target.rep)
        length = len(rep)
        first_index = target.first_index

        chain = self._select_target_chain(target)
        chain.represents(
            positions=list(range(first_index, first_index + length)),
            sequence=list(rep),
            allow_missing=True,
            raise_invalid=True,
        )

        pdb_dict = _structure_to_pdb_dict(chain)
        res_ids = chain.atom_array.res_id
        start = int(res_ids.min()) - first_index
        self._structure_range = (start, start + len(pdb_dict["seq"]))
        self._pdb_dict = pdb_dict
        model = self._load_model()
        with torch.no_grad():
            etab, E_idx, wt_seq = get_etab(model, [pdb_dict], self._cfg(), None)
        self._etab, self._E_idx, self._wt_seq = etab.double().cpu(), E_idx.cpu(), wt_seq
        # only the energy table is needed for scoring
        del model

        status_done(status_callback, "PottsMPNN model built")
        return self

    def score(
        self,
        instances: Sequence[SystemInstance],
        status_callback: StatusCallback | None = None,
    ) -> list[SystemInstance]:
        """
        Score sequences as E(target) - E(instance) under the target's Potts model.

        Parameters
        ----------
        instances
            Instances to score
        status_callback
            Optional status callback

        Returns
        -------
        list[SystemInstance]
            Instances with scores assigned; the target scores 0
        """
        self.ready_or_raise()
        self._validate_instances(instances)

        if len(instances) == 0:
            return []

        status_start(status_callback, "Scoring sequences")


        s_start, s_end = self._structure_range
        target_rep = np.asarray(self.system[0].rep)
        wt = self._wt_seq
        seqs = []
        for instance in instances:
            rep = np.asarray(instance[0].rep)
            seq = list(wt)
            for pos in np.flatnonzero(rep != target_rep):
                if not s_start <= pos < s_end:
                    raise ValueError(
                        f"Mutated position {pos + self.system[0].first_index} lies outside the "
                        "structure-covered range"
                    )
                seq[pos - s_start] = str(rep[pos]).upper()
            seqs.append("".join(seq))

        scores = -self._ddg(seqs)

        status_done(status_callback, "Scoring complete")

        return assign_scores_to_instances(instances, np.asarray(scores, dtype=float))

    def _ddg(self, seqs: list[str]) -> np.ndarray:
        """
        Compute ddG = E(seq) - E(wild type) as in upstream run_utils.score_seqs.

        Summed in float64 to avoid float32 rounding ties between mutants.

        Parameters
        ----------
        seqs
            Full sequences over the structure-covered range

        Returns
        -------
        np.ndarray
            ddG per sequence
        """
        etab = self._etab.to(self.device)
        E_idx = self._E_idx.to(self.device)
        all_seqs = etab_utils.seqs_to_tensor([self._wt_seq, *seqs], dev="cpu").unsqueeze(0)
        n = all_seqs.shape[1]
        batch_size = int(self.max_tokens / etab.size(1)) if etab.size(1) * n > self.max_tokens else n
        energies = []
        with torch.no_grad():
            for b in range(0, n, max(batch_size, 1)):
                batch = all_seqs[:, b:b + batch_size].to(self.device)
                e, _, _ = etab_utils.calc_eners(etab, E_idx, batch, None, filter=False)
                energies.append(e.cpu())
        energies = torch.cat(energies, 1).squeeze(0).numpy()
        return energies[1:] - energies[0]
