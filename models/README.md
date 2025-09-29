# Model

This README details how to add a model to the benchmark.

## Entrypoints

A model requires only one entrypoint: the `train` method, which you can referecen from below two models:

* [esm/src/pg2_model_esm/__main__.py](esm/src/pg2_model_esm/__main__.py)
* [pls/src/pg2_model_pls/__main__.py](pls/src/pg2_model_pls/__main__.py)

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

## Building the Dockerfile

A Dockerfile is a text file that contains instructions for building a Docker image - think of it as a recipe that tells Docker how to create a consistent, isolated environment for your model. This ensures your model runs the same way across different machines and environments. Docker solves the "it works on my machine" problem, allowing to run models identically on various hardware and configurations for optimal reproducibility. 

### Basic Dockerfile Structure

Every model needs a Dockerfile that follows this pattern:

```dockerfile
# 1. Start with a base Python image
FROM python:3.12-slim-bookworm

# 2. Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    git \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 3. Install uv (fast Python package manager)
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

# 4. Set working directory
# We specifically use /opt/program as AWS expect the files to be present in this location.
WORKDIR /opt/program

# 5. Copy benchmark framework
COPY ./README.md ./pg2-benchmark/README.md
COPY ./pyproject.toml ./pg2-benchmark/pyproject.toml
COPY ./src ./pg2-benchmark/src

# 6. Copy your model's configuration
COPY ./models/YOUR_MODEL/README.md ./README.md
COPY ./models/YOUR_MODEL/pyproject.toml ./pyproject.toml

# 7. Handle private repository access (if needed)
ARG GIT_CACHE_BUST=1
RUN --mount=type=secret,id=git_auth \
    git config --global credential.helper store && \
    cat /run/secrets/git_auth > ~/.git-credentials && \
    chmod 600 ~/.git-credentials

# 8. Install Python dependencies
RUN uv sync --no-cache

# 9. Copy your model's source code
COPY ./models/YOUR_MODEL/src ./src

# 10. Set the entry point
ENTRYPOINT ["uv", "run", "pg2-model"]
```

### Building and Testing

To build your Docker image:

```bash
# From the project root directory
docker build -f models/YOUR_MODEL/Dockerfile -t your-model .
```

To test it locally:

```bash
docker run --rm your-model train --help
```

