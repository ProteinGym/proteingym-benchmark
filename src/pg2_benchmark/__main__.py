import logging
from pathlib import Path
from typing import Annotated

import typer

from pg2_benchmark.cli.dataset import dataset_app
from pg2_benchmark.cli.metric import metric_app
from pg2_benchmark.cli.sagemaker import sagemaker_app
from pg2_benchmark.logger import setup_logger
from pg2_benchmark.model_card import ModelCard

app = typer.Typer(
    name="benchmark",
    help="ProteinGym2 - Benchmark CLI",
    add_completion=False,
)

app.add_typer(dataset_app, name="dataset", help="Dataset operations")
app.add_typer(metric_app, name="metric", help="Metric operations")
app.add_typer(sagemaker_app, name="sagemaker", help="SageMaker operations")

setup_logger()
logger = logging.getLogger("pg2_benchmark")


class ModelPath:
    """Configuration class for model-related file paths.

    This class defines standard paths used throughout the benchmark system
    for locating model-related files and configurations.
    """

    MODEL_CARD_PATH = Path("README.md")
    """Default location for model card files relative to model root directory."""


@app.command()
def validate(
    model_name: Annotated[
        str, typer.Argument(help="Model name defined in the model card")
    ],
    model_path: Annotated[
        Path,
        typer.Argument(
            help="Root path containting the model source code and model card",
            exists=True,
            dir_okay=True,
            file_okay=False,
        ),
    ],
):
    model_card_path = model_path / ModelPath.MODEL_CARD_PATH

    if not model_card_path.exists():
        logger.error(
            f"❌ Model {model_name} does not have a model card at {str(model_card_path)}"
        )
        raise typer.Exit(1)

    try:
        model_card = ModelCard.from_path(model_card_path)
        logger.info(
            f"✅ Loaded {model_card.name} with hyper parameters {model_card.hyper_params}."
        )
    except Exception as e:
        logger.error(f"❌ Error loading model card from {str(model_card_path)}: {e}")
        raise typer.Exit(1)


if __name__ == "__main__":
    app()
