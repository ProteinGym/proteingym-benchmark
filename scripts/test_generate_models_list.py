"""Checks the model card -> models.json derivation."""

import pytest

from scripts.generate_models_list import collect

CARD = "---\nname: {name}\ntags: {tags}\n---\n\nbody\n"


def card(tmp_path, folder, name, tags):
    (tmp_path / folder).mkdir()
    (tmp_path / folder / "README.md").write_text(CARD.format(name=name, tags=tags))


def test_tags_pick_the_game_and_the_id_comes_from_the_card(tmp_path):
    # huggingface-regressor really is named ritaregressor, so folder != id
    card(tmp_path, "blosum62", "blosum62", '["zero-shot"]')
    card(tmp_path, "huggingface-regressor", "ritaregressor", '["supervised"]')

    lists = collect(None, tmp_path)

    assert lists["zero_shot"] == [{"name": "blosum62", "image": "blosum62:latest"}]
    assert lists["supervised"] == [
        {"name": "ritaregressor", "image": "huggingface-regressor:latest"}
    ]


def test_only_restricts_the_list(tmp_path):
    card(tmp_path, "a", "a", '["zero-shot"]')
    card(tmp_path, "b", "b", '["zero-shot"]')

    assert collect({"a"}, tmp_path)["zero_shot"] == [
        {"name": "a", "image": "a:latest"}
    ]


def test_only_rejects_a_folder_with_no_card(tmp_path):
    # guards CI against a changed-files list that is not a model folder
    card(tmp_path, "a", "a", '["zero-shot"]')

    with pytest.raises(SystemExit):
        collect({"docs"}, tmp_path)


def test_a_card_with_no_game_tag_fails_loudly(tmp_path):
    card(tmp_path, "x", "x", '["embeddable"]')

    with pytest.raises(SystemExit):
        collect(None, tmp_path)


def test_an_unchecked_out_submodule_fails_loudly(tmp_path):
    with pytest.raises(SystemExit):
        collect(None, tmp_path)
