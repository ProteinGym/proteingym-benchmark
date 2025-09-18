# 1. Model validation

Date: 2025-09-15
Status: WIP

## Context and Problem Statement

There are two roles in a model benchmarking system:
* Model provider: They either provide a model with a GitHub repo, a distribution package or a Docker image, or only share its API for a remote call.
* The person who benchmarks a select list of models: They need a uniform API to call each model to get the same format of result in return, so they can compare them on a equal basis.

In order for a benchmarking repo to work for a variety of models, we need to validate the models to sanity check if these models conform to a certain standard, such as:
* If they have the model card defined as expected, so we can load the model's hyperparamters.
* If they have the mandatory entrypoint, with expected input and output.

In the model validation, we now only consider the following constraints:
- [x] The model is implemented in Python.
- [x] The model provides its source code.
- [x] The model project has a [src layout](https://packaging.python.org/en/latest/discussions/src-layout-vs-flat-layout/).
- [ ] [Optional] The model is containerised which comes with its Dockerfile.
- [ ] [Optional] The model exposes its entrypoints by CLI, such as when executing `pg2-model`, we can list its entrypoints.

Given the above three constraints, we can import the Python module directly from the model to carry out a quick sanity check.

## Decision

Currently, we use the *Option 3*, as it is robust and it verifies the code, instead of the data.

## Decision Drivers

- Driver 1: Least dependencies, e.g., it is ideal to be independent from `uv`; only validate the source code without using `subprocess` and without creating a virtual env.
- Driver 2: Work across platforms, e.g., UNIX platforms or Windows.
- Driver 3: No hardcoded paths and entrypoint names and parameters, such as `train`.
- Driver 4: Least assumptions, e.g., model providers are expected to write tests; model providers are expected to create a CLI application for its model entrypoints.
- Driver 5: Robustness, meaning it is easy to perform this option with robust support, such as Docker or `uv` is actively maintained.

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

## Decision matrix

| Option            | Least dependencies | Work across platforms | No hardcoded paths and names | Least assumptions  | Robust             |
| ----------------- | ------------------ | --------------------- | ---------------------------- | ------------------ | -------------------|
| `pytest`          |                    | :white_check_mark:    | :white_check_mark:           |                    |                    |
| only check src    |                    | :white_check_mark:    |                              |                    |                    |
| install and check |                    | :white_check_mark:    |                              |                    | :white_check_mark: |
| docker            | :white_check_mark: | :white_check_mark:    |                              | :white_check_mark: | :white_check_mark: |

## Option 5: Just use the entry_points.txt

## Consequences

It is an initial step to start to validate models, which model benchmarkers have least control of, so we also assume least dependecies to make it robust first.

The consequence is that it will not try to cover all aspects at first.
