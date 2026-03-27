import pytest
from pathlib import Path


@pytest.fixture(scope="session")
def datasets_path():
    return Path(__file__).parent.parent.parent.parent / "datasets"


@pytest.fixture(scope="session")
def dummy_data_path(datasets_path):
    dummy_path = datasets_path / "splits/Dummy_test_P0DX94.splits.pgdata"
    return dummy_path


@pytest.fixture(scope="session")
def model_card_path():
    return Path(__file__).parent / "data/test_model_card.md"
