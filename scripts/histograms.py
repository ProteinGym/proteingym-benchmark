"""Per-dataset score histograms for `static/histograms.json`.

Written to drop into `generate_metadata.py` in the Hugging Face dataset repo,
where the `.pgdata` archives are; standard library only, so it needs nothing
that script does not already import. CONTRIBUTING.md, "Static website", says why
it lives here rather than there.
"""

import csv
import io
import math
import zipfile


def histogram(values: list[float], target: str) -> dict:
    """Uniform-width bin counts, in the shape the dataset page expects.

    `values` must be non-empty and finite; `dataset_histogram` ensures both.
    """
    # shortcut: sqrt bin count, not the Freedman-Diaconis rule numpy's
    # bins="auto" uses; swap in np.histogram if the charts ever need it
    k = max(10, min(60, round(len(values) ** 0.5)))
    lo, hi = min(values), max(values)
    if hi == lo:
        hi = lo + 1.0  # a single-valued assay still gets one visible bar
    width = (hi - lo) / k
    counts = [0] * k
    for x in values:
        counts[min(int((x - lo) / width), k - 1)] += 1  # the max joins the last bin
    return {
        "target": target,
        "n": len(values),
        "edges": [lo + i * width for i in range(k + 1)],
        "counts": counts,
    }


def dataset_histogram(archive: zipfile.ZipFile, manifest: dict) -> dict | None:
    """The histogram of a dataset's first scorable assay target, or None.

    `archive` is the `dataset.pgdata` zip and `manifest` its parsed
    `manifest.lock`, which names the assay CSV and its target columns.
    """
    for assay in manifest.get("assays", []):
        for target in assay.get("targets", []):
            name = target.get("name")
            values = []
            with archive.open(assay["path"]) as f:
                for row in csv.DictReader(io.TextIOWrapper(f, "utf-8")):
                    try:
                        value = float(row[name])
                    except (KeyError, TypeError, ValueError):
                        continue  # blank and non-numeric cells are not scored
                    # "nan" and "inf" parse fine and then break the bin maths
                    if math.isfinite(value):
                        values.append(value)
            if values:
                return histogram(values, name)
    return None
