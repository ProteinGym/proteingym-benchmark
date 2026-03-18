import pytest

from proteingym.models.pkermut.__main__ import train


def test_train(otu7a_human_path, model_card_path):
    train(otu7a_human_path, model_card_path)
