import polars as pl
from rich.console import Console
from pg2_dataset.dataset import Manifest
from pg2_dataset.splits.abstract_split_strategy import TrainTestValid
from pg2_model_pls.manifest import Manifest as ModelManifest
from pg2_model_pls.utils import load_x_and_y, train_model, predict_model

import typer

app = typer.Typer(
    help="ProteinGym2 - Model CLI",
    add_completion=True,
)

console = Console()


@app.command()
def predict(
    dataset_toml_file: str = typer.Option(help="Path to the dataset TOML file"),
    model_toml_file: str = typer.Option(help="Path to the model TOML file"),
):
    console.print(f"Loading {dataset_toml_file} and {model_toml_file}...")

    dataset_name = Manifest.from_path(dataset_toml_file).name

    model_path = "/model.pkl"
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

    df.write_csv(f"/output/{dataset_name}_{model_name}.csv")
    console.print(f"Saved the metrics in CSV in output/{dataset_name}_{model_name}.csv")

    console.print("Done.")


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
