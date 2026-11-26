"""Checks the score-histogram shape the dataset page relies on."""

import io
import json
import tomllib
import zipfile
from pathlib import Path

import pytest

from scripts.histograms import dataset_histogram, histogram

DUMMY = Path("datasets/splits/Dummy_test_P0DX94.splits.pgdata")


def test_counts_cover_every_value_and_edges_bound_them():
    h = histogram([0.0, 1.0, 2.0, 3.0, 4.0] * 40, "DMS Score")

    assert sum(h["counts"]) == h["n"] == 200
    assert len(h["edges"]) == len(h["counts"]) + 1
    assert h["edges"][0] == 0.0 and h["edges"][-1] == pytest.approx(4.0)


def test_the_largest_value_lands_in_the_last_bin():
    # it sits exactly on the top edge, so the naive bin index is out of range
    h = histogram([0.0] * 10 + [10.0], "t")

    assert h["counts"][-1] == 1
    assert sum(h["counts"]) == 11


def test_a_single_valued_assay_does_not_divide_by_zero():
    h = histogram([2.5] * 12, "t")

    assert sum(h["counts"]) == 12
    assert h["edges"][0] == 2.5 and h["edges"][-1] > 2.5


def test_it_reads_a_real_pgdata_archive():
    outer = zipfile.ZipFile(DUMMY)
    inner = zipfile.ZipFile(io.BytesIO(outer.read("dataset.pgdata")))
    manifest = tomllib.loads(inner.read("manifest.lock").decode())

    h = dataset_histogram(inner, manifest)

    assert h["target"] == "charge"
    assert h["n"] == 49  # numeric rows in assays/charge_ladder.csv
    assert sum(h["counts"]) == h["n"]
    assert json.dumps(h)  # the output has to survive json.dump


def test_nan_and_blank_scores_are_skipped_not_binned(tmp_path):
    # a real DMS column carries NaN, which float() accepts and min/max poison
    archive = tmp_path / "d.pgdata"
    with zipfile.ZipFile(archive, "w") as z:
        z.writestr(
            "assays/a.csv", "sequence,DMS Score\nA,1.0\nB,nan\nC,\nD,NA\nE,3.0\n"
        )
    manifest = {
        "assays": [{"path": "assays/a.csv", "targets": [{"name": "DMS Score"}]}]
    }

    h = dataset_histogram(zipfile.ZipFile(archive), manifest)

    assert h["n"] == 2
    assert h["edges"][0] == 1.0 and h["edges"][-1] == pytest.approx(3.0)


def test_a_manifest_with_no_assays_yields_nothing():
    assert dataset_histogram(zipfile.ZipFile(DUMMY), {}) is None
