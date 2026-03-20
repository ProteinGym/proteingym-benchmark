import pytest
from pathlib import Path


@pytest.fixture(scope="session")
def otu7a_human_path():
    datasets_path = Path(__file__).parent / "data"
    otu7a_human_path = datasets_path / "OTU7A_HUMAN_Tsuboyama_2023_2L2D.pgdata"
    return otu7a_human_path


@pytest.fixture(scope="session")
def model_card_path():
    return Path(__file__).parent / "data/test_model_card.md"
