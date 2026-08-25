from pathlib import Path
from typing import Annotated

import typer
from proteingym.base import Subsets
from proteingym.base.model import ModelCard

from .model import Cytolexmuta

app = typer.Typer(help="cytolexmuta zero-shot ProteinGym2 runner")


@app.command()
def ping() -> None:
    """Verify that the container entrypoint is installed."""
    print("cytolexmuta-ok")


@app.command()
def train(
    dataset_file: Annotated[
        Path, typer.Option(help="Path to the archived ProteinGym dataset")
    ],
    target: Annotated[str, typer.Option(help="Target field to predict")],
    split: Annotated[
        str | None, typer.Option(help="Benchmark split; ignored for zero-shot")
    ] = None,
    test_fold: Annotated[
        int | None, typer.Option(help="Benchmark fold; ignored for zero-shot")
    ] = None,
    model_card_file: Annotated[
        Path, typer.Option(help="Path to the model card")
    ] = Path("/opt/program/README.md"),
) -> None:
    del split, test_fold
    subsets = Subsets.from_path(dataset_file)
    card = ModelCard.from_path(model_card_file)
    model = Cytolexmuta(**card.hyper_parameters)
    predictions = model.predict(subsets.dataset, target)
    output_dir = Path("/opt/program/output")
    output_dir.mkdir(parents=True, exist_ok=True)
    output_dataset = subsets.dataset.predictions_delta(
        predictions, target=target, allow_extra_predictions=True
    )
    output_dataset.dump(path=output_dir)
    print(
        "Saved predictions to "
        f"{output_dir / (subsets.dataset.name + '_predictions.pgdata')}"
    )


if __name__ == "__main__":
    app()
