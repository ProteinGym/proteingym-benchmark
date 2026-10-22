"""Precompute assay score histograms for the dataset pages.

Downloads each dataset archive from Hugging Face one at a time, reads the
primary assay target column and writes binned counts to static/histograms.json.

Usage: python scripts/generate_histograms.py [SLUG ...]   (default: all datasets)
"""

import csv
import io
import json
import math
import sys
import tempfile
import tomllib
import urllib.request
import zipfile
from pathlib import Path

import numpy as np

BASE_URL = "https://huggingface.co/datasets/ProteinGym/ProteinGym2.0/resolve/main"
MANIFESTS = Path("static/manifests.json")
OUTPUT = Path("static/histograms.json")
MAX_BINS = 200


def round_sig(x: float, sig: int = 6) -> float:
    return 0.0 if x == 0 else round(x, sig - 1 - math.floor(math.log10(abs(x))))


def histogram(slug: str, manifest: dict) -> dict:
    assay_path = manifest["assays"][0]["path"]
    target = manifest["assay_targets"][0]["name"]
    with tempfile.TemporaryFile() as tmp:
        with urllib.request.urlopen(f"{BASE_URL}/{slug}.pgdata") as resp:
            while chunk := resp.read(1 << 20):
                tmp.write(chunk)
        with zipfile.ZipFile(tmp) as outer:
            inner = zipfile.ZipFile(io.BytesIO(outer.read("dataset.pgdata")))
    with inner.open(assay_path) as f:
        rows = csv.DictReader(io.TextIOWrapper(f, encoding="utf-8"))
        scores = np.array([float(r[target]) for r in rows if r[target] not in ("", "nan", "NaN")])
    scores = scores[np.isfinite(scores)]
    edges = np.histogram_bin_edges(scores, bins="auto")
    if len(edges) - 1 > MAX_BINS:
        edges = np.histogram_bin_edges(scores, bins=MAX_BINS)
    counts, edges = np.histogram(scores, bins=edges)
    return {
        "target": target,
        "n": int(scores.size),
        "edges": [round_sig(float(e)) for e in edges],
        "counts": counts.tolist(),
    }


def main() -> None:
    manifests = json.loads(MANIFESTS.read_text())["manifests"]
    slugs = sys.argv[1:] or sorted(manifests)
    out = json.loads(OUTPUT.read_text()) if OUTPUT.exists() and sys.argv[1:] else {}
    failed = []
    for i, slug in enumerate(slugs, 1):
        try:
            out[slug] = histogram(slug, tomllib.loads(manifests[slug]))
            print(f"[{i}/{len(slugs)}] {slug}: n={out[slug]['n']} bins={len(out[slug]['counts'])}")
        except Exception as e:
            failed.append(slug)
            print(f"[{i}/{len(slugs)}] {slug}: FAILED {e!r}", file=sys.stderr)
    OUTPUT.write_text(json.dumps(out, separators=(",", ":"), sort_keys=True))
    print(f"Wrote {len(out)} histograms to {OUTPUT} ({len(failed)} failed)")
    if failed and len(failed) == len(slugs):
        sys.exit(1)


if __name__ == "__main__":
    main()
