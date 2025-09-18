# 1. Model validation

Date: 2025-09-15
Status: WIP

## Context and Problem Statement

There are two roles in a model benchmarking system:
* Model provider or builder: They either provide a model with a GitHub repo, a distribution package or a Docker image, or only share its API for a remote call.
* The person who benchmarks models: They need a uniform API to call each model to get the same format of result in return, so they can compare them on a equal basis.

In order for a benchmarking repo to work for a variety of models, we need to validate the models to sanity check if these models conform to a certain standard, such as:
* If they have the model card defined as expected, so we can load the model's hyperparamters.
* If they have the mandatory entrypoint, with expected input and output.

It is worth being aware that these model validations are also performed from two perspectives:
* Model provider or builder: They want to sanity check their models at first hand to see if they can be integrated in the `pg2-benchark` universe.
* The person who benchmarks models: They want to check for all models, if they provide the same format of input and output, given the same entrypoint. If so, they can list their models in the `pg2-benchmark` websites with their defined model cards.

Given the above context, in the model validation, we now only consider the following constraints:
- [x] The model is implemented in Python.
- [x] The model provides its source code.
- [x] The model project has a [src layout](https://packaging.python.org/en/latest/discussions/src-layout-vs-flat-layout/).
- [x] The model providers or builders can use this `pg2-benchmark` model validation tool to validate models themselves for a feeling of confidence.
- [ ] [Optional] The model is containerised which comes with its Dockerfile.
- [ ] [Optional] The model exposes its entrypoints by CLI, such as when executing `pg2-model`, we can list its entrypoints.
- [ ] [Optional] The model benchmarker can also use this model validation tool to validate all the models. Then it is a debate between static check, execution in venv or only data check.

Given the above three constraints, we can import the Python module directly from the model to carry out a quick sanity check.

## Decision

Currently, we use the *Option 3*, as it is robust and it verifies the code, instead of the data.

## Decision Drivers

- Driver 1: Least dependencies, e.g., it is ideal to be independent from `uv`; only validate the source code without using `subprocess` and without creating a virtual env.
- Driver 2: Work across platforms, e.g., UNIX platforms or Windows.
- Driver 3: No hardcoded paths and entrypoint names and parameters, such as `train`.
- Driver 4: Least assumptions, e.g., model providers are expected to write tests; model providers are expected to create a CLI application for its model entrypoints.
- Driver 5: Robustness, meaning it is easy to perform this option with robust support, such as Docker or `uv` is actively maintained.
- Driver 6: Usefulness, meaning it can capture the failures as early as possible, and be a strong indicator that the model will work or not for the benchmarking system.

## Considered Options

### Option 1: Use `pytest` to verify its own APIs.

The benefit is that the model providers are required to test their own entrypoints, whereas the downside is that this requirement cannot be enforced.

#### Example

In the model project, the following test is defined:

```python
from importlib.metadata import entry_points


def test_entrypoints():
    """Test function that validates the model's entrypoints and their parameters."""

    eps = entry_points()
    package_name = "package-name"

    package_entrypoints = [ep for ep in eps if ep.dist.name == package_name]

    ... 
```

In the benchmark's repo, we only need to run `pytest` for a quick sanity check:

```python
import subprocess

result = subprocess.run(
    [
        "python",
        "-m",
        "pytest",
        "tests/",
        "--import-mode=importlib",
    ],
    cwd=package_path,
    capture_output=True,
    text=True,
)
```

### Option 2: Validate only the source code (with its dependencies)

The benefit is that we only need to check the source code with less assumptions, whereas the downside is that we still need to install the dependencies of the model, e.g., `torch`, which will interfere with the current Python environment. To avoid this, we need to create a separate virtual environment to run the model, which is also the driving force in the first place that we want to containerise the model to expose its entrypoints in a uniform way.

#### Example

In the benchmark's repo, we just import the source code and walk through its main module to check its entrypoints:

```python
import sys

src_path = package_path / "src"
sys.path.insert(0, str(src_path))

# Import the main module
module_name = package_name.replace('-', '_')
main_module = __import__(f"{module_name}.__main__", fromlist=[module_name])

...

# Clean up sys.path
sys.path.remove(str(src_path))
```

### Option 3: Install the model's package with runtime dependencies in a virtual env

The benefit is that in addition to checking the source code, it also checks whether the package can be installed properly and it will be more robust for future usage of the model. The drawback is that it has more dependencies, such as using `uv` to create the virtual env and install the package. Additionally, it expects a CLI application, such as Typer or Click.

#### Example

```python
import subprocess

result = subprocess.run(
    [
        "uv",
        "run",
        "--active",
        "python",
        str(validator_script),
        package_name,
    ],
    cwd=package_path,
    capture_output=True,
    text=True,
)
```

### Option 4: Only verify its exposed Docker entrypoints

The benefit is that we verify it from end to end using the prepared sample data and check if the returned data conforms to our data contract. Besides, it has the least assumptions, as it is more high-level, and it works across all platforms. The downside is that it has more dependencies, such as Docker and the sample data.

#### Example

```shell
docker run --rm ... model-image entrypoint --params ...
```

### Option 5: Only verify the distribution package and its entrypoints

There are two major [package formats](https://packaging.python.org/en/latest/discussions/package-formats/): `wheel` and `sdist`. Both of them have the entrypoints and metadata defined in files inside the package:
* For `wheel`, it is in `entry_points.txt` and `METADATA`.
* For `sdist`, it is in `PKG-INFO`.

The benefit of only checking these files is that we don't need to load the package and its dependencies in the execution envinronment. We only roughly check the definition, referenced from [importlib.metadata – Accessing package metadata](https://docs.python.org/3/library/importlib.metadata.html#entry-points). The downside is that it only checks the entrypoints literally, not on the execution level. Besides, the entrypoints listed in the file are not detailed enough, such as:

```txt
[console_scripts]
pg2-model = pg2_model_esm.__main__:app
```

#### Example

```python
from importlib.metadata import Distribution
from pathlib import Path

dist = Distribution(Path("dist/my_package-0.1.0-py3-none-any.whl"))
print(dist.entry_points)
```

## Decision matrix

| Option               | Least dependencies | Work across platforms | No hardcoded paths and names | Least assumptions  | Robust             | Usefulness         |
| -------------------- | ------------------ | --------------------- | ---------------------------- | ------------------ | -------------------| ------------------ |
| `pytest`             |                    | :white_check_mark:    | :white_check_mark:           |                    |                    |
| only check src       |                    | :white_check_mark:    |                              |                    |                    |
| install and check    |                    | :white_check_mark:    |                              |                    | :white_check_mark: | :white_check_mark: |
| docker               | :white_check_mark: | :white_check_mark:    |                              | :white_check_mark: | :white_check_mark: | :white_check_mark: |
| `importlib.metadata` | :white_check_mark: | :white_check_mark:    | :white_check_mark:           | :white_check_mark: | :white_check_mark: |                    |

## Consequences

It is an initial step to start to validate models, which model benchmarkers have least control of, so we also assume least dependecies to make it robust first.

The consequence is that it will not try to cover all aspects at first.
