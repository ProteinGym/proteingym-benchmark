from pg2_benchmark.dummy_data import (
    charge_ladder_dataset,
    adjust_target_with_two_dummy_features,
)


def test_dummy_data_n_rows() -> None:
    ladder = charge_ladder_dataset(n_rows=5, seq_len=10)

    assert len(ladder) <= 5


def test_dummy_data_seq_len() -> None:
    ladder = charge_ladder_dataset(n_rows=5, seq_len=10)

    for row in ladder.itertuples():
        assert len(row.sequence) == 10


def test_dummy_data_with_extra_features() -> None:
    """func:adjust_target_with_two_dummy_features adds the 'foo' and 'bar' columns."""
    expected_columns = ["foo", "bar"]

    ladder = charge_ladder_dataset(n_rows=5, seq_len=10)
    ladder = ladder.pipe(adjust_target_with_two_dummy_features, target="charge")

    assert set(expected_columns).issubset(set(ladder.columns))
