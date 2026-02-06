import argparse
import json
import math
from pathlib import Path
import numpy as np
import polars as pl


def aggregate_metrics(metric_dir: Path, dataset_name: str, model_name: str, output_path: Path, prediction_dir: Path = None):
    """Aggregate metrics from all folds into a single JSON file."""
    
    pattern = f"{dataset_name}_{model_name}_fold*"
    fold_dirs = [d for d in metric_dir.glob(pattern) if d.is_dir()]
    
    if not fold_dirs:
        print(f"No fold directories found for {dataset_name}_{model_name}")
        return
    
    targets = {}
    for fold_dir in fold_dirs:
        fold = fold_dir.name.split('fold')[-1]
        
        for metric_file in fold_dir.glob("*.json"):
            target = metric_file.stem
            
            if target not in targets:
                targets[target] = {}
            
            with open(metric_file) as f:
                data = json.load(f)
                for metric_name, value in data.items():
                    if metric_name not in targets[target]:
                        targets[target][metric_name] = {}
                    targets[target][metric_name][fold] = value
    
    result = {}
    for target, metrics in targets.items():
        result[target] = {}
        for metric_name, folds in metrics.items():
            fold_values = [v for v in folds.values() if v is not None]
            result[target][metric_name] = {
                **folds,
                "all": np.mean(fold_values) if fold_values else None
            }
    
    output_path.write_text(json.dumps(result, indent=2))
    
    """Also combines the CSV files."""
    if prediction_dir:
        pred_pattern = f"{dataset_name}_{model_name}_fold*"
        pred_dirs = [d for d in prediction_dir.glob(pred_pattern) if d.is_dir()]
        
        if pred_dirs:
            dfs = []
            for pred_dir in pred_dirs:
                for csv_file in pred_dir.glob("*.csv"):
                    dfs.append(pl.read_csv(csv_file))
            if dfs:
                combined = pl.concat(dfs)
                combined_path = prediction_dir / f"{dataset_name}_{model_name}_combined.csv"
                combined.write_csv(combined_path)


def generate_metrics_csv(metric_dir: Path, output_path: Path, game: str):
    """Generate metrics CSV from aggregated JSON files."""
    rows = ["game,model,dataset,target,spearman,stdev"]
    
    for metric_file in sorted(metric_dir.glob("*_aggregated.json")):
        filename = metric_file.stem.replace("_aggregated", "")
        parts = filename.split('_')
        model = parts[-1]
        dataset = '_'.join(parts[:-1])
        
        with open(metric_file) as f:
            data = json.load(f)
        
        for target, metrics in data.items():
            if 'spearman' in metrics:
                spearman_data = metrics['spearman']
                fold_values = [v for k, v in spearman_data.items() if k != 'all' and v is not None]
                mean = spearman_data.get('all')
                if mean is None and fold_values:
                    mean = sum(fold_values) / len(fold_values)
                if len(fold_values) > 1:
                    variance = sum((x - mean) ** 2 for x in fold_values) / len(fold_values)
                    stdev = math.sqrt(variance)
                else:
                    stdev = 0.0
                rows.append(f"{game},{model},{dataset},{target},{mean},{stdev}")
    
    output_path.write_text('\n'.join(rows) + '\n')


def main():
    parser = argparse.ArgumentParser(description="Aggregate metrics from all folds")
    subparsers = parser.add_subparsers(dest="command", required=True)
    
    aggregate_parser = subparsers.add_parser("aggregate", help="Aggregate metrics from folds")
    aggregate_parser.add_argument("--metric-dir", type=Path, required=True)
    aggregate_parser.add_argument("--dataset-name", type=str, required=True)
    aggregate_parser.add_argument("--model-name", type=str, required=True)
    aggregate_parser.add_argument("--output-path", type=Path, required=True)
    aggregate_parser.add_argument("--prediction-dir", type=Path, required=False)
    
    csv_parser = subparsers.add_parser("generate-csv", help="Generate metrics CSV")
    csv_parser.add_argument("--metric-dir", type=Path, required=True)
    csv_parser.add_argument("--output-path", type=Path, required=True)
    csv_parser.add_argument("--game", type=str, required=True)
    
    args = parser.parse_args()
    
    if args.command == "aggregate":
        aggregate_metrics(args.metric_dir, args.dataset_name, args.model_name, args.output_path, args.prediction_dir)
    elif args.command == "generate-csv":
        generate_metrics_csv(args.metric_dir, args.output_path, args.game)


if __name__ == "__main__":
    main()
