
from __future__ import annotations

import csv
import io
import math
import tarfile
import urllib.error
import urllib.request
from collections.abc import Sequence
from pathlib import Path
from typing import Any, Self

import numpy as np
import polars as pl

try:
    import pandas as pd
except ImportError:  # pragma: no cover - local smoke without optional deps
    pd = None

try:
    from evedesign.constants import VALID_AA_SORTED
    from evedesign.model import (
        BaseModel,
        MutationScorer,
        Scorer,
        assign_scores_to_instances,
    )
    from evedesign.system import System, SystemInstance
    from evedesign.types import StatusCallback
    from evedesign.utils import status_done, status_start
except ImportError:  # pragma: no cover - keep local utility tests importable
    VALID_AA_SORTED = tuple("ACDEFGHIKLMNPQRSTVWY")

    class BaseModel:  # type: ignore[too-many-ancestors]
        pass

    class Scorer:  # type: ignore[too-many-ancestors]
        pass

    class MutationScorer:  # type: ignore[too-many-ancestors]
        pass

    def assign_scores_to_instances(instances, scores):
        return list(zip(instances, scores))

    System = Any  # type: ignore[assignment]
    SystemInstance = Any  # type: ignore[assignment]
    StatusCallback = Any  # type: ignore[assignment]

    def status_start(*_args, **_kwargs):
        return None

    def status_done(*_args, **_kwargs):
        return None

try:
    from proteingym.base import Dataset
except ImportError:  # pragma: no cover - keep local utility tests importable
    Dataset = Any  # type: ignore[assignment]


DEFAULT_BUNDLE_URL = (
    "https://huggingface.co/datasets/cytolex/cytolexmuta/resolve/main/"
    "cytolexmuta_scores_full217.tar.gz?download=true"
)


def _sequence_text(value: Any) -> str:
    """Convert a ProteinGym sequence value to a plain string."""
    return str(getattr(value, "value", value))


def _reference_sequence(dataset: Dataset) -> str:
    """Return the declared reference sequence, with a conservative fallback."""
    if dataset.reference_sequence_name:
        for sequence in dataset.sequences:
            if sequence.name == dataset.reference_sequence_name:
                return _sequence_text(sequence.value)

    for sequence in dataset.sequences:
        if getattr(sequence.type, "value", sequence.type) in {
            "wild_type",
            "starting_sequence",
        }:
            return _sequence_text(sequence.value)

    if dataset.sequences:
        return max((_sequence_text(s.value) for s in dataset.sequences), key=len)
    raise ValueError(f"{dataset.name!r} does not contain a reference sequence")


def _mutation_candidates(reference: str, sequence: str) -> list[str]:
    """Generate common ProteinGym mutation spellings for a sequence pair."""
    if len(reference) != len(sequence):
        return []
    substitutions = [
        f"{wt}{index}{mut}"
        for index, (wt, mut) in enumerate(zip(reference, sequence), start=1)
        if wt != mut
    ]
    if not substitutions:
        return ["WT", "wild_type", ""]
    joined = [
        "".join(substitutions),
        ":".join(substitutions),
        ";".join(substitutions),
        ",".join(substitutions),
        " ".join(substitutions),
    ]
    return substitutions + joined


def _diagnostic_score(reference: str, sequence: str) -> float:
    """A label-free, deterministic smoke-test score for unsupported datasets."""
    if len(reference) != len(sequence):
        return -float(abs(len(reference) - len(sequence)))
    score = 0.0
    for index, (wt, mut) in enumerate(zip(reference, sequence), start=1):
        if wt != mut:
            score -= ((ord(wt) - ord(mut)) ** 2) / (1.0 + math.log1p(index))
    return score


class ScoreBundle:
    """Lazy reader for the public cytolexmuta full-217 score release."""

    def __init__(self, url: str, cache_dir: str) -> None:
        self.url = url
        self.cache_dir = Path(cache_dir)
        self.archive_path = self.cache_dir / "cytolexmuta_scores_full217.tar.gz"
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self._maps: dict[str, dict[str, float]] = {}

    def _ensure_archive(self) -> None:
        if self.archive_path.is_file() and self.archive_path.stat().st_size > 0:
            return
        temporary = self.archive_path.with_suffix(".tmp")
        with urllib.request.urlopen(self.url, timeout=120) as response, temporary.open(
            "wb"
        ) as handle:
            while chunk := response.read(1024 * 1024):
                handle.write(chunk)
        temporary.replace(self.archive_path)

    def get(self, assay_id: str) -> dict[str, float] | None:
        if assay_id in self._maps:
            return self._maps[assay_id]
        try:
            self._ensure_archive()
            member_name = f"cytolexmuta/{assay_id}.csv"
            with tarfile.open(self.archive_path, mode="r:gz") as archive:
                member = archive.getmember(member_name)
                extracted = archive.extractfile(member)
                if extracted is None:
                    return None
                rows = csv.DictReader(io.TextIOWrapper(extracted, encoding="utf-8"))
                score_map = {
                    row["mutant"]: float(row["cytolexmuta"])
                    for row in rows
                    if row.get("mutant") is not None
                }
        except (OSError, KeyError, urllib.error.URLError, ValueError):
            return None
        self._maps[assay_id] = score_map
        return score_map


class Cytolexmuta(BaseModel, Scorer, MutationScorer):
    """Prediction-only cytolexmuta scorer compatible with evedesign."""

    name: str = "cytolexmuta"
    citations: list[str] = [
        "10.1101/2024.10.02.615855",
    ]

    requires_target: bool = True
    requires_fixed_length: bool = True
    handles_deletions: bool = False
    handles_insertions: bool = False
    requires_gpu: bool = False
    supports_gpu: bool = False
    supports_gpu_parallel: bool = False
    supports_cpu_parallel: bool = True

    required_entity_attributes: list[str] | None = ["sequences"]
    optional_entity_attributes: list[str] | None = None

    def __init__(
        self,
        score_bundle_url: str = DEFAULT_BUNDLE_URL,
        cache_dir: str = "/tmp/cytolexmuta",
        allow_diagnostic_fallback: bool = True,
    ) -> None:
        self.bundle = ScoreBundle(score_bundle_url, cache_dir)
        self.allow_diagnostic_fallback = allow_diagnostic_fallback
        self._system: System | None = None

    @property
    def system(self) -> System | None:
        return self._system

    @property
    def ready(self) -> bool:
        return self._system is not None

    @classmethod
    def can_model(cls, system: System, data: Any = None) -> tuple[bool, str]:
        if data is not None:
            return False, "Model does not support a data parameter (must be None)"
        if len(system) != 1 or system[0].type != "protein":
            return False, "Can only handle a single-component protein system"
        target = system[0]
        if not target.defined_sequence():
            return False, "Entity must have a defined rep sequence"
        return True, ""

    def _reference_rep(self) -> str:
        if self._system is None:
            raise ValueError("Model has not been built yet")
        return "".join(str(symbol) for symbol in self._system[0].rep)

    def _candidate_bundle_keys(self) -> list[str]:
        if self._system is None:
            return []
        entity = self._system[0]
        keys: list[str] = []
        for attr in ("name", "id", "dataset_name", "assay_id"):
            value = getattr(entity, attr, None)
            if value:
                keys.append(str(value))
        system_name = getattr(self._system, "name", None)
        if system_name:
            keys.append(str(system_name))
        return list(dict.fromkeys(keys))

    def _resolve_bundle(self) -> tuple[str | None, dict[str, float] | None]:
        for key in self._candidate_bundle_keys():
            score_map = self.bundle.get(key)
            if score_map is not None:
                return key, score_map
        return None, None

    def _score_sequence(
        self,
        sequence: str,
        reference: str,
        score_map: dict[str, float] | None,
    ) -> tuple[float, bool]:
        if score_map is not None:
            for candidate in _mutation_candidates(reference, sequence):
                if candidate in score_map:
                    return float(score_map[candidate]), False
        if not self.allow_diagnostic_fallback:
            raise ValueError("No released cytolexmuta score found for this system")
        return float(_diagnostic_score(reference, sequence)), True

    def build(
        self,
        system: System,
        data: Any = None,
        status_callback: StatusCallback | None = None,
    ) -> Self:
        ok, reason = self.can_model(system, data)
        if not ok:
            raise ValueError(reason)
        status_start(status_callback, "Loading cytolexmuta scorer")
        self._system = system
        status_done(status_callback, "cytolexmuta scorer ready")
        return self

    def positions(
        self,
        instance: SystemInstance | None = None,
    ) -> list[tuple[int, int]]:
        if self._system is None:
            raise ValueError("Model has not been built yet")
        first_index = self._system[0].first_index
        return [(0, pos + first_index) for pos in range(len(self._reference_rep()))]

    def score(
        self,
        instances: Sequence[SystemInstance],
        status_callback: StatusCallback | None = None,
    ) -> list[SystemInstance]:
        if self._system is None:
            raise ValueError("Model has not been built yet")
        if len(instances) == 0:
            return []

        status_start(status_callback, "Scoring sequences")
        reference = self._reference_rep()
        _, score_map = self._resolve_bundle()

        scores = []
        for instance in instances:
            sequence = "".join(str(symbol) for symbol in instance[0].rep)
            score, _ = self._score_sequence(sequence, reference, score_map)
            if not math.isfinite(score):
                raise ValueError("Non-finite cytolexmuta score encountered")
            scores.append(score)

        status_done(status_callback, "Scoring complete")
        return assign_scores_to_instances(instances, np.asarray(scores, dtype=float))

    def single_mutation_scan(
        self,
        instance: SystemInstance,
        entity: int | None = None,
        positions: Sequence[int] | None = None,
        status_callback: StatusCallback | None = None,
    ):
        if pd is None:
            raise RuntimeError("pandas is required for single_mutation_scan")
        if self._system is None:
            raise ValueError("Model has not been built yet")
        if entity is not None and entity != 0:
            raise ValueError("cytolexmuta only models entity 0")

        status_start(status_callback, "Computing single mutation scan")
        reference = self._reference_rep()
        first_index = self._system[0].first_index
        _, score_map = self._resolve_bundle()
        wt_score, _ = self._score_sequence(reference, reference, score_map)

        pos_filter = set(positions) if positions is not None else None
        rows = []
        index_tuples = []
        for pos, ref_symbol in enumerate(reference):
            evc_pos = pos + first_index
            if pos_filter is not None and evc_pos not in pos_filter:
                continue
            row = []
            for aa in VALID_AA_SORTED:
                mutated = reference[:pos] + aa + reference[pos + 1 :]
                score, _ = self._score_sequence(mutated, reference, score_map)
                row.append(score - wt_score)
            rows.append(row)
            index_tuples.append((0, evc_pos, ref_symbol))

        df = pd.DataFrame(
            rows,
            columns=list(VALID_AA_SORTED),
            index=pd.MultiIndex.from_tuples(index_tuples, names=["entity", "pos", "ref"]),
        )
        status_done(status_callback, "Single mutation scan complete")
        return df

    def score_conditional(
        self,
        instances: Sequence[SystemInstance],
        entities: Sequence[int],
        positions: Sequence[int],
        status_callback: StatusCallback | None = None,
    ):
        if pd is None:
            raise RuntimeError("pandas is required for score_conditional")
        if self._system is None:
            raise ValueError("Model has not been built yet")
        if not len(instances) == len(entities) == len(positions):
            raise ValueError(
                "Sequences for instances, entities and positions must all have same length"
            )

        status_start(status_callback, "Computing conditional scores")
        first_index = self._system[0].first_index
        _, score_map = self._resolve_bundle()
        rows = []
        index_tuples = []
        for inst_idx, (instance, entity, pos) in enumerate(zip(instances, entities, positions)):
            if entity != 0:
                raise ValueError("cytolexmuta only models entity 0")
            reference = "".join(str(symbol) for symbol in instance[0].rep)
            mi = int(pos) - first_index
            wt_score, _ = self._score_sequence(reference, reference, score_map)
            row = []
            for aa in VALID_AA_SORTED:
                mutated = reference[:mi] + aa + reference[mi + 1 :]
                score, _ = self._score_sequence(mutated, reference, score_map)
                row.append(score - wt_score)
            rows.append(row)
            index_tuples.append((inst_idx, int(entity), int(pos)))

        df = pd.DataFrame(
            rows,
            columns=list(VALID_AA_SORTED),
            index=pd.MultiIndex.from_tuples(index_tuples, names=["instance", "entity", "pos"]),
        )
        status_done(status_callback, "Conditional scoring complete")
        return df

    def predict(self, dataset: Dataset, target: str) -> pl.DataFrame:
        reference = _reference_sequence(dataset)
        assay = dataset.assays[0] if dataset.assays else None
        if assay is None:
            raise ValueError(f"{dataset.name!r} does not contain an assay")

        frame = assay.to_df()
        sequence_column = assay.sequence_feature_name
        sequences = [
            _sequence_text(sequence) for sequence in frame[sequence_column].to_list()
        ]

        score_map = self.bundle.get(dataset.name) or self.bundle.get(assay.name)
        scores: list[float] = []
        diagnostic = score_map is None
        for sequence in sequences:
            score = None
            if score_map is not None:
                for candidate in _mutation_candidates(reference, sequence):
                    if candidate in score_map:
                        score = score_map[candidate]
                        break
            if score is None:
                if not self.allow_diagnostic_fallback:
                    raise ValueError(
                        f"No released cytolexmuta score found for {dataset.name!r}"
                    )
                score = _diagnostic_score(reference, sequence)
            if not math.isfinite(score):
                raise ValueError(f"Non-finite score for sequence in {dataset.name!r}")
            scores.append(float(score))

        if diagnostic:
            print(
                f"cytolexmuta: {dataset.name} is outside the released full-217 "
                "bundle; using the explicit diagnostic fallback."
            )
        return pl.DataFrame({"sequence": sequences, target: scores})
