from typing import Annotated
from pathlib import Path
import polars as pl
from rich.console import Console
from pg2_dataset.dataset import Dataset
from pg2_dataset.splits.abstract_split_strategy import TrainTestValid
from pg2_benchmark.manifest import Manifest as Manifest
from pg2_model_pls.utils import load_x_and_y, train_model, predict_model

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
    PARAMS_PATH = PREFIX / "input" / "config" / "hyperparameters.json"
    OUTPUT_PATH = PREFIX / "model"

    MODEL_PATH = Path("/model.pkl")


@app.command()
def train(
    dataset_file: Annotated[
        Path,
        typer.Option(
            default=SageMakerTrainingJobPath.TRAINING_JOB_PATH,
            help="Path to the dataset file",
        ),
    ],
    model_toml_file: Annotated[
        Path,
        typer.Option(
            default=SageMakerTrainingJobPath.MANIFEST_PATH,
            help="Path to the model TOML file",
        ),
    ],
):
    console.print(f"Loading {dataset_file} and {model_toml_file}...")

    dataset = Dataset.from_path(dataset_file)

    manifest = Manifest.from_path(model_toml_file)

    train_X, train_Y = load_x_and_y(
        dataset=dataset,
        split=TrainTestValid.train,
    )

    console.print(f"Loaded {len(train_Y)} training records.")

    console.print("Start the training...")

    train_model(
        train_X=train_X,
        train_Y=train_Y,
        model_toml_file=model_toml_file,
        model_path=SageMakerTrainingJobPath.MODEL_PATH,
    )

    console.print("Finished the training...")

    valid_X, valid_Y = load_x_and_y(
        dataset=dataset,
        split=TrainTestValid.valid,
    )

    console.print(f"Loaded {len(valid_Y)} test records.")

    console.print("Start the scoring...")

    pred_y = predict_model(
        test_X=valid_X,
        model_toml_file=model_toml_file,
        model_path=SageMakerTrainingJobPath.MODEL_PATH,
    )

    console.print("Finished the scoring...")

    df = pl.DataFrame(
        {
            "sequence": valid_X,
            "test": valid_Y,
            "pred": pred_y,
        }
    )

    df.write_csv(
        f"{SageMakerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{manifest.name}.csv"
    )
    console.print(
        f"Saved the metrics in CSV in {SageMakerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{manifest.name}.csv"
    )

    console.print("Done.")


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
