import polars as pl
from pathlib import Path
from rich.console import Console
from pg2_dataset.dataset import Manifest
from pg2_dataset.splits.abstract_split_strategy import TrainTestValid
from pg2_model_pls.manifest import Manifest as ModelManifest
from pg2_model_pls.utils import load_x_and_y, train_model, predict_model
from typing import Tuple
import json
import toml
import typer

app = typer.Typer(
    help="ProteinGym2 - Model CLI",
    add_completion=True,
)

console = Console()

prefix = Path("/opt/ml")
training_data_path = prefix / "input" / "data" / "training"
params_path = prefix / "input" / "config" / "hyperparameters.json"
model_path = Path("/model.pkl")


def _configure_container_paths(
    dataset_toml_file: str, model_toml_file: str
) -> Tuple[str, str, str]:
    if not dataset_toml_file and not model_toml_file:
        typer.echo(
            "Configuring the paths to where SageMaker mounts interesting things in the container."
        )

        output_path = prefix / "model"

        with open(params_path, "r") as f:
            training_params = json.load(f)

        dataset_toml_file = training_data_path / training_params.get(
            "dataset_toml_file"
        )
        model_toml_file = training_data_path / training_params.get("model_toml_file")

        with open(dataset_toml_file, "r") as f:
            data = toml.load(f)

        data["assays_meta"]["file_path"] = str(
            training_data_path / data["assays_meta"]["file_path"]
        )

        with open(dataset_toml_file, "w") as f:
            toml.dump(data, f)

        return str(output_path), str(dataset_toml_file), str(model_toml_file)

    else:
        output_path = Path("/output")
        return str(output_path), str(dataset_toml_file), str(model_toml_file)


@app.command()
def train(
    dataset_toml_file: str = typer.Option(
        default="", help="Path to the dataset TOML file"
    ),
    model_toml_file: str = typer.Option(default="", help="Path to the model TOML file"),
):
    output_path, dataset_toml_file, model_toml_file = _configure_container_paths(
        dataset_toml_file=dataset_toml_file,
        model_toml_file=model_toml_file,
    )

    console.print(f"Loading {dataset_toml_file} and {model_toml_file}...")

    dataset_name = Manifest.from_path(dataset_toml_file).name

    model_name = ModelManifest.from_path(model_toml_file).name

    train_X, train_Y = load_x_and_y(
        dataset_toml_file=dataset_toml_file,
        split=TrainTestValid.train,
    )

    console.print(f"Loaded {len(train_Y)} training records.")

    console.print("Start the training...")

    train_model(
        train_X=train_X,
        train_Y=train_Y,
        model_toml_file=model_toml_file,
        model_path=model_path,
    )

    console.print("Finished the training...")

    valid_X, valid_Y = load_x_and_y(
        dataset_toml_file=dataset_toml_file,
        split=TrainTestValid.valid,
    )

    console.print(f"Loaded {len(valid_Y)} test records.")

    console.print("Start the scoring...")

    pred_y = predict_model(
        test_X=valid_X,
        model_toml_file=model_toml_file,
        model_path=model_path,
    )

    console.print("Finished the scoring...")

    df = pl.DataFrame(
        {
            "sequence": valid_X,
            "test": valid_Y,
            "pred": pred_y,
        }
    )

    df.write_csv(f"/{output_path}/{dataset_name}_{model_name}.csv")
    console.print(
        f"Saved the metrics in CSV in {output_path}/{dataset_name}_{model_name}.csv"
    )

    console.print("Done.")


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
