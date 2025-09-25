import typer
from typing import Annotated
from pathlib import Path

from proteingym.benchmark.dummy_data import (
    charge_ladder_dataset,
    adjust_target_with_two_dummy_features,
)

dataset_app = typer.Typer()



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

    ladder.pipe(adjust_target_with_two_dummy_features, target="charge").to_csv(
        data_file, index=False
    )
