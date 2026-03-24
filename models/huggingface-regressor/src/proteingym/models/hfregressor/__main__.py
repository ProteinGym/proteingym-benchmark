import tempfile
from pathlib import Path
from typing import Annotated

import typer
from rich.console import Console

import polars as pl
import numpy as np

import torch

from proteingym.base import Dataset, Subsets
from proteingym.base.model import ModelCard

from proteingym.models.hfregressor.huggingface_regressor import HuggingFaceRegressor
from .utils import prepare_dataframe, is_container


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
    split: Annotated[
        str,
        typer.Option(
            help="Split name to use",
        ),
    ],
    test_fold: Annotated[
        int,
        typer.Option(
            help="Test fold index",
        ),
    ],
    target: Annotated[
        str,
        typer.Option(
            help="Target name to use",
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

    subsets = Subsets.from_path(dataset_file)
    dataset = subsets[split].dataset
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

        if is_container():
            output_path = str(ContainerTrainingJobPath.OUTPUT_PATH)
        else:
            output_path = Path(temp_dir) / "output"
            output_path.mkdir(parents=True, exist_ok=True)

        data = prepare_dataframe(subsets, split, test_fold)
        embedding_indices = np.arange(len(data))
        data = data.with_columns(pl.Series("embedding_index", embedding_indices))

        model = HuggingFaceRegressor(
            model_card=model_card,
            data=data,
            target=target,
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
                "test": test_data[target],
                "pred": test_preds.tolist(),
            }
        )

        output_file = f"{output_path}/predictions.json"
        df.write_json(output_file)
        console.print(f"Saved predictions to {output_file}")


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
