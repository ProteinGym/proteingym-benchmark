import importlib
from pathlib import Path
from typing import Annotated, Any

import polars as pl
import typer
from evedesign.proteingym import dataset_to_evedesign
from proteingym.base import Subsets
from proteingym.base.model import ModelCard
from rich.console import Console

app = typer.Typer(
    help="ProteinGym2 - generic evedesign model runner",
    add_completion=True,
)

console = Console()


class ContainerTrainingJobPath:
    PREFIX = Path("/opt/program")
    MODEL_CARD_PATH = PREFIX / "README.md"
    OUTPUT_PATH = PREFIX / "output"


def import_class(import_string: str) -> Any:
    """Resolve an import string to the class it points at.

    Accepts either entry-point style ("evedesign.models.esm2:ESM2") or fully
    dotted style ("evedesign.models.esm2.ESM2"). The resolved class is expected
    to be an evedesign-style model exposing build and score methods and
    a constructor that accepts the model card's hyper_parameters as kwargs.

    Args:
        import_string: Import path to the model class.

    Returns:
        The model class object.
    """
    module_path, _, attr = import_string.partition(":")
    if not attr:
        module_path, _, attr = import_string.rpartition(".")
    if not module_path or not attr:
        raise ValueError(
            f"Could not parse import string {import_string!r}; expected "
            "'package.module:ClassName' or 'package.module.ClassName'."
        )
    module = importlib.import_module(module_path)
    return getattr(module, attr)


@app.command()
def train(
    model_class: Annotated[
        str,
        typer.Option(
            help=(
                "Import string for the evedesign model class to run, e.g. "
                "'evedesign.models.esm2:ESM2'. The class is instantiated with "
                "the model card hyper_parameters, then built and scored."
            ),
        ),
    ],
    dataset_file: Annotated[
        Path,
        typer.Option(
            help="Path to the dataset file",
        ),
    ],
    target: Annotated[
        str,
        typer.Option(
            help="Target name to use",
        ),
    ],
    split: Annotated[
        str | None,
        typer.Option(
            help="Split name to use. Omit for zero-shot / whole-dataset scoring.",
        ),
    ] = None,
    test_fold: Annotated[
        int | None,
        typer.Option(
            help="Test fold index. Omit for zero-shot / whole-dataset scoring.",
        ),
    ] = None,
    model_card_file: Annotated[
        Path,
        typer.Option(
            help="Path to the model card markdown file",
        ),
    ] = ContainerTrainingJobPath.MODEL_CARD_PATH,
):
    subsets = Subsets.from_path(dataset_file)
    model_card = ModelCard.from_path(model_card_file)

    zero_shot = "zero-shot" in model_card.tags

    if not zero_shot and (split is None or test_fold is None):
        raise typer.BadParameter(
            "--split and --test-fold are required for non-zero-shot models"
            "they may only be omitted when the model card is tagged zero-shot."
        )

    system, dataset = dataset_to_evedesign(
        subsets,
        split=None if zero_shot else split,
        target=target,
        test_fold=None if zero_shot else test_fold,
    )

    # Supervised models build against the training data
    # but everything else builds from the System alone.
    data = dataset if "supervised" in model_card.tags else None

    ModelClass = import_class(model_class)

    # load -> build -> score, using the imported model's own API
    model = ModelClass(**model_card.hyper_parameters)
    model = model.build(system, data=data)

    # zero-shot scores the whole dataset
    eval_set = dataset.training_set if zero_shot else dataset.test_set
    instances, values = eval_set.select(name=target, drop_missing=False)
    preds = model.score(instances)

    sequences = ["".join(instance[0].rep) for instance in instances]

    df = pl.DataFrame(
        {
            "sequence": sequences,
            "test": values,
            "pred": [float(p) for p in preds],
        }
    )

    output_file = f"{ContainerTrainingJobPath.OUTPUT_PATH}/predictions.json"
    df.write_json(output_file)
    console.print(f"Saved predictions to {output_file}")


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
