"""
Metric calculation script for ProteinGym benchmark evaluation.

This script provides a flexible, extensible framework for calculating performance metrics
for machine learning models by comparing actual and predicted values. It uses dynamic metric
discovery to automatically detect and execute custom metric functions.

The script reads prediction results from CSV files and computes selected metrics using
a plugin-style architecture: any function with the 'metric_' prefix is automatically
discovered and made available for calculation. Results are saved as JSON for further analysis.

Example Usage:
    ```bash
    python metric.py actual_column predicted_column \\
        --prediction predictions.csv \\
        --metric metrics.json \\
        --selected-metrics spearman
    ```

Example metric JSON output:
    ```json
    {
        "spearman": 0.8542
    }
    ```

Adding Custom Metrics:
    To add a new metric, define a function following this pattern:
    ```python
    def metric_<name>(actual_values: list[float], predicted_values: list[float]) -> float:
        result = custom_calculation(actual_values, predicted_values)
        return result
    ```
"""

import inspect
import sys
import argparse
import json
from pathlib import Path

import polars as pl
from scipy.stats import spearmanr

metric_functions = {}


def metric_spearman(
    actual_values: list[float],
    predicted_values: list[float],
) -> float:
    """
    Compute the Spearman rank correlation coefficient between two lists of values.

    The Spearman correlation assesses how well the relationship between two variables
    can be described using a monotonic function. It measures the strength and direction
    of association between the ranked versions of the input variables.

    Args:
        actual_values: A list of actual values.
        predicted_values: A list of predicted values.

    Returns:
        float: The Spearman rank correlation coefficient, ranging from -1 to 1.
            - 1 indicates a perfect positive monotonic relationship.
            - -1 indicates a perfect negative monotonic relationship.
            - 0 indicates no monotonic relationship.

    Example:
        >>> metric_spearman([1, 2, 3, 4], [10, 20, 30, 40])
        1.0
    """
    spearman_corr, _ = spearmanr(actual_values, predicted_values)
    return spearman_corr

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
            return result

    Args:
        actual_values: List of actual/ground truth values
        predicted_values: List of predicted values
        selected_metrics: List of metric names to calculate (e.g., ["spearman"])
                         Names should match the function suffix after 'metric_'

    Returns:
        Dictionary mapping metric names to their computed values
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
                actual_values, predicted_values
            )
            results[metric_name] = metric_value
        else:
            print(f"Warning: Metric '{metric_name}' not found in available metrics")

    return results


def evaluate(
    prediction_path: Path,
    metric_path: Path,
    selected_metrics: list[str] | None = None,
) -> Path:
    """Calculate performance metrics from prediction output and save to metric JSON formats.

    Reads prediction results from a CSV file, computes classification metrics using
    a confusion matrix. All metrics are saved to a JSON file.

    Args:
        prediction_path: Path to the CSV file containing prediction results
        metric_path: Path where the calculated metrics JSON will be saved
        selected_metrics: Optional list of metric names to include. If None, all metrics are included.

    Returns:
        Metric path where the files were saved
    """

    print("Start to calculate metrics.")

    prediction_dataframe = pl.read_csv(prediction_path).drop_nulls()

    actual_values = prediction_dataframe.to_series(0)
    predicted_values = prediction_dataframe.to_series(1)
    
    selected_metrics = calculate_selected_metrics(actual_values, predicted_values, selected_metrics)

    metric_path.write_text(json.dumps(selected_metrics, indent=2))

    return metric_path


def main():
    parser = argparse.ArgumentParser(
        description="Calculate metric for ProteinGym benchmark evaluation."
    )

    parser.add_argument(
        "--prediction-path",
        type=Path,
        required=True,
        help="Path to the CSV file containing prediction results",
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
        help="Optional list of metric names to include (e.g., 'Overall ACC' 'F1 Macro'). If not specified, all metrics are included.",
    )

    args = parser.parse_args()
    
    return evaluate(
        prediction_path=args.prediction_path,
        metric_path=args.metric_path,
        selected_metrics=args.selected_metrics,
    )


if __name__ == "__main__":
    print(f"Metrics have been saved to {main()}.")
