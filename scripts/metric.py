"""
Metric calculation script for ProteinGym benchmark evaluation.

This script provides a flexible, extensible framework for calculating performance metrics
for machine learning models on protein datasets. It uses dynamic metric discovery to
automatically detect and execute custom metric functions.

The script loads ground truth data from ProteinGym dataset archives (.pgdata or
.splits.pgdata files), loads predictions from prediction archives, and computes selected
metrics. Results are saved as JSON with metadata for further analysis.

Metrics operate on Dataset or Subsets objects from the proteingym-base package, enabling
evaluation on complete datasets or specific cross-validation folds. All metrics use a
plugin-style architecture: any function with the 'metric_' prefix is automatically
discovered and made available for calculation.

Example Usage:
    ```bash
    # Evaluate predictions on a specific dataset slice (e.g., cross-validation fold)
    python metric.py \\
        --prediction-path predictions.pgdata \\
        --dataset-path dataset.splits.pgdata \\
        --metric-path metrics.json \\
        --selected-metrics spearman \\
        --target DMS_score \\
        --split test \\
        --fold 0
    ```

Example metric JSON output:
    ```json
    {
        "metadata": {
            "dataset": "BRCA1_HUMAN",
            "model": "ESM2",
            "split": "test",
            "target": "DMS_score",
            "fold": "0"
        },
        "spearman": 0.8542
    }
    ```

Adding Custom Metrics:
    To add a new metric, define a function following this pattern:
    ```python
    def metric_<name>(
        ground_truth: Subsets | Dataset,
        predicted: Dataset,
        target: str,
        split: str | None = None,
        fold: int | None = None
    ) -> float:
        # Use prepare_and_validate_scoring_df to get aligned data
        scoring_df = prepare_and_validate_scoring_df(
            ground_truth, predicted, target, split, fold
        )
        gt_values = scoring_df[target].to_numpy()
        pred_values = scoring_df[f"{target}_pred"].to_numpy()

        # Your metric calculation logic here
        result = custom_calculation(gt_values, pred_values)
        return result
    ```

    The metric function will be automatically discovered and made available for
    calculation. The function name after 'metric_' becomes the metric name used
    in the --selected-metrics argument.
"""

import inspect
import sys
import argparse
import json
import warnings
from pathlib import Path

import numpy as np
import polars as pl
from scipy.stats import spearmanr

from proteingym.base.dataset import Subsets, Dataset, SEQUENCE

metric_functions = {}


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
        ...     ground_truth=test_dataset,
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
            gt_dfs.append(ground_truth.dataset[dataset_slice].to_df(target_names=target))
            pred_dfs.append(predicted[dataset_slice].to_df(target_names=target))

        gt_df = pl.concat(gt_dfs, how="vertical_relaxed")
        pred_df = pl.concat(pred_dfs, how="vertical_relaxed")
    else:
        raise TypeError("'ground_truth' must be a Dataset or a Subsets object.")

    # Join on (sequence, variables) to align predictions with ground truth
    # Only use variables that are actually present and non-null in both DataFrames
    if isinstance(ground_truth, Subsets):
        declared_variable_names = [v.name for v in ground_truth.dataset.assay_variables]
    else:
        declared_variable_names = [v.name for v in ground_truth.assay_variables]

    variable_names = [
        var for var in declared_variable_names
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


def metric_recovery(
    ground_truth: Subsets | Dataset,
    predicted: Dataset,
    target: str,
    split: str | None = None,
    fold: int | list[int] | None = None,
) -> float | None:
    """Compute the recovery metric: percentage of top-k variants correctly identified.

    The recovery metric measures what percentage of the true top-k highest-value
    variants (from ground truth) are also ranked in the top-k of predictions. This
    metric is useful for evaluating whether a model successfully identifies the best
    candidates, which is often more important than precise value prediction.

    The top-k threshold is retrieved from the dataset slice metadata. If the metadata
    does not contain a "top_k" value, or if ground_truth is a plain Dataset (not
    Subsets), the metric returns None.

    Args:
        ground_truth: The ground truth data, either as a complete Dataset or
            a Subsets object containing dataset slices.
        predicted: The predicted Dataset containing model predictions for the target.
        target: The name of the target variable to score (e.g., 'fitness').
        split: Required when ground_truth is a Subsets object. The name of the
            splitting strategy to evaluate (e.g., 'random').
        fold: Required when ground_truth is a Subsets object. The fold index
            (0-based integer) within the specified split. Must be a single integer,
            not a list.

    Returns:
        The recovery percentage (0.0 to 1.0) representing the fraction of true top-k
        variants that appear in the predicted top-k, or None if top_k is not available
        or if top_k is invalid (e.g., <= 0).

    Raises:
        TypeError: If ground_truth is neither a Dataset nor a Subsets object.
        ValueError: If split or fold is None when ground_truth is a Subsets object.
        ValueError: If any ground truth records lack corresponding predictions.

    Examples:
        >>> # Score predictions on a cross-validation fold with top_k metadata
        >>> recovery = metric_recovery(
        ...     ground_truth=cv_subsets,
        ...     predicted=predictions_dataset,
        ...     target='fitness',
        ...     split='test',
        ...     fold=0
        ... )
        >>> print(f"Recovery: {recovery:.2%}")
        Recovery: 85.00%
    """
    top_k = _get_top_k_from_slice(ground_truth, split, fold)
    if top_k is None:
        return None

    scoring_df = prepare_and_validate_scoring_df(
        ground_truth, predicted, target, split, fold
    )

    gt_values = scoring_df[target].to_numpy()
    pred_values = scoring_df[f"{target}_pred"].to_numpy()

    n_samples = len(gt_values)
    if top_k > n_samples:
        warnings.warn(
            f"top_k ({top_k}) is larger than the number of samples ({n_samples}). "
            f"Using effective_k={n_samples} instead.",
            UserWarning,
            stacklevel=2
        )
    effective_k = min(top_k, n_samples)

    if effective_k <= 0:
        return None

    top_k_gt_indices = set(np.argsort(gt_values)[-effective_k:])
    top_k_pred_indices = set(np.argsort(pred_values)[-effective_k:])

    overlap = len(top_k_gt_indices & top_k_pred_indices)
    return overlap / effective_k


def metric_spearman(
    ground_truth: Subsets | Dataset,
    predicted: Dataset,
    target: str,
    split: str | None = None,
    fold: int | list[int] | None = None,
) -> float:
    """Compute the Spearman rank correlation coefficient between ground truth and
    predictions.

    The Spearman correlation assesses how well the relationship between ground truth
    and predicted values can be described using a monotonic function. It measures the
    strength and direction of association between the ranked versions of the values.

    This metric is rank-based and robust to outliers, making it suitable for
    evaluating model predictions when the absolute scale matters less than the
    relative ordering of samples.

    Args:
        ground_truth: The ground truth data, either as a complete Dataset or
            a Subsets object containing dataset slices.
        predicted: The predicted Dataset containing model predictions for the target.
        target: The name of the target variable to score (e.g., 'fitness').
        split: Required when ground_truth is a Subsets object. The name of the
            splitting strategy to evaluate (e.g., 'random').
        fold: Required when ground_truth is a Subsets object. The fold index
            (0-based integer) within the specified split.

    Returns:
        The Spearman rank correlation coefficient, ranging from -1 to 1:
            - 1.0 indicates a perfect positive monotonic relationship
            - -1.0 indicates a perfect negative monotonic relationship
            - 0.0 indicates no monotonic relationship

    Raises:
        TypeError: If ground_truth is neither a Dataset nor a Subsets object.
        ValueError: If split or fold is None when ground_truth is a Subsets object.
        ValueError: If any ground truth records lack corresponding predictions.

    Examples:
        >>> # Score predictions on a test dataset
        >>> correlation = metric_spearman(
        ...     ground_truth=test_dataset,
        ...     predicted=predictions_dataset,
        ...     target='fitness'
        ... )
        >>> # Score predictions on a specific dataset slice
        >>> correlation = metric_spearman(
        ...     ground_truth=cv_subsets,
        ...     predicted=predictions_dataset,
        ...     target='fitness',
        ...     split='test',
        ...     fold=0
        ... )
    """
    scoring_df = prepare_and_validate_scoring_df(
        ground_truth, predicted, target, split, fold
    )
    gt_values = scoring_df[target].to_numpy()
    pred_values = scoring_df[f"{target}_pred"].to_numpy()
    spearman_corr, _ = spearmanr(gt_values, pred_values)
    return spearman_corr


def calculate_selected_metrics(
    selected_metrics: list[str],
    ground_truth: Subsets | Dataset,
    predicted: Dataset,
    target: str,
    split: str | None = None,
    fold: int | list[int] | None = None,
) -> dict[str, float]:
    """Calculate selected metrics by comparing ground truth and predictions.

    This function dynamically discovers all functions with the 'metric_' prefix in the
    current module and executes the requested ones. Each metric function receives the
    ground truth, predictions, and scoring parameters.

    To add a new custom metric, define a function following this pattern:
        ```python
        def metric_<name>(
            ground_truth: Subsets | Dataset,
            predicted: Dataset,
            target: str,
            split: str | None = None,
            fold: int | None = None
        ) -> float:
            # Your metric calculation logic here
            return metric_value
        ```

    Args:
        selected_metrics: List of metric names to calculate (e.g., ["spearman"]).
            Names should match the function suffix after 'metric_'.
        ground_truth: The ground truth data, either as a complete Dataset or
            a Subsets object containing dataset slices.
        predicted: The predicted Dataset containing model predictions for the target.
        target: The name of the target variable to score (e.g., 'fitness').
        split: Required when ground_truth is a Subsets object. The name of the
            splitting strategy to evaluate (e.g., 'random').
        fold: Required when ground_truth is a Subsets object. The fold index
            (0-based integer) within the specified split.

    Returns:
        Dictionary mapping metric names to their computed values. For example:
            {"spearman": 0.85, "pearson": 0.82}

    Examples:
        >>> # Calculate metrics on a complete dataset
        >>> metrics = calculate_selected_metrics(
        ...     selected_metrics=["spearman"],
        ...     ground_truth=test_dataset,
        ...     predicted=predictions_dataset,
        ...     target='fitness'
        ... )
        >>> # Calculate metrics on a dataset slice
        >>> metrics = calculate_selected_metrics(
        ...     selected_metrics=["spearman", "pearson"],
        ...     ground_truth=cv_subsets,
        ...     predicted=predictions_dataset,
        ...     target='fitness',
        ...     split='test',
        ...     fold=0
        ... )
    """
    current_module = sys.modules[__name__]
    global metric_functions

    for name, obj in inspect.getmembers(current_module, inspect.isfunction):
        if name.startswith("metric_"):
            metric_name = name.replace("metric_", "", 1)
            metric_functions[metric_name] = obj

    results = {}

    for metric_name in selected_metrics:
        if metric_name in metric_functions:
            metric_value = metric_functions[metric_name](
                ground_truth, predicted, target, split, fold
            )
            results[metric_name] = metric_value
        else:
            print(f"Warning: Metric '{metric_name}' not found in available metrics")

    return results


def calculate_metrics_by_mode(
    selected_metrics: list[str],
    ground_truth: Subsets,
    predicted: Dataset,
    target: str,
    split: str,
    test_fold: int,
    score_modes: list[str] | None = None,
) -> dict[str, dict[str, float]]:
    """Calculate metrics in different scoring modes (test, train_available, per_fold).

    Args:
        selected_metrics: List of metric names to calculate (e.g., ["spearman"]).
        ground_truth: The Subsets object containing cross-validation splits.
        predicted: The predicted Dataset containing model predictions.
        target: The name of the target variable to score.
        split: The name of the splitting strategy (e.g., 'random', 'kfold_random').
        test_fold: The fold index designated as the test fold.
        score_modes: List of scoring modes. Options:
            - "test": Score only the test fold
            - "train_available": Score all non-test folds in aggregate
            - "per_fold": Score each fold individually
            If None, defaults to ["test", "train_available", "per_fold"].

    Returns:
        Dictionary with structure:
            {
                "test": {"spearman": 0.85, ...},
                "train_available": {"spearman": 0.92, ...},
                "per_fold": {
                    "fold_0": {"spearman": 0.91, ...},
                    "fold_1": {"spearman": 0.93, ...},
                    ...
                },
                "metadata": {
                    "test_folds": [4],
                    "train_available_folds": [0, 1, 2, 3],
                    "total_folds": 5
                }
            }
    """
    if score_modes is None:
        score_modes = ["test", "train_available", "per_fold"]

    all_fold_indices = get_fold_indices(ground_truth, split)
    train_folds = [f for f in all_fold_indices if f != test_fold]

    results = {}

    if "test" in score_modes:
        results["test"] = calculate_selected_metrics(
            selected_metrics, ground_truth, predicted, target, split, test_fold
        )

    if "train_available" in score_modes:
        results["train_available"] = calculate_selected_metrics(
            selected_metrics, ground_truth, predicted, target, split, train_folds
        )

    if "per_fold" in score_modes:
        results["per_fold"] = {}
        for fold_idx in all_fold_indices:
            fold_metrics = calculate_selected_metrics(
                selected_metrics, ground_truth, predicted, target, split, fold_idx
            )
            results["per_fold"][f"fold_{fold_idx}"] = fold_metrics

    results["metadata"] = {
        "test_folds": [test_fold],
        "train_available_folds": train_folds,
        "total_folds": len(all_fold_indices),
    }

    return results


def evaluate(
    prediction_path: Path,
    metric_path: Path,
    dataset_path: Path | None = None,
    selected_metrics: list[str] | None = None,
    model_name: str | None = None,
    split: str | None = None,
    target: str | None = None,
    fold: str | None = None,
    score_modes: list[str] | None = None,
) -> Path:
    """Calculate performance metrics from predictions and save results to JSON.

    Loads ground truth data from a dataset archive (.pgdata or .splits.pgdata),
    loads predictions from a prediction archive, calculates the selected metrics,
    and saves the results to a JSON file with metadata.

    The function automatically detects whether the dataset is a plain Dataset
    (.pgdata) or Subsets (.splits.pgdata) based on the file extension.

    If the prediction file is not found, an error JSON with null metric values
    is written instead.

    Args:
        prediction_path: Path to the prediction dataset archive (.pgdata file)
            containing model predictions.
        metric_path: Path where the calculated metrics JSON will be saved.
        dataset_path: Path to the ground truth dataset archive. Can be either:
            - .pgdata file: Plain Dataset for single-fold scoring
            - .splits.pgdata file: Subsets with cross-validation splits
        selected_metrics: Optional list of metric names to calculate (e.g., ["spearman"]).
            If None, all discovered metrics are included.
        model_name: Name of the model that generated predictions (stored in metadata).
        split: Name of the splitting strategy to evaluate (e.g., 'random', 'kfold_random').
            Required when dataset_path is a .splits.pgdata file.
        target: Name of the target variable to score (e.g., 'DMS_score', 'fitness').
            Required for all metric calculations.
        fold: Fold index (as string) designated as the test fold.
            Required when dataset_path is a .splits.pgdata file.
        score_modes: List of scoring modes. Options: "test", "train_available", "per_fold".
            If None, defaults to ["test", "train_available", "per_fold"].
            Only used when dataset_path is a .splits.pgdata file.

    Returns:
        The path to the saved metrics JSON file (same as metric_path input).

    Examples:
        >>> # Evaluate predictions on a test fold
        >>> evaluate(
        ...     prediction_path=Path("predictions.pgdata"),
        ...     metric_path=Path("metrics.json"),
        ...     dataset_path=Path("dataset.splits.pgdata"),
        ...     selected_metrics=["spearman"],
        ...     model_name="ESM2",
        ...     split="test",
        ...     target="fitness",
        ...     fold="0"
        ... )
        PosixPath('metrics.json')

    Notes:
        The output JSON has the structure:
            {
                "test": {
                    "spearman": 0.85
                },
                "train_available": {
                    "spearman": 0.92
                },
                "per_fold": {
                    "fold_0": {"spearman": 0.91},
                    "fold_1": {"spearman": 0.93},
                    ...
                },
                "metadata": {
                    "dataset": "dataset_name",
                    "model": "model_name",
                    "split": "random",
                    "target": "fitness",
                    "test_fold": 0,
                    "test_folds": [0],
                    "train_available_folds": [1, 2, 3, 4],
                    "total_folds": 5
                }
            }
    """

    print("Start to calculate metrics.")

    if not prediction_path.exists():
        print(f"Error: Prediction file not found: {prediction_path}", file=sys.stderr)
        error_result = {
            "error": f"Prediction file not found: {prediction_path}",
            "status": "failed",
        }

        if selected_metrics:
            for metric_name in selected_metrics:
                error_result[metric_name] = None

        metric_path.write_text(json.dumps(error_result, indent=2))
        return metric_path

    if dataset_path.name.endswith('.splits.pgdata'):
        ground_truth = Subsets.from_path(dataset_path)
        is_subsets = True
    else:
        ground_truth = Dataset.from_path(dataset_path)
        is_subsets = False

    predicted = Dataset.from_path(prediction_path)

    if is_subsets:
        if split is None or fold is None or target is None:
            raise ValueError(
                "Parameters --split, --fold, and --target are required when dataset_path "
                "is a Subsets file (.splits.pgdata)."
            )

        test_fold = int(fold)

        metrics_result = calculate_metrics_by_mode(
            selected_metrics, ground_truth, predicted, target, split, test_fold, score_modes
        )
    else:
        if target is None:
            raise ValueError(
                "The 'target' parameter is required for metric calculation. "
                "Please provide --target."
            )

        metrics_result = {
            "full_dataset": calculate_selected_metrics(
                selected_metrics, ground_truth, predicted, target, None, None
            )
        }

    dataset_name = dataset_path.stem
    if any([dataset_name, model_name, split, target, fold]):
        if "metadata" not in metrics_result:
            metrics_result["metadata"] = {}
        if dataset_name:
            metrics_result["metadata"]["dataset"] = dataset_name
        if model_name:
            metrics_result["metadata"]["model"] = model_name
        if split:
            metrics_result["metadata"]["split"] = split
        if target:
            metrics_result["metadata"]["target"] = target
        if fold:
            metrics_result["metadata"]["test_fold"] = test_fold

    result_with_metadata = metrics_result

    metric_path.parent.mkdir(parents=True, exist_ok=True)
    metric_path.write_text(json.dumps(result_with_metadata, indent=2))
    return metric_path


def main():
    parser = argparse.ArgumentParser(
        description="Calculate metric for ProteinGym benchmark evaluation."
    )

    parser.add_argument(
        "--prediction-path",
        type=Path,
        required=True,
        help="Path to the prediction dataset archive (.pgdata file)",
    )
    parser.add_argument(
        "--metric-path",
        type=Path,
        required=True,
        help="Path where the calculated metrics JSON will be saved",
    )
    parser.add_argument(
        "--selected-metrics",
        type=str,
        nargs="*",
        default=None,
        help="Optional list of metric names to calculate (e.g., 'spearman'). If not specified, all metrics are calculated.",
    )
    parser.add_argument(
        "--dataset-path",
        type=Path,
        default=None,
        help="Path to the ground truth dataset archive (.pgdata or .splits.pgdata file)",
    )
    parser.add_argument(
        "--model-name",
        type=str,
        default=None,
        help="Model name for metadata",
    )
    parser.add_argument(
        "--split",
        type=str,
        default=None,
        help="Name of the splitting strategy (e.g., 'random', 'kfold_random'). Required for .splits.pgdata files.",
    )
    parser.add_argument(
        "--target",
        type=str,
        default=None,
        help="Name of the target variable to score (e.g., 'DMS_score', 'fitness'). Required.",
    )
    parser.add_argument(
        "--fold",
        type=str,
        default=None,
        help="Fold index designated as the test fold",
    )
    parser.add_argument(
        "--score-modes",
        type=str,
        nargs="*",
        default=None,
        help="Scoring modes to calculate (e.g., 'test' 'train_available' 'per_fold'). If not specified, all modes are calculated.",
    )

    args = parser.parse_args()

    return evaluate(
        prediction_path=args.prediction_path,
        metric_path=args.metric_path,
        selected_metrics=args.selected_metrics,
        dataset_path=args.dataset_path,
        model_name=args.model_name,
        split=args.split,
        target=args.target,
        fold=args.fold,
        score_modes=args.score_modes,
    )


if __name__ == "__main__":
    print(f"Metrics have been saved to {main()}.")
