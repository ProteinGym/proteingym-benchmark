import typer
from pg2_dataset.dataset import Dataset, Manifest
from pg2_dataset.backends import Assays
from pg2_benchmark.split import load_assays

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