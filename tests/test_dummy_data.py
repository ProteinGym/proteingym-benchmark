import pytest
from pg2_benchmark.dummy_data import charge_ladder_dataset, add_extra_features


def test_dummy_data_n_rows() -> None:
    ladder = charge_ladder_dataset(5, 10)

    assert len(ladder) <= 5


def test_dummy_data_seq_len() -> None:
    ladder = charge_ladder_dataset(5, 10)

    for row in ladder.itertuples():
        assert len(row.sequence) == 10


@pytest.mark.parametrize("extra_features", ["foo", "bar"])
def test_dummy_data_with_extra_features(extra_features) -> None:
    ladder = charge_ladder_dataset(5, 10)
    ladder = ladder.pipe(add_extra_features, target="charge")

    assert extra_features in ladder.columns
