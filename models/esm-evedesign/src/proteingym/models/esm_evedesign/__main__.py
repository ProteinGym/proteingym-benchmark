from pathlib import Path
from typing import Annotated

import polars as pl
import typer
from evedesign.proteingym import dataset_to_evedesign
from proteingym.base import Subsets
from proteingym.base.model import ModelCard
from rich.console import Console

from .model import build, load, score

app = typer.Typer(
    help="ProteinGym2 - ESM2 (evedesign wrapped)",
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
    model_card = ModelCard.from_path(model_card_file)

    # ESM2 is zero-shot, so only the System and the test
    # instances are used--the training split is ignored
    # probably eventually need some way to score whole csv?
    system, data = dataset_to_evedesign(
        subsets,
        split=split,
        target=target,
        test_fold=test_fold,
    )

    model = load(model_card)
    model = build(model, system, data=None)

    test_instances, test_values = data.test_set.select(
        name=target, drop_missing=False
    )
    preds = score(model, test_instances)

    test_sequences = ["".join(instance[0].rep) for instance in test_instances]

    df = pl.DataFrame(
        {
            "sequence": test_sequences,
            "test": test_values,
            "pred": [float(p) for p in preds],
        }
    )

    output_file = f"{ContainerTrainingJobPath.OUTPUT_PATH}/predictions.json"
    df.write_json(output_file)
    console.print(f"Saved predictions to {output_file}")


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
