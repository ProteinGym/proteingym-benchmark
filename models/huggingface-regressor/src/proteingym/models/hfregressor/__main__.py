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


class SageMakerTrainingJobPath:
    PREFIX = Path("/opt/ml")
    TRAINING_JOB_PATH = PREFIX / "input" / "data" / "training" / "dataset.zip"
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

    if model_card.hyper_params["device"] == "cuda":
        if not CUDA_AVAILABLE:
            console.print(f"No cuda enabled GPUs available, falling back to CPU")
            torch.set_default_device("cpu")
        else:
            torch.set_default_device("cuda")

    with tempfile.TemporaryDirectory() as temp_dir:
        if model_card.hyper_params["cache_dir"] == "TEMP":
            cache_dir = temp_dir
        else:
            cache_dir = model_card.hyper_params["cache_dir"]
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

        preds = model.predict(
            data=data,
        )

    df = data[["sequence", "split", "target"]]
    df = df.with_columns(pl.Series("pred", preds))

    if Path(SageMakerTrainingJobPath.OUTPUT_PATH).is_dir():
        df.write_csv(
            f"{SageMakerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{model_card.name}.csv"
        )

        console.print(
            f"Saved the metrics in CSV in {SageMakerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{model_card.name}.csv"
        )
    else:
        console.print(f"Predictions:\n {df}")


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
