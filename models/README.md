# Model

This README details how to add a model to the benchmark.

## Entrypoints

A model requires the following entrypoints: `train` and `predict`:

The `train` entrypoint is only required for **supervised** models.
The `predict` entrypoint is required for all models.

Both entrypoints expect a reference to a dataset: `dataset_reference`.
Additionally, the `train` entrypoint expects a reference to the model card
and the `predict` entrypoint expects a reference to the peristed model:
`model_card_reference` and `model_reference`, respectively.

Finally, the `train` entrypoint outputs the model reference, which is the input
for the `predict` entrypoint next to the dataset. The `predict` entrypoints
outputs the inferred predictions:

From the commandline these entrypoints interact as follows:

``` bash
$ train ./path/to/dataset_train.pgdata ./path/to/model_card.md
./path/to/model.pickle
$ predict ./path/to/dataset_validate.pgdata ./path/to/model.pickle
[0.8, 0.5, ..., .04]
```

For reference, below an example Python implementation with `typer`:


``` python
# In `__main__.py`
import typer
from pg2_dataset import Dataset
from pg2_benchmark import Manifest


app = typer.Typer(
    help="My ProteinGym model",
    add_completion=True,
)


@app.command()
def train(
    dataset_reference: Annotated[
        Path,
        typer.Option(
            help="Path to the archived dataset",
        ),
    ],
    model_reference: Annotated[
        Path,
        typer.Option(
            help="Path to the model card file",
        ),
    ],
) -> Path:
    """Train the model on the dataset.
    
    Args:
        dataset_reference (Path) : Path to the archived dataset.
        model_reference (Path) : Path to the model card file.

    Returns:
        Path : The trained and persisted model.
    """
    dataset = Dataset.from_path(dataset_path)
    manifest = Manifest.from_path(model_card_path)

    # Train the model below
    model_reference = ...
    return model_reference


def predict(
    dataset_reference: Annotated[
        Path,
        typer.Option(
            help="Path to the archived dataset",
        ),
    ],
    model_reference: Annotated[
        Path,
        typer.Option(
            help="Path to the model file",
        ),
    ],
) -> Iterable[float]:
    """Predict (aka infer) given the dataset and the model. 
    
    Args:
        dataset_reference (Path) : Path to the archived dataset.
        model_reference (Path) : Path to the persisted and trained model file.
    
    Returns:
        Iterable[float] : The predictions.
    """
    dataset = Dataset.from_path(dataset_path)
    model = pickle.load(model_reference)

    # Predict the model below 
    predictions = ...
    return predictions


if __name__ == "__main__":
    app()

```

## Suggested code structure

> [!NOTE]
> Python examples below translates to other languages too.

In addition to the [**required** entrypoints](#entrypoints), we suggest the
following code structure:

``` tree
├── __main__.py
├── predict.py       # For supervised models only
├── preprocess.py
└── train.py
```

### `__main__.py` 

The `__main__.py` contains the `train` and `predict` entrypoints as shown above.
The code loads the dataset and model (card) before passing it to the `train_model`
or `predict_model` methods after preprocessing.

### `preprocess.py

`preprocess.py` contains the data preprocessing code, functions like:

``` python
def encode(data: np.ndarray) -> np.ndarray:
    """Encode the data."""
    return encoded_data
```

``` python
def train_test_split(data: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Split the data."""
    return train_data, test_data
```

### `train.py`

`train.py` contains the training code, functions like:

``` python
def train(model, Any, X: np.ndarray, y: np.array) -> Path
    """Train the model."""
    model.fit(X, y)
    model_path = model.persist()
    return model_path
```

``` python
def load(model_card_reference: Path) -> Any:
    """Load the model."""
    model_config = ModelCard.from_path(model_card_reference)
    model = Model.from_config(model_config)
    return model
```

### `predict.py`

``` python
def predict(model: Any, X: np.ndarray) -> np.array:
    """Infer predictions on the data."""
    predictions = model.predict(X)
    return predictions
```

## Backends

This section details common logic per backend.

### SageMaker 

When using SageMaker, the references point to S3 paths mounted to the (Docker)
container. Containers are destroyed after running them, but the data can be
safely persisted in the S3 buckets. These mounted paths are defined as below

```python
class SageMakerPathLayout:
    """SageMaker's paths layout."""

    PREFIX: Path = Path("/opt/ml")
    """All Sagemaker paths start with this prefix."""

    TRAINING_JOB_PATH: Path = PREFIX / "input" / "data" / "training" / "dataset.zip"
    """Path to training data."""

    MODEL_CARD_PATH: PAth = PREFIX / "input" / "config" / "model_card.md"
    """Path to the model card."""

    MODEL_PATH: Path = Path("/model.pkl")
    """Model path."""

    OUTPUT_PATH = PREFIX / "output"
    """Output path"""
```

For example, to persist the score for a given dataset and model as csv:

``` python
scores: pd.DataFrame
scores.to_csv(
    f"{SageMakerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{model.name}.csv",
    index=False,
)
```
