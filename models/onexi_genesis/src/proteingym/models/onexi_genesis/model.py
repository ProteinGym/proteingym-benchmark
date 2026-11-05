"""
ONE XI Genesis zero-shot sequence & biophysical context scorer (evedesign-wrapped).
Runs 100% offline (--network none) with wild-type anchored profile scoring (WT = 0.0).
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

GAP = "-"

# Standardized biophysical scales (Kyte-Doolittle hydropathy, normalized side-chain volume,
# net formal charge at pH 7.4, and alpha/beta packing propensity)
_AA_BIOPHYS: dict[str, tuple[float, float, float, float]] = {
    "A": ( 1.8, 0.15,  0.0, 1.42),
    "C": ( 2.5, 0.28,  0.0, 0.70),
    "D": (-3.5, 0.30, -1.0, 1.01),
    "E": (-3.5, 0.43, -1.0, 1.51),
    "F": ( 2.8, 0.68,  0.0, 1.13),
    "G": (-0.4, 0.00,  0.0, 0.57),
    "H": (-3.2, 0.52,  0.5, 1.00),
    "I": ( 4.5, 0.56,  0.0, 1.08),
    "K": (-3.9, 0.58,  1.0, 1.16),
    "L": ( 3.8, 0.56,  0.0, 1.21),
    "M": ( 1.9, 0.55,  0.0, 1.45),
    "N": (-3.5, 0.35,  0.0, 0.67),
    "P": (-1.6, 0.32,  0.0, 0.57),
    "Q": (-3.5, 0.47,  0.0, 1.11),
    "R": (-4.5, 0.65,  1.0, 0.98),
    "S": (-0.8, 0.18,  0.0, 0.77),
    "T": (-0.7, 0.32,  0.0, 0.83),
    "V": ( 4.2, 0.42,  0.0, 1.06),
    "W": (-0.9, 0.88,  0.0, 1.08),
    "Y": (-1.3, 0.71,  0.0, 0.69),
}


def load_matrix(name: str) -> dict[tuple[str, str], float]:
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


class OneXIGenesisScorer(
    BaseModel, Scorer, MutationScorer, ConditionalMutationScorer
):
    """
    Zero-shot biophysical and sequence-context-conditioned variant scorer,
    strictly anchored so the wild-type sequence scores 0.0.
    """

    name: str = "onexi_genesis"
    citations: list[str] = [
        "10.1073/pnas.89.22.10915",
    ]

    requires_target: bool = True
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
        context_window: int = 7,
        context_weight: float = 0.35,
        gap_penalty: float = -4.5,
    ):
        self.matrix_name = matrix_name
        self.context_window = max(1, int(context_window))
        self.context_weight = float(context_weight)
        self.gap_penalty = float(gap_penalty)

        self._system: System | None = None
        self._profile: np.ndarray | None = None
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
        self.can_model_or_raise(system, data)
        status_start(status_callback, f"Building {self.name} profile")

        self._system = system
        target = system[0]
        rep = "".join(target.rep)
        seq_len = len(rep)

        matrix = load_matrix(self.matrix_name)
        self._alphabet = [*VALID_AA_SORTED, GAP]

        # Precompute per-position biophysical features for local window conditioning
        feats = np.array(
            [_AA_BIOPHYS.get(aa, (0.0, 0.35, 0.0, 1.0)) for aa in rep],
            dtype=float,
        )
        half_w = self.context_window // 2

        profile = np.full((seq_len, len(self._alphabet)), np.nan, dtype=float)
        for pos, wt in enumerate(rep):
            if (wt, wt) not in matrix:
                raise ValueError(
                    f"Target residue {wt!r} at position {pos} is not in the "
                    f"{self.matrix_name} alphabet"
                )
            self_score = matrix[(wt, wt)]
            wt_hydro, wt_vol, wt_chg, wt_prop = _AA_BIOPHYS.get(
                wt, (0.0, 0.35, 0.0, 1.0)
            )

            lo = max(0, pos - half_w)
            hi = min(seq_len, pos + half_w + 1)
            local_mean_hydro = float(np.mean(feats[lo:hi, 0]))
            burial_factor = 1.0 + 0.12 * max(0.0, local_mean_hydro)

            for col, symbol in enumerate(self._alphabet):
                if symbol == GAP:
                    profile[pos, col] = self.gap_penalty * burial_factor
                elif symbol == wt:
                    profile[pos, col] = 0.0
                elif (wt, symbol) in matrix:
                    base_diff = min(0.0, matrix[(wt, symbol)] - self_score)
                    mut_hydro, mut_vol, mut_chg, mut_prop = _AA_BIOPHYS.get(
                        symbol, (0.0, 0.35, 0.0, 1.0)
                    )
                    # Non-positive biophysical mismatch penalty anchored at 0 for WT
                    d_hydro = abs(wt_hydro - mut_hydro) / 9.0
                    d_vol = abs(wt_vol - mut_vol)
                    d_chg = abs(wt_chg - mut_chg) / 2.0
                    d_pro = 1.25 if (wt == "P" or symbol == "P") else abs(wt_prop - mut_prop)
                    biophys_penalty = -self.context_weight * burial_factor * (
                        0.40 * d_hydro + 0.30 * d_vol + 0.15 * d_chg + 0.15 * d_pro
                    )
                    profile[pos, col] = min(-1e-6, base_diff + biophys_penalty)

        wt_cols = [self._alphabet.index(wt) for wt in rep]
        wt_scores = profile[np.arange(seq_len), wt_cols]
        if not np.allclose(wt_scores, 0.0):
            raise RuntimeError(f"{self.name} profile is not anchored at the wild type")

        aa_profile = profile[:, : len(VALID_AA_SORTED)]
        if np.nanmax(aa_profile) > 0.0:
            raise RuntimeError(f"{self.name} scores some substitution above the wild type")

        self._profile = profile
        by_char = np.zeros((seq_len, 256), dtype=float)
        for col, symbol in enumerate(self._alphabet):
            by_char[:, ord(symbol)] = np.nan_to_num(profile[:, col])
        self._profile_by_char = by_char

        status_done(status_callback, "Profile complete")
        return self

    def positions(
        self,
        instance: SystemInstance | None = None,
    ) -> list[tuple[int, int]]:
        self.ready_or_raise()
        first_index = self.system[0].first_index
        return [(0, pos + first_index) for pos in range(self._profile.shape[0])]

    def score(
        self,
        instances: Sequence[SystemInstance],
        status_callback: StatusCallback | None = None,
    ) -> list[SystemInstance]:
        self.ready_or_raise()
        self._validate_instances(instances)

        if len(instances) == 0:
            return []

        status_start(status_callback, "Scoring sequences")

        length, _ = self._profile.shape
        pos_index = np.arange(length)
        chunk = max(1, 4_000_000 // max(length, 1))
        scores = np.empty(len(instances), dtype=float)
        for start in range(0, len(instances), chunk):
            batch = instances[start : start + chunk]
            buf = "".join(
                "".join(str(symbol) for symbol in instance[0].rep)
                for instance in batch
            ).encode("ascii", "replace")
            codes = np.frombuffer(buf, dtype=np.uint8).reshape(len(batch), length)
            scores[start : start + len(batch)] = self._profile_by_char[
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
        self.ready_or_raise()
        self._validate_instances([instance])

        if entity is not None and entity != 0:
            raise ValueError(f"{self.name} scorer only models entity 0")

        if positions is not None:
            if entity is None:
                raise ValueError(
                    "Parameter entity must be explicitly specified if using parameter positions"
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
        self.ready_or_raise()

        if not len(instances) == len(entities) == len(positions):
            raise ValueError(
                "Sequences for instances, entities and positions must all have same length"
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
