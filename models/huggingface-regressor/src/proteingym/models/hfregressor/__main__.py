import tempfile
from pathlib import Path
from typing import Annotated

import typer
from rich.console import Console

import polars as pl

import torch

from proteingym.base import Dataset
from proteingym.base.model import ModelCard

from proteingym.models.hfregressor.huggingface_regressor import HuggingFaceRegressor
from .preprocess import prepare_dataframe


CUDA_AVAILABLE = torch.cuda.is_available()


app = typer.Typer(
    help="HuggingFace regressor model CLI",
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

    if model_card.hyper_parameters["device"] == "cuda":
        if not CUDA_AVAILABLE:
            console.print(f"No cuda enabled GPUs available, falling back to CPU")
            torch.set_default_device("cpu")
        else:
            torch.set_default_device("cuda")

    with tempfile.TemporaryDirectory() as temp_dir:
        if model_card.hyper_parameters["cache_dir"] == "TEMP":
            cache_dir = temp_dir
        else:
            cache_dir = model_card.hyper_parameters["cache_dir"]
        console.print(f"Caching embeddings to {cache_dir}")

        # WIP Load embeddings by downloading embeddings to cache_dir

        # WARNING: Not reading splits from dataset file yet
        data = prepare_dataframe(dataset, test_size=0.2)

        model = HuggingFaceRegressor(
            model_card=model_card,
            data=data,
            cache_dir=cache_dir,
        )
        model.fit(data.filter(pl.col("split") == "train"))

        test_data = data.filter(pl.col("split") == "test")
        test_preds = model.predict(
            data=test_data,
        )

    df = pl.DataFrame(
        {
            "sequence": test_data["sequence"],
            "test": test_data["target"],
            "pred": test_preds.tolist(),
        }
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
