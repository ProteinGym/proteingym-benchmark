"""Derive `benchmark/<game>/models.json` from the model cards in `models/`.

The card is the single source of truth: its `name` is the model id used in
results, its `tags` pick the game, and the image is always `<folder>:latest`.
Submitting a model therefore needs no edit in this repository — CI runs this
with `--only` to synthesise a list covering just the models a pull request
touched. `--check` exits non-zero when the committed lists have drifted.
"""

import argparse
import json
from pathlib import Path

import frontmatter

REPO_ROOT = Path(__file__).resolve().parent.parent
MODELS_DIR = REPO_ROOT / "models"
# card tag -> directory under benchmark/
GAMES = {"zero-shot": "zero_shot", "supervised": "supervised"}


def collect(only: set[str] | None, models_dir: Path = MODELS_DIR) -> dict[str, list[dict]]:
    """Map each game to its models.json entries, read from the model cards."""
    lists: dict[str, list[dict]] = {game: [] for game in GAMES.values()}
    folders = set()

    for card in sorted(models_dir.glob("*/README.md")):
        folder = card.parent.name
        folders.add(folder)
        if only is not None and folder not in only:
            continue
        post = frontmatter.load(card)
        name, tags = post.get("name"), post.get("tags") or []
        if not name:
            raise SystemExit(f"{card}: model card has no 'name'")
        games = [game for tag, game in GAMES.items() if tag in tags]
        if not games:
            raise SystemExit(
                f"{card}: no 'zero-shot' or 'supervised' tag (tags: {tags})"
            )
        for game in games:
            lists[game].append({"name": name, "image": f"{folder}:latest"})

    if not folders:
        raise SystemExit(
            f"no model cards under {models_dir}; run: "
            "git submodule update --init models"
        )
    if only and only - folders:
        raise SystemExit(
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
    args = ap.parse_args()

    only = {s.strip() for s in args.only.split(",") if s.strip()} if args.only else None

    stale = False
    for game, models in collect(only).items():
        target = REPO_ROOT / "benchmark" / game / "models.json"
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
