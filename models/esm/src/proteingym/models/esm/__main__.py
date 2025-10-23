from pathlib import Path
from typing import Annotated

import typer
from proteingym.base import Dataset
from proteingym.base.model import ModelCard
from rich.console import Console

from .model import infer, load

app = typer.Typer(
    help="ProteinGym2 - Model CLI",
    add_completion=True,
)

console = Console()


class SageMakerTrainingJobPath:
    PREFIX = Path("/opt/ml")
    TRAINING_JOB_PATH = PREFIX / "input" / "data" / "training" / "dataset.pgdata"
    MODEL_CARD_PATH = PREFIX / "input" / "data" / "model_card" / "README.md"
    OUTPUT_PATH = PREFIX / "model"


@app.command()
def train(
    dataset_file: Annotated[
        Path,
        typer.Option(
            help="Path to the dataset file",
        ),
    ] = SageMakerTrainingJobPath.TRAINING_JOB_PATH,
    model_card_file: Annotated[
        Path,
        typer.Option(
            help="Path to the model card markdown file",
        ),
    ] = SageMakerTrainingJobPath.MODEL_CARD_PATH,
):
    console.print(f"Loading {dataset_file} and {model_card_file}...")

    dataset = Dataset.from_path(dataset_file)
    model_card = ModelCard.from_path(model_card_file)

    model, alphabet = load(model_card)

    df = infer(
        dataset=dataset,
        model_card=model_card,
        model=model,
        alphabet=alphabet,
    )

    df.to_csv(
        f"{SageMakerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{model_card.name}.csv",
        index=False,
    )

    console.print(
        f"Saved the metrics in CSV in {SageMakerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{model_card.name}.csv"
    )


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
