import pytest
from proteingym.models.hfregressor.__main__ import train


def test_entrypoint(charge_ladder_path, model_card_path):
    train(charge_ladder_path, model_card_path)
