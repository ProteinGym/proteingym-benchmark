"""Derive `benchmark/<game>/models.json` from the model cards in `models/`.

The model card is the single source of truth: its `name` is the model id used in
results, and its `tags` decide which game it runs in (`zero-shot` or
`supervised`). The image tag is always `<model-folder>:latest`, which is what
the DVC pipelines and CI expect.

Two uses:

1. Regenerating the committed lists after the `models/` submodule is bumped::

       python -m scripts.generate_models_list

2. CI synthesising an ephemeral list limited to the models a pull request
   touched, so submitting a model needs no edit in this repository::

       python -m scripts.generate_models_list --only esm2,blosum62

   With `--only`, a game whose models are all filtered out gets an empty list,
   and the caller is expected to skip that game.

`--check` exits non-zero if the committed lists disagree with the cards, which
is what the drift check in CI uses.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import frontmatter

REPO_ROOT = Path(__file__).resolve().parent.parent
# card tag -> benchmark directory under benchmark/
GAMES = {"zero-shot": "zero_shot", "supervised": "supervised"}


def discover(models_dir: Path) -> list[dict]:
    """Read every model card under models_dir.

    Returns one record per model folder, in folder order, with the card's
    model id, the image tag the pipelines use, and the games it belongs to.
    """
    found = []
    for card in sorted(models_dir.glob("*/README.md")):
        folder = card.parent.name
        post = frontmatter.load(card)
        name = post.get("name")
        tags = post.get("tags") or []
        if not name:
            raise SystemExit(f"{card}: model card has no 'name'")
        games = [g for tag, g in GAMES.items() if tag in tags]
        if not games:
            raise SystemExit(
                f"{card}: model card has no 'zero-shot' or 'supervised' tag "
                f"(tags: {tags})"
            )
        found.append(
            {"folder": folder, "name": name, "image": f"{folder}:latest", "games": games}
        )
    if not found:
        raise SystemExit(
            f"no model cards found under {models_dir} -- is the models/ "
            "submodule checked out? (git submodule update --init)"
        )
    return found


def build(models: list[dict], game: str) -> dict:
    """Shape the models.json payload for one game."""
    return {
        "models": [
            {"name": m["name"], "image": m["image"]}
            for m in models
            if game in m["games"]
        ]
    }


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--models-dir",
        type=Path,
        default=REPO_ROOT / "models",
        help="directory holding the model folders (default: ./models)",
    )
    ap.add_argument(
        "--only",
        default="",
        help="comma-separated model FOLDER names to restrict the output to",
    )
    ap.add_argument(
        "--check",
        action="store_true",
        help="exit non-zero if the committed lists differ from the cards",
    )
    args = ap.parse_args()

    models = discover(args.models_dir)

    if args.only:
        wanted = {s.strip() for s in args.only.split(",") if s.strip()}
        unknown = wanted - {m["folder"] for m in models}
        if unknown:
            raise SystemExit(
                f"--only names folders with no model card: {sorted(unknown)}"
            )
        models = [m for m in models if m["folder"] in wanted]

    failed = False
    for game in GAMES.values():
        payload = build(models, game)
        target = REPO_ROOT / "benchmark" / game / "models.json"
        rendered = json.dumps(payload, indent=2) + "\n"
        if args.check:
            current = target.read_text() if target.exists() else ""
            if current != rendered:
                print(f"{target} is out of date; run scripts/generate_models_list.py")
                failed = True
        else:
            target.write_text(rendered)
            print(f"wrote {target} ({len(payload['models'])} model(s))")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
