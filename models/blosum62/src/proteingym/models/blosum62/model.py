"""
Wrapper around a fixed amino-acid substitution matrix (BLOSUM62 by default, via Biopython)
"""
from typing import Any, Self, Sequence

import numpy as np
import pandas as pd
from Bio.Align import substitution_matrices

from evedesign.constants import VALID_AA_SORTED
from evedesign.model import (
    BaseModel,
    ConditionalMutationScorer,
    MutationScorer,
    Scorer,
    assign_scores_to_instances,
)
from evedesign.system import Entity, System, SystemInstance
from evedesign.types import StatusCallback
from evedesign.utils import status_done, status_start

# Gap symbol evedesign uses to encode a deletion in an instance rep
GAP = "-"


def load_matrix(name: str) -> dict[tuple[str, str], float]:
    """
    Load a named substitution matrix from Biopython's bundled NCBI data files.

    Parameters
    ----------
    name
        Matrix name accepted by Biopython's `substitution_matrices.load()`

    Returns
    -------
    dict[tuple[str, str], float]
        Matrix scores keyed by (residue, residue) pair

    Raises
    ------
    ValueError
        If Biopython does not provide a matrix with this name
    """
    available = substitution_matrices.load()
    if name not in available:
        raise ValueError(
            f"Unknown substitution matrix {name!r}; Biopython provides "
            f"{sorted(available)}"
        )
    matrix = substitution_matrices.load(name)
    return {
        (str(a), str(b)): float(matrix[a, b])
        for a in matrix.alphabet
        for b in matrix.alphabet
    }


class SubstitutionMatrixScorer(
    BaseModel, Scorer, MutationScorer, ConditionalMutationScorer
):
    """
    Score variants by a fixed substitution matrix, anchored so the wild type scores 0.
    """

    name: str = "BLOSUM62"
    citations: list[str] = [
        # Amino acid substitution matrices from protein blocks
        "10.1073/pnas.89.22.10915",
    ]

    requires_target: bool = True
    # Deletions are in-place gaps, so fixed length admits them but rejects insertions
    requires_fixed_length: bool = True
    handles_deletions: bool = True
    handles_insertions: bool = False
    requires_gpu: bool = False
    supports_gpu: bool = False
    supports_gpu_parallel: bool = False
    supports_cpu_parallel: bool = False

    required_entity_attributes: list[str] | None = None
    optional_entity_attributes: list[str] | None = None

    def __init__(
        self,
        matrix_name: str = "BLOSUM62",
        gap_penalty: float = -4.0,
    ):
        """
        Instantiate a substitution-matrix scorer.

        Parameters
        ----------
        matrix_name
            Any matrix name Biopython's `substitution_matrices.load()`
            provides (e.g. BLOSUM62, BLOSUM80, PAM250)
        gap_penalty
            Score contributed by a deleted position, relative to the
            wild-type residue
        """
        self.matrix_name = matrix_name
        self.gap_penalty = float(gap_penalty)

        self._system: System | None = None
        # (length, len(self._alphabet)) score profile built in build()
        self._profile: np.ndarray | None = None
        # the same profile indexed by byte value, for vectorized scoring
        self._profile_by_char: np.ndarray | None = None
        self._alphabet: list[str] | None = None

    @property
    def system(self) -> System | None:
        return self._system

    @property
    def ready(self) -> bool:
        return self._system is not None and self._profile is not None

    @classmethod
    def can_model(cls, system: System, data: Any = None) -> tuple[bool, str]:
        if data is not None:
            return False, "Model does not support a data parameter (must be None)"

        if len(system) != 1 or system[0].type != "protein":
            return False, "Can only handle a single-component protein system"

        if not system[0].defined_sequence():
            return False, "Entity must have a defined rep sequence"

        return True, ""

    def build(
        self,
        system: System,
        data: None = None,
        status_callback: StatusCallback | None = None,
    ) -> Self:
        """
        Build the wild-type-anchored per-position score profile.

        Parameters
        ----------
        system
            Single-entity protein system with a defined target sequence
        data
            Unused, must be None
        status_callback
            Optional progress callback

        Returns
        -------
        Self
            The built model
        """
        self.can_model_or_raise(system, data)
        status_start(status_callback, f"Building {self.matrix_name} profile")

        self._system = system
        target = system[0]
        rep = "".join(target.rep)

        matrix = load_matrix(self.matrix_name)
        # 20 standard amino acids plus the gap symbol for deletions
        self._alphabet = [*VALID_AA_SORTED, GAP]

        profile = np.full((len(rep), len(self._alphabet)), np.nan, dtype=float)
        for pos, wt in enumerate(rep):
            if (wt, wt) not in matrix:
                raise ValueError(
                    f"Target residue {wt!r} at position {pos} is not in the "
                    f"{self.matrix_name} alphabet"
                )
            self_score = matrix[(wt, wt)]
            for col, symbol in enumerate(self._alphabet):
                if symbol == GAP:
                    profile[pos, col] = self.gap_penalty
                elif (wt, symbol) in matrix:
                    profile[pos, col] = matrix[(wt, symbol)] - self_score

        # Sanity check for configurable matrices: WT scores 0, no substitution above it
        wt_cols = [self._alphabet.index(wt) for wt in rep]
        wt_scores = profile[np.arange(len(rep)), wt_cols]
        if not np.allclose(wt_scores, 0.0):
            raise RuntimeError(
                f"{self.matrix_name} profile is not anchored at the wild type"
            )
        aa_profile = profile[:, : len(VALID_AA_SORTED)]
        if np.nanmax(aa_profile) > 0.0:
            raise RuntimeError(
                f"{self.matrix_name} scores some substitution above the wild "
                "type; the anchoring assumption in the module docstring does "
                "not hold for this matrix"
            )

        self._profile = profile
        # Profile indexed by byte value for vectorized scoring; unknown symbols contribute 0
        by_char = np.zeros((len(rep), 256), dtype=float)
        for col, symbol in enumerate(self._alphabet):
            by_char[:, ord(symbol)] = np.nan_to_num(profile[:, col])
        self._profile_by_char = by_char

        status_done(status_callback, "Profile complete")
        return self

    def positions(
        self,
        instance: SystemInstance | None = None,
    ) -> list[tuple[int, int]]:
        """
        Return the modelled positions (all positions of entity 0).

        Parameters
        ----------
        instance
            Unused; the model is fixed-length

        Returns
        -------
        list[tuple[int, int]]
            (entity, position) tuples
        """
        self.ready_or_raise()
        first_index = self.system[0].first_index
        return [(0, pos + first_index) for pos in range(self._profile.shape[0])]

    def score(
        self,
        instances: Sequence[SystemInstance],
        status_callback: StatusCallback | None = None,
    ) -> list[SystemInstance]:
        """
        Score full sequences as the sum of per-position profile values.

        Parameters
        ----------
        instances
            Instances to score
        status_callback
            Optional progress callback

        Returns
        -------
        list[SystemInstance]
            Scored copies of the instances (wild type scores 0)
        """
        self.ready_or_raise()
        self._validate_instances(instances)

        if len(instances) == 0:
            return []

        status_start(status_callback, "Scoring sequences")

        length, _ = self._profile.shape
        pos_index = np.arange(length)
        # Chunked to bound memory on large variant sets
        chunk = max(1, 4_000_000 // max(length, 1))
        scores = np.empty(len(instances), dtype=float)
        for start in range(0, len(instances), chunk):
            batch = instances[start:start + chunk]
            buf = "".join(
                "".join(str(symbol) for symbol in instance[0].rep)
                for instance in batch
            ).encode("ascii", "replace")
            codes = np.frombuffer(buf, dtype=np.uint8).reshape(len(batch), length)
            scores[start:start + len(batch)] = self._profile_by_char[
                pos_index[None, :], codes
            ].sum(axis=1)

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
        Compute all single substitutions to an instance from the cached profile.

        Parameters
        ----------
        instance
            Instance to mutate
        entity
            Entity index (only 0 is supported)
        positions
            Optional subset of positions to scan; requires entity
        status_callback
            Optional progress callback

        Returns
        -------
        pd.DataFrame
            Scores relative to the instance's residue, indexed by (entity, pos, ref)
        """
        self.ready_or_raise()
        self._validate_instances([instance])

        if entity is not None and entity != 0:
            raise ValueError(
                f"{self.matrix_name} scorer only models entity 0"
            )

        if positions is not None:
            if entity is None:
                raise ValueError(
                    "Parameter entity must be explicitly specified if using "
                    "parameter positions"
                )
            self.valid_positions(positions, instance, entity, raise_invalid=True)

        status_start(status_callback, "Computing single mutation scan")

        first_index = self.system[0].first_index
        col = {symbol: i for i, symbol in enumerate(self._alphabet)}
        rep = instance[0].rep
        pos_filter = set(positions) if positions is not None else None

        rows, index_tuples = [], []
        for pos in range(self._profile.shape[0]):
            evc_pos = pos + first_index
            if pos_filter is not None and evc_pos not in pos_filter:
                continue
            ref_symbol = str(rep[pos])
            ref_score = (
                self._profile[pos, col[ref_symbol]]
                if ref_symbol in col
                else np.nan
            )
            rows.append(self._profile[pos, :] - ref_score)
            index_tuples.append((0, evc_pos, ref_symbol))

        df = pd.DataFrame(
            rows,
            columns=list(self._alphabet),
            index=pd.MultiIndex.from_tuples(
                index_tuples, names=["entity", "pos", "ref"]
            ),
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

    def score_conditional(
        self,
        instances: Sequence[SystemInstance],
        entities: Sequence[int],
        positions: Sequence[int],
        status_callback: StatusCallback | None = None,
    ) -> pd.DataFrame:
        """
        Return profile scores for one position per instance.

        The profile depends only on the wild-type residue, so rows do not depend
        on the rest of the instance's sequence.

        Parameters
        ----------
        instances
            Instances to score
        entities
            Entity index per instance
        positions
            Position per instance
        status_callback
            Optional progress callback

        Returns
        -------
        pd.DataFrame
            Profile rows indexed by (instance, entity, pos)
        """
        self.ready_or_raise()

        if not len(instances) == len(entities) == len(positions):
            raise ValueError(
                "Sequences for instances, entities and positions must all "
                "have same length"
            )

        self._validate_instances(instances)

        for instance, entity, pos in zip(instances, entities, positions):
            self.valid_positions(
                positions=[pos],
                instance=instance,
                entities=[entity],
                raise_invalid=True,
            )

        status_start(status_callback, "Computing conditional scores")

        first_index = self.system[0].first_index
        rows, index_tuples = [], []
        for inst_idx, (instance, entity, pos) in enumerate(
            zip(instances, entities, positions)
        ):
            rows.append(self._profile[int(pos) - first_index, :])
            index_tuples.append((inst_idx, int(entity), int(pos)))

        df = pd.DataFrame(
            rows,
            columns=list(self._alphabet),
            index=pd.MultiIndex.from_tuples(
                index_tuples, names=["instance", "entity", "pos"]
            ),
        )

        merged_alphabet = Entity.merge_alphabet_symbols([
            self.system[entity_idx].alphabet(
                include_gap=self.handles_deletions,
                include_inserts=self.handles_insertions,
            )
            for entity_idx in set(entities)
        ])
        df = df.reindex(merged_alphabet, axis=1)

        status_done(status_callback, "Conditional scoring complete")
        return df
