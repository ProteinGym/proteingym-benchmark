import logging

import pandas as pd
import typer
from pycm import ConfusionMatrix
from scipy.stats import spearmanr

metric_app = typer.Typer()


@metric_app.command()
def calc(
    output_path: str = typer.Option(help="Path to the model output file"),
    metric_path: str = typer.Option(help="Path to the metric output file"),
):
    logger = logging.getLogger("pg2_benchmark")
    logger.info("Calculating metrics...")

    df = pd.read_csv(output_path)

    cm = ConfusionMatrix(
        actual_vector=df["test"].tolist(), predict_vector=df["pred"].tolist()
    )

    correlation, p_value = spearmanr(df["test"].tolist(), df["pred"].tolist())

    df = pd.DataFrame(
        list(cm.overall_stat.items()) + [("Spearman", correlation)],
        columns=["Metric", "Value"],
    )

    df.to_csv(metric_path, index=False)

    logger.info(f"Metrics saved to {metric_path}.")
