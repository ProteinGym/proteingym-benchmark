import pytest
from pathlib import Path


@pytest.fixture(scope="session")
def charge_ladder_path():
    datasets_path = Path(__file__).parent / "data"
    charge_ladder_path = datasets_path / "charge_ladder.pgdata"
    return charge_ladder_path


@pytest.fixture(scope="session")
def model_card_path():
    return Path(__file__).parent / "test_model.md"
