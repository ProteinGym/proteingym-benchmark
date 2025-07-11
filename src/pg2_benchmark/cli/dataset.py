import typer
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
    n_rows: int = typer.Option(help="Number of rows to generate in a data frame"),
    seq_len: int = typer.Option(help="Length of sequence for the sequence column"),
    data_dir: str = typer.Option(help="Directory to the dummy dataset folder"),
) -> None:
    ladder = charge_ladder_dataset(n_rows, seq_len)

    data_dir = Path(data_dir)

    ladder.to_csv(data_dir / "charge_ladder.csv", index=False)
    add_extra_features(ladder, "charge").to_csv(
        data_dir / "charge_ladder_with_extra.csv", index=False
    )
