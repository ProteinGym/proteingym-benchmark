import tempfile
from pathlib import Path
from typing import Annotated
import typer
from rich.console import Console

import polars as pl

import torch

from proteingym.base import Dataset, Subsets
from proteingym.base.model import ModelCard
from proteingym.base.sequence import SequenceType

from kermut.pg_model.kermut_run import main as kermut_run

from .utils import (
    is_container,
    prepare_dataframe,
    dump_pg_structure,
    add_pseudo_if_variant_matches_reference,
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
        if is_container():
            output_path = str(ContainerTrainingJobPath.OUTPUT_PATH)
        else:
            output_path = str(Path(temp_dir) / "output")
        df = prepare_dataframe(subsets, target, split, test_fold)
        df.write_csv(data_path)

        # TODO: Parse structure form dataset object
        pdb_path = str(Path(temp_dir) / "structure.pdb")
        structure = dataset.structures[0]
        dump_pg_structure(pdb_path, structure)

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

        # TODO: pg-benchmark expects a dataframe with only test gt and predictions,
        # This should be removed later
        results = pl.read_csv(Path(output_path) / f"{dataset.name}.csv")
        results = results.rename({"y_var": "y_pred_var"})
        results.select(["sequence", "split", "y", "y_pred", "y_pred_var"]).write_csv(
            Path(output_path) / "predictions.csv"
        )
        test_data = results.filter(pl.col("split") == "test")
        df = pl.DataFrame(
            {
                "sequence": test_data["sequence"],
                "test": test_data["y"],
                "pred": test_data["y_pred"],
            }
        )

        df.write_csv(f"{output_path}/{dataset.name}_{model_card.name}.csv")

        console.print(
            f"Saved the metrics in CSV in {output_path}/{dataset.name}_{model_card.name}.csv"
        )


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
