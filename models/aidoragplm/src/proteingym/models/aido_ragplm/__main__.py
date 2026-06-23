"""Typer CLI entrypoint for the AIDO.RAGPLM ProteinGym2 model.

The harness invokes ``uv run aidoragplm train --dataset-file ...`` from
inside the model's Docker container. The signature mirrors the existing
zero-shot baseline (``models/esm``): ``--dataset-file``, ``--split``,
``--test-fold``, ``--target``, plus an optional ``--model-card-file`` that
defaults to ``/opt/program/README.md`` (the model card baked into the image).
"""

from __future__ import annotations

from pathlib import Path
from typing import Annotated

import typer
from proteingym.base import Subsets
from proteingym.base.model import ModelCard
from rich.console import Console

from .model import infer, load

app = typer.Typer(
    help="AIDO.RAGPLM (genbio-ai/AIDO.Protein-RAG-3B) zero-shot mutation scorer",
    add_completion=True,
)

console = Console()


class ContainerTrainingJobPath:
    PREFIX = Path("/opt/program")
    MODEL_CARD_PATH = PREFIX / "README.md"
    OUTPUT_PATH = PREFIX / "output"


@app.command()
def train(
    dataset_file: Annotated[
        Path,
        typer.Option(help="Path to the .splits.pgdata Subsets archive"),
    ],
    split: Annotated[
        str,
        typer.Option(help="Split name to use (e.g. 'random', 'contiguous', 'modulo')"),
    ],
    test_fold: Annotated[
        int,
        typer.Option(help="Index of the slice within the split to treat as test"),
    ],
    target: Annotated[
        str,
        typer.Option(help="Name of the target column (e.g. 'DMS_score')"),
    ],
    model_card_file: Annotated[
        Path,
        typer.Option(help="Path to the model card markdown file"),
    ] = ContainerTrainingJobPath.MODEL_CARD_PATH,
):
    """Score the test fold of a ProteinGym dataset with AIDO.RAGPLM.

    The result is written as JSON to ``$OUTPUT_PATH/predictions.json`` where
    ``$OUTPUT_PATH`` is ``/opt/program/output`` inside the harness container.
    """
    subsets = Subsets.from_path(dataset_file)
    model_card = ModelCard.from_path(model_card_file)

    model = load(model_card)
    df = infer(
        split_dataset=subsets,
        split=split,
        test_fold=test_fold,
        target=target,
        model_card=model_card,
        model=model,
    )

    output_file = f"{ContainerTrainingJobPath.OUTPUT_PATH}/predictions.json"
    df.write_json(output_file)
    console.print(f"[green]Saved predictions to {output_file}")


@app.command()
def ping():
    """Health-check command used by harness smoke tests."""
    console.print("pong")


if __name__ == "__main__":
    app()
