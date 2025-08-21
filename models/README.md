# Containerise model in Docker

## Structure

The basic structure for a supervised and a zero-shot model is listed respectively as below. The difference is that a supervised model has `train.py`, whereas a zero-shot model doesn't have it.

What is in common is `__main__.py`, which contains the glue code to glue the model's original source code with `pg2-dataset` / `pg2-benchmark`.

### Supervised model

For a supervised model, since it needs to be trained with the training dataset, its source code structure is as below:

```shell
├── __main__.py
├── predict.py
├── preprocess.py
└── train.py
```

### Zero-shot model

For a zero-shot model, since it does not need training, its source code structure is as below:

```shell
├── __main__.py
├── predict.py
├── preprocess.py
```

> [!TIP]
> * `__main__.py` contains the glue code to load the dataset using `pg2-dataset` and the model manifest using `pg2-benchmark`. Namely, the following two classes are imported and used: `from pg2_dataset.dataset import Dataset` and `from pg2_benchmark.manifest import Manifest`.
>
> * `preprocess.py` contains the data preprocessing code, like encoding and load training or test split of the dataset.
>
> * `train.py` contains the training code, which might use `preprocess.py`'s `encode()` function to encode the data before feeding into the model and the model's `Manifest` to load hyper parameters.
>
> * `predict.py` contains the scoring code, which might use `preprocess.py`'s `encode()` function and model's `Manifest` as well.

### API via train()

The entrypoint for each model is defined in the function `train()`, which is the same for all models:

```python
from typing import Annotated
from pathlib import Path
import typer
from pg2_dataset.dataset import Dataset
from pg2_benchmark.manifest import Manifest

def train(
    dataset_file: Annotated[
        Path,
        typer.Option(
            help="Path to the dataset file",
        ),
    ] = SageMakerTrainingJobPath.TRAINING_JOB_PATH,
    model_toml_file: Annotated[
        Path,
        typer.Option(
            help="Path to the model TOML file",
        ),
    ] = SageMakerTrainingJobPath.MANIFEST_PATH,
):

    dataset = Dataset.from_path(dataset_file)

    manifest = Manifest.from_path(model_toml_file)

    # After dataset and model manifest are loaded,
    # the remaining glue code goes below to bind `preprocess.py`, `train.py` and `predict.py`.
    ...
```

> [!IMPORTANT]
> The input for the function `train()` is universal for all models, which are:
> * `dataset_file`: the archive file of the desired dataset.
> * `model_toml_file`: the model's manifest file, which contains the definition of hyper parameters.

### SageMaker settings

The other common thing is the SageMaker settings, which is the same for every model.

They are the paths internal to SageMaker to mount S3 to the paths inside the containers. When the containers are destroyed, the results can be kept safely inside S3 buckets. The paths are defined as below, you can copy and paste the below class for each model you want to containerise, if you also plan to use AWS to run benchmarking.

```python
class SageMakerTrainingJobPath:
    PREFIX = Path("/opt/ml")
    TRAINING_JOB_PATH = PREFIX / "input" / "data" / "training" / "dataset.zip"
    MANIFEST_PATH = PREFIX / "input" / "data" / "manifest" / "manifest.toml"
    PARAMS_PATH = PREFIX / "input" / "config" / "hyperparameters.json"
    OUTPUT_PATH = PREFIX / "model"

    MODEL_PATH = Path("/model.pkl")
```

The last thing to pay attention to is to save the result data frame in CSV files in the desired paths, which are always as below:

```python
df.to_csv(
    f"{SageMakerTrainingJobPath.OUTPUT_PATH}/{dataset.name}_{manifest.name}.csv",
    index=False,
)
```
