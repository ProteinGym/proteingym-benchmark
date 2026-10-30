"""
Wrapper class around Kermut (https://github.com/florisvdf/kermut-package), a
Gaussian process with a composite structure/sequence kernel and an ESM2
zero-shot mean function
"""
import tempfile
from importlib import resources
from pathlib import Path
from typing import Self, Sequence

import numpy as np
import torch
from loguru import logger
from omegaconf import OmegaConf

from evedesign.dataset import LabeledInstanceDataset
from evedesign.model import Scorer, SupervisedBaseModel, assign_scores_to_instances
from evedesign.system import System, SystemInstance
from evedesign.types import DeviceType, StatusCallback
from evedesign.utils import model_param_context, status_done, status_start

from esm import pretrained

from kermut.kermut.constants import ALPHABET
from kermut.kermut.data import Tokenizer, prepare_GP_kwargs
from kermut.kermut.gp import instantiate_gp, optimize_gp
from kermut.pg_model.scripts.conditionals import (
    extract_proteinmpnn_conditional_probabilities,
)
from kermut.pg_model.scripts.coords import extract_3d_coords
from kermut.pg_model.scripts.process_conditionals import process_probabilities


# name used for kermut's per-dataset artifact files inside the build tempdir
_ARTIFACT_NAME = "kermut"

# ESM2 context window (1024 tokens - BOS/EOS), as in kermut's embedding script
_ESM_MAX_LENGTH = 1022


class kermut(SupervisedBaseModel, Scorer):
    """
    Kermut Gaussian process regression on substitution variants of a single
    protein, using the composite kernel and ESM2 zero-shot mean from the paper.
    """
    name: str = "kermut"
    citations: list[str] = [
        # Kermut: Composite kernel regression for protein variant effects
        "10.48550/arXiv.2407.00002",
        # Robust deep learning-based protein sequence design using ProteinMPNN
        "10.1126/science.add2187",
        # Evolutionary-scale prediction of atomic-level protein structure with a
        # language model (ESM2)
        "10.1126/science.ade2574",
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
        n_steps: int = 150,
        lr: float = 0.1,
        standardize: bool = True,
        seed: int = 2024,
        esm_model_name: str = "esm2_t33_650M_UR50D",
        batch_size: int = 16,
        structure_name: str | None = None,
        target_name: str | None = None,
        device: DeviceType = "cpu",
    ):
        """
        Instantiate a Kermut model.

        Parameters
        ----------
        n_steps
            Number of AdamW steps used to maximise the GP marginal likelihood
        lr
            Learning rate of the GP hyperparameter optimisation
        standardize
            Standardize training labels before fitting; predictions are mapped
            back to the original label scale
        seed
            Random seed set before fitting the GP
        esm_model_name
            fair-esm model used for the sequence embeddings and the zero-shot
            mean (kermut's default configuration uses the 650M ESM2 model)
        batch_size
            Number of sequences per ESM2 forward pass
        structure_name
            Key into the entity's structures mapping to select which structure
            to use, if more than one is present
        target_name
            Label series to fit; may be None if the dataset has a single series
        device
            "cuda" to run ESM2 and the GP on GPU, falls back to CPU if CUDA is
            not available
        """
        self.n_steps = n_steps
        self.lr = lr
        self.standardize = standardize
        self.seed = seed
        self.esm_model_name = esm_model_name
        self.batch_size = batch_size
        self.structure_name = structure_name
        self.target_name = target_name

        if device == "cuda" and not torch.cuda.is_available():
            logger.warning("CUDA requested but not available, running Kermut on CPU")
            device = "cpu"
        self.device = device

        self._system: System | None = None
        self._esm_model = None
        self._esm_alphabet = None
        # (length x vocab) masked-marginal ESM2 log-probabilities of the
        # reference sequence, used for the zero-shot mean function
        self._wt_log_probs: torch.Tensor | None = None
        self._gp = None
        self._likelihood = None
        self._y_mean = 0.0
        self._y_std = 1.0

    @property
    def system(self) -> System | None:
        return self._system

    @property
    def ready(self) -> bool:
        return self._system is not None and self._gp is not None

    @classmethod
    def can_model(cls, system: System, data: LabeledInstanceDataset) -> tuple[bool, str]:
        if not isinstance(data, LabeledInstanceDataset):
            return False, "Model requires a LabeledInstanceDataset as data parameter"

        if len(system) != 1 or system[0].type != "protein":
            return False, "Can only handle a single-component protein system"

        target = system[0]
        if not target.defined_sequence():
            return False, "Entity must have a defined rep sequence"

        target_rep = "".join(target.rep)
        if len(target_rep) > _ESM_MAX_LENGTH:
            return False, (
                f"Sequence length ({len(target_rep)}) exceeds maximum allowed "
                f"({_ESM_MAX_LENGTH})"
            )

        if set(target_rep) - set(ALPHABET):
            return False, "Target sequence must only contain the 20 standard amino acids"

        if not target.structures:
            return False, "Must provide a structure (entity.structures) for the structure kernel"

        return True, ""

    def _select_structure(self, target):
        """
        Select which structure to use from the entity's structures mapping (see
        structure_name in __init__'s docstring)
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

        if isinstance(structure, list):
            if len(structure) != 1:
                raise ValueError(
                    "Kermut only supports a single-chain monomer structure, got "
                    f"a list of {len(structure)} structures"
                )
            structure = structure[0]

        chains = structure.chains()
        if len(chains) != 1:
            raise ValueError(
                f"Kermut only supports a single-chain monomer structure, got chains={chains}"
            )

        return structure

    def _pseudo_if_reference(self, seq: str) -> str:
        """
        Kermut's structure kernel requires every sequence to carry at least one
        mutation, so the reference itself is replaced by a pseudo variant with
        the final residue mutated (same convention as kermut's
        add_pseudo_if_variant_matches_reference)
        """
        reference = "".join(self._system[0].rep)
        if seq != reference:
            return seq
        replacement_aa = "A" if reference[-1] == "G" else "G"
        return reference[:-1] + replacement_aa

    def _load_esm(self):
        if self._esm_model is not None:
            return
        model, alphabet = pretrained.load_model_and_alphabet(self.esm_model_name)
        self._esm_model = model.eval().to(self.device)
        self._esm_alphabet = alphabet

    def _delete_esm(self):
        self._esm_model = None
        if self.device == "cuda":
            torch.cuda.empty_cache()

    def _esm_wt_log_probs(self, reference: str) -> torch.Tensor:
        """
        Masked-marginal ESM2 log-probabilities for every position of the
        reference sequence (kermut's extract_esm2_zero_shots)
        """
        _, _, tokens = self._esm_alphabet.get_batch_converter()([("wt", reference)])
        positions = list(range(1, len(reference) + 1))  # skip BOS

        log_probs = []
        with torch.no_grad():
            for start in range(0, len(positions), self.batch_size):
                batch_pos = positions[start:start + self.batch_size]
                masked = tokens.repeat(len(batch_pos), 1)
                for row, pos in enumerate(batch_pos):
                    masked[row, pos] = self._esm_alphabet.mask_idx
                logits = self._esm_model(masked.to(self.device))["logits"]
                for row, pos in enumerate(batch_pos):
                    log_probs.append(torch.log_softmax(logits[row, pos], dim=-1).cpu())

        return torch.stack(log_probs)

    def _esm_embeddings(self, seqs: Sequence[str]) -> torch.Tensor:
        """
        Mean-pooled final-layer ESM2 embeddings (kermut's extract_esm2_embeddings)
        """
        batch_converter = self._esm_alphabet.get_batch_converter()
        repr_layer = self._esm_model.num_layers

        embeddings = []
        with torch.no_grad():
            for start in range(0, len(seqs), self.batch_size):
                batch = [(str(i), s) for i, s in enumerate(seqs[start:start + self.batch_size])]
                _, _, tokens = batch_converter(batch)
                out = self._esm_model(tokens.to(self.device), repr_layers=[repr_layer])
                reps = out["representations"][repr_layer].cpu()
                for row, (_, s) in enumerate(batch):
                    embeddings.append(reps[row, 1:len(s) + 1].mean(dim=0))

        return torch.stack(embeddings)

    def _zero_shot(self, seqs: Sequence[str]) -> torch.Tensor:
        """
        Additive ESM2 masked-marginal score of each sequence's substitutions
        relative to the reference (kermut's _label_row)
        """
        reference = "".join(self._system[0].rep)
        get_idx = self._esm_alphabet.get_idx
        scores = [
            sum(
                (self._wt_log_probs[pos, get_idx(mt)] - self._wt_log_probs[pos, get_idx(wt)]).item()
                for pos, (wt, mt) in enumerate(zip(reference, seq))
                if wt != mt
            )
            for seq in seqs
        ]
        return torch.tensor(scores, dtype=torch.float32)

    def _featurize(self, instances: Sequence[SystemInstance]) -> tuple[torch.Tensor, ...]:
        """
        Build kermut's GP inputs (one-hot tokens, ESM2 embeddings, zero-shot
        scores) for a set of instances
        """
        seqs = [self._pseudo_if_reference("".join(inst[0].rep)) for inst in instances]
        with model_param_context(self._load_esm, self._delete_esm, keep_model=False):
            x_embed = self._esm_embeddings(seqs)
        x_toks = Tokenizer()(seqs)
        x_zero_shot = self._zero_shot(seqs)
        return tuple(x.to(self.device) for x in (x_toks, x_embed, x_zero_shot))

    def build(
        self,
        system: System,
        data: LabeledInstanceDataset,
        status_callback: StatusCallback | None = None,
    ) -> Self:
        self.can_model_or_raise(system, data)

        status_start(status_callback, "Fitting Kermut model")

        self._system = system
        self._gp = None
        target = system[0]
        reference = "".join(target.rep)

        instances, values, _, _ = data.select(self.target_name, drop_missing=True)
        self._validate_instances(instances)

        # kermut drops duplicate sequences before fitting, keeping the first
        seen = set()
        keep = []
        for i, inst in enumerate(instances):
            seq = "".join(inst[0].rep)
            if seq not in seen:
                seen.add(seq)
                keep.append(i)
        instances = [instances[i] for i in keep]
        values = [values[i] for i in keep]

        # kermut's default (paper) kernel configuration; GP inputs are built
        # in memory, so only the structure artifact paths are filled in below
        kernel_cfg = OmegaConf.load(
            resources.files("kermut").joinpath("kermut/hydra_configs/kernel/kermut.yaml")
        )

        structure = self._select_structure(target)

        torch.set_default_dtype(torch.float32)
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            pdb_path = tmp / "structure.pdb"
            structure.to_file(str(pdb_path), format="pdb")

            coords_dir = tmp / "coords"
            conditional_probs_dir = tmp / "conditional_probs"

            extract_3d_coords(
                dataset_name=_ARTIFACT_NAME,
                pdb_file=str(pdb_path),
                coords_dir=str(coords_dir),
            )
            extract_proteinmpnn_conditional_probabilities(
                pdb_file=str(pdb_path),
                dataset_name=_ARTIFACT_NAME,
                conditional_probs_dir=str(conditional_probs_dir),
            )
            process_probabilities(
                dataset_name=_ARTIFACT_NAME,
                reference_sequence=reference,
                pdb_file=str(pdb_path),
                conditional_probs_dir=str(conditional_probs_dir),
            )

            cfg = OmegaConf.create({
                "kernel": kernel_cfg,
                "use_gpu": self.device == "cuda",
                "preferential": False,
                "data": {
                    "paths": {
                        "coords": str(coords_dir),
                        "conditional_probs": str(conditional_probs_dir),
                    }
                },
            })
            gp_inputs = prepare_GP_kwargs(
                cfg, _ARTIFACT_NAME, reference, dtype=torch.get_default_dtype()
            )

        if "coords" in gp_inputs and len(gp_inputs["coords"]) != len(reference):
            raise ValueError(
                f"Structure has {len(gp_inputs['coords'])} CA atoms but the target "
                f"sequence has {len(reference)} residues"
            )
        gp_inputs = {
            k: v.to(self.device) if isinstance(v, torch.Tensor) else v
            for k, v in gp_inputs.items()
        }

        # the zero-shot mean only depends on the reference, compute it once
        with model_param_context(self._load_esm, self._delete_esm, keep_model=False):
            self._wt_log_probs = self._esm_wt_log_probs(reference)

        train_inputs = self._featurize(instances)

        y = torch.tensor(values, dtype=torch.get_default_dtype())
        if self.standardize and len(y) > 1 and y.std() > 0:
            self._y_mean, self._y_std = y.mean().item(), y.std().item()
        else:
            self._y_mean, self._y_std = 0.0, 1.0
        y = ((y - self._y_mean) / self._y_std).to(self.device)

        torch.manual_seed(self.seed)
        np.random.seed(self.seed)

        gp, likelihood = instantiate_gp(
            cfg=cfg,
            train_inputs=train_inputs,
            train_targets=y,
            gp_inputs=gp_inputs,
        )
        self._gp, self._likelihood = optimize_gp(
            gp=gp,
            likelihood=likelihood,
            train_inputs=train_inputs,
            train_targets=y,
            lr=self.lr,
            n_steps=self.n_steps,
            progress_bar=False,
        )

        status_done(status_callback, "Kermut model finished fitting")

        return self

    def score(
        self,
        instances: Sequence[SystemInstance],
        status_callback: StatusCallback | None = None,
    ) -> list[SystemInstance]:
        """
        Score instances with the GP posterior predictive mean, mapped back to the
        training label scale. Higher scores follow the orientation of the
        training labels.
        """
        self.ready_or_raise()
        self._validate_instances(instances)

        if len(instances) == 0:
            return []

        status_start(status_callback, "Scoring sequences")

        x_test = self._featurize(instances)

        self._gp.eval()
        self._likelihood.eval()
        with torch.no_grad():
            pred = self._likelihood(self._gp(*x_test))
            mean = pred.mean.detach().cpu().numpy()

        scores = mean * self._y_std + self._y_mean

        status_done(status_callback, "Scoring complete")

        return assign_scores_to_instances(instances, scores.astype(float))
