import json
import subprocess
from pathlib import Path
from typing import Annotated

import typer

from pg2_benchmark.cli.dataset import dataset_app
from pg2_benchmark.cli.metric import metric_app
from pg2_benchmark.cli.sagemaker import sagemaker_app
from pg2_benchmark.model_card import ModelCard


class ModelPath:
    ROOT_PATH = Path("models")
    SRC_PATH = Path("src")
    PACKAGE_PREFIX = "pg2_model"
    MODEL_CARD_PATH = Path("README.md")
    MAIN_PY_PATH = Path("__main__.py")
    APP_NAME = "app"
    COMMAND_NAME = "train"
    COMMAND_PARAMS = ["dataset_file", "model_card_file"]


app = typer.Typer(
    name="benchmark",
    help="ProteinGym2 - Benchmark CLI",
    add_completion=False,
)

app.add_typer(dataset_app, name="dataset", help="Dataset operations")
app.add_typer(metric_app, name="metric", help="Metric operations")
app.add_typer(sagemaker_app, name="sagemaker", help="SageMaker operations")


@app.command()
def validate(
    model_name: Annotated[
        str, typer.Argument(help="The model name listed in the `models` folder")
    ],
):
    model_card_path = ModelPath.ROOT_PATH / model_name / ModelPath.MODEL_CARD_PATH

    if not model_card_path.exists():
        typer.echo(
            f"❌ Model {model_name} does not have a model card at {model_card_path}"
        )
        raise typer.Exit(1)

    try:
        model_card = ModelCard.from_path(model_card_path)
        typer.echo(
            f"✅ Loaded {model_card.name} with hyper parameters {model_card.hyper_params}."
        )

    except Exception as e:
        typer.echo(f"❌ Error loading model card from {model_card_path}: {e}")
        raise typer.Exit(1)

    main_py_path = (
        ModelPath.ROOT_PATH
        / model_name
        / ModelPath.SRC_PATH
        / f"{ModelPath.PACKAGE_PREFIX}_{model_name}"
        / ModelPath.MAIN_PY_PATH
    )

    if not main_py_path.exists():
        typer.echo(
            f"❌ Model {model_name} does not have a {ModelPath.MAIN_PY_PATH} file at {main_py_path}"
        )
        raise typer.Exit(1)

    try:
        validator_script = Path(__file__).parent / "model_validator.py"

        result = subprocess.run(
            [
                "uv",
                "run",
                "--active",
                "python",
                str(validator_script),
                model_name,
                ModelPath.PACKAGE_PREFIX,
                ModelPath.APP_NAME,
                ModelPath.COMMAND_NAME,
                json.dumps(ModelPath.COMMAND_PARAMS),
            ],
            cwd=ModelPath.ROOT_PATH / model_name / ModelPath.SRC_PATH,
            capture_output=True,
            text=True,
        )

        if result.returncode == 0:
            validation_data = json.loads(result.stdout.strip())

            if not validation_data["entrypoint_command_found"]:
                typer.echo(
                    f"❌ Model {model_name} does not have a '{ModelPath.COMMAND_NAME}' command"
                )
                raise typer.Exit(1)

            if not validation_data["entrypoint_params_found"]:
                typer.echo(
                    f"❌ Model {model_name}'s '{ModelPath.COMMAND_NAME}' command does not have the required params: {ModelPath.COMMAND_PARAMS}"
                )
                raise typer.Exit(1)

            typer.echo(
                f"✅ Model {model_name} has a valid '{ModelPath.COMMAND_NAME}' entrypoint with required params: {ModelPath.COMMAND_PARAMS}"
            )
        else:
            typer.echo(f"❌ Error loading module {main_py_path}: {result.stderr}")
            raise typer.Exit(1)

    except Exception as e:
        typer.echo(f"❌ Error running validation subprocess: {e}")
        raise typer.Exit(1)


if __name__ == "__main__":
    app()
