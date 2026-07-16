import json
import sys
from collections import defaultdict
from pathlib import Path
from typing import Annotated

import numpy as np
import polars as pl
import typer

_METADATA_FOLD_KEYS = ("test_fold", "test_folds", "train_available_folds")


def _aggregate_mode(metrics: dict[str, list[float]]) -> dict[str, float]:
    """Reduce a mode's per-fold metric values to their mean and sample std.

    For each metric, emits ``{name}`` (mean) and ``{name}_std`` (sample standard
    deviation, or 0.0 when only a single value is present).
    """
    aggregated: dict[str, float] = {}
    for metric_name, values in metrics.items():
        if not values:
            continue
        aggregated[metric_name] = float(np.mean(values))
        aggregated[f"{metric_name}_std"] = (
            float(np.std(values, ddof=1)) if len(values) > 1 else 0.0
        )
    return aggregated


def aggregate_metrics(
    metric_dir: Path,
    dataset_name: str,
    model_name: str,
    split: str,
    target: str,
    output_path: Path,
) -> None:
    """Aggregate per-fold or single-file metrics into one JSON file.

    Supervised pipelines write one file per fold under a ``{split}/`` directory;
    zero-shot pipelines write a single ``{split}.json`` scored once against the full
    dataset. Both layouts are collected here. "test" and "train_available" scores are
    reduced to their mean and sample standard deviation across folds, while
    "full_dataset" metrics are preserved as-is (identical across folds).

    Input structure per file:
        {
            "test": {"spearman": 0.85},
            "train_available": {"spearman": 0.92},
            "full_dataset": {"spearman": 0.88},
            "per_fold": {...},
            "metadata": {...}
        }

    Output structure (aggregated):
        {
            "test": {"spearman": 0.86, "spearman_std": 0.02},
            "train_available": {"spearman": 0.93, "spearman_std": 0.01},
            "full_dataset": {"spearman": 0.88},
            "metadata": {...}
        }
    """
    fold_pattern = f"{dataset_name}/{model_name}/{target}/{split}/fold*.json"
    single_file = metric_dir / dataset_name / model_name / target / f"{split}.json"
    metric_files = sorted(metric_dir.glob(fold_pattern))
    if single_file.exists():
        metric_files.append(single_file)

    if not metric_files:
        print( #print to log to stderr in dvc
            f"Warning: No metric files found for "
            f"{dataset_name}/{model_name}/{target}/{split}",
            file=sys.stderr,
        )
        return

    test_metrics: dict[str, list[float]] = defaultdict(list)
    train_available_metrics: dict[str, list[float]] = defaultdict(list)
    full_dataset_metrics: dict[str, float] | None = None
    metadata: dict | None = None

    for metric_file in metric_files:
        data = json.loads(metric_file.read_text())

        if metadata is None and "metadata" in data:
            metadata = {
                k: v
                for k, v in data["metadata"].items()
                if k not in _METADATA_FOLD_KEYS
            }

        for mode, accumulator in (
            ("test", test_metrics),
            ("train_available", train_available_metrics),
        ):
            for metric_name, value in data.get(mode, {}).items():
                if value is not None:
                    accumulator[metric_name].append(value)

        if full_dataset_metrics is None and "full_dataset" in data:
            full_dataset_metrics = data["full_dataset"]

    result: dict = {
        "metadata": metadata
        or {
            "dataset": dataset_name,
            "model": model_name,
            "split": split,
            "target": target,
        }
    }

    if test_metrics:
        result["test"] = _aggregate_mode(test_metrics)
    if train_available_metrics:
        result["train_available"] = _aggregate_mode(train_available_metrics)
    if full_dataset_metrics is not None:
        result["full_dataset"] = full_dataset_metrics

    output_path.write_text(json.dumps(result, indent=2))


def generate_metrics_csv(metric_dir: Path, output_path: Path, game: str) -> None:
    """Generate metrics CSV from aggregated JSON files.

    Reads aggregated JSON files with structure:
        {
            "test": {"spearman": 0.86, "spearman_std": 0.02, ...},
            "train_available": {"spearman": 0.93, "spearman_std": 0.01, ...},
            "full_dataset": {"spearman": 0.88, ...},
            "metadata": {...}
        }

    And creates CSV with columns:
        game, model, dataset, split, target, test_spearman, test_spearman_std,
        train_available_spearman, train_available_spearman_std,
        full_dataset_spearman, ...
    """
    rows = []
    for metric_file in sorted(metric_dir.glob("*_aggregated.json")):
        with open(metric_file) as f:
            data = json.load(f)
        if "metadata" not in data:
            print(f"Warning: No metadata found in {metric_file}, skipping", file=sys.stderr)
            continue
        metadata = data["metadata"]
        row = {
            "game": game,
            "model": metadata.get("model", "unknown"),
            "dataset": metadata.get("dataset", "unknown"),
            "split": metadata.get("split", "unknown"),
            "target": metadata.get("target", "unknown"),
        }

        if "test" in data:
            for metric_name, value in data["test"].items():
                row[f"test_{metric_name}"] = value

        if "train_available" in data:
            for metric_name, value in data["train_available"].items():
                row[f"train_available_{metric_name}"] = value

        if "full_dataset" in data:
            for metric_name, value in data["full_dataset"].items():
                row[f"full_dataset_{metric_name}"] = value

        rows.append(row)

    key_cols = ["game", "model", "dataset", "split", "target"]
    new_df = pl.DataFrame(rows)

    if output_path.exists():
        combined = pl.concat([pl.read_csv(output_path), new_df], how="diagonal_relaxed")
        combined.unique(subset=key_cols, keep="last").write_csv(output_path)
    else:
        new_df.write_csv(output_path)


app = typer.Typer()


@app.command()
def aggregate(
    metric_dir: Annotated[Path, typer.Option()],
    dataset_name: Annotated[str, typer.Option()],
    model_name: Annotated[str, typer.Option()],
    split: Annotated[str, typer.Option()],
    target: Annotated[str, typer.Option()],
    output_path: Annotated[Path, typer.Option()],
) -> None:
    """Aggregate metrics from folds."""
    aggregate_metrics(
        metric_dir, dataset_name, model_name, split, target, output_path
    )


@app.command()
def generate_csv(
    metric_dir: Annotated[Path, typer.Option()],
    output_path: Annotated[Path, typer.Option()],
    game: Annotated[str, typer.Option()],
) -> None:
    """Generate metrics CSV."""
    generate_metrics_csv(metric_dir, output_path, game)


if __name__ == "__main__":
    app()
