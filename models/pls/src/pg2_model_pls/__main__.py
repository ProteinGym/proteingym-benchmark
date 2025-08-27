from typing import Annotated
from pathlib import Path
from rich.console import Console
from pg2_dataset.dataset import Dataset
from pg2_model_pls.model import train as train_model, predict
from pg2_benchmark.manifest import Manifest

import typer

app = typer.Typer(
    help="ProteinGym2 - Model CLI",
    add_completion=True,
)

console = Console()


class SageMakerTrainingJobPath:
    PREFIX = Path("/opt/ml")
    TRAINING_JOB_PATH = PREFIX / "input" / "data" / "training" / "dataset.zip"
    MANIFEST_PATH = PREFIX / "input" / "data" / "manifest" / "manifest.toml"
    OUTPUT_PATH = PREFIX / "model"


@app.command()
def train(
    dataset_file: Annotated[
        Path,
        typer.Option(
            help="Path to the dataset file",
        ),
    ] = SageMakerTrainingJobPath.TRAINING_JOB_PATH,
    model_toml_file: Annotated[
        Path,
        typer.Option(
            help="Path to the model TOML file",
        ),
    ] = SageMakerTrainingJobPath.MANIFEST_PATH,
):
    console.print(f"Loading {dataset_file} and {model_toml_file}...")

    dataset = Dataset.from_path(dataset_file)
    manifest = Manifest.from_path(model_toml_file)

    model = train_model(
        dataset=dataset,
        manifest=manifest,
    )

    df = predict(
        dataset=dataset,
        manifest=manifest,
        model=model,
    )

    df.write_csv(
        f"{SageMakerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{manifest.name}.csv"
    )

    console.print(
        f"Saved the metrics in CSV in {SageMakerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{manifest.name}.csv"
    )


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
