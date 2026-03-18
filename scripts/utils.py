import json
import math
from pathlib import Path
from typing import Annotated
import numpy as np
import polars as pl
import typer


def aggregate_metrics(metric_dir: Path, dataset_name: str, model_name: str, split: str, target: str, output_path: Path, prediction_dir: Path = None):
    """Aggregate metrics from all folds into a single JSON file.

    This function aggregates metrics across folds and stores metadata both in the JSON
    structure and in the file path (hybrid approach for easier aggregation and caching).
    """

    pattern = f"{dataset_name}/{model_name}/{target}/{split}/fold*"
    fold_dirs = list(metric_dir.glob(pattern))

    if not fold_dirs:
        print(f"No fold directories found for {dataset_name}/{model_name}/{target}/{split}")
        return

    metrics_data = {}
    for fold_dir in fold_dirs:
        fold = fold_dir.name.replace('fold', '')
        metric_file = fold_dir.with_suffix('.json')

        if metric_file.exists():
            with open(metric_file) as f:
                data = json.load(f)
                # Skip metadata key when aggregating metrics
                for metric_name, value in data.items():
                    if metric_name == "metadata":
                        continue
                    if metric_name not in metrics_data:
                        metrics_data[metric_name] = {}
                    metrics_data[metric_name][fold] = value

    result = {
        "metadata": {
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
    
    """Also combines the CSV files."""
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
    """Generate metrics CSV from aggregated JSON files.

    Reads metadata from JSON files (hybrid approach) with fallback to filename parsing
    for backward compatibility with existing files without metadata.
    """
    rows = ["game,model,dataset,split,target,spearman,stdev"]

    for metric_file in sorted(metric_dir.glob("*_aggregated.json")):
        with open(metric_file) as f:
            data = json.load(f)

        # Try to read metadata from JSON first (new hybrid approach)
        if "metadata" in data:
            metadata = data["metadata"]
            dataset = metadata.get("dataset")
            model = metadata.get("model")
            split = metadata.get("split")
            target = metadata.get("target")
        else:
            # Fallback: parse from filename for backward compatibility
            filename = metric_file.stem.replace("_aggregated", "")
            parts = filename.split('_')
            target = parts[-1]
            split = parts[-2]
            model = parts[-3]
            dataset = '_'.join(parts[:-3])

        if 'spearman' in data:
            spearman_data = data['spearman']
            fold_values = [v for k, v in spearman_data.items() if k != 'all' and v is not None]
            mean = spearman_data.get('all')
            if mean is None and fold_values:
                mean = sum(fold_values) / len(fold_values)
            if len(fold_values) > 1:
                variance = sum((x - mean) ** 2 for x in fold_values) / len(fold_values)
                stdev = math.sqrt(variance)
            else:
                stdev = 0.0
            rows.append(f"{game},{model},{dataset},{split},{target},{mean},{stdev}")

    output_path.write_text('\n'.join(rows) + '\n')


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
