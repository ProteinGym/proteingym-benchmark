import pytest
from proteingym.base.dataset import Assay, Dataset, Sequence, Subsets
import polars as pl
import numpy as np
from pathlib import Path
import shutil
from tempfile import mkdtemp


@pytest.fixture
def fold() -> int:
    return 0


@pytest.fixture
def target() -> str:
    return "charge"


@pytest.fixture
def split() -> str:
    return "random"


@pytest.fixture(scope="session")
def tmpdir_session() -> Path:
    path = mkdtemp()
    yield Path(path)
    shutil.rmtree(path)


@pytest.fixture
def dummy_predictions_path(
    dummy_subsets: Subsets, target: str, split: str, fold: int, tmpdir_session: Path
) -> Path:
    test_mask = dummy_subsets[split].slices[fold]
    test_data = dummy_subsets[split].dataset[test_mask].to_df()

    def proxy_prediction(y_true, noise_scale, freq=17.3, phase=0.0):
        phi = 1.618
        noise = (
            noise_scale
            * np.sin(freq * y_true + phase)
            * np.cos(freq * phi * y_true + phase)
        )
        return y_true + noise

    test_ground_truth = test_data[target]
    test_predictions = proxy_prediction(
        test_ground_truth, noise_scale=test_ground_truth.std()
    )
    predictions = pl.DataFrame(
        {
            "sequence": test_data["sequence"],
            "test": test_ground_truth,
            "pred": test_predictions,
        }
    )
    predictions_path = tmpdir_session / "predictions.csv"
    predictions.write_csv(predictions_path)
    return predictions_path


def test_predictions_path_exists(dummy_predictions_path: Path):
    assert dummy_predictions_path.is_file()
