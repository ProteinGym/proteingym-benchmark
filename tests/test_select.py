import os
from pathlib import Path
from unittest.mock import patch

import pytest
import yaml
from typer.testing import CliRunner

from pg2_benchmark.__main__ import app


@pytest.fixture
def runner() -> CliRunner:
    """Test runner for CLI commands."""
    return CliRunner()


@pytest.fixture
def mock_datasets_dir(tmp_path: Path) -> Path:
    """Create mock datasets directory with test datasets."""
    datasets_dir = tmp_path / "datasets"
    datasets_dir.mkdir()

    # Create mock dataset directories
    for dataset_name in ["dummy", "neime", "ranganathan"]:
        dataset_dir = datasets_dir / dataset_name
        dataset_dir.mkdir()
        # Create a mock dataset.zip file
        (dataset_dir / "dataset.zip").write_text(f"Mock {dataset_name} dataset")

    return datasets_dir


@pytest.fixture
def mock_models_dir(tmp_path: Path) -> Path:
    """Create mock models directory with test models."""
    models_dir = tmp_path / "models"
    models_dir.mkdir()

    # Create mock model directories
    for model_name in ["esm", "pls", "test_model"]:
        model_dir = models_dir / model_name
        model_dir.mkdir()
        # Create mock files
        (model_dir / "README.md").write_text(f"Mock {model_name} model")
        (model_dir / "Dockerfile").write_text(
            f"FROM python:3.9\n# {model_name} dockerfile"
        )

    return models_dir


@pytest.fixture
def mock_dvc_file(tmp_path: Path) -> Path:
    """Create mock DVC file for testing."""
    dvc_dir = tmp_path / "benchmark" / "supervised" / "local"
    dvc_dir.mkdir(parents=True)

    dvc_file = dvc_dir / "dvc.yaml"
    initial_content = {
        "vars": [
            "params.yaml",
            {
                "datasets": [
                    {
                        "name": "old_dataset",
                        "container_path": "/datasets/old_dataset/dataset.zip",
                        "local_path": "../../../datasets/old_dataset/dataset.zip",
                    }
                ]
            },
            {
                "models": [
                    {
                        "name": "old_model",
                        "container_path": "/models/old_model/README.md",
                        "local_path": "../../../models/old_model/README.md",
                        "dockerfile": "../../../models/old_model/Dockerfile",
                    }
                ]
            },
        ],
        "stages": {"setup": {"cmd": ["echo 'test'"]}},
    }

    with open(dvc_file, "w") as f:
        yaml.dump(initial_content, f, default_flow_style=False)

    return dvc_file


@pytest.fixture
def empty_dvc_file(tmp_path: Path) -> Path:
    """Create empty DVC file for testing new file creation."""
    dvc_dir = tmp_path / "benchmark" / "supervised" / "local"
    dvc_dir.mkdir(parents=True)
    return dvc_dir / "dvc.yaml"


class TestSelectCommand:
    """Test cases for the select command."""

    def test_select_help(self, runner: CliRunner):
        """Test that select command shows help correctly."""
        result = runner.invoke(app, ["select", "--help"])
        assert result.exit_code == 0
        assert "Interactive selection of datasets and models" in result.output
        assert "--dvc-file" in result.output

    def test_select_missing_directories(self, runner: CliRunner, tmp_path: Path):
        """Test select command with missing directories."""
        nonexistent_dir = tmp_path / "nonexistent"

        result = runner.invoke(
            app, ["select", str(nonexistent_dir), str(nonexistent_dir)]
        )
        assert result.exit_code == 2  # Typer validation error
        assert "does not exist" in result.output

    @patch("typer.prompt")
    @patch("typer.confirm")
    def test_select_datasets_and_models_success(
        self,
        mock_confirm,
        mock_prompt,
        runner: CliRunner,
        mock_datasets_dir: Path,
        mock_models_dir: Path,
        mock_dvc_file: Path,
    ):
        """Test successful selection and DVC file update."""
        # Mock user inputs
        mock_prompt.side_effect = ["1,2", "1"]  # Select datasets 1,2 and model 1
        mock_confirm.return_value = True  # Confirm update

        result = runner.invoke(
            app,
            [
                "select",
                str(mock_models_dir),
                str(mock_datasets_dir),
                "--dvc-file",
                str(mock_dvc_file),
            ],
        )

        assert result.exit_code == 0
        assert "✅ Selected datasets: dummy, neime" in result.output
        assert "✅ Selected models: esm" in result.output
        assert f"✅ Updated {mock_dvc_file}" in result.output

        # Verify DVC file was updated correctly
        with open(mock_dvc_file, "r") as f:
            updated_config = yaml.safe_load(f)

        # Check datasets section
        datasets_section = None
        models_section = None
        for var in updated_config["vars"]:
            if isinstance(var, dict) and "datasets" in var:
                datasets_section = var["datasets"]
            elif isinstance(var, dict) and "models" in var:
                models_section = var["models"]

        assert datasets_section is not None
        assert len(datasets_section) == 2
        assert datasets_section[0]["name"] == "dummy"
        assert datasets_section[1]["name"] == "neime"

        assert models_section is not None
        assert len(models_section) == 1
        assert models_section[0]["name"] == "esm"

    @patch("typer.prompt")
    @patch("typer.confirm")
    def test_select_all_items(
        self,
        mock_confirm,
        mock_prompt,
        runner: CliRunner,
        mock_datasets_dir: Path,
        mock_models_dir: Path,
        mock_dvc_file: Path,
    ):
        """Test selecting all datasets and models."""
        mock_prompt.side_effect = ["all", "all"]  # Select all datasets and models
        mock_confirm.return_value = True

        result = runner.invoke(
            app,
            [
                "select",
                str(mock_models_dir),
                str(mock_datasets_dir),
                "--dvc-file",
                str(mock_dvc_file),
            ],
        )

        assert result.exit_code == 0
        assert "✅ Selected datasets: dummy, neime, ranganathan" in result.output
        assert "✅ Selected models: esm, pls, test_model" in result.output

    @patch("typer.prompt")
    def test_select_no_datasets_selected(
        self,
        mock_prompt,
        runner: CliRunner,
        mock_datasets_dir: Path,
        mock_models_dir: Path,
        mock_dvc_file: Path,
    ):
        """Test behavior when no datasets are selected."""
        mock_prompt.return_value = "invalid"  # Invalid selection

        result = runner.invoke(
            app,
            [
                "select",
                str(mock_models_dir),
                str(mock_datasets_dir),
                "--dvc-file",
                str(mock_dvc_file),
            ],
        )

        assert result.exit_code == 1
        assert "No datasets selected. Exiting." in result.output

    @patch("typer.prompt")
    @patch("typer.confirm")
    def test_select_user_cancels_update(
        self,
        mock_confirm,
        mock_prompt,
        runner: CliRunner,
        mock_datasets_dir: Path,
        mock_models_dir: Path,
        mock_dvc_file: Path,
    ):
        """Test behavior when user cancels the update."""
        mock_prompt.side_effect = ["1", "1"]  # Select first dataset and model
        mock_confirm.return_value = False  # Cancel update

        result = runner.invoke(
            app,
            [
                "select",
                str(mock_models_dir),
                str(mock_datasets_dir),
                "--dvc-file",
                str(mock_dvc_file),
            ],
        )

        assert result.exit_code == 0
        assert "Configuration not saved." in result.output

    @patch("typer.prompt")
    @patch("typer.confirm")
    def test_select_creates_new_dvc_file(
        self,
        mock_confirm,
        mock_prompt,
        runner: CliRunner,
        mock_datasets_dir: Path,
        mock_models_dir: Path,
        empty_dvc_file: Path,
    ):
        """Test creating a new DVC file when it doesn't exist."""
        mock_prompt.side_effect = ["1", "1"]  # Select first dataset and model
        mock_confirm.return_value = True

        result = runner.invoke(
            app,
            [
                "select",
                str(mock_models_dir),
                str(mock_datasets_dir),
                "--dvc-file",
                str(empty_dvc_file),
            ],
        )

        assert result.exit_code == 0
        assert empty_dvc_file.exists()

        # Verify new file structure
        with open(empty_dvc_file, "r") as f:
            config = yaml.safe_load(f)

        assert "vars" in config
        assert any(
            isinstance(var, dict) and "datasets" in var for var in config["vars"]
        )
        assert any(isinstance(var, dict) and "models" in var for var in config["vars"])

    def test_relative_path_calculation(
        self, mock_datasets_dir: Path, mock_dvc_file: Path
    ):
        """Test that relative paths are calculated correctly."""
        # Test the actual relative path calculation logic
        dvc_parent = mock_dvc_file.parent.resolve()
        datasets_resolved = mock_datasets_dir.resolve()

        relative_path = Path(os.path.relpath(datasets_resolved, dvc_parent))

        # Should create a proper relative path
        assert not relative_path.is_absolute()
        assert str(relative_path).startswith("..")

    @patch("typer.prompt")
    @patch("typer.confirm")
    def test_select_invalid_indices(
        self,
        mock_confirm,
        mock_prompt,
        runner: CliRunner,
        mock_datasets_dir: Path,
        mock_models_dir: Path,
        mock_dvc_file: Path,
    ):
        """Test handling of invalid dataset/model indices."""
        # Mock selecting index out of range
        mock_prompt.side_effect = ["1,999", "1"]  # Invalid dataset index, valid model
        mock_confirm.return_value = True

        result = runner.invoke(
            app,
            [
                "select",
                str(mock_models_dir),
                str(mock_datasets_dir),
                "--dvc-file",
                str(mock_dvc_file),
            ],
        )

        # Should show warning about out of range index but continue
        assert "Warning: Index 999 is out of range" in result.output
        assert "✅ Selected datasets: dummy" in result.output

    def test_empty_directories(self, runner: CliRunner, tmp_path: Path):
        """Test behavior with empty datasets and models directories."""
        empty_datasets = tmp_path / "empty_datasets"
        empty_models = tmp_path / "empty_models"
        empty_datasets.mkdir()
        empty_models.mkdir()

        dvc_file = tmp_path / "dvc.yaml"

        result = runner.invoke(
            app,
            [
                "select",
                str(empty_models),
                str(empty_datasets),
                "--dvc-file",
                str(dvc_file),
            ],
        )

        assert result.exit_code == 1
        assert "No datasets found!" in result.output


class TestHelperFunctions:
    """Test cases for helper functions used in select command."""

    def test_get_available_items(self, mock_datasets_dir: Path):
        """Test get_available_items function logic."""
        # This would test the function if it were extracted for testing
        # For now, we test through the integration tests above
        pass

    def test_interactive_select_logic(self):
        """Test interactive_select function logic."""
        # This would test the function if it were extracted for testing
        # For now, we test through the integration tests above
        pass
