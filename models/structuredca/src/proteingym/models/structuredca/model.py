"""
Wrapper class around the StructureDCA model

It is assumed the provided System already carries an aligned MSA (entity.sequences, a3m 
format, target sequence first) and one attached 3D structure (entity.structures) 
"""
import tempfile
from pathlib import Path
from typing import Any, Self, Sequence

import numpy as np
import pandas as pd

from evedesign.model import BaseModel, Scorer, MutationScorer, assign_scores_to_instances
from evedesign.system import Entity, System, SystemInstance
from evedesign.types import StatusCallback
from evedesign.utils import status_done, status_start

try:
    from structuredca import StructureDCA as _StructureDCACore
    IMPORT_AVAILABLE = True
except ImportError:
    IMPORT_AVAILABLE = False


_RESERVED_KWARGS = ("msa_path", "pdb_path", "chains", "init_dca", "homomeric_chains")


class StructureDCA(BaseModel, Scorer, MutationScorer):
    """
    Wrapper around structuredca.StructureDCA (structure-informed Potts model).
    """
    available = IMPORT_AVAILABLE
    name: str = "StructureDCA"
    citations: list[str] = [
        "10.64898/2026.03.27.714804v1",
    ]

    # core properties
    requires_target: bool = True
    requires_fixed_length: bool = True
    handles_deletions: bool = False
    handles_insertions: bool = False
    requires_gpu: bool = False
    supports_gpu: bool = False
    supports_gpu_parallel: bool = False
    supports_cpu_parallel: bool = True

    required_entity_attributes: list[str] | None = ["sequences", "structures"]
    optional_entity_attributes: list[str] | None = None

    def __init__(
        self,
        reweight_by_rsa: bool = False,
        **structuredca_kwargs: Any,
    ):
        """
        Initialise the StructureDCA wrapper.

        Parameters
        ----------
        reweight_by_rsa
            If True, use StructureDCA's RSA-complement reweighting. Default False
        **structuredca_kwargs
            Additional keyword arguments forwarded directly to structuredca.StructureDCA
            (ex. distance_cutoff, lambda_h, lambda_J, min_seqid, weights_seqid, num_threads,
            max_iterations, solver, ...). msa_path, pdb_path, chains and init_dca are always
            derived/fixed internally and can't be passed here.
        """
        if not self.available:
            raise ImportError(
                "structuredca package could not be imported. Install w/ pip install structuredca"
            )

        reserved_given = [key for key in _RESERVED_KWARGS if key in structuredca_kwargs]
        if reserved_given:
            raise ValueError(
                f"The following arguments are derived internally from the System and must not "
                f"be passed to StructureDCA(): {reserved_given}"
            )

        structuredca_kwargs.setdefault("verbose", False)

        self.reweight_by_rsa = reweight_by_rsa
        self.structuredca_kwargs = structuredca_kwargs

        self._system: System | None = None
        # underlying fitted structuredca.StructureDCA instance; pickles directly with the wrapper
        self.model: "_StructureDCACore | None" = None

    @property
    def system(self) -> System | None:
        return self._system

    @property
    def ready(self) -> bool:
        return self._system is not None and self.model is not None

    @classmethod
    def can_model(cls, system: System, data: Any = None) -> tuple[bool, str]:
        if data is not None:
            return False, "Model does not support a data parameter (must be None)"

        if len(system) != 1 or system[0].type != "protein":
            return False, "Can only handle a single-component protein system"

        target = system[0]
        if not target.defined_sequence():
            return False, "Entity must have a defined rep sequence"

        if target.sequences is None or len(target.sequences.seqs) == 0:
            return False, "Must provide an MSA (entity.sequences) for model inference"

        if not target.sequences.aligned:
            return False, "Provided sequences must be aligned"

        if target.sequences.format_ != "a3m":
            return False, "Only a3m-format sequences are currently supported"

        if not target.structures or len(target.structures) != 1:
            return False, (
                "Entity must have exactly one attached structure (entity.structures); "
                "homomeric/multi-structure entities are not supported"
            )

        return True, ""

    @staticmethod
    def _target_structure(target: Entity):
        """
        Extract the single structure/chain to hand to StructureDCA from entity.structures
        (already checked by can_model() to contain exactly one entry).
        """
        (structure,) = target.structures.values()
        if isinstance(structure, list):
            if len(structure) != 1:
                raise ValueError(
                    "StructureDCA does not support homomeric/multi-copy structures; "
                    "provide exactly one structure chain"
                )
            structure = structure[0]

        chains = structure.chains()
        if len(chains) != 1:
            raise ValueError(
                f"Structure must contain exactly one chain, found chains {chains}"
            )

        return structure, chains[0]

    def build(
        self,
        system: System,
        data: None = None,
        status_callback: StatusCallback | None = None,
    ) -> Self:
        self.can_model_or_raise(system, data)

        status_start(status_callback, "Fitting StructureDCA model")

        target = system[0]

        target_rep = "".join(target.rep)
        msa_seqs = target.sequences.to_a3m().remove_inserts().seqs
        if msa_seqs[0].seq != target_rep:
            raise ValueError(
                "First sequence of entity.sequences (match-state) must equal the "
                "target/focus sequence (entity.rep)"
            )

        structure, chain_id = self._target_structure(target)

        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_dir = Path(tmp_dir)

            msa_path = tmp_dir / "msa.fasta"
            with open(msa_path, "w") as f:
                for i, seq in enumerate(msa_seqs):
                    header = seq.id_ if seq.id_ else f"seq{i}"
                    f.write(f">{header}\n{seq.seq}\n")

            pdb_path = tmp_dir / "structure.pdb"
            structure.to_file(str(pdb_path), format="pdb")

            self.model = _StructureDCACore(
                msa_path=str(msa_path),
                pdb_path=str(pdb_path),
                chains=chain_id,
                **self.structuredca_kwargs,
            )

        self._system = system

        status_done(status_callback, "StructureDCA model finished fitting")

        return self

    def score(
        self,
        instances: Sequence[SystemInstance],
        status_callback: StatusCallback | None = None,
    ) -> list[SystemInstance]:
        """
        Score full sequences by (-1) * StructureDCA energy (see module
        docstring for the sign convention).
        """
        self.ready_or_raise()
        self._validate_instances(instances)

        if len(instances) == 0:
            return []

        status_start(status_callback, "Scoring sequences")

        scores = [
            -float(
                self.model.eval_sequence(
                    "".join(str(c) for c in instance[0].rep),
                    reweight_by_rsa=self.reweight_by_rsa,
                )
            )
            for instance in instances
        ]

        status_done(status_callback, "Scoring complete")

        return assign_scores_to_instances(instances, scores)

    def single_mutation_scan(
        self,
        instance: SystemInstance,
        entity: int | None = None,
        positions: Sequence[int] | None = None,
        status_callback: StatusCallback | None = None,
    ) -> pd.DataFrame:
        """
        Compute all single substitutions to an instance via StructureDCA's batched
        eval_mutations_table().
        """
        self.ready_or_raise()
        self._validate_instances([instance])

        if entity is not None and entity != 0:
            raise ValueError("StructureDCA only models entity 0")

        if positions is not None:
            if entity is None:
                raise ValueError(
                    "Parameter entity must be explicitly specified if using parameter positions"
                )
            self.valid_positions(positions, instance, entity, raise_invalid=True)

        status_start(status_callback, "Computing single mutation scan")

        first_index = self.system[0].first_index
        seq_str = "".join(str(c) for c in instance[0].rep)
        alphabet = self.system[0].alphabet(include_gap=False, include_inserts=False)
        n_symbols = len(alphabet)
        pos_filter = set(positions) if positions is not None else None

        mutation_strs = []
        row_index = []
        for i, wt in enumerate(seq_str):
            evc_pos = i + first_index
            if pos_filter is not None and evc_pos not in pos_filter:
                continue
            fasta_pos = i + 1
            row_index.append((0, evc_pos, wt))
            mutation_strs.extend(f"{wt}{fasta_pos}{mt}" for mt in alphabet)

        score_key = "RSA*StructureDCA" if self.reweight_by_rsa else "StructureDCA"
        results = self.model.eval_mutations_table(
            mutations=mutation_strs,
            background_sequence=seq_str,
            round_digit=None,
            log_output_sample=False,
        )

        # flip sign: StructureDCA dE > 0 = destabilizing, evedesign wants score > 0 = beneficial
        scores = -np.array([row[score_key] for row in results], dtype=float)
        rows = scores.reshape(-1, n_symbols)

        df = pd.DataFrame(
            rows,
            columns=alphabet,
            index=pd.MultiIndex.from_tuples(row_index, names=["entity", "pos", "ref"]),
        )

        merged_alphabet = Entity.merge_alphabet_symbols([
            self.system[0].alphabet(
                include_gap=self.handles_deletions,
                include_inserts=self.handles_insertions,
            )
        ])
        df = df.reindex(merged_alphabet, axis=1)

        status_done(status_callback, "Single mutation scan complete")

        return df
