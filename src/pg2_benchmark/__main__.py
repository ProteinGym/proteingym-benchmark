import json
import logging
import subprocess
import tarfile
import tempfile
from pathlib import Path
from typing import Annotated

import typer
from packaging.utils import parse_sdist_filename

from pg2_benchmark.__about__ import __version__
from pg2_benchmark.cli.dataset import dataset_app
from pg2_benchmark.cli.metric import metric_app
from pg2_benchmark.cli.sagemaker import sagemaker_app
from pg2_benchmark.model_card import ModelCard

app = typer.Typer(
    name="benchmark",
    help="ProteinGym2 - Benchmark CLI",
    add_completion=False,
)

app.add_typer(dataset_app, name="dataset", help="Dataset operations")
app.add_typer(metric_app, name="metric", help="Metric operations")
app.add_typer(sagemaker_app, name="sagemaker", help="SageMaker operations")


class ModelPath:
    """Configuration class for model-related file paths.

    This class defines standard paths used throughout the benchmark system
    for locating model-related files and configurations.
    """

    MODEL_CARD_PATH = Path("README.md")
    """Default location for model card files relative to model root directory."""


def setup_logger(*, level: int = logging.CRITICAL) -> None:
    """Set up the logger for the application.

    Args:
        log_level (int): The logging level to set. Defaults to `logging.CRITICAL`.
    """
    logger = logging.getLogger("pg2_benchmark")
    logger.setLevel(level)

    stream_handler = logging.StreamHandler()
    stream_handler.setLevel(level)

    formatter = logging.Formatter(
        "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    )
    stream_handler.setFormatter(formatter)
    logger.addHandler(stream_handler)


@app.callback(invoke_without_command=True)
def main(
    ctx: typer.Context,
    verbose: Annotated[int, typer.Option("--verbose", "-v", count=True)] = 3,
    version: Annotated[
        bool, typer.Option("--version", help="Show version and exit")
    ] = False,
) -> None:
    """Main entry point for the CLI.

    Args:
        ctx (typer.Context): The context for the CLI.
        verbose (int): The verbosity level. Use `-v` or `--verbose` to increase
            verbosity. Each `-v` increases the verbosity level:
            0: CRITICAL, 1: ERROR, 2: WARNING, 3: INFO, 4: DEBUG.
            Defaults to 3 (INFO).
        version (bool): If `True`, show the package version. Defaults to `False`.

    Raises:
        typer.Exit: If version is `True`, exits after showing the version.
    """
    setup_logger(level=logging.CRITICAL - verbose * 10)

    if version:
        typer.echo(f"v{__version__}")
        raise typer.Exit()

    if not ctx.invoked_subcommand:
        typer.echo("Welcome to the PG2 Dataset CLI!")
        typer.echo("Use --help to see available commands.")


@app.command()
def validate(
    sdist_path: Annotated[
        Path,
        typer.Argument(
            help="Root path to the model package containting the model source code and model card",
            exists=True,
            resolve_path=True,
        ),
    ],
):
    logger = logging.getLogger("pg2_benchmark")

    validator_script = Path(__file__).parent / "model_validator.py"

    package_name, package_version = parse_sdist_filename(Path(sdist_path).name)
    package_path = Path(f"{package_name.replace('-', '_')}-{package_version}")

    with tempfile.TemporaryDirectory() as tmpdir:
        tmpdir_path = Path(tmpdir)

        with tarfile.open(sdist_path, "r:gz") as tar:
            tar.extractall(tmpdir, filter="data")

        model_card_path = tmpdir_path / package_path / ModelPath.MODEL_CARD_PATH

        # First: Validate model card
        try:
            model_card = ModelCard.from_path(model_card_path)
            logger.info(
                f"✅ Loaded {model_card.name} with hyper parameters {model_card.hyper_params}."
            )
        except Exception as e:
            logger.error(
                f"❌ Error loading model card from {str(model_card_path)}: {e}"
            )
            raise typer.Exit(1)

        # Second: Validate model entrypoints
        try:
            result = subprocess.run(
                [
                    "uv",
                    "run",
                    "--active",
                    "python",
                    str(validator_script),
                    package_name,
                ],
                cwd=tmpdir_path / package_path,
                capture_output=True,
                text=True,
            )

            validation_data = json.loads(result.stdout.strip())

            if not validation_data["module_loaded"]:
                logger.error(
                    f"❌ Model {model_card.name} failed to load: {validation_data['error']}"
                )
                raise typer.Exit(1)

            if not validation_data["entry_points_found"]:
                logger.error(
                    f"❌ Model {model_card.name} loaded with empty entrypoints."
                )
                raise typer.Exit(1)

            logger.info(
                f"✅ Model {model_card.name} loaded successfully with entrypoints: {validation_data['entry_points_found']}"
            )

        except Exception as e:
            logger.error(f"❌ Error running validation subprocess: {e}")
            raise typer.Exit(1)


if __name__ == "__main__":
    app()
