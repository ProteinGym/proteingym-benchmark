import inspect
from importlib.metadata import entry_points
from pathlib import Path
from typing import Any, Self

import frontmatter
from pydantic import BaseModel, ConfigDict, Field


class ModelCard(BaseModel):
    """A model card representing configuration for a protein language model.

    This class loads and validates model configuration from markdown files, containing
    model metadata and hyperparameters in the front matter for benchmarking tasks.

    Attributes:
        name: The name of the model
        hyper_params: Dictionary containing model hyperparameters and configuration

    The model allows extra fields beyond the defined attributes to accommodate
    varying model configurations.
    """

    model_config = ConfigDict(extra="allow")

    name: str
    hyper_params: dict[str, Any] = Field(default_factory=dict)

    @classmethod
    def from_path(cls, path: Path) -> Self:
        with path.open("r", encoding="utf-8") as file:
            model_card = frontmatter.load(file)

        return cls.model_validate(model_card.metadata)


class EntryPoint(BaseModel):
    """Represents a model entry point with its name and parameters.

    An entry point is a callable function or command that serves as an interface
    to the model package, typically used for training, evaluation, or inference.

    Attributes:
        name: The name of the entry point function or command
        params: List of parameter names that the entry point accepts
    """

    name: str
    params: list[str] = Field(default_factory=list)


class ValidationResult(BaseModel):
    """Result of validating a model package's entry points.

    This class contains the outcome of validating whether a model package
    has the required entry points and can be properly loaded for benchmarking.

    Attributes:
        module_loaded: Whether the model package was successfully loaded
        entry_points: List of discovered entry points in the package
        error: Error message if validation failed, empty string if successful
    """

    module_loaded: bool
    entry_points: list[EntryPoint] = Field(default_factory=list)
    error: str = ""


def validate_model_entrypoint(package_name: str) -> ValidationResult:
    """Validate if a model package has the required 'train' entrypoint."""

    result = ValidationResult(
        module_loaded=True,
    )

    try:
        # Get all entry points and filter by package name
        eps = entry_points()

        package_entrypoints = [ep for ep in eps if ep.dist.name == package_name]

        # Look for console_scripts entry points (where typer apps are typically registered)
        for ep in package_entrypoints:
            if ep.group == "console_scripts":
                # Load the entry point to get the typer app
                app = ep.load()

                if hasattr(app, "registered_commands"):
                    for command in app.registered_commands:
                        sig = inspect.signature(command.callback)

                        entry_point = EntryPoint(
                            name=command.callback.__name__,
                            params=list(sig.parameters.keys()),
                        )

                        result.entry_points.append(entry_point)
        return result

    except Exception as e:
        result.module_loaded = False
        result.error = str(e)
        return result
