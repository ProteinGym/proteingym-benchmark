import importlib
import pickle
from pathlib import Path
from typing import Annotated, Any

import polars as pl
import typer
from evedesign.model import Scorer, Transformer
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


def resolve_model_class(model_card: ModelCard) -> str:
    """Read the evedesign model class import string off the model card.

    Args:
        model_card: Loaded model card.

    Returns:
        The import string for the evedesign model class.

    Raises:
        typer.BadParameter: If the card is missing the model_class field.
    """
    model_class = getattr(model_card, "model_class", None)
    if not model_class:
        raise typer.BadParameter(
            "Model card is missing the required 'model_class' field (import "
            "string for the evedesign model class, e.g. "
            "'evedesign.models.esm2:ESM2')."
        )
    return model_class


@app.command()
def train(
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
    embeddings_file: Annotated[
        Path | None,
        typer.Option(
            help="Path to a precomputed embeddings file (as written by the "
            "'embed' command). When supplied, the enhanced instances are "
            "substituted into the train/test datasets before building.",
        ),
    ] = None,
):
    subsets = Subsets.from_path(dataset_file)
    model_card = ModelCard.from_path(model_card_file)

    # Import string for the model class is carried on the model card itself.
    model_class = resolve_model_class(model_card)

    zero_shot = "zero-shot" in model_card.tags

    if not zero_shot and (split is None or test_fold is None):
        raise typer.BadParameter(
            "--split and --test-fold are required for non-zero-shot models"
            "they may only be omitted when the model card is tagged zero-shot."
        )

    system, training_dataset, test_dataset = dataset_to_evedesign(
        subsets,
        split=None if zero_shot else split,
        target=target,
        test_fold=None if zero_shot else test_fold,
    )

    # Optionally swap in instances enhanced with precomputed embeddings/scores
    # (step 1 of an evedesign pipeline). The embeddings are produced upstream by
    # an embeddable model, so this is independent of the current card's tags.
    if embeddings_file is not None:
        console.print(f"Loading precomputed embeddings from {embeddings_file}")
        with open(embeddings_file, "rb") as f:
            embedded_instances = pickle.load(f)

        # Map each enhanced instance back to its sequence so it can be
        # substituted into the train/test datasets. Let it fail with KeyError
        # if the precomputed set does not cover every dataset instance.
        instance_map = {
            "".join(instance[0].rep): instance for instance in embedded_instances
        }

        if training_dataset is not None:
            training_dataset.instances = [
                instance_map["".join(instance[0].rep)]
                for instance in training_dataset.instances
            ]

        if test_dataset is not None:
            test_dataset.instances = [
                instance_map["".join(instance[0].rep)]
                for instance in test_dataset.instances
            ]

    ModelClass = import_class(model_class)

    # load -> build -> score, using the imported model's own API.
    model = ModelClass(**model_card.hyper_parameters)

    # training_dataset is None in the zero-shot/unsupervised case, consistent
    # with the method signature of unsupervised models.
    model = model.build(system, data=training_dataset)

    # Score the test dataset (the whole dataset in the zero-shot case).
    instances, values, _, _ = test_dataset.select(name=target, drop_missing=False)
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
def embed(
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
    model_card_file: Annotated[
        Path,
        typer.Option(
            help="Path to the model card markdown file",
        ),
    ] = ContainerTrainingJobPath.MODEL_CARD_PATH,
):
    """Precompute embeddings/scores for the whole dataset and serialize them.

    Applies the model's Transformer (or, failing that, Scorer) interface to
    every instance to enhance it with embeddings/scores, then pickles the
    enhanced instances so a downstream model can load them via
    'train --embeddings-file'.
    """
    subsets = Subsets.from_path(dataset_file)
    model_card = ModelCard.from_path(model_card_file)

    # import string for the model class is carried on the model card itself
    model_class = resolve_model_class(model_card)

    # embedding availability is advertised by a capability tag on the card
    if "embeddable" not in model_card.tags:
        raise typer.BadParameter(
            "This model does not support embedding; its model card is not "
            "tagged 'embeddable'."
        )

    # retrieve the entire dataset without splitting
    system, _, test_dataset = dataset_to_evedesign(
        subsets,
        split=None,
        target=target,
        test_fold=None,
    )

    ModelClass = import_class(model_class)

    # load -> build, using the imported model wrapper
    model = ModelClass(**model_card.hyper_parameters)
    model = model.build(system, data=None)

    # only the instances are needed here (the full dataset in this case)
    instances, _, _, _ = test_dataset.select(name=target, drop_missing=False)

    # Prefer the Transformer interface, which computes embeddings (and scores
    # when the model can produce both). Transformer.transform returns copies
    # the Scorer fallback updates instances in place
    if isinstance(model, Transformer):
        instances = model.transform(instances)
    elif isinstance(model, Scorer):
        scores = model.score(instances)
        for instance, score in zip(instances, scores):
            instance.score = score
    else:
        raise typer.BadParameter(
            "Model must implement the Transformer or Scorer interface to embed."
        )

    # Embeddings are large--pickle the enhanced instances rather than
    # serializing them to text
    output_file = f"{ContainerTrainingJobPath.OUTPUT_PATH}/embeddings.pkl"
    with open(output_file, "wb") as f:
        pickle.dump(instances, f)

    console.print(f"Saved embeddings to {output_file}")


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
