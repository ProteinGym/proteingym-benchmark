# Model

This README details how to add a model to the benchmark.

## Entrypoints

A model requires only one entrypoint: the `train` method, which you can reference from below two models:

* [models/esm/src/proteingym/models/esm/__main__.py]
* [models/pls/src/proteingym/models/pls/__main__.py]

Both **supervised** models and **zero-shot** models call this `train` method, because it is the glue method to glue the packages: `pg2-dataset`, `pg2-benchmark` and the models' original source code together. The method is named `train`, because for SageMaker, it looks for the `train` method as a entrypoint, thus it becomes the common method for both environments: local and AWS.

This entrypoint expects a reference to a dataset, e.g., loaded by `pg2-dataset`: 

```python
from pg2_dataset.dataset import Dataset
dataset = Dataset.from_path(dataset_file)
```

Additionally, this entrypoint also expects a reference to a model card, e.g., loaded by `pg2-benchmark`:

```python
from pg2_benchmark.model import ModelCard
model_card = ModelCard.from_path(model_card_file)
```

Finally, inside this `train` method:

* For a **supervised** model, like [esm](esm/), it calls `load` and `infer` in order:
    * `load` uses `model_card` as input, and returns a model object as output.
    * `infer` uses `dataset`, `model_card` and the model object as input, and returns the inferred predictions in a data frame as output.

* For a **zero-shot** model, like [pls](pls/), it calls `train` and `infer` in order:
    * `train` uses `dataset` and `model_card` as input, and returns a model object as output.
    * `infer` uses `dataset`, `model_card` and the model object as input, and returns the inferred predictions in a data frame as output.

The result data frame is saved on the disk in the local environment and stored in AWS S3 in the cloud environment. After the container is destroyed, the result data frame is persisted for the later metric calculation.

For reference, below an example Python implementation with `typer`:

``` python
# In `__main__.py`
import typer
from pg2_dataset.dataset import Dataset
from pg2_benchmark.model import ModelCard


app = typer.Typer(
    help="My ProteinGym model",
    add_completion=True,
)


@app.command()
def train(
    dataset_file: Annotated[
        Path,
        typer.Option(
            help="Path to the archived dataset",
        ),
    ],
    model_card_file: Annotated[
        Path,
        typer.Option(
            help="Path to the model card file",
        ),
    ],
) -> Path:

    dataset = Dataset.from_path(dataset_file)
    model_card = ModelCard.from_path(model_card_file)

    # For a supervised model
    model = load(model_card)
    df = infer(dataset, model_card, model)
    df.to_csv(...)

    # For a zero-shot model
    model = train(dataset, model_card)
    df = infer(dataset, model_card, model)
    df.to_csv(...)


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
├── model.py
├── preprocess.py
└── utils.py
```

### `__main__.py` 

The `__main__.py` contains the `train` entrypoint as shown above.
The code loads the dataset and model (card) before passing it to the `load`, `train`
or `infer` methods.

### `preprocess.py`

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

### `model.py`

`model.py` contains the code related with model:

``` python
def train(dataset: Dataset, model_card: ModelCard) -> Any
    """Train the model."""
    X, y = load_x_and_y(
        dataset=dataset,
        split="train",
    )

    model = Model(model_card)
    model.fit(X, y)

    return model
```

``` python
def load(model_card: ModelCard) -> Any:
    """Load the model."""
    model = Model(model_card)
    return model
```

``` python
def infer(dataset: Dataset, model_card: ModelCard, model: Any) -> DataFrame:
    """Infer predictions on the data."""
    X, y = load_x_and_y(
        dataset=dataset,
        split="test",
    )

    predictions = model.predict(model_card, X)

    df = DataFrame(predictions)

    return df
```

### `utils.py`

It contains the supporting methods from the original models' code to facilitate the `model.py`.

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

    MODEL_CARD_PATH: Path = PREFIX / "input" / "data" / "model_card" / "README.md"
    """Path to the model card."""

    OUTPUT_PATH = PREFIX / "output"
    """Path to the output, such as the result data frames."""
```

For example, to persist the score for a given dataset and model as csv:

``` python
scores: pd.DataFrame
scores.to_csv(
    f"{SageMakerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{model.name}.csv",
    index=False,
)
```
