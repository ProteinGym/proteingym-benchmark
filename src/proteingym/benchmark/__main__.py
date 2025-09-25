import logging
from pathlib import Path
from typing import Annotated

import typer

from pg2_benchmark.__about__ import __version__
from pg2_benchmark.cli.dataset import dataset_app
from pg2_benchmark.cli.metric import metric_app
from pg2_benchmark.cli.sagemaker import sagemaker_app
from pg2_benchmark.model import ModelCard, ModelProject

app = typer.Typer(
    name="benchmark",
    help="ProteinGym2 - Benchmark CLI",
    add_completion=False,
)

app.add_typer(dataset_app, name="dataset", help="Dataset operations")
app.add_typer(metric_app, name="metric", help="Metric operations")
app.add_typer(sagemaker_app, name="sagemaker", help="SageMaker operations")


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
    project_path: Annotated[
        Path,
        typer.Argument(
            help="Root path to the model project containting the model source code and model card",
            exists=True,
            resolve_path=True,
        ),
    ],
):
    logger = logging.getLogger("pg2_benchmark")

    try:
        model_project = ModelProject.from_path(project_path)
        logger.info(
            f"✅ Model {model_project.project_name} loaded successfully with entry points: {model_project.entry_points}"
        )

        model_card = ModelCard.from_path(model_project.model_card_path)
        logger.info(
            f"✅ Loaded {model_card.name} with hyper parameters {model_card.hyper_params}."
        )
    except ValueError as e:
        logger.error(f"❌ Validation failed: {str(e)}")
        raise typer.Exit(1)
    except Exception as e:
        logger.error("❌ Error running validation", exc_info=e)
        raise typer.Exit(1)


if __name__ == "__main__":
    app()
