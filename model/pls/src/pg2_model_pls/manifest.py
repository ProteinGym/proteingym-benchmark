from pydantic import BaseModel, Field
from pathlib import Path
from typing import Self, Any
import toml


class Manifest(BaseModel):
    name: str = ""
    hyper_params: dict[str, Any] = Field(default_factory=dict)

    @classmethod
    def from_path(cls, toml_file: Path) -> Self:
        return cls.model_validate(toml.load(toml_file))
