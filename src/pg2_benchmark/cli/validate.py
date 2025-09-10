from pathlib import Path
from typing import Annotated

import typer

from pg2_benchmark.model_card import ModelCard

validate_app = typer.Typer()


class ModelPath:
    ROOT_PATH = Path("models")
    SRC_PATH = Path("src")
    PACKAGE_PREFIX = "pg2_model"
    MODEL_CARD_PATH = Path("README.md")
    MAIN_PY_PATH = Path("__main__.py")
    APP_NAME = "app"
    COMMAND_NAME = "train"
    COMMAND_PARAMS = ["dataset_file", "model_card_file"]


@validate_app.command()
def model_card(
    model_name: Annotated[
        str, typer.Argument(help="The model name listed in the `models` folder")
    ],
):
    model_card_path = ModelPath.ROOT_PATH / model_name / ModelPath.MODEL_CARD_PATH

    if not model_card_path.exists():
        typer.echo(
            f"❌ Model {model_name} does not have a model card at {model_card_path}"
        )
        raise typer.Exit(1)

    try:
        model_card = ModelCard.from_path(model_card_path)
        typer.echo(
            f"✅ Loaded {model_card.name} with hyper parameters {model_card.hyper_params}."
        )
    except Exception as e:
        typer.echo(f"❌ Error loading model card from {model_card_path}: {e}")
        raise typer.Exit(1)
