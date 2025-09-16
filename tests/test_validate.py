import json
from pathlib import Path
from unittest.mock import MagicMock, patch

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


@pytest.fixture
def valid_pyproject_content() -> str:
    """Valid pyproject.toml content for testing."""
    return """[project]
name = "test_model"
version = "1.0.0"
description = "Test model package"
"""


def create_model_project(
    tmp_path: Path,
    model_card_content: str,
    pyproject_content: str,
    project_name: str = "test_model",
) -> Path:
    """Helper function to create a model project directory with given content."""
    project_dir = tmp_path / project_name
    project_dir.mkdir(parents=True)

    # Create the model card file
    (project_dir / ModelPath.MODEL_CARD_PATH).write_text(model_card_content)
    # Create the pyproject.toml file
    (project_dir / ModelPath.PYPROJECT_PATH).write_text(pyproject_content)

    return project_dir


def test_model_card_validation_success(
    tmp_path: Path,
    valid_model_card_content: str,
    valid_pyproject_content: str,
    runner: CliRunner,
    caplog,
):
    """Test successful model card validation."""
    project_path = create_model_project(
        tmp_path, valid_model_card_content, valid_pyproject_content, "test_model"
    )

    with patch("subprocess.run") as mock_run:
        mock_result = MagicMock()
        mock_result.stdout = json.dumps(
            {
                "module_loaded": True,
                "entry_points_found": [
                    {"name": "train", "params": ["data_path", "model_path"]},
                    {"name": "predict", "params": ["model_path", "input_path"]},
                ],
            }
        )
        mock_run.return_value = mock_result

        result = runner.invoke(app, ["validate", str(project_path)])

        assert result.exit_code == 0
        assert "✅ Loaded test_model" in caplog.text
        assert "learning_rate" in caplog.text and "batch_size" in caplog.text
        assert (
            "✅ Model test_model loaded successfully with entrypoints:" in caplog.text
        )


def test_model_card_validation_missing_file(runner: CliRunner):
    """Test validation when project directory doesn't exist."""
    nonexistent_project = "nonexistent_model"

    result = runner.invoke(app, ["validate", str(nonexistent_project)])

    # Typer returns exit code 2 for parameter validation errors
    assert result.exit_code == 2


def test_model_card_validation_invalid_content(
    tmp_path: Path,
    invalid_model_card_content: str,
    valid_pyproject_content: str,
    runner: CliRunner,
    caplog,
):
    """Test validation with invalid model card content."""
    project_path = create_model_project(
        tmp_path, invalid_model_card_content, valid_pyproject_content, "invalid_model"
    )

    result = runner.invoke(app, ["validate", str(project_path)])

    assert result.exit_code == 1
    assert "❌ Error loading model card" in caplog.text


def test_model_card_validation_empty_file(
    tmp_path: Path, valid_pyproject_content: str, runner: CliRunner, caplog
):
    """Test validation with empty model card file."""
    project_path = create_model_project(
        tmp_path, "", valid_pyproject_content, "empty_model"
    )

    result = runner.invoke(app, ["validate", str(project_path)])

    assert result.exit_code == 1
    assert "❌ Error loading model card" in caplog.text


def test_entrypoint_validation_subprocess_failure(
    tmp_path: Path,
    valid_model_card_content: str,
    valid_pyproject_content: str,
    runner: CliRunner,
    caplog,
):
    """Test validation when subprocess execution fails."""
    project_path = create_model_project(
        tmp_path, valid_model_card_content, valid_pyproject_content, "test_model"
    )

    with patch("subprocess.run") as mock_run:
        mock_run.side_effect = Exception("Subprocess failed")

        result = runner.invoke(app, ["validate", str(project_path)])

        assert result.exit_code == 1
        assert (
            "❌ Error running validation subprocess: Subprocess failed" in caplog.text
        )


def test_entrypoint_validation_model_failed_to_load(
    tmp_path: Path,
    valid_model_card_content: str,
    valid_pyproject_content: str,
    runner: CliRunner,
    caplog,
):
    """Test validation when model fails to load."""
    project_path = create_model_project(
        tmp_path, valid_model_card_content, valid_pyproject_content, "test_model"
    )

    mock_result = MagicMock()
    mock_result.stdout = json.dumps(
        {
            "module_loaded": False,
            "entry_points_found": [],
            "error": "Module import failed",
        }
    )

    with patch("subprocess.run", return_value=mock_result):
        result = runner.invoke(app, ["validate", str(project_path)])

        assert result.exit_code == 1
        assert "❌ Model test_model failed to load: Module import failed" in caplog.text


def test_entrypoint_validation_empty_entrypoints(
    tmp_path: Path,
    valid_model_card_content: str,
    valid_pyproject_content: str,
    runner: CliRunner,
    caplog,
):
    """Test validation when model loads but has no entrypoints."""
    project_path = create_model_project(
        tmp_path, valid_model_card_content, valid_pyproject_content, "test_model"
    )

    mock_result = MagicMock()
    mock_result.stdout = json.dumps({"module_loaded": True, "entry_points_found": []})

    with patch("subprocess.run", return_value=mock_result):
        result = runner.invoke(app, ["validate", str(project_path)])

        assert result.exit_code == 1
        assert "❌ Model test_model loaded with empty entrypoints." in caplog.text


def test_entrypoint_validation_success(
    tmp_path: Path,
    valid_model_card_content: str,
    valid_pyproject_content: str,
    runner: CliRunner,
    caplog,
):
    """Test successful entrypoint validation."""
    project_path = create_model_project(
        tmp_path, valid_model_card_content, valid_pyproject_content, "test_model"
    )

    mock_result = MagicMock()
    mock_result.stdout = json.dumps(
        {
            "module_loaded": True,
            "entry_points_found": [
                {"name": "train", "params": ["data_path", "model_path"]},
                {"name": "predict", "params": ["model_path", "input_path"]},
            ],
        }
    )

    with patch("subprocess.run", return_value=mock_result):
        result = runner.invoke(app, ["validate", str(project_path)])

        assert result.exit_code == 0
        assert (
            "✅ Model test_model loaded successfully with entrypoints:" in caplog.text
        )
        assert "train" in caplog.text and "predict" in caplog.text


def test_entrypoint_validation_json_parse_error(
    tmp_path: Path,
    valid_model_card_content: str,
    valid_pyproject_content: str,
    runner: CliRunner,
    caplog,
):
    """Test validation when JSON parsing fails."""
    project_path = create_model_project(
        tmp_path, valid_model_card_content, valid_pyproject_content, "test_model"
    )

    mock_result = MagicMock()
    mock_result.stdout = "invalid json"

    with patch("subprocess.run", return_value=mock_result):
        result = runner.invoke(app, ["validate", str(project_path)])

        assert result.exit_code == 1
        assert "❌ Error running validation subprocess:" in caplog.text
