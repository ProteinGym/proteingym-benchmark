import pytest
from pathlib import Path
from pg2_benchmark.manifest import Manifest
from pydantic import ValidationError


@pytest.fixture
def manifest_contents() -> str:
    return """
name = "dummy"

[hyper_params]
nogpu = false
"""


@pytest.fixture
def manifest_path(tmp_path: Path, manifest_contents: str) -> Path:
    """A (temporary) manifest file."""
    manifest_file = tmp_path / "manifest.toml"
    manifest_file.write_text(manifest_contents, encoding="utf-8")
    return manifest_file


def test_manifest_from_path(manifest_path: Path) -> None:
    """Happy flow for loading a Manifest from a file path."""
    try:
        Manifest.from_path(manifest_path)
    except ValidationError as e:
        raise ValidationError("ValidationError raised") from e
    else:
        assert True, "Manifest loaded successfully from path-like object."


def test_manifest_name(manifest_path: Path) -> None:
    try:
        manifest = Manifest.from_path(manifest_path)
    except ValidationError as e:
        raise ValidationError("ValidationError raised") from e
    else:
        assert manifest.name == "dummy"


def test_manifest_hyper_params(manifest_path: Path) -> None:
    try:
        manifest = Manifest.from_path(manifest_path)
    except ValidationError as e:
        raise ValidationError("ValidationError raised") from e
    else:
        assert manifest.hyper_params["nogpu"] is False
