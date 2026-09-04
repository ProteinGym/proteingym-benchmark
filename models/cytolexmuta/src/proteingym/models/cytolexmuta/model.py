from __future__ import annotations

import tempfile
from collections.abc import Sequence
from pathlib import Path
from typing import Any, ClassVar, Self

import numpy as np
from evedesign.model import BaseModel, Scorer, assign_scores_to_instances
from evedesign.system import System, SystemInstance
from evedesign.types import StatusCallback
from evedesign.utils import status_done, status_start

from .runtime import RuntimeConfig, score_live


class Cytolexmuta(BaseModel, Scorer):
    """Label-free fusion of four independently computed mutation energy maps."""

    name: str = "cytolexmuta"
    citations: ClassVar[list[str]] = [
        "https://biohub.ai/papers/esm_protein.pdf",
        "10.1093/molbev/msz179",
        "10.1101/2022.04.10.487779",
        "10.1101/2024.04.15.589672",
    ]

    requires_target: bool = True
    requires_fixed_length: bool = True
    handles_deletions: bool = False
    handles_insertions: bool = False
    requires_gpu: bool = False
    supports_gpu: bool = True
    supports_gpu_parallel: bool = False
    supports_cpu_parallel: bool = False

    required_entity_attributes: ClassVar[list[str] | None] = [
        "sequences",
        "structures",
    ]
    optional_entity_attributes: list[str] | None = None

    def __init__(
        self,
        structure_name: str | None = None,
        esmc_model_id: str = "biohub/ESMC-600M",
        esmc_revision: str = "a7e82012c83126b9eedb055fea9fa84b6c02f094",
        prosst_model_id: str = "AI4Protein/ProSST-2048",
        prosst_revision: str = "e94ffee7846d7f55c1bf5efa8ec7372a336ac4b8",
        prosst_repo: str = "/opt/ProSST",
        gemme_path: str = "/opt/GEMME",
        jet_path: str = "/opt/JET2",
        device: str = "auto",
        position_batch_size: int = 8,
        sequence_batch_size: int = 8,
        max_residues: int = 1022,
        gemme_nseqs: int = 20000,
    ) -> None:
        self.structure_name = structure_name
        self.config = RuntimeConfig(
            esmc_model_id=esmc_model_id,
            esmc_revision=esmc_revision,
            prosst_model_id=prosst_model_id,
            prosst_revision=prosst_revision,
            prosst_repo=prosst_repo,
            gemme_path=gemme_path,
            jet_path=jet_path,
            device=device,
            position_batch_size=position_batch_size,
            sequence_batch_size=sequence_batch_size,
            max_residues=max_residues,
            gemme_nseqs=gemme_nseqs,
        )
        self._system: System | None = None
        self._reference: str | None = None
        self._msa_sequences: tuple[str, ...] | None = None
        self._structure: Any = None
        self._chain: str | None = None

    @property
    def system(self) -> System | None:
        return self._system

    @property
    def ready(self) -> bool:
        return all(
            value is not None
            for value in (
                self._system,
                self._reference,
                self._msa_sequences,
                self._structure,
                self._chain,
            )
        )

    @classmethod
    def can_model(cls, system: System, data: Any = None) -> tuple[bool, str]:
        if data is not None:
            return False, "Model does not support a data parameter (must be None)"
        if len(system) != 1 or system[0].type != "protein":
            return False, "Can only handle a single-component protein system"
        target = system[0]
        if not target.defined_sequence():
            return False, "Entity must have a defined representative sequence"
        if target.sequences is None or len(target.sequences.seqs) == 0:
            return False, "Must provide an MSA (entity.sequences)"
        if not target.sequences.aligned:
            return False, "Provided sequences must be aligned"
        if not target.structures:
            return False, "Must provide a structure (entity.structures)"
        return True, ""

    @staticmethod
    def _match_state_sequences(target: Any) -> tuple[str, tuple[str, ...]]:
        reference = "".join(target.rep)
        raw_sequences = target.sequences.to_a3m().remove_inserts().seqs
        sequences = tuple(sequence.seq.upper() for sequence in raw_sequences)
        bad = [
            index
            for index, sequence in enumerate(sequences)
            if len(sequence) != len(reference)
        ]
        if bad:
            raise ValueError(
                "MSA match-state length does not match the target sequence "
                f"for sequence index {bad[0]}"
            )
        if sequences[0] != reference:
            raise ValueError(
                "The first MSA sequence must equal the WT/reference sequence"
            )
        return reference, sequences

    def _select_structure(self, target: Any) -> tuple[Any, str]:
        structures = target.structures
        if self.structure_name is not None:
            if self.structure_name not in structures:
                raise ValueError(
                    f"structure_name={self.structure_name!r} not found; "
                    f"available keys: {list(structures)}"
                )
            structure = structures[self.structure_name]
        else:
            if len(structures) != 1:
                raise ValueError(
                    "Entity has more than one structure; set structure_name to one of "
                    f"{list(structures)}"
                )
            structure = next(iter(structures.values()))
        if isinstance(structure, list):
            if len(structure) != 1:
                raise ValueError(
                    "cytolexmuta supports a single-chain monomer structure"
                )
            structure = structure[0]
        chains = structure.chains()
        if len(chains) != 1:
            raise ValueError(
                "cytolexmuta supports a single-chain monomer structure; "
                f"received chains={chains}"
            )
        return structure, chains[0]

    def build(
        self,
        system: System,
        data: None = None,
        status_callback: StatusCallback | None = None,
    ) -> Self:
        self.can_model_or_raise(system, data)
        status_start(status_callback, "Preparing cytolexmuta inputs")
        target = system[0]
        reference, msa_sequences = self._match_state_sequences(target)
        structure, chain = self._select_structure(target)
        self._system = system
        self._reference = reference
        self._msa_sequences = msa_sequences
        self._structure = structure
        self._chain = chain
        status_done(status_callback, "cytolexmuta inputs ready")
        return self

    def score(
        self,
        instances: Sequence[SystemInstance],
        status_callback: StatusCallback | None = None,
    ) -> list[SystemInstance]:
        self.ready_or_raise()
        self._validate_instances(instances)
        if not instances:
            return []
        status_start(status_callback, "Scoring with ESM-C, ProSST, GEMME, and ESM-IF1")
        sequences = tuple("".join(instance[0].rep) for instance in instances)
        with tempfile.TemporaryDirectory(prefix="cytolexmuta_") as temporary:
            pdb_path = Path(temporary) / "structure.pdb"
            self._structure.to_file(str(pdb_path), format="pdb")
            scores = score_live(
                reference=self._reference,
                sequences=sequences,
                msa_sequences=self._msa_sequences,
                pdb_path=pdb_path,
                chain=self._chain,
                first_index=self._system[0].first_index,
                config=self.config,
            )
        if scores.shape != (len(instances),) or not np.isfinite(scores).all():
            raise ValueError("cytolexmuta returned invalid scores")
        status_done(status_callback, "cytolexmuta scoring complete")
        return assign_scores_to_instances(instances, scores)
