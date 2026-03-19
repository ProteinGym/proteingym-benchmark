import json
import math
from pathlib import Path
from typing import Annotated
import numpy as np
import polars as pl
import typer


def aggregate_metrics(metric_dir: Path, dataset_name: str, model_name: str, split: str, target: str, output_path: Path, prediction_dir: Path = None):
    """Aggregate metrics from all folds into a single JSON file.

    Reads all fold metric files from the directory structure and aggregates them.
    All metrics are handled generically using the same aggregation logic.
    """

    pattern = f"{dataset_name}/{model_name}/{target}/{split}/fold*.json"
    fold_files = list(metric_dir.glob(pattern))

    if not fold_files:
        print(f"No fold files found for {dataset_name}/{model_name}/{target}/{split}")
        return

    metrics_data = {}
    metadata = None

    for fold_file in fold_files:
        with open(fold_file) as f:
            data = json.load(f)

            # Extract metadata from the first file (should be consistent across folds)
            # Exclude "fold" since aggregated file represents all folds, not a specific one
            if metadata is None and "metadata" in data:
                metadata = {k: v for k, v in data["metadata"].items() if k != "fold"}

            fold_id = data.get("metadata", {}).get("fold", fold_file.stem.replace('fold', ''))

            for metric_name, value in data.items():
                if metric_name == "metadata":
                    continue
                if metric_name not in metrics_data:
                    metrics_data[metric_name] = {}
                metrics_data[metric_name][fold_id] = value

    result = {
        "metadata": metadata or {
            "dataset": dataset_name,
            "model": model_name,
            "split": split,
            "target": target
        }
    }

    for metric_name, folds in metrics_data.items():
        fold_values = [v for v in folds.values() if v is not None]
        result[metric_name] = {
            **folds,
            "all": np.mean(fold_values) if fold_values else None
        }

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
                combined_path = prediction_dir / f"{dataset_name}_{model_name}_{target}_{split}_combined.json"
                combined.write_json(combined_path)


def generate_metrics_csv(metric_dir: Path, output_path: Path, game: str):
    """Generate metrics CSV from aggregated JSON files."""
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
        for metric_name, metric_data in data.items():
            if metric_name == "metadata":
                continue
            fold_values = [v for k, v in metric_data.items() if k != "all" and v is not None]
            mean = metric_data.get("all") or (sum(fold_values) / len(fold_values) if fold_values else None)
            stdev = math.sqrt(sum((x - mean) ** 2 for x in fold_values) / len(fold_values)) if len(fold_values) > 1 and mean is not None else 0.0
            row[metric_name] = mean
            row[f"{metric_name}_stdev"] = stdev
        rows.append(row)

    key_cols = ["game", "model", "dataset", "split", "target"]
    new_df = pl.DataFrame(rows)

    if output_path.exists():
        combined = pl.concat([pl.read_csv(output_path), new_df], how="diagonal")
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
    prediction_dir: Annotated[Path, typer.Option()] = None
):
    """Aggregate metrics from folds."""
    aggregate_metrics(metric_dir, dataset_name, model_name, split, target, output_path, prediction_dir)


@app.command()
def generate_csv(
    metric_dir: Annotated[Path, typer.Option()],
    output_path: Annotated[Path, typer.Option()],
    game: Annotated[str, typer.Option()]
):
    """Generate metrics CSV."""
    generate_metrics_csv(metric_dir, output_path, game)


if __name__ == "__main__":
    app()
