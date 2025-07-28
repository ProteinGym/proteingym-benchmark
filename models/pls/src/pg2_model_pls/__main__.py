import polars as pl
from pathlib import Path
from rich.console import Console
from pg2_dataset.dataset import Dataset
from pg2_dataset.splits.abstract_split_strategy import TrainTestValid
from pg2_model_pls.manifest import Manifest
from pg2_model_pls.utils import load_x_and_y, train_model, predict_model
import typer

app = typer.Typer(
    help="ProteinGym2 - Model CLI",
    add_completion=True,
)

console = Console()

prefix = Path("/opt/ml")
training_data_path = prefix / "input" / "data" / "training" / "dataset.zip"
manifest_path = prefix / "input" / "data" / "manifest" / "manifest.toml"
params_path = prefix / "input" / "config" / "hyperparameters.json"
output_path = prefix / "model"

model_path = Path("/model.pkl")


@app.command()
def train(
    dataset_zip_file: str = typer.Option(
        default="", help="Path to the dataset ZIP file"
    ),
    model_toml_file: str = typer.Option(default="", help="Path to the model TOML file"),
):
    console.print(f"Loading {dataset_zip_file} and {model_toml_file}...")

    dataset_zip_file = dataset_zip_file or training_data_path
    dataset = Dataset.from_path(dataset_zip_file)
    dataset_name = dataset.name

    model_toml_file = model_toml_file or manifest_path
    hyper_params = Manifest.from_path(model_toml_file).hyper_params
    model_name = hyper_params["name"]

    train_X, train_Y = load_x_and_y(
        dataset=dataset,
        split=TrainTestValid.train,
    )

    console.print(f"Loaded {len(train_Y)} training records.")

    console.print("Start the training...")

    train_model(
        train_X=train_X,
        train_Y=train_Y,
        model_path=model_path,
        hyper_params=hyper_params,
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
        model_path=model_path,
        hyper_params=hyper_params,
    )

    console.print("Finished the scoring...")

    df = pl.DataFrame(
        {
            "sequence": valid_X,
            "test": valid_Y,
            "pred": pred_y,
        }
    )

    df.write_csv(f"{output_path}/{dataset_name}_{model_name}.csv")
    console.print(
        f"Saved the metrics in CSV in {output_path}/{dataset_name}_{model_name}.csv"
    )

    console.print("Done.")


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
