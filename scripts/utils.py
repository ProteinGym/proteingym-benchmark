import json
from pathlib import Path
from typing import Annotated
import numpy as np
import polars as pl

from proteingym.base.dataset import Subsets, Dataset, SEQUENCE
import typer


def get_fold_indices(subsets: Subsets, split: str) -> list[int]:
    """Get all fold indices for a given split strategy.

    Args:
        subsets: The Subsets object containing split information.
        split: The name of the split strategy (e.g., 'random', 'kfold_random').

    Returns:
        List of fold indices available for the split.
    """
    return list(range(len(subsets.slices[split])))


def prepare_and_validate_scoring_df(
    ground_truth: Subsets | Dataset,
    predicted: Dataset,
    target: str,
    split: str | None = None,
    fold: int | list[int] | None = None,
) -> pl.DataFrame:
    """Prepare and validate a scoring dataframe from ground truth and predictions.

    Joins ground truth and predicted datasets on sequence and assay variables,
    ensuring complete prediction coverage. The returned DataFrame contains both
    ground truth and predicted values for the specified target, aligned by
    sequence and variables.

    Args:
        ground_truth: The ground truth data, either as a complete Dataset or
            a Subsets object containing dataset slices.
        predicted: The predicted Dataset containing model predictions for the
            target. Must have the same structure (assays and variables) as the
            ground truth.
        target: The name of the target variable to score (e.g., 'fitness',
            'binding_affinity'). Must be present in both datasets' assay_targets.
        split: Required when ground_truth is a Subsets object. The name of the
            splitting strategy to evaluate (e.g., 'random', 'kfold_random').
        fold: Required when ground_truth is a Subsets object. Can be:
            - A single fold index (int) to score one fold
            - A list of fold indices to score multiple folds in aggregate

    Returns:
        A Polars DataFrame with columns:
            - 'sequence': The protein sequence
            - assay variable columns (e.g., 'temperature', 'pH')
            - target column: ground truth values
            - target_pred column: predicted values (with '_pred' suffix)

    Raises:
        TypeError: If ground_truth is neither a Dataset nor a Subsets object.
        ValueError: If split or fold is None when ground_truth is a Subsets object.
        ValueError: If any ground truth records lack corresponding predictions
            (incomplete coverage).

    Examples:
        >>> # Score a complete dataset
        >>> df = prepare_and_validate_scoring_df(
        ...     ground_truth=dataset,
        ...     predicted=predictions_dataset,
        ...     target='fitness'
        ... )
        >>> # Score a specific fold from dataset slices
        >>> df = prepare_and_validate_scoring_df(
        ...     ground_truth=cv_subsets,
        ...     predicted=predictions_dataset,
        ...     target='fitness',
        ...     split='test',
        ...     fold=0
        ... )
    """
    if isinstance(ground_truth, Dataset):
        gt_df = ground_truth.to_df(target_names=target)
        pred_df = predicted.to_df(target_names=target)
    elif isinstance(ground_truth, Subsets):
        if split is None or fold is None:
            raise ValueError(
                "Both 'split' and 'fold' must be provided when scoring Subsets."
            )

        fold_indices = [fold] if isinstance(fold, int) else fold

        gt_dfs = []
        pred_dfs = []
        for fold_idx in fold_indices:
            dataset_slice = ground_truth.slices[split][fold_idx]
            gt_dfs.append(
                ground_truth.dataset[dataset_slice].to_df(target_names=target)
            )
            pred_dfs.append(predicted[dataset_slice].to_df(target_names=target))

        gt_df = pl.concat(gt_dfs, how="vertical_relaxed")
        pred_df = pl.concat(pred_dfs, how="vertical_relaxed")
    else:
        raise TypeError("'ground_truth' must be a Dataset or a Subsets object.")

    # Validate that ground_truth and predicted have the same assay_variables structure
    if isinstance(ground_truth, Subsets):
        gt_variables = ground_truth.dataset.assay_variables
        pred_variables = predicted.assay_variables
    else:
        gt_variables = ground_truth.assay_variables
        pred_variables = predicted.assay_variables

    if gt_variables != pred_variables:
        gt_var_names = [v.name for v in gt_variables]
        pred_var_names = [v.name for v in pred_variables]
        raise ValueError(
            f"Ground truth and predicted datasets must have identical assay_variables. "
            f"Ground truth has: {gt_var_names}, predicted has: {pred_var_names}"
        )

    # Join on (sequence, variables) to align predictions with ground truth
    # Only use variables that are present and not all-null in both dataframes
    # (Polars doesn't match null values in joins: NULL != NULL)
    declared_variable_names = [v.name for v in gt_variables]
    variable_names = [
        var
        for var in declared_variable_names
        if var in gt_df.columns
        and var in pred_df.columns
        and not gt_df[var].is_null().all()
        and not pred_df[var].is_null().all()
    ]
    join_keys = [SEQUENCE] + variable_names

    joined = gt_df.join(pred_df, on=join_keys, how="inner", suffix="_pred")

    missing_predictions = len(gt_df) - len(joined)
    if missing_predictions > 0:
        raise ValueError(f"Missing {missing_predictions} prediction(s).")

    return joined


def _get_top_k_from_slice(
    ground_truth: Subsets | Dataset,
    split: str | None,
    fold: int | None,
) -> int | None:
    """Extract top_k from slice metadata if available.

    Args:
        ground_truth: The ground truth data (Dataset or Subsets).
        split: The split name (required for Subsets).
        fold: The fold index (required for Subsets, must be int not list).

    Returns:
        The top_k value from metadata, or None if:
            - ground_truth is not a Subsets object
            - split or fold is not provided
            - fold is a list (not supported for recovery)
            - metadata doesn't exist or doesn't contain top_k
    """
    if not isinstance(ground_truth, Subsets):
        return None
    if split is None or fold is None:
        return None
    if isinstance(fold, list):
        return None

    try:
        dataset_slice = ground_truth.slices[split][fold]
        if dataset_slice.metadata is None:
            return None
        top_k = dataset_slice.metadata.get("top_k")
        return int(top_k) if top_k is not None else None
    except (KeyError, IndexError, AttributeError, ValueError, TypeError):
        return None


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
    and "train_available" scores across all folds. Preserves "full_dataset" metrics
    from any fold (all folds have identical full_dataset values).

    Input structure per fold file:
        {
            "test": {"spearman": 0.85},
            "train_available": {"spearman": 0.92},
            "full_dataset": {"spearman": 0.88},
            "per_fold": {...},
            "metadata": {...}
        }

    Output structure (aggregated across all folds):
        {
            "test": {"spearman": 0.86, "spearman_std": 0.02},
            "train_available": {"spearman": 0.93, "spearman_std": 0.01},
            "full_dataset": {"spearman": 0.88},
            "metadata": {...}
        }
    """

    pattern = f"{dataset_name}/{model_name}/{target}/{split}/fold*.json"
    fold_files = list(metric_dir.glob(pattern))

    if not fold_files:
        print(f"No fold files found for {dataset_name}/{model_name}/{target}/{split}")
        return

    test_metrics = {}
    train_available_metrics = {}
    full_dataset_metrics = None
    metadata = None

    for fold_file in fold_files:
        with open(fold_file) as f:
            data = json.load(f)

            if metadata is None and "metadata" in data:
                metadata = {
                    k: v
                    for k, v in data["metadata"].items()
                    if k not in ["test_fold", "test_folds", "train_available_folds"]
                }

            if "test" in data:
                for metric_name, value in data["test"].items():
                    if value is not None:
                        if metric_name not in test_metrics:
                            test_metrics[metric_name] = []
                        test_metrics[metric_name].append(value)

            if "train_available" in data:
                for metric_name, value in data["train_available"].items():
                    if value is not None:
                        if metric_name not in train_available_metrics:
                            train_available_metrics[metric_name] = []
                        train_available_metrics[metric_name].append(value)

            if "full_dataset" in data and full_dataset_metrics is None:
                full_dataset_metrics = data["full_dataset"]

    result = {
        "metadata": metadata
        or {
            "dataset": dataset_name,
            "model": model_name,
            "split": split,
            "target": target,
        }
    }

    if test_metrics:
        result["test"] = {}
        for metric_name, values in test_metrics.items():
            if values:
                result["test"][metric_name] = np.mean(values)
                result["test"][f"{metric_name}_std"] = (
                    np.std(values, ddof=1) if len(values) > 1 else 0.0
                )

    if train_available_metrics:
        result["train_available"] = {}
        for metric_name, values in train_available_metrics.items():
            if values:
                result["train_available"][metric_name] = np.mean(values)
                result["train_available"][f"{metric_name}_std"] = (
                    np.std(values, ddof=1) if len(values) > 1 else 0.0
                )

    if full_dataset_metrics is not None:
        result["full_dataset"] = full_dataset_metrics

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
