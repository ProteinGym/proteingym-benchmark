from pathlib import Path
from unittest.mock import Mock, patch

import pytest
from typer.testing import CliRunner

from pg2_benchmark.__main__ import app


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


@pytest.fixture
def invalid_pyproject_content_missing_project() -> str:
    """Invalid pyproject.toml content for testing."""
    return """[build-system]
requires = ["setuptools"]
# Missing [project] section
"""


@pytest.fixture
def invalid_pyproject_content_missing_name() -> str:
    """Invalid pyproject.toml content for testing."""
    return """[project]
version = "1.0.0"
description = "Test model package"
# Missing name
"""


@pytest.fixture
def mock_valid_entry_points():
    """Context manager fixture for mocking valid entry points."""
    mock_ep = Mock()
    mock_ep.dist.name = "test_model"
    mock_ep.group = "console_scripts"

    mock_app = Mock()
    mock_command = Mock()
    mock_command.callback.__name__ = "train"

    # Create a real function to get signature from
    def mock_train_func(dataset: str, model_path: str):
        pass

    mock_command.callback = mock_train_func
    mock_app.registered_commands = [mock_command]
    mock_ep.load.return_value = mock_app

    return patch("pg2_benchmark.model.metadata.entry_points", return_value=[mock_ep])


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
    (project_dir / "README.md").write_text(model_card_content)
    # Create the pyproject.toml file
    (project_dir / "pyproject.toml").write_text(pyproject_content)

    return project_dir


def test_validation_success(
    tmp_path: Path,
    valid_model_card_content: str,
    valid_pyproject_content: str,
    mock_valid_entry_points,
    runner: CliRunner,
    caplog,
):
    """Test successful model validation with valid entry points and model card."""

    project_path = create_model_project(
        tmp_path, valid_model_card_content, valid_pyproject_content
    )

    with mock_valid_entry_points:
        result = runner.invoke(app, ["validate", str(project_path)])

        assert result.exit_code == 0
        assert (
            "✅ Model test_model loaded successfully with entry points:" in caplog.text
        )
        assert "✅ Loaded test_model with hyper parameters" in caplog.text


def test_validation_missing_project_directory(runner: CliRunner):
    """Test validation when project directory doesn't exist."""
    nonexistent_project = "nonexistent_model"

    result = runner.invoke(app, ["validate", str(nonexistent_project)])

    # Typer returns exit code 2 for parameter validation errors
    assert result.exit_code == 2


def test_validation_pyproject_missing_project_section(
    tmp_path: Path,
    valid_model_card_content: str,
    invalid_pyproject_content_missing_project: str,
    runner: CliRunner,
    caplog,
):
    """Test validation when pyproject.toml is missing [project] section."""
    project_path = create_model_project(
        tmp_path,
        valid_model_card_content,
        invalid_pyproject_content_missing_project,
    )

    result = runner.invoke(app, ["validate", str(project_path)])

    assert result.exit_code == 1
    assert (
        "❌ Validation failed: File does not contain a project header:" in caplog.text
    )


def test_validation_pyproject_missing_name(
    tmp_path: Path,
    valid_model_card_content: str,
    invalid_pyproject_content_missing_name: str,
    runner: CliRunner,
    caplog,
):
    """Test validation when pyproject.toml is missing name under [project] section."""
    project_path = create_model_project(
        tmp_path,
        valid_model_card_content,
        invalid_pyproject_content_missing_name,
    )

    result = runner.invoke(app, ["validate", str(project_path)])

    assert result.exit_code == 1
    assert (
        "❌ Validation failed: The project header does not contain a name:"
        in caplog.text
    )


def test_validation_invalid_model_card(
    tmp_path: Path,
    invalid_model_card_content: str,
    valid_pyproject_content: str,
    mock_valid_entry_points,
    runner: CliRunner,
    caplog,
):
    """Test validation with invalid model card content."""
    project_path = create_model_project(
        tmp_path,
        invalid_model_card_content,
        valid_pyproject_content,
    )

    with mock_valid_entry_points:
        result = runner.invoke(app, ["validate", str(project_path)])

        assert result.exit_code == 1
        assert "❌ Error running validation" in caplog.text


def test_validation_empty_model_card(
    tmp_path: Path,
    valid_pyproject_content: str,
    mock_valid_entry_points,
    runner: CliRunner,
    caplog,
):
    """Test validation with empty model card file."""
    project_path = create_model_project(
        tmp_path, "", valid_pyproject_content, "empty_model"
    )

    with mock_valid_entry_points:
        result = runner.invoke(app, ["validate", str(project_path)])

        assert result.exit_code == 1
        assert "❌ Validation failed: 1 validation error for ModelCard" in caplog.text
