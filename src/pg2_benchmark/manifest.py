from pydantic import BaseModel, Field, ConfigDict
from pathlib import Path
from typing import Self, Any
import toml


class Manifest(BaseModel):
    model_config = ConfigDict(extra="allow")

    name: str = ""
    hyper_params: dict[str, Any] = Field(default_factory=dict)

    @classmethod
    def from_path(cls, toml_file: Path) -> Self:
        return cls.model_validate(toml.load(toml_file))
