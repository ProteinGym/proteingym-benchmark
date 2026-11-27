"""Derive `benchmark/<setting>/models.json` from the model cards in `models/`.

The card is the single source of truth: its `name` is the model id used in
results, its `tags` pick the setting, and the image is always `<folder>:latest`.
Submitting a model therefore needs no edit in this repository — CI runs this
with `--only` to synthesise a list covering just the models a pull request
touched. `--check` exits non-zero when the committed lists have drifted, and
`--validate` parses every card without writing anything.
"""

import argparse
import json
from pathlib import Path

from proteingym.base.model import ModelCard
from pydantic import ValidationError

REPO_ROOT = Path(__file__).resolve().parent.parent
MODELS_DIR = REPO_ROOT / "models"
# card tag -> directory under benchmark/
SETTINGS = {"zero-shot": "zero_shot", "supervised": "supervised"}


class ModelCardError(Exception):
    """The model cards cannot be turned into models.json entries."""


def collect(
    only: set[str] | None, models_dir: Path = MODELS_DIR
) -> dict[str, list[dict]]:
    """Map each setting to its models.json entries, read from the model cards.

    Raises ModelCardError rather than exiting, so importers stay in control.
    """
    lists: dict[str, list[dict]] = {setting: [] for setting in SETTINGS.values()}
    folders = set()
    claimed_by: dict[str, str] = {}  # model id -> the folder whose card claimed it

    for path in sorted(models_dir.glob("*/README.md")):
        folder = path.parent.name
        folders.add(folder)
        if only is not None and folder not in only:
            continue

        try:
            card = ModelCard.from_path(path)
        except ValidationError as exc:
            raise ModelCardError(f"{path}: invalid model card: {exc}") from exc

        # Two cards with one id collide in results and in the DVC stage names
        if card.name in claimed_by:
            raise ModelCardError(
                f"{path}: name '{card.name}' is already used by "
                f"{models_dir / claimed_by[card.name] / 'README.md'}"
            )
        claimed_by[card.name] = folder

        tags = getattr(card, "tags", None) or []
        settings = [setting for tag, setting in SETTINGS.items() if tag in tags]
        if not settings:
            raise ModelCardError(
                f"{path}: no 'zero-shot' or 'supervised' tag (tags: {tags})"
            )
        for setting in settings:
            lists[setting].append({"name": card.name, "image": f"{folder}:latest"})

    if not folders:
        raise ModelCardError(
            f"no model cards under {models_dir}; run: "
            "git submodule update --init models"
        )
    if only and only - folders:
        raise ModelCardError(
            f"--only names folders with no model card: {sorted(only - folders)}"
        )
    return lists


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--only", help="comma-separated model FOLDER names to restrict the output to"
    )
    ap.add_argument(
        "--check",
        action="store_true",
        help="exit non-zero if the committed lists differ from the cards",
    )
    ap.add_argument(
        "--validate",
        action="store_true",
        help="read every model card and exit; writes nothing",
    )
    args = ap.parse_args()

    only = {s.strip() for s in args.only.split(",") if s.strip()} if args.only else None
    if args.check and only:
        ap.error("--check cannot be combined with --only")
    if args.validate and (args.check or only):
        ap.error("--validate cannot be combined with --check or --only")

    try:
        # --validate covers the whole zoo: `--only` never reads the other cards,
        # so a name clash there would surface only in bump-models-pin.yml.
        lists = collect(None if args.validate else only)
    except ModelCardError as exc:
        raise SystemExit(str(exc)) from exc

    if args.validate:
        entries = sum(len(models) for models in lists.values())
        print(f"every model card is usable ({entries} model/setting entries)")
        return 0

    stale = False
    for setting, models in lists.items():
        target = REPO_ROOT / "benchmark" / setting / "models.json"
        rendered = json.dumps({"models": models}, indent=2) + "\n"
        if args.check:
            # a missing file counts as drifted, not a crash
            if (target.read_text() if target.exists() else "") != rendered:
                print(f"{target} is out of date; run scripts/generate_models_list.py")
                stale = True
        else:
            target.write_text(rendered)
            print(f"wrote {target} ({len(models)} model(s))")

    return 1 if stale else 0


if __name__ == "__main__":
    raise SystemExit(main())
