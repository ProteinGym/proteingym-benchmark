import json
import logging
from enum import StrEnum
from pathlib import Path
from typing import Annotated

import click
import typer
from jinja2 import Environment, FileSystemLoader

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


class BenchmarkPath:
    """Configuration class for benchmark-related file paths.
    This class defines standard paths used throughout the benchmark system
    for locating benchmark configuration files and directories.
    """

    ROOT_PATH = Path("benchmark")
    """Root directory for benchmark configurations."""

    DVC_PATH = Path("dvc.yaml")
    """Standard DVC pipeline configuration file."""

    PARAMS_PATH = Path("params.yaml")
    """Parameters file for benchmark configuration."""


class GameType(StrEnum):
    """Enumeration of available game types for benchmarking.
    Defines the different evaluation modes available in the benchmark system.
    """

    SUPERVISED = "supervised"
    """Supervised learning evaluation mode."""

    ZERO_SHOT = "zero_shot"
    """Zero-shot evaluation mode."""


class EnvType(StrEnum):
    """Enumeration of available environment types for running benchmarks.
    Defines the different execution environments where benchmarks can be run.
    """

    LOCAL = "local"
    """Local execution environment."""

    AWS = "aws"
    """Amazon Web Services cloud environment."""


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


@app.command()
def select(
    models_path: Annotated[
        Path,
        typer.Argument(
            help="Path to a file containting all the models",
            exists=True,
            file_okay=True,
        ),
    ],
    datasets_path: Annotated[
        Path,
        typer.Argument(
            help="Path to a file containting all the datasets",
            exists=True,
            file_okay=True,
        ),
    ],
    game: Annotated[
        GameType,
        typer.Option(
            "--game",
            "-g",
            help="Type of game to benchmark for the selected models and datasets",
        ),
    ],
    env: Annotated[
        EnvType,
        typer.Option(
            "--env",
            "-e",
            click_type=click.Choice(["local", "aws"], case_sensitive=False),
            help="Environment to run benchmarking for the selected game",
        ),
    ],
):
    """Interactive selection of datasets and models permutations for benchmark."""
    logger = logging.getLogger("proteingym.benchmark")

    dvc_path = BenchmarkPath.ROOT_PATH / game / env / BenchmarkPath.DVC_PATH

    with datasets_path.open("r", encoding="utf-8") as f:
        datasets = json.load(f)

    with open(models_path, "r", encoding="utf-8") as f:
        models = json.load(f)

    def write_rendered_content(path: Path, rendered_content: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)

        with path.open("w", encoding="utf-8") as f:
            f.write(rendered_content)

    def render_dataset_entry(dataset: dict, exc_env: EnvType) -> dict:
        dataset_name = dataset["name"]
        dataset_path = Path(dataset["path"])

        if exc_env == EnvType.LOCAL:
            return {
                "name": dataset_name,
                "container_path": f"/{dataset_path.name}",
                "local_path": dataset_path.resolve().as_posix(),
            }

        elif exc_env == EnvType.AWS:
            return {
                "name": dataset_name,
                "local_path": dataset_path.resolve().as_posix(),
            }

    def render_model_entry(model: dict, exc_env: EnvType) -> dict:
        model_name = model["name"]
        model_path = Path(model["path"])

        if exc_env == EnvType.LOCAL:
            return {
                "name": model_name,
                "container_path": f"/{model_path.parent.name}",
                "container_model_card": f"/{model_path.parent.name}/README.md",
                "local_path": model_path.parent.resolve().as_posix(),
                "local_dockerfile": f"{model_path.parent}/Dockerfile",
            }

        elif exc_env == EnvType.AWS:
            return {
                "name": model_name,
                "local_path": model_path.parent.resolve().as_posix(),
                "local_dockerfile": f"{model_path.parent}/Dockerfile",
            }

    def render_dvc_yaml(
        path: Path, exc_env: EnvType, selected_datasets: dict, selected_models: dict
    ) -> None:
        jinja_env = Environment(loader=FileSystemLoader(path.parent))

        dvc_template = jinja_env.get_template(f"{BenchmarkPath.DVC_PATH}.jinja")

        updated_datasets = []
        updated_models = []

        for dataset in selected_datasets:
            dataset_entry = render_dataset_entry(dataset=dataset, exc_env=exc_env)
            updated_datasets.append(dataset_entry)

        for model in selected_models:
            model_entry = render_model_entry(model=model, exc_env=exc_env)
            updated_models.append(model_entry)

        dvc_rendered_content = dvc_template.render(
            datasets=updated_datasets, models=updated_models
        )

        write_rendered_content(path, dvc_rendered_content)

    try:
        typer.echo(f"\nSelected datasets: {json.dumps(datasets, indent=2)}")
        typer.echo(f"\nSelected models: {json.dumps(models, indent=2)}")

        if typer.confirm(f"\nUpdate {dvc_path}?"):
            try:
                render_dvc_yaml(
                    path=dvc_path,
                    exc_env=env,
                    selected_datasets=datasets,
                    selected_models=models,
                )

                typer.echo(f"✅ Updated {dvc_path} with selected models and datasets")
                logger.info(
                    f"Successfully updated {dvc_path} with {len(datasets)} datasets and {len(models)} models"
                )

            except Exception as e:
                logger.error(f"Failed to update {dvc_path}", exc_info=e)
                raise typer.Exit(1)
        else:
            logger.info(f"Configuration not saved to {dvc_path}.")

    except Exception as e:
        logger.error("Error in select command", exc_info=e)
        raise typer.Exit(1)


if __name__ == "__main__":
    app()
