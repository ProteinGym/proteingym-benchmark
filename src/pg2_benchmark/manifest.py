from pydantic import BaseModel, Field, ConfigDict
from pathlib import Path
from typing import Self, Any
import toml


class Manifest(BaseModel):
    """A manifest representing configuration for a protein language model.

    This class loads and validates model configuration from TOML files, containing
    model metadata and hyperparameters for benchmarking tasks.

    Attributes:
        name: The name of the model
        hyper_params: Dictionary containing model hyperparameters and configuration

    The model allows extra fields beyond the defined attributes to accommodate
    varying model configurations.
    """

    model_config = ConfigDict(extra="allow")

    name: str = ""
    hyper_params: dict[str, Any] = Field(default_factory=dict)

    @classmethod
    def from_path(cls, toml_file: Path) -> Self:
        return cls.model_validate(toml.load(toml_file))
