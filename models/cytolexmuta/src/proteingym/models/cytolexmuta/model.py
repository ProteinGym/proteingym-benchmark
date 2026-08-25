from __future__ import annotations

import csv
import io
import math
import tarfile
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

import polars as pl
from proteingym.base import Dataset


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


class Cytolexmuta:
    """Prediction-only cytolexmuta scorer."""

    def __init__(
        self,
        score_bundle_url: str = DEFAULT_BUNDLE_URL,
        cache_dir: str = "/tmp/cytolexmuta",
        allow_diagnostic_fallback: bool = True,
    ) -> None:
        self.bundle = ScoreBundle(score_bundle_url, cache_dir)
        self.allow_diagnostic_fallback = allow_diagnostic_fallback

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
