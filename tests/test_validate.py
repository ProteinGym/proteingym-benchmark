from pathlib import Path

import pytest
from typer.testing import CliRunner

from pg2_benchmark.__main__ import ModelPath, app


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
    tmp_path: Path, valid_model_card_content: str, runner: CliRunner, caplog
):
    """Test successful model card validation."""
    model_name = "test_model"
    model_dir = tmp_path / "models" / model_name
    model_dir.mkdir(parents=True)

    model_card_file = model_dir / ModelPath.MODEL_CARD_PATH
    model_card_file.write_text(valid_model_card_content)

    result = runner.invoke(app, ["validate", model_name, str(model_dir)])

    assert result.exit_code == 0
    assert "✅ Loaded test_model" in caplog.text
    assert "learning_rate" in caplog.text and "batch_size" in caplog.text


def test_model_card_validation_missing_file(tmp_path: Path, runner: CliRunner, caplog):
    """Test validation when model card file doesn't exist."""
    model_name = "nonexistent_model"
    model_dir = tmp_path / "models" / model_name
    model_dir.mkdir(parents=True)

    result = runner.invoke(app, ["validate", model_name, str(model_dir)])

    assert result.exit_code == 1
    assert "❌ Model nonexistent_model does not have a model card" in caplog.text


def test_model_card_validation_invalid_content(
    tmp_path: Path, invalid_model_card_content: str, runner: CliRunner, caplog
):
    """Test validation with invalid model card content."""
    model_name = "invalid_model"
    model_dir = tmp_path / "models" / model_name
    model_dir.mkdir(parents=True)

    model_card_file = model_dir / ModelPath.MODEL_CARD_PATH
    model_card_file.write_text(invalid_model_card_content)

    result = runner.invoke(app, ["validate", model_name, str(model_dir)])

    assert result.exit_code == 1
    assert "❌ Error loading model card" in caplog.text


def test_model_card_validation_empty_file(tmp_path: Path, runner: CliRunner, caplog):
    """Test validation with empty model card file."""
    model_name = "empty_model"
    model_dir = tmp_path / "models" / model_name
    model_dir.mkdir(parents=True)

    model_card_file = model_dir / ModelPath.MODEL_CARD_PATH
    model_card_file.write_text("")

    result = runner.invoke(app, ["validate", model_name, str(model_dir)])

    assert result.exit_code == 1
    assert "❌ Error loading model card" in caplog.text
