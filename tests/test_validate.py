from pathlib import Path
from unittest.mock import patch

import pytest
from typer.testing import CliRunner

from pg2_benchmark.cli.validate import ModelPath, validate_app


@pytest.fixture
def runner() -> CliRunner:
    """Test runner for CLI commands."""
    return CliRunner()


@pytest.fixture
def valid_model_card_content() -> str:
    """Valid model card content for testing."""
    return """---
name: "test_model"
hyper_params:
    learning_rate: 0.001
    batch_size: 32
---

# Test Model
This is a test model card.
"""


@pytest.fixture
def invalid_model_card_content() -> str:
    """Invalid model card content for testing."""
    return """---
invalid_yaml: [
---

# Invalid Model Card
"""


def test_model_card_validation_success(
    tmp_path: Path, valid_model_card_content: str, runner: CliRunner
):
    """Test successful model card validation."""
    model_name = "test_model"
    model_dir = tmp_path / "models" / model_name
    model_dir.mkdir(parents=True)

    model_card_file = model_dir / "README.md"
    model_card_file.write_text(valid_model_card_content)

    with patch.object(ModelPath, "ROOT_PATH", tmp_path / "models"):
        result = runner.invoke(validate_app, ["model-card", model_name])

    assert result.exit_code == 0
    assert "✅ Loaded test_model" in result.stdout
    assert "learning_rate" in result.stdout and "batch_size" in result.stdout


def test_model_card_validation_missing_file(tmp_path: Path, runner: CliRunner):
    """Test validation when model card file doesn't exist."""
    model_name = "nonexistent_model"

    with patch.object(ModelPath, "ROOT_PATH", tmp_path / "models"):
        result = runner.invoke(validate_app, ["model-card", model_name])

    assert result.exit_code == 1
    assert "❌ Model nonexistent_model does not have a model card" in result.stdout


def test_model_card_validation_invalid_content(
    tmp_path: Path, invalid_model_card_content: str, runner: CliRunner
):
    """Test validation with invalid model card content."""
    model_name = "invalid_model"
    model_dir = tmp_path / "models" / model_name
    model_dir.mkdir(parents=True)

    model_card_file = model_dir / "README.md"
    model_card_file.write_text(invalid_model_card_content)

    with patch.object(ModelPath, "ROOT_PATH", tmp_path / "models"):
        result = runner.invoke(validate_app, ["model-card", model_name])

    assert result.exit_code == 1
    assert "❌ Error loading model card" in result.stdout


def test_model_card_validation_empty_file(tmp_path: Path, runner: CliRunner):
    """Test validation with empty model card file."""
    model_name = "empty_model"
    model_dir = tmp_path / "models" / model_name
    model_dir.mkdir(parents=True)

    model_card_file = model_dir / "README.md"
    model_card_file.write_text("")

    with patch.object(ModelPath, "ROOT_PATH", tmp_path / "models"):
        result = runner.invoke(validate_app, ["model-card", model_name])

    assert result.exit_code == 1
    assert "❌ Error loading model card" in result.stdout


def test_model_entrypoint_missing_main_file(tmp_path: Path, runner: CliRunner):
    """Test model_entrypoint validation when __main__.py file doesn't exist."""
    model_name = "test_model"

    with patch.object(ModelPath, "ROOT_PATH", tmp_path / "models"):
        result = runner.invoke(validate_app, ["model-entrypoint", model_name])

    assert result.exit_code == 1
    assert f"❌ Model {model_name} does not have a __main__.py file at" in result.stdout


def test_model_entrypoint_success(tmp_path: Path, runner: CliRunner):
    """Test successful model_entrypoint validation."""
    model_name = "test_model"
    model_dir = tmp_path / "models" / model_name
    src_dir = model_dir / "src" / f"pg2_model_{model_name}"
    src_dir.mkdir(parents=True)

    main_py_file = src_dir / "__main__.py"
    main_py_file.write_text("# Dummy main file")

    with (
        patch.object(ModelPath, "ROOT_PATH", tmp_path / "models"),
        patch("subprocess.run") as mock_subprocess,
    ):
        mock_subprocess.return_value.returncode = 0
        mock_subprocess.return_value.stdout = (
            '{"entrypoint_command_found": true, "entrypoint_params_found": true}'
        )

        result = runner.invoke(validate_app, ["model-entrypoint", model_name])

    assert result.exit_code == 0
    assert (
        f"✅ Model {model_name} has a valid 'train' entrypoint with required params"
        in result.stdout
    )


def test_model_entrypoint_missing_command(tmp_path: Path, runner: CliRunner):
    """Test model_entrypoint validation when train command is missing."""
    model_name = "test_model"
    model_dir = tmp_path / "models" / model_name
    src_dir = model_dir / "src" / f"pg2_model_{model_name}"
    src_dir.mkdir(parents=True)

    main_py_file = src_dir / "__main__.py"
    main_py_file.write_text("# Dummy main file")

    with (
        patch.object(ModelPath, "ROOT_PATH", tmp_path / "models"),
        patch("subprocess.run") as mock_subprocess,
    ):
        mock_subprocess.return_value.returncode = 0
        mock_subprocess.return_value.stdout = (
            '{"entrypoint_command_found": false, "entrypoint_params_found": false}'
        )

        result = runner.invoke(validate_app, ["model-entrypoint", model_name])

    assert result.exit_code == 1
    assert f"❌ Model {model_name} does not have a 'train' command" in result.stdout


def test_model_entrypoint_missing_params(tmp_path: Path, runner: CliRunner):
    """Test model_entrypoint validation when required params are missing."""
    model_name = "test_model"
    model_dir = tmp_path / "models" / model_name
    src_dir = model_dir / "src" / f"pg2_model_{model_name}"
    src_dir.mkdir(parents=True)

    main_py_file = src_dir / "__main__.py"
    main_py_file.write_text("# Dummy main file")

    with (
        patch.object(ModelPath, "ROOT_PATH", tmp_path / "models"),
        patch("subprocess.run") as mock_subprocess,
    ):
        mock_subprocess.return_value.returncode = 0
        mock_subprocess.return_value.stdout = (
            '{"entrypoint_command_found": true, "entrypoint_params_found": false}'
        )

        result = runner.invoke(validate_app, ["model-entrypoint", model_name])

    assert result.exit_code == 1
    assert (
        f"❌ Model {model_name}'s 'train' command does not have the required params"
        in result.stdout
    )
