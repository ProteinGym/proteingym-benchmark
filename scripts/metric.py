"""
Metric calculation script for ProteinGym benchmark evaluation.

This script provides functionality to calculate performance metrics for machine learning models
by comparing actual and predicted values. It computes classification metrics via confusion
matrix from CSV output files.

The main function `calc` reads prediction results from a CSV file, generates a confusion matrix
with comprehensive classification statistics, and outputs all metrics to both a JSON file for
further analysis and a plot-ready JSON file for visualization.

Example metric JSON output:
    {
        "Overall ACC": "0.85",
        "PPV Macro": "None",
        "Kappa 95% CI": "(0.0, 0.0)",
        ...
    }

Example plot JSON output:
    [
        {"metric": "Overall ACC", "value": 0.85},
        {"metric": "F1 Macro", "value": "None"},
        {"metric": "Kappa 95% CI", "value": "(0.0, 0.0)"},
        ...
    ]

Functions:
    calculate_all_metrics: Calculate all metrics including confusion matrix and custom metrics
    calc: Calculate and save performance metrics from prediction output files to metric and plot JSON formats
"""

import argparse
import json
from pathlib import Path

import polars as pl
from pycm import ConfusionMatrix
from scipy.stats import spearmanr


def calculate_all_metrics(
    actual_values: list,
    predicted_values: list,
) -> dict:
    """Calculate all metrics including confusion matrix metrics and custom metrics.

    This function computes both standard classification metrics via confusion matrix
    and custom metrics like Spearman correlation. Add new custom metrics here.

    Args:
        actual_values: List of actual/ground truth values
        predicted_values: List of predicted values

    Returns:
        Dictionary of metric names and their values
    """
    # Calculate confusion matrix metrics
    cm = ConfusionMatrix(
        actual_vector=actual_values,
        predict_vector=predicted_values,
    )

    all_metrics = dict(cm.overall_stat.items())

    # Calculate custom metrics
    # Add your custom metrics below this line

    # Custom metric: Average Spearman correlation
    spearman_corr, _ = spearmanr(actual_values, predicted_values)
    all_metrics["Average Spearman"] = spearman_corr

    return all_metrics


def calc(
    prediction: Path,
    metric: Path,
    plot: Path,
    actual_vector_col: str,
    predict_vector_col: str,
    selected_metrics: list[str] | None = None,
) -> Path:
    """Calculate performance metrics from prediction output and save to metric and plot JSON formats.

    Reads prediction results from a CSV file, computes classification metrics using
    a confusion matrix. All metrics are saved to a JSON file, and a plot-ready version
    is saved to a separate JSON file for visualization purposes.

    Args:
        prediction: Path to the CSV file containing prediction results
        metric: Path where the calculated metrics JSON will be saved
        plot: Path where the plot-ready metrics JSON will be saved
        actual_vector_col: Column name containing actual/ground truth values
        predict_vector_col: Column name containing predicted values
        selected_metrics: Optional list of metric names to include. If None, all metrics are included.

    Returns:
        Tuple of (metric, plot) paths where the files were saved
    """

    print("Start to calculate metrics.")

    prediction_dataframe = pl.read_csv(prediction).drop_nulls()

    actual_values = prediction_dataframe[actual_vector_col].to_list()
    predicted_values = prediction_dataframe[predict_vector_col].to_list()
    
    all_metrics = calculate_all_metrics(actual_values, predicted_values)

    if selected_metrics:
        filtered_metrics = {
            key: value for key, value in all_metrics.items() if key in selected_metrics
        }
    else:
        filtered_metrics = all_metrics

    metric_data = {
        key: str(value) for key, value in filtered_metrics.items()
    }

    with open(metric, "w") as f:
        json.dump(metric_data, f, indent=2)


    plot_data = [
        {"metric": key, "value": str(value)}
        for key, value in filtered_metrics.items()
    ]

    pl.DataFrame(data=plot_data, schema={"metric": pl.String, "value": pl.String}).write_json(plot)

    return metric, plot


def main():
    parser = argparse.ArgumentParser(
        description="Calculate metric for ProteinGym benchmark evaluation."
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
        "--plot",
        type=Path,
        required=True,
        help="Path where the plot-ready metrics JSON will be saved",
    )
    parser.add_argument(
        "--actual-vector-col",
        type=str,
        required=True,
        help="Column name containing actual/ground truth values",
    )
    parser.add_argument(
        "--predict-vector-col",
        type=str,
        required=True,
        help="Column name containing predicted values",
    )
    parser.add_argument(
        "--selected-metrics",
        type=str,
        nargs="*",
        default=None,
        help="Optional list of metric names to include (e.g., 'Overall ACC' 'F1 Macro'). If not specified, all metrics are included.",
    )

    args = parser.parse_args()

    return calc(
        prediction=args.prediction,
        metric=args.metric,
        plot=args.plot,
        actual_vector_col=args.actual_vector_col,
        predict_vector_col=args.predict_vector_col,
        selected_metrics=args.selected_metrics,
    )


if __name__ == "__main__":
    print(f"Metrics and plots have been saved to {main()}.")
