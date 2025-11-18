from pathlib import Path
from typing import Annotated

import typer
from proteingym.base import Dataset
from proteingym.base.model import ModelCard
from rich.console import Console

from .model import infer
from .model import train as train_model

app = typer.Typer(
    help="PLS model CLI",
    add_completion=True,
)

console = Console()


class ContainerTrainingJobPath:
    PREFIX = Path("/opt/program")
    MODEL_CARD_PATH = PREFIX / "README.md"
    OUTPUT_PATH = PREFIX / "output"


@app.command()
def train(
    dataset_file: Annotated[
        Path,
        typer.Option(
            help="Path to the dataset file",
        ),
    ],
    model_card_file: Annotated[
        Path,
        typer.Option(
            help="Path to the model card markdown file",
        ),
    ] = ContainerTrainingJobPath.MODEL_CARD_PATH,
):
    console.print(f"Loading {dataset_file} and {model_card_file}...")

    dataset = Dataset.from_path(dataset_file)
    model_card = ModelCard.from_path(model_card_file)

    model = train_model(
        dataset=dataset,
        model_card=model_card,
    )

    df = infer(
        dataset=dataset,
        model_card=model_card,
        model=model,
    )

    df.write_csv(
        f"{ContainerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{model_card.name}.csv"
    )

    console.print(
        f"Saved the metrics in CSV in {ContainerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{model_card.name}.csv"
    )


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
