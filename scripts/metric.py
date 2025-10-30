"""
Metric calculation script for ProteinGym benchmark evaluation.

This script provides a flexible, extensible framework for calculating performance metrics
for machine learning models by comparing actual and predicted values. It uses dynamic metric
discovery to automatically detect and execute custom metric functions.

The script reads prediction results from CSV files and computes selected metrics using
a plugin-style architecture: any function with the 'metric_' prefix is automatically
discovered and made available for calculation. Results are saved as JSON for further analysis.

Key Features:
    - Dynamic metric discovery: Add new metrics by defining 'metric_<name>' functions
    - Selective metric calculation: Choose which metrics to compute via command-line args
    - JSON output format for easy integration with analysis pipelines
    - Built-in support for correlation metrics (e.g., Spearman)

Example Usage:
    ```bash
    python metric.py actual_column predicted_col \\
        --prediction predictions.csv \\
        --metric metrics.json \\
        --selected-metrics spearman
    ```

Example metric JSON output:
    ```json
    {
        "Average Spearman": 0.8542
    }
    ```

Adding Custom Metrics:
    To add a new metric, define a function following this pattern:
    ```python
    def metric_<name>(actual_values: list[float], predicted_values: list[float]) -> tuple[str, float]:
        result = custom_calculation(actual_values, predicted_values)
        return ("Metric Display Name", result)
    ```

Functions:
    metric_spearman: Calculate Spearman rank correlation coefficient
    calculate_selected_metrics: Dynamically discover and execute selected metric functions
    evaluate: Calculate and save performance metrics from prediction CSV to JSON format
    main: Command-line interface for metric calculation
"""

import inspect
import sys
import argparse
import json
from pathlib import Path

import polars as pl
from scipy.stats import spearmanr


def metric_spearman(
    actual_values: list[float],
    predicted_values: list[float],
) -> tuple[str, float]:
    spearman_corr, _ = spearmanr(actual_values, predicted_values)
    return ("Average Spearman", spearman_corr)

def calculate_selected_metrics(
    actual_values: list[float],
    predicted_values: list[float],
    selected_metrics: list[str],
) -> dict[str, float]:
    """Calculate selected custom metrics using auto-discovered metric functions.

    This function dynamically discovers all functions with the 'metric_' prefix in the
    current module and calls the requested ones. To add a new custom metric, define a
    function with the naming pattern 'metric_<name>' that takes actual_values and
    predicted_values as parameters and returns a tuple of (metric_name, metric_value).

    Example:
        def metric_custom(actual_values, predicted_values):
            result = custom_calculation(actual_values, predicted_values)
            return ("Custom Metric Name", result)

    Args:
        actual_values: List of actual/ground truth values
        predicted_values: List of predicted values
        selected_metrics: List of metric names to calculate (e.g., ["spearman"])
                         Names should match the function suffix after 'metric_'

    Returns:
        Dictionary mapping metric names to their computed values
    """
    current_module = sys.modules[__name__]
    metric_functions = {}

    for name, obj in inspect.getmembers(current_module, inspect.isfunction):
        if name.startswith("metric_"):
            metric_name = name.replace("metric_", "", 1)
            metric_functions[metric_name] = obj

    results = {}

    for metric_name in selected_metrics:
        if metric_name in metric_functions:
            metric_key, metric_value = metric_functions[metric_name](
                actual_values, predicted_values
            )
            results[metric_key] = metric_value
        else:
            print(f"Warning: Metric '{metric_name}' not found in available metrics")

    return results


def evaluate(
    actual_column: str,
    predict_column: str,
    prediction: Path,
    metric: Path,
    selected_metrics: list[str] | None = None,
) -> Path:
    """Calculate performance metrics from prediction output and save to metric JSON formats.

    Reads prediction results from a CSV file, computes classification metrics using
    a confusion matrix. All metrics are saved to a JSON file.

    Args:
        actual_column: Column name containing actual/ground truth values
        predict_column: Column name containing predicted values
        prediction: Path to the CSV file containing prediction results
        metric: Path where the calculated metrics JSON will be saved
        selected_metrics: Optional list of metric names to include. If None, all metrics are included.

    Returns:
        Metric path where the files were saved
    """

    print("Start to calculate metrics.")

    prediction_dataframe = pl.read_csv(prediction).drop_nulls()

    actual_values = prediction_dataframe[actual_column].to_list()
    predicted_values = prediction_dataframe[predict_column].to_list()
    
    selected_metrics = calculate_selected_metrics(actual_values, predicted_values, selected_metrics)

    metric_data = {
        key: str(value) for key, value in selected_metrics.items()
    }

    metric.write_text(json.dumps(metric_data, indent=2))

    return metric


def main():
    parser = argparse.ArgumentParser(
        description="Calculate metric for ProteinGym benchmark evaluation."
    )

    parser.add_argument(
        "actual_column",
        type=str,
        help="Column name containing actual/ground truth values",
    )
    parser.add_argument(
        "predict_column",
        type=str,
        help="Column name containing predicted values",
    )
    parser.add_argument(
        "--prediction",
        type=Path,
        required=True,
        help="Path to the CSV file containing prediction results",
    )
    parser.add_argument(
        "--metric",
        type=Path,
        required=True,
        help="Path where the calculated metrics JSON will be saved",
    )
    parser.add_argument(
        "--selected-metrics",
        type=str,
        nargs="*",
        default=None,
        help="Optional list of metric names to include (e.g., 'Overall ACC' 'F1 Macro'). If not specified, all metrics are included.",
    )

    args = parser.parse_args()
    
    return evaluate(
        actual_column=args.actual_column,
        predict_column=args.predict_column,
        prediction=args.prediction,
        metric=args.metric,
        selected_metrics=args.selected_metrics,
    )


if __name__ == "__main__":
    print(f"Metrics have been saved to {main()}.")
