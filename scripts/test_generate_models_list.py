"""Checks the model card -> models.json derivation."""

import sys

import pytest

from scripts.generate_models_list import ModelCardError, collect, main

CARD = "---\nname: {name}\ntags: {tags}\n---\n\nbody\n"


def card(tmp_path, folder, name, tags):
    (tmp_path / folder).mkdir()
    (tmp_path / folder / "README.md").write_text(CARD.format(name=name, tags=tags))


def test_tags_pick_the_setting_and_the_id_comes_from_the_card(tmp_path):
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

    assert collect({"a"}, tmp_path)["zero_shot"] == [{"name": "a", "image": "a:latest"}]


def test_only_rejects_a_folder_with_no_card(tmp_path):
    # guards CI against a changed-files list that is not a model folder
    card(tmp_path, "a", "a", '["zero-shot"]')

    with pytest.raises(
        ModelCardError, match=r"--only names folders with no model card: \['docs'\]"
    ):
        collect({"docs"}, tmp_path)


def test_a_card_with_no_setting_tag_fails_loudly(tmp_path):
    card(tmp_path, "x", "x", '["embeddable"]')

    with pytest.raises(ModelCardError, match="no 'zero-shot' or 'supervised' tag"):
        collect(None, tmp_path)


def test_a_card_with_no_name_fails_loudly(tmp_path):
    # the id is ModelCard's required field; it reports the omission for us
    (tmp_path / "x").mkdir()
    (tmp_path / "x" / "README.md").write_text('---\ntags: ["zero-shot"]\n---\n\nbody\n')

    with pytest.raises(ModelCardError, match="invalid model card"):
        collect(None, tmp_path)


def test_two_cards_may_not_share_a_name(tmp_path):
    # otherwise one silently shadows the other in results
    card(tmp_path, "a", "clash", '["zero-shot"]')
    card(tmp_path, "b", "clash", '["supervised"]')

    with pytest.raises(ModelCardError, match="name 'clash' is already used by"):
        collect(None, tmp_path)


def test_an_unchecked_out_submodule_fails_loudly(tmp_path):
    with pytest.raises(ModelCardError, match="no model cards under"):
        collect(None, tmp_path)


def test_check_cannot_be_combined_with_only(monkeypatch):
    monkeypatch.setattr(sys, "argv", ["generate_models_list", "--check", "--only", "a"])

    with pytest.raises(SystemExit) as exc:
        main()

    assert exc.value.code == 2  # argparse usage error


def test_only_cannot_see_a_clash_in_a_folder_it_skips(tmp_path):
    # why CI runs --validate too: --only never reads the other cards
    card(tmp_path, "a", "a", '["zero-shot"]')
    card(tmp_path, "b", "clash", '["zero-shot"]')
    card(tmp_path, "c", "clash", '["zero-shot"]')

    assert collect({"a"}, tmp_path)["zero_shot"] == [{"name": "a", "image": "a:latest"}]

    with pytest.raises(ModelCardError, match="name 'clash' is already used by"):
        collect(None, tmp_path)


def test_validate_cannot_be_combined_with_only(monkeypatch):
    monkeypatch.setattr(
        sys, "argv", ["generate_models_list", "--validate", "--only", "a"]
    )

    with pytest.raises(SystemExit) as exc:
        main()

    assert exc.value.code == 2  # argparse usage error
