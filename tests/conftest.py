import pytest
from pathlib import Path
from proteingym.base.dataset import Assay, Dataset, Sequence, Subsets


@pytest.fixture
def dummy_dataset_path() -> Path:
    return (
        Path(__file__).resolve().parent.parent
        / "datasets/splits/Dummy_test_P0DX94.splits.pgdata"
    )


@pytest.fixture
def dummy_subsets(dummy_dataset_path: Path) -> Subsets:
    return Subsets.from_path(dummy_dataset_path)
