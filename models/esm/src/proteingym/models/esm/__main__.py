from pathlib import Path
from typing import Annotated

import typer
from proteingym.base import Subsets
from proteingym.base.model import ModelCard
from rich.console import Console

from .model import infer, load

app = typer.Typer(
    help="ProteinGym2 - Model CLI",
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

    targets = [target.name for target in dataset.assay_targets]

    model, alphabet = load(model_card)

    for target in targets:
        df = infer(
            split_dataset=subsets,
            split=split,
            test_fold=test_fold,
            target=target,
            model_card=model_card,
            model=model,
            alphabet=alphabet,
        )

        output_file = f"{ContainerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{model_card.name}_fold{test_fold}_{target}.csv"
        df.to_csv(output_file, index=False)
        console.print(f"Saved predictions to {output_file}")


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
