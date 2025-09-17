import logging
import os
from enum import Enum
from pathlib import Path
from typing import Annotated

import typer
from jinja2 import Environment, FileSystemLoader
from pg2_dataset.dataset import Dataset
from pydantic import BaseModel

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

    ROOT_PATH = Path("models")
    """Root directory to all models."""

    MODEL_CARD_PATH = Path("README.md")
    """Default location for model card files relative to model root directory."""


class DatasetPath:
    """Configuration class for dataset-related file paths.

    This class defines standard paths used throughout the benchmark system
    for locating dataset-related files and configurations.
    """

    ROOT_PATH = Path("datasets")
    """Root directory to all datasets."""

    DATASET_FILE = Path("dataset.zip")
    """Standard dataset archive file name."""


class BenchmarkPath:
    """Configuration class for benchmark-related file paths.

    This class defines standard paths used throughout the benchmark system
    for locating benchmark configuration files and directories.
    """

    ROOT_PATH = Path("benchmark")
    """Root directory for benchmark configurations."""

    DVC_FILE = Path("dvc.yaml")
    """Standard DVC pipeline configuration file."""

    PARAMS_FILE = Path("params.yaml")
    """Parameters file for benchmark configuration."""


class GameType(str, Enum):
    """Enumeration of available game types for benchmarking.

    Defines the different evaluation modes available in the benchmark system.
    """

    SUPERVISED = "supervised"
    """Supervised learning evaluation mode."""

    ZERO_SHOT = "zero_shot"
    """Zero-shot evaluation mode."""


class EnvType(str, Enum):
    """Enumeration of available environment types for running benchmarks.

    Defines the different execution environments where benchmarks can be run.
    """

    LOCAL = "local"
    """Local execution environment."""

    AWS = "aws"
    """Amazon Web Services cloud environment."""


class ItemType(str, Enum):
    """Enumeration of available item types to select before benchmarking.

    Defines the different types of items that can be selected and configured
    in the benchmark system.
    """

    DATASETS = "datasets"
    """Dataset items for benchmarking."""

    MODELS = "models"
    """Model items for benchmarking."""


class Item(BaseModel):
    """Data model representing an available dataset or model item.

    This class encapsulates the essential information about a dataset or model
    that is available for benchmarking operations.
    """

    name: str
    """The name of the selected item, either a dataset or a model."""

    root_path: Path
    """The root directory of the selected item."""

    item_type: ItemType
    """The type of the selected item."""


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
    model_path: Annotated[
        Path,
        typer.Argument(
            help="Root path containting the model source code and model card",
            exists=True,
            dir_okay=True,
            file_okay=True,
        ),
    ],
):
    logger = logging.getLogger("pg2_benchmark")

    if model_path.is_dir():
        model_card_path = model_path / ModelPath.MODEL_CARD_PATH
    else:
        model_card_path = model_path

    try:
        model_card = ModelCard.from_path(model_card_path)
        logger.info(
            f"✅ Loaded {model_card.name} with hyper parameters {model_card.hyper_params}."
        )
    except Exception as e:
        logger.error(f"❌ Error loading model card from {str(model_card_path)}: {e}")
        raise typer.Exit(1)


@app.command()
def select(
    models_path: Annotated[
        Path,
        typer.Argument(
            help="Root path containting all the models",
            exists=True,
            dir_okay=True,
            file_okay=False,
        ),
    ],
    datasets_path: Annotated[
        Path,
        typer.Argument(
            help="Root path containting all the datasets",
            exists=True,
            dir_okay=True,
            file_okay=False,
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
            "--env", "-e", help="Environment to run benchmarking for the selected game"
        ),
    ],
):
    """Interactive selection of datasets and models permutations for benchmark."""
    logger = logging.getLogger("pg2_benchmark")

    dvc_file = BenchmarkPath.ROOT_PATH / game / env / BenchmarkPath.DVC_FILE
    params_file = BenchmarkPath.ROOT_PATH / game / env / BenchmarkPath.PARAMS_FILE

    relative_datasets_path = Path(
        os.path.relpath(datasets_path.resolve(), dvc_file.parent.resolve())
    )
    relative_models_path = Path(
        os.path.relpath(models_path.resolve(), dvc_file.parent.resolve())
    )

    def get_available_items(base_path: Path, item_type: ItemType) -> list[Item]:
        """Get list of available datasets or models."""
        available_items = []

        for entry in base_path.iterdir():
            if entry.is_dir() and not entry.name.startswith("."):
                try:
                    if item_type == ItemType.DATASETS:
                        loaded_item = Dataset.from_path(
                            DatasetPath.ROOT_PATH
                            / entry.name
                            / DatasetPath.DATASET_FILE
                        )
                    elif item_type == ItemType.MODELS:
                        loaded_item = ModelCard.from_path(
                            ModelPath.ROOT_PATH / entry.name / ModelPath.MODEL_CARD_PATH
                        )
                    else:
                        continue

                    available_item = Item(
                        name=loaded_item.name,
                        root_path=Path(entry.name),
                        item_type=item_type,
                    )

                    available_items.append(available_item)
                except Exception as e:
                    # Skip items that can't be loaded (e.g., missing files, invalid format)
                    logger.warning(f"Skipping {entry.name}: {e}")
                    continue

        return sorted(available_items, key=lambda item: item.name)

    def interactive_select(items: list[Item], item_type: ItemType) -> list[Item]:
        """Interactive multi-selection of items."""
        if not items:
            typer.echo(f"No {item_type} found!")
            return []

        typer.echo(f"\nAvailable {item_type}:")
        for i, item in enumerate(items, 1):
            typer.echo(f"  {i}. {item.name}")

        typer.echo(
            f"\nSelect {item_type} (comma-separated numbers, e.g., 1,3,5 or 'all' for all):"
        )
        selection = typer.prompt("Selection")

        if selection.lower() == "all":
            return items

        selected_items = []
        try:
            indices = [int(x.strip()) - 1 for x in selection.split(",")]
            for idx in indices:
                if 0 <= idx < len(items):
                    selected_items.append(items[idx])
                else:
                    typer.echo(f"Warning: Index {idx + 1} is out of range, skipping")
        except ValueError:
            typer.echo("Invalid selection format. Please use comma-separated numbers.")
            return []

        return selected_items

    def write_rendered_content(file: Path, rendered_content: str) -> None:
        file.parent.mkdir(parents=True, exist_ok=True)

        with open(file, "w") as f:
            f.write(rendered_content)

    def render_dvc_yaml(dvc_file: Path, env: EnvType) -> None:
        jinja_env = Environment(loader=FileSystemLoader(dvc_file.parent))

        dvc_template = jinja_env.get_template(f"{BenchmarkPath.DVC_FILE}.jinja")

        updated_datasets = []
        updated_models = []

        if env == EnvType.LOCAL:
            for dataset in selected_datasets:
                dataset_entry = {
                    "name": dataset.name,
                    "container_path": f"/{DatasetPath.ROOT_PATH}/{dataset.root_path}/{DatasetPath.DATASET_FILE}",
                    "local_path": f"{relative_datasets_path}/{dataset.root_path}/{DatasetPath.DATASET_FILE}",
                }
                updated_datasets.append(dataset_entry)

            for model in selected_models:
                model_entry = {
                    "name": model.name,
                    "container_path": f"/{ModelPath.ROOT_PATH}/{model.root_path}/{ModelPath.MODEL_CARD_PATH}",
                    "local_path": f"{relative_models_path}/{model.root_path}/{ModelPath.MODEL_CARD_PATH}",
                    "dockerfile": f"{relative_models_path}/{model.root_path}/Dockerfile",
                }
                updated_models.append(model_entry)

        elif env == EnvType.AWS:
            for dataset in selected_datasets:
                dataset_entry = {
                    "name": dataset.name,
                    "aws_prefix": dataset.name,
                }
                updated_datasets.append(dataset_entry)

            for model in selected_models:
                model_entry = {
                    "name": model.name,
                    "aws_prefix": model.name,
                    "dockerfile": f"{relative_models_path}/{model.root_path}/Dockerfile",
                }
                updated_models.append(model_entry)

        dvc_rendered_content = dvc_template.render(
            datasets=updated_datasets, models=updated_models
        )

        write_rendered_content(dvc_file, dvc_rendered_content)

    def render_params_yaml(params_file: Path) -> None:
        jinja_env = Environment(loader=FileSystemLoader(params_file.parent))

        params_template = jinja_env.get_template(f"{BenchmarkPath.PARAMS_FILE}.jinja")

        params_rendered_content = params_template.render(
            datasets_dir=str(relative_datasets_path),
            models_dir=str(relative_models_path),
        )

        write_rendered_content(params_file, params_rendered_content)

    try:
        available_datasets = get_available_items(datasets_path, ItemType.DATASETS)
        available_models = get_available_items(models_path, ItemType.MODELS)

        typer.echo("🔍 Dataset and Model Selection Tool")
        typer.echo("=" * 40)

        selected_datasets = interactive_select(available_datasets, ItemType.DATASETS)
        if not selected_datasets:
            typer.echo("No datasets selected. Exiting.")
            raise typer.Exit(1)

        selected_models = interactive_select(available_models, ItemType.MODELS)
        if not selected_models:
            typer.echo("No models selected. Exiting.")
            raise typer.Exit(1)

        typer.echo(
            f"\n✅ Selected datasets: {', '.join([dataset.name for dataset in selected_datasets])}"
        )
        typer.echo(
            f"✅ Selected models: {', '.join([model.name for model in selected_models])}"
        )

        if typer.confirm(f"\nUpdate {dvc_file} and {params_file}?"):
            try:
                render_dvc_yaml(dvc_file, env)

                typer.echo(f"✅ Updated {dvc_file} with selected models and datasets")
                logger.info(
                    f"Successfully updated {dvc_file} with {len(selected_datasets)} datasets and {len(selected_models)} models"
                )

                # Render content for params.yaml
                render_params_yaml(params_file)

                typer.echo(f"✅ Updated {params_file} source paths")
                logger.info(f"Successfully updated {params_file} source section")

            except Exception as e:
                typer.echo(f"❌ Error updating {dvc_file}: {e}")
                logger.error(f"Failed to update {dvc_file}: {e}")
                raise typer.Exit(1)
        else:
            typer.echo("Configuration not saved.")

    except Exception as e:
        logger.error(f"Error in select command: {e}")
        typer.echo(f"❌ Error: {e}")
        raise typer.Exit(1)


if __name__ == "__main__":
    app()
