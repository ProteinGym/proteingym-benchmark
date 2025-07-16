import typer
from typing import Annotated
from pathlib import Path
from pg2_dataset.dataset import Dataset, Manifest
from pg2_dataset.backends import Assays
from pg2_benchmark.split import load_assays
from pg2_benchmark.dummy_data import charge_ladder_dataset, add_extra_features

dataset_app = typer.Typer()


@dataset_app.command()
def load_assays_from_manifest(
    dataset_manifest: str = typer.Option(help="Path to the dataset TOML file"),
) -> Assays:
    dataset = Manifest.from_path(dataset_manifest).ingest()
    assays = load_assays(dataset)

    return assays


@dataset_app.command()
def load_assays_from_path(
    file_path: str = typer.Option(help="Path to the dataset ZIP file"),
) -> Assays:
    dataset = Dataset.from_path(file_path)
    assays = load_assays(dataset)

    return assays


@dataset_app.command()
def generate_dummy_data(
    data_file: Annotated[
        Path,
        typer.Argument(
            exists=False,
            help="Dummy dataset file",
        ),
    ],
    *,
    n_rows: Annotated[
        int, typer.Option(help="Number of rows to generate in a data frame")
    ] = 500,
    sequence_length: Annotated[
        int, typer.Option(help="Length of sequence for the sequence column")
    ] = 100,
) -> None:
    ladder = charge_ladder_dataset(n_rows, sequence_length)

    ladder.pipe(add_extra_features, target="charge").to_csv(
        data_file, index=False
    )
