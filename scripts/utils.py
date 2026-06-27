import json
from pathlib import Path
from typing import Annotated
import numpy as np
import polars as pl
import typer


def aggregate_metrics(
    metric_dir: Path,
    dataset_name: str,
    model_name: str,
    split: str,
    target: str,
    output_path: Path,
    prediction_dir: Path = None,
):
    """Aggregate metrics from all folds into a single JSON file.

    Reads all fold metric files and computes the mean and standard deviation of "test"
    and "train_available" scores across all folds.

    Input structure per fold file:
        {
            "test": {"spearman": 0.85},
            "train_available": {"spearman": 0.92},
            "per_fold": {...},
            "metadata": {...}
        }

    Output structure (aggregated across all folds):
        {
            "test": {"spearman": 0.86, "spearman_std": 0.02},
            "train_available": {"spearman": 0.93, "spearman_std": 0.01},
            "metadata": {...}
        }
    """

    pattern = f"{dataset_name}/{model_name}/{target}/{split}/fold*.json"
    fold_files = list(metric_dir.glob(pattern))

    if not fold_files:
        print(f"No fold files found for {dataset_name}/{model_name}/{target}/{split}")
        return

    # Structure: {scoring_mode: {metric_name: [values]}}
    test_metrics = {}
    train_available_metrics = {}
    metadata = None

    for fold_file in fold_files:
        with open(fold_file) as f:
            data = json.load(f)

            # Extract metadata from the first file
            if metadata is None and "metadata" in data:
                metadata = {
                    k: v
                    for k, v in data["metadata"].items()
                    if k not in ["test_fold", "test_folds", "train_available_folds"]
                }

            # Aggregate test scores
            if "test" in data:
                for metric_name, value in data["test"].items():
                    if value is not None:
                        if metric_name not in test_metrics:
                            test_metrics[metric_name] = []
                        test_metrics[metric_name].append(value)

            # Aggregate train_available scores
            if "train_available" in data:
                for metric_name, value in data["train_available"].items():
                    if value is not None:
                        if metric_name not in train_available_metrics:
                            train_available_metrics[metric_name] = []
                        train_available_metrics[metric_name].append(value)

    # Build result with means and standard deviations
    result = {
        "metadata": metadata
        or {
            "dataset": dataset_name,
            "model": model_name,
            "split": split,
            "target": target,
        }
    }

    # Add mean and std of test metrics
    if test_metrics:
        result["test"] = {}
        for metric_name, values in test_metrics.items():
            if values:
                result["test"][metric_name] = np.mean(values)
                result["test"][f"{metric_name}_std"] = np.std(values, ddof=1) if len(values) > 1 else 0.0

    # Add mean and std of train_available metrics
    if train_available_metrics:
        result["train_available"] = {}
        for metric_name, values in train_available_metrics.items():
            if values:
                result["train_available"][metric_name] = np.mean(values)
                result["train_available"][f"{metric_name}_std"] = np.std(values, ddof=1) if len(values) > 1 else 0.0

    output_path.write_text(json.dumps(result, indent=2))

    if prediction_dir:
        pred_pattern = f"{dataset_name}/{model_name}/{target}/{split}/fold*"
        pred_paths = list(prediction_dir.glob(pred_pattern))

        if pred_paths:
            dfs = []
            for pred_path in pred_paths:
                json_file = pred_path / "predictions.json"
                if json_file.exists():
                    dfs.append(pl.read_json(json_file))
            if dfs:
                combined = pl.concat(dfs)
                combined_path = (
                    prediction_dir
                    / f"{dataset_name}_{model_name}_{target}_{split}_combined.json"
                )
                combined.write_json(combined_path)


def generate_metrics_csv(metric_dir: Path, output_path: Path, game: str):
    """Generate metrics CSV from aggregated JSON files.

    Reads aggregated JSON files with structure:
        {
            "test": {"spearman": 0.86, "spearman_std": 0.02, ...},
            "train_available": {"spearman": 0.93, "spearman_std": 0.01, ...},
            "metadata": {...}
        }

    And creates CSV with columns:
        game, model, dataset, split, target, test_spearman, test_spearman_std,
        train_available_spearman, train_available_spearman_std, ...
    """
    rows = []
    for metric_file in sorted(metric_dir.glob("*_aggregated.json")):
        with open(metric_file) as f:
            data = json.load(f)
        if "metadata" not in data:
            print(f"Warning: No metadata found in {metric_file}, skipping")
            continue
        metadata = data["metadata"]
        row = {
            "game": game,
            "model": metadata.get("model", "unknown"),
            "dataset": metadata.get("dataset", "unknown"),
            "split": metadata.get("split", "unknown"),
            "target": metadata.get("target", "unknown"),
        }

        # Extract test metrics
        if "test" in data:
            for metric_name, value in data["test"].items():
                row[f"test_{metric_name}"] = value

        # Extract train_available metrics
        if "train_available" in data:
            for metric_name, value in data["train_available"].items():
                row[f"train_available_{metric_name}"] = value

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
    prediction_dir: Annotated[Path, typer.Option()] = None,
):
    """Aggregate metrics from folds."""
    aggregate_metrics(
        metric_dir, dataset_name, model_name, split, target, output_path, prediction_dir
    )


@app.command()
def generate_csv(
    metric_dir: Annotated[Path, typer.Option()],
    output_path: Annotated[Path, typer.Option()],
    game: Annotated[str, typer.Option()],
):
    """Generate metrics CSV."""
    generate_metrics_csv(metric_dir, output_path, game)


if __name__ == "__main__":
    app()
