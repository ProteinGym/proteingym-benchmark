from typer.testing import CliRunner

from proteingym.benchmark.__about__ import __version__
from proteingym.benchmark.__main__ import app


def test_cli_callback() -> None:
    """CLI runs the callback function when invoked."""

    runner = CliRunner()
    result = runner.invoke(app)
    assert result.exit_code == 0
    assert "Welcome to the ProteinGym benchmark CLI!" in result.stdout


def test_cli_version() -> None:
    """CLI shows version when --version is used."""

    runner = CliRunner()
    result = runner.invoke(app, ["--version"])

    assert result.exit_code == 0
    assert result.stdout.startswith("v")
    assert __version__ in result.stdout


def test_cli_help() -> None:
    """CLI shows help message when --help is used."""

    runner = CliRunner()
    result = runner.invoke(app, ["--help"])

    assert result.exit_code == 0
    assert "version" in result.stdout
