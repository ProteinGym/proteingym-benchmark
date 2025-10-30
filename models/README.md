# Model

This document details the requirements and suggestions for implementing a model.

## Entrypoint

A model requires at minimum an entrypoint for training: `train`.  The benchmark
framework accesses models via the `train` entrypoint, for example:
- [models/esm/src/proteingym/models/esm/__main__.py]
- [models/pls/src/proteingym/models/pls/__main__.py]

> [!NOTE]
> The `train` entrypoint is convention for ProteinGym benchmark. And, required
> by AWS SageMaker.

The `train` entrypoint expects a reference to a dataset archive, e.g., loaded by
`proteingym.base.Dataset`:

```python
from proteingym.base import Dataset
dataset = Dataset.from_path(dataset_path)
```

Additionally, the `train` entrypoint expects a reference to a model card, e.g., 
loaded by `proteingym.benchmark.model.ModelCard`:

```python
from proteingym.benchmark.model import ModelCard
model_card = ModelCard.from_path(model_card_path)
```

Finally, common logic in the `train` method:

- For a **supervised** model, like [esm](models/esm/), it calls `load` and `infer` in order: 
  - `load` uses `model_card` as input, and returns a model object as output.  
  - `infer` uses `dataset`, `model_card` and the model object as input, and returns the inferred predictions in a data frame as output.

- For a **zero-shot** model, like [pls](models/pls/), it calls `train` and `infer` in order:
    - `train` uses `dataset` and `model_card` as input, and returns a model object as output.
    - `infer` uses `dataset`, `model_card` and the model object as input, and returns the inferred predictions in a data frame as output.

For reference, below an example Python implementation with `typer`:

``` python
# In `__main__.py`
import typer
from proteingym.base import Dataset
from proteingym.benchmark.model import ModelCard


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

## Output

The framework runs ephemeral containers for each model-dataset pair. Therefore,
the resulting data frame is persisted on disk in the local environment or stored
in AWS S3 bucket in the cloud environment, so that the data frame can be later used
for metric calculation.

## Suggested code structure

> [!NOTE]
> Python examples below translates to other languages.

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

It contains the supporting methods from the original models' code to facilitate
the `model.py`.

## Containerization

Models in the ProteinGym benchmark are containerized using Docker to ensure
reproducibility, portability, and compatibility with different execution
environments (local and cloud-based like AWS SageMaker).

### Dockerfile Structure

Each model should include a `Dockerfile` in its root directory. The Dockerfile
follows a standard structure:

```dockerfile
FROM python:3.12-slim-bookworm

# Copy uv package manager from official image
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /opt/program

# Copy ProteinGym dependencies (required until packages are public)
COPY dist/proteingym*.whl dist/

# Copy model files and install dependencies
COPY README.md README.md
COPY pyproject.toml pyproject.toml
COPY src/ src/
RUN uv sync --no-cache

# Set the entrypoint to your model's CLI command
ENTRYPOINT ["uv", "run", "<model-name>"]
```

### Key Components

1. **Base Image**: Uses `python:3.12-slim-bookworm` for a minimal Python
   environment.

2. **Package Manager**: Integrates [uv](https://docs.astral.sh/uv/) for fast,
   reliable dependency management.

3. **Working Directory**: Sets `/opt/program` as the working directory, which is
   the standard location for SageMaker training containers.

4. **Dependencies**:
   - Copies pre-built `proteingym` wheel files from the `dist/` directory
   - Copies `pyproject.toml` to define model-specific dependencies
   - Runs `uv sync --no-cache` to install all dependencies

5. **Entrypoint**: Configures the container to run your model's CLI command
   (e.g., `esm`, `pls`, etc.) using `uv run`.

### Building a Docker Image

To build a Docker image for your model, navigate to the model directory and run:

```bash
docker build -t proteingym-<model-name>:latest .
```

For example:
```bash
cd models/esm
docker build -t proteingym-esm:latest .
```

### Running a Containerized Model

Run the container with the required volume mounts for datasets and model cards:

```bash
docker run --rm \
  -v /path/to/dataset:/opt/ml/input/data/training \
  -v /path/to/model-card:/opt/ml/input/data/model_card \
  -v /path/to/output:/opt/ml/output \
  proteingym-<model-name>:latest \
  train \
  --dataset-file /opt/ml/input/data/training/dataset.pgdata \
  --model-card-file /opt/ml/input/data/model_card/README.md
```

### Best Practices

- **Layer Caching**: Structure your Dockerfile to maximize Docker layer caching.
  Copy dependency files (like `pyproject.toml`) before source code.

- **Minimal Images**: Use slim base images and avoid installing unnecessary
  dependencies to keep image sizes small.

- **No Cache**: Use `--no-cache` with `uv sync` to prevent caching issues in
  containerized environments.

- **Testing**: Always test your containerized model locally before deploying to
  cloud environments.

- **Validation**: Use `proteingym-base validate_model your-model-root-folder` to
  verify your model structure and configuration before containerization.

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
