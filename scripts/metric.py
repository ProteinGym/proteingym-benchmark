"""
Metric calculation script for ProteinGym benchmark evaluation.

This script provides functionality to calculate performance metrics for machine learning models
by comparing actual and predicted values. It computes both classification metrics (via confusion
matrix) and correlation metrics (Spearman correlation) from CSV output files.

The main function `calc` reads prediction results from a CSV file, generates a confusion matrix
with comprehensive classification statistics, calculates Spearman correlation between actual
and predicted values, and outputs all metrics to a CSV file for further analysis.

Example output CSV:
    | Metric       | Value      |
    |--------------|------------|
    | Overall ACC  | 0.85       |
    | PPV Macro    | 'None'     |
    | Kappa 95% CI | (0.0, 0.0) |
    | Spearman     | 0.82       |

Functions:
    calc: Calculate and save performance metrics from prediction output files
"""

import argparse
from pathlib import Path

import polars as pl
from pycm import ConfusionMatrix
from scipy.stats import spearmanr


def calc(output: Path, metric: Path, actual_vector_col: str, predict_vector_col: str):
    """Calculate performance metrics from prediction output and save to CSV.

    Reads prediction results from a CSV file, computes classification metrics using
    a confusion matrix and calculates Spearman correlation between actual and
    predicted values. All metrics are saved to a CSV file.

    Args:
        output: Path to the CSV file containing prediction results
        metric: Path where the calculated metrics CSV will be saved
        actual_vector_col: Column name containing actual/ground truth values
        predict_vector_col: Column name containing predicted values
    """

    output_dataframe = pl.read_csv(output)

    cm = ConfusionMatrix(
        actual_vector=output_dataframe[actual_vector_col].to_list(),
        predict_vector=output_dataframe[predict_vector_col].to_list(),
    )

    correlation, p_value = spearmanr(
        a=output_dataframe[actual_vector_col].to_list(),
        b=output_dataframe[predict_vector_col].to_list(),
    )

    metrics_data = [
        {"metric_name": key, "metric_value": str(value)}
        for key, value in list(cm.overall_stat.items()) + [("Spearman", correlation)]
    ]

    metric_dataframe = pl.DataFrame(
        data=metrics_data,
        schema={"metric_name": pl.String, "metric_value": pl.String},
    )

    metric_dataframe.write_csv(metric)


def main():
    parser = argparse.ArgumentParser(
        description="Calculate metric for ProteinGym benchmark evaluation."
    )

    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="Path to the CSV file containing prediction results",
    )
    parser.add_argument(
        "--metric",
        type=Path,
        required=True,
        help="Path where the calculated metrics CSV will be saved",
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

    args = parser.parse_args()

    calc(
        output=args.output,
        metric=args.metric,
        actual_vector_col=args.actual_vector_col,
        predict_vector_col=args.predict_vector_col,
    )


if __name__ == "__main__":
    main()
