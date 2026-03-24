import pytest

from proteingym.models.pkermut.__main__ import train


def test_train(dummy_data_path, model_card_path):
    train(
        dataset_file=dummy_data_path,
        split="random",
        target="charge",
        test_fold=1,
        model_card_file=model_card_path
    )
