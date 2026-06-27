from pathlib import Path
from typing import Annotated

import polars as pl
import typer
from proteingym.base import Subsets
from proteingym.base.model import ModelCard
from rich.console import Console

from .preprocess import encode
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
    subsets = Subsets.from_path(dataset_file)
    dataset = subsets[split].dataset
    model_card = ModelCard.from_path(model_card_file)

    model = train_model(
        split_dataset=subsets,
        split=split,
        test_fold=test_fold,
        target=target,
        model_card=model_card,
    )

    all_sequences_df = dataset.to_df(target_names=target)
    all_sequences = all_sequences_df["sequence"].to_list()

    encodings = encode(split_X=all_sequences, hyper_params=model_card.hyper_parameters)
    predictions = model.predict(encodings)

    if len(predictions.shape) > 1:
        predictions = predictions.flatten()

    predictions_df = pl.DataFrame({
        "sequence": all_sequences,
        target: predictions.tolist(),
    })

    predictions_dataset = dataset.predictions_delta(
        predictions_df,
        target=target,
        allow_extra_predictions=True
    )

    output_file = ContainerTrainingJobPath.OUTPUT_PATH / "predictions.pgdata"
    predictions_dataset.dump(path=ContainerTrainingJobPath.OUTPUT_PATH)
    console.print(f"Saved predictions to {output_file}")


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
