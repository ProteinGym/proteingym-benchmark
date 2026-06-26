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

        # Read kermut results
        results = pl.read_csv(Path(output_path) / f"{dataset.name}.csv")
        console.print(f"Kermut results: {len(results)} rows, {results['sequence'].n_unique()} unique sequences")

        results = results.rename({"y_var": "y_pred_var"})
        results.select(["sequence", "split", "y", "y_pred", "y_pred_var"]).write_csv(
            Path(output_path) / "predictions.csv"
        )

        # Kermut may deduplicate sequences internally (e.g., if reference sequence
        # is in the dataset and gets a pseudo mutation that collides with another variant).
        # We need to ensure we have predictions for all original sequences.
        original_df = df.select([pl.col("sequence")])
        console.print(f"Original input: {len(original_df)} sequences")

        # Create predictions DataFrame with target name
        predictions_df = results.select([
            pl.col("sequence"),
            pl.col("y_pred").alias(target)
        ])

        # Check for missing sequences
        original_seqs = set(original_df["sequence"].to_list())
        result_seqs = set(predictions_df["sequence"].to_list())
        missing = original_seqs - result_seqs

        if missing:
            console.print(f"⚠️  {len(missing)} sequences missing from kermut output: {missing}")
            console.print(f"   Kermut's internal deduplication dropped these sequences")
            console.print(f"   Filling with mean prediction as a fallback...")

            # Fill missing sequences with mean prediction to avoid metric calculation errors
            mean_pred = predictions_df[target].mean()
            missing_df = pl.DataFrame({
                "sequence": list(missing),
                target: [mean_pred] * len(missing)
            })
            predictions_df = pl.concat([predictions_df, missing_df])

        console.print(f"Creating predictions dataset with {len(predictions_df)} predictions")

        # Create predictions delta dataset
        predictions_dataset = dataset.predictions_delta(
            predictions_df,
            target=target,
            allow_extra_predictions=True
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
