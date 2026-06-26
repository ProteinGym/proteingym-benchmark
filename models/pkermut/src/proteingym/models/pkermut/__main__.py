import tempfile
from pathlib import Path
from typing import Annotated
import typer
from rich.console import Console

import polars as pl

import torch

from proteingym.base import Subsets
from proteingym.base.model import ModelCard
from proteingym.base.sequence import SequenceType

from kermut.pg_model.kermut_run import main as kermut_run

from .utils import (
    prepare_dataframe,
)


CUDA_AVAILABLE = torch.cuda.is_available()


app = typer.Typer(
    help="Kermut model CLI",
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
    reference_sequence = str(
        next(
            seq.value for seq in dataset.sequences if seq.type == SequenceType.WILD_TYPE
        )
    )

    with tempfile.TemporaryDirectory() as temp_dir:
        data_path = str(Path(temp_dir) / f"{dataset.name}.csv")
        output_path = str(ContainerTrainingJobPath.OUTPUT_PATH)
        df = prepare_dataframe(subsets, target, split, test_fold)
        df.write_csv(data_path)

        # TODO: Parse structure form dataset object
        structure = dataset.structures[0]
        structure.dump(path=Path(temp_dir))
        pdb_path = Path(temp_dir) / f"{structure.name}.pdb"

        kermut_run(
            dataset_name=dataset.name,
            target="target",
            data_dir=temp_dir,
            pdb_file=pdb_path,
            output_path=output_path,
            reference_sequence=reference_sequence,
            prepare_artifacts=True,
            n_steps=model_card.hyper_parameters.get("n_steps"),
            preferential=model_card.hyper_parameters.get("preferential"),
            preference_sampling_strategy=model_card.hyper_parameters.get(
                "preference_sampling_strategy"
            ),
            device=model_card.hyper_parameters.get("device"),
        )

        # TODO: Also store predictions of other splits
        results = pl.read_csv(Path(output_path) / f"{dataset.name}.csv")
        results = results.rename({"y_var": "y_pred_var"})
        results.select(["sequence", "split", "y", "y_pred", "y_pred_var"]).write_csv(
            Path(output_path) / "predictions.csv"
        )

        # Create predictions DataFrame with target name
        predictions_df = results.select([
            pl.col("sequence"),
            pl.col("y_pred").alias(target)
        ])

        # Create predictions delta dataset
        predictions_dataset = dataset.predictions_delta(
            predictions_df,
            target=target
        )

        # Save as .pgdata archive
        output_file = Path(output_path) / "predictions.pgdata"
        predictions_dataset.dump(path=Path(output_path))
        console.print(f"Saved predictions to {output_file}")


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
