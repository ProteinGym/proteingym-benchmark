# 1. Model validation

Date: 2025-09-15
Status: WIP

## Context and Problem Statement

There are two roles in a model benchmarking system:
* Model provider: They either provide a model with a GitHub repo or a distribution package, or only share its API for a remote call.
* The person who benchmarks a select list of models: They need a uniform API to call each model to get the same format of result in return, so they can compare them on a equal basis.

In order for a benchmarking repo to work for a variety of models, we need to validate them to check if these models conform to a certain standard, such as:
* If they have the model card defined as expected, so we can load the model's hyperparamters.
* If they have the mandatory entrypoint, with expected input and output.

In the model validation, we now only consider the following constraints:
* The model is defined in a public project with its source code accessible.
* The model project has a [src layout](https://packaging.python.org/en/latest/discussions/src-layout-vs-flat-layout/).
* The model is containerised which comes with its Dockerfile.
* The model exposes its entrypoints by CLI, such as when executing `pg2-model`, we can list its entrypoints.

## Decision



## Decision Drivers

- Driver 1: Least dependencies, such as it is ideal to be independent from `uv`.
- Driver 2: Work across platforms.
- Driver 3: Just validate the source code without using `subprocess` and without creating a virtual env.
- Driver 4: Don't have hardcoded paths and entrypoint names, such as `README.md` or `train`.

## Considered Options

### Option 1: Use pytest to verify its own APIs.

In the model project, the following test is defined:

```python
from importlib.metadata import entry_points


def test_entrypoint_validation():
    """Test function that validates the model's entrypoints and their parameters."""

    eps = entry_points()
    package_name = "package-name"

    package_entrypoints = [ep for ep in eps if ep.dist.name == package_name]
        
```

In the benchmark's repo, we only need to run to verify if it passes.:

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

### Option 2: Validate only the source code.

In the benchmark's repo, we just import the source code and walk through its main module to check its entrypoints:

```python
import sys

src_path = package_path / "src"
sys.path.insert(0, str(src_path))

# Import the main module
module_name = package_name.replace('-', '_')
main_module = __import__(f"{module_name}.__main__", fromlist=[module_name])
```


### Option 3: Install the model's package via uv

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

## Decision matrix

| Option | Driver 1 | Driver 2 | Driver 3 | Driver 4 |
| ------ | -------- | -------- | -------- | -------- |
| 1      | High     | High     | High     | High     |
| 2      | High     | High     | High     | Low      |
| 3      | Low      | High     | Low      | Low      |


All the options depends on:
* The source code is either provided or the [source distribution](https://packaging.python.org/en/latest/discussions/package-formats/#what-is-a-source-distribution) package is provided.

## Consequences

