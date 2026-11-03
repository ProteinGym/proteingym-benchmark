# Models

This document describes how a model is structured so the benchmark can run it.

Models are run through [evedesign](https://github.com/evedesignbio/evedesign),
which we use as the framework to standardize model building and scoring. Every
model ships the same generic runner (`__main__.py`); what differs between models
is the evedesign model class named in the model card and its hyperparameters.

> [!NOTE]
> All models will use the evedesign runner. The supervised models `pls`,
> `kermut`, `pkermut` and `huggingface-regressor` still have their own custom
> entrypoints and have not been migrated yet, so do not use them as a template.
> Use one of the zero-shot models (e.g. [blosum62](blosum62/),
> [esm2](esm2/), [esmc](esmc/)) instead.

## Layout

```tree
models/<model-folder>/
├── README.md            # model card: YAML front matter + description
├── Dockerfile           # image the benchmark runs
├── Singularity.def      # optional, for HPC clusters without Docker
├── pyproject.toml       # pinned dependencies + console script
├── uv.lock
├── src/proteingym/models/<package>/
│   ├── __main__.py      # the shared evedesign runner (copy it unchanged)
│   └── model.py         # only if you write your own evedesign model class
└── tests/
```

## Model card

The `README.md` is the model card. Its YAML front matter is read by the runner
(via `proteingym.base.model.ModelCard`), by the benchmark tooling and by the
website; the Markdown body is shown on the website's model page.

```yaml
---
# Model identifier used in the benchmark and on the website
name: blosum62

# Import string for the evedesign model class, either
# "package.module:ClassName" or "package.module.ClassName"
model_class: "proteingym.models.blosum62.model:SubstitutionMatrixScorer"

# "zero-shot" or "supervised" (which benchmark it runs in);
# add "embeddable" if the model can produce embeddings (see `embed` below)
tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Forwarded verbatim as keyword arguments to the model class constructor
hyper_parameters:
    matrix_name: "BLOSUM62"
    gap_penalty: -4.0
---
```

| Field              | Required | Description |
|--------------------|----------|-------------|
| `name`             | Yes      | Model identifier. Usually the folder name, but it may differ (e.g. `huggingface-regressor` is named `ritaregressor`). |
| `model_class`      | Yes      | evedesign model class the runner imports, instantiates and scores with. |
| `tags`             | Yes      | `zero-shot` or `supervised`; optionally `embeddable`. |
| `multi_y`          | No       | Whether the model supports multiple targets. |
| `hyper_parameters` | No       | Constructor keyword arguments for `model_class`. |

Extra fields are allowed. The Markdown body should describe the architecture,
training data and hyperparameters, credit the original paper/code, and state
licences and build-time network access (see [CONTRIBUTING.md](../CONTRIBUTING.md#model-submission-policy)).

## The evedesign runner

`__main__.py` is identical across evedesign models; copy it from an existing
model (e.g. [blosum62](blosum62/src/proteingym/models/blosum62/__main__.py)).
It exposes three commands:

- **`train`** – what the benchmark calls. It:
  1. loads the dataset (`proteingym.base.Subsets`) and model card,
  2. converts them with `evedesign.proteingym.dataset_to_evedesign` into an
     evedesign `System` plus training/test datasets (for `zero-shot` models the
     whole dataset is scored and `--split`/`--test-fold` are ignored),
  3. instantiates `model_class(**hyper_parameters)`, calls `build(system, data=...)`
     and then `score(...)` on the test instances,
  4. writes `/opt/program/output/<dataset>_predictions.pgdata`.

  Options: `--dataset-file`, `--target`, `--split`, `--test-fold`,
  `--model-card-file` (defaults to `/opt/program/README.md`, i.e. the card baked
  into the image), `--model-file` (load a dill-pickled built model, or save one
  there), `--precomputed-instances-file` (use embeddings produced by `embed`).
- **`embed`** – for `embeddable` models: runs `transform()` (or `score()`) over
  the whole dataset and pickles the instances to `/opt/program/output/embeddings.pkl`
  so a downstream model can consume them.
- **`ping`** – health check.

### Choosing the model class

- If evedesign already wraps the model (e.g. `evedesign.models.esm2:ESM2`),
  point `model_class` at it and set its constructor arguments in
  `hyper_parameters`. See [esm2](esm2/), [evmutation](evmutation/),
  [evmutation2](evmutation2/).
- Otherwise, write a class in `model.py` that subclasses evedesign's `BaseModel`
  (or `SupervisedBaseModel`) and the interfaces it supports, such as `Scorer`
  and `Transformer`, and point `model_class` at it. See
  [blosum62](blosum62/src/proteingym/models/blosum62/model.py) for a minimal
  example, or [esmc](esmc/), [gemme](gemme/), [rsalor](rsalor/).

## Containerization

The benchmark runs one ephemeral container per dataset/model (and fold), so the
image must contain everything needed to run.

```dockerfile
FROM python:3.12-slim-bookworm

RUN apt-get update && apt-get install -y git curl ca-certificates && rm -rf /var/lib/apt/lists/*

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /opt/program

# README.md is the model card the runner reads by default
COPY README.md README.md
COPY pyproject.toml pyproject.toml
COPY uv.lock uv.lock
COPY src/ src/
RUN uv sync --no-cache

# Bake weights into the image at build time; containers may run without network.
# See models/esmc/Dockerfile for a pinned Hugging Face download.

ENTRYPOINT ["uv", "run", "<console-script>"]
```

The `<console-script>` is the `[project.scripts]` entry in `pyproject.toml`,
e.g. `blosum62 = "proteingym.models.blosum62.__main__:app"`.

### Build and run

The benchmark expects the image to be tagged `<model-folder>:latest`:

```shell
docker build -t blosum62:latest models/blosum62

docker run --rm \
  -v $(pwd)/datasets/splits/Dummy_test_P0DX94.splits.pgdata:/data.pgdata \
  -v $(pwd)/output:/opt/program/output \
  blosum62:latest train \
  --dataset-file /data.pgdata --target charge --split random --test-fold 0
```

### Test

Run the model's unit tests with `uv run pytest` from its folder.
