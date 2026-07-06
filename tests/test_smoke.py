"""Smoke tests for the benchmark repo after metric logic moved to proteingym-base.

Metric calculation now lives in proteingym-base (invoked via the
``proteingym-base evaluate`` console entry point from the DVC pipelines), so
the benchmark repo no longer ships its own metric implementation or tests. These
smoke tests guard the remaining contract: the base CLI is available and the
local aggregation helpers are still exposed.
"""

from typer.testing import CliRunner

from proteingym.base.__main__ import app as base_app
from scripts.utils import app as utils_app


def test_base_evaluate_command_available() -> None:
    """The proteingym-base evaluate command is available for the DVC pipeline."""
    runner = CliRunner()
    result = runner.invoke(base_app, ["evaluate", "--help"])

    assert result.exit_code == 0
    assert "--prediction-path" in result.stdout
    assert "--metric-path" in result.stdout


def test_utils_commands_available() -> None:
    """The local aggregate / generate-csv commands are still registered."""
    runner = CliRunner()
    result = runner.invoke(utils_app, ["--help"])

    assert result.exit_code == 0
    assert "aggregate" in result.stdout
    assert "generate-csv" in result.stdout
