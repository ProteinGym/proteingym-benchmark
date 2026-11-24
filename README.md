# ProteinGym Benchmark

This repository holds the DVC pipelines that benchmark the ProteinGym model zoo
and the website that shows model cards, datasets and results.

## Models

The model zoo lives in its own repository,
[proteingym-models](https://github.com/ProteinGym/proteingym-models), mounted
here as a git submodule at [models](models/):

```shell
git submodule update --init models      # after cloning
```

Each model is a model card, a container recipe and pinned dependencies, run
through the shared `evedesign-proteingym` entrypoint from
[evedesign](https://github.com/evedesignbio/evedesign), which we use to
standardize model building and scoring.

A model's `README.md` (modelcard) contains:
- Metadata: a YAML front matter section at the top (name, evedesign model class, tags, hyper parameters)
- A Markdown description of the model

See the [proteingym-models README](https://github.com/ProteinGym/proteingym-models#readme)
for the full structure, and [CONTRIBUTING.md](CONTRIBUTING.md) for the
submission policy.

> [!IMPORTANT]
> Submit models to [proteingym-models](https://github.com/ProteinGym/proteingym-models),
> not here. A model submission needs no change in this repository: CI reads the
> model card to work out how to run it.

## Datasets

Datasets are `.pgdata` archives built with
[proteingym-base](https://github.com/ProteinGym/proteingym-base) (see its
[archive guide](https://github.com/ProteinGym/proteingym-base?tab=readme-ov-file#archive-data)).
The full collection is published on Hugging Face at
[ProteinGym](https://huggingface.co/datasets/ProteinGym/ProteinGym2.0).

The [datasets](datasets/) folder holds a few local archives: `original/` contains
plain datasets and `splits/` contains datasets with cross-validation splits
(`*.splits.pgdata`), which the benchmark consumes.

## Benchmark

The benchmark is defined in the [benchmark](benchmark/) folder, with two regimes:

- Supervised: [benchmark/supervised/dvc.yaml](benchmark/supervised/dvc.yaml)
- Zero-shot: [benchmark/zero_shot/dvc.yaml](benchmark/zero_shot/dvc.yaml)

Each folder has a `default.yaml` (output folders, metrics, folds),
a `datasets.json` and a `models.json`. DVC expands them into a matrix, roughly:

```python
for dataset in datasets:
    for model in models:
        for fold in folds:              # supervised only
            create_training_job()       # docker run <image> train ...
            calculate_metric()          # proteingym-base evaluate-subset / evaluate-zero-shot
        aggregate_metrics()             # scripts/utils.py aggregate

generate_metrics_csv()                  # scripts/utils.py generate-csv -> benchmark/metrics.csv
```

### Prerequisites

1. Install the Python environment (see [CONTRIBUTING.md](CONTRIBUTING.md#installation)).
2. Check out the models submodule: `git submodule update --init models`.
3. Generate `datasets.json`.
4. Generate `models.json` from the model cards.
5. Have the model Docker images locally.

#### Generate `datasets.json`

```shell
python scripts/generate_datasets_list.py
```

This writes `datasets.json` in both `benchmark/supervised` and `benchmark/zero_shot`,
with one entry per dataset, split and target in `datasets/splits/`. Use `jq` to
filter it, for example to the `random` split:

```shell
jq '{datasets: [.datasets[] | select(.split == "random")]}' benchmark/supervised/datasets.json
```

#### Build a Docker model image

The pipelines run local Docker images, tagged `<model-folder>:latest`:

```shell
docker build -t pls:latest models/pls
```

#### Generate `models.json`

`models.json` is derived from the model cards, never hand-edited:

```shell
python -m scripts.generate_models_list              # both games, every model
python -m scripts.generate_models_list --only pls   # just one, for a quick run
```

Each card's `name` becomes the model id and its `tags` (`zero-shot` /
`supervised`) decide which game it lands in; the image is always
`<model-folder>:latest`. Regenerate after bumping the `models` submodule;
`--check` exits non-zero when the committed lists have drifted from the cards.

### Running

With `datasets.json` and `models.json` in the game folder and Docker running:

```shell
dvc repro benchmark/supervised/dvc.yaml --single-item
dvc repro benchmark/zero_shot/dvc.yaml --single-item
```

> [!NOTE]
> By default `dvc repro` checks every `dvc.yaml` in the repository, so a missing
> `datasets.json` or `models.json` in the other game would raise an error.
> `--single-item` (`-s`) restricts it to the given pipeline.

> [!TIP]
> - Run part of a pipeline: `dvc repro --downstream <stage_name>`, e.g. `dvc repro --downstream calculate_metric`.
> - Ignore the cache and rerun: `dvc repro --force`.
> - Keep going when one dataset/model pair fails: `dvc repro --keep-going`.

## CI

The [validate-model.yml](.github/workflows/validate-model.yml) workflow validates
models through the real pipelines on a small dummy dataset
([ci_datasets.json](.github/workflows/ci_datasets.json)) and posts the resulting
metrics as a PR comment. It runs in two situations:

- **a pull request in [proteingym-models](https://github.com/ProteinGym/proteingym-models)**,
  which calls it as a reusable workflow. This repository is checked out at `main`
  and the PR head is overlaid at `models/`, so only the models that PR touched
  are built and run.
- **a pull request here**, which validates a small representative pair
  (`blosum62` + `pls`, one per game) whenever `benchmark/`, `scripts/`,
  `requirements.txt` or the `models` submodule pin changes. Add the
  `validate-all-models` label, or run the workflow manually with
  `all_models=true`, to sweep every model instead.

Each model is built, run and then **pruned before the next one**: a runner has
roughly 14 GB free and these images run to several GB each, so building them all
up front exhausts the disk. The job also reclaims ~30 GB of unused toolchains
first. Images are reused from the GitHub Container Registry when the model
folder's content hash is unchanged (fork PRs always build).

> [!WARNING]
> A full sweep builds every model sequentially and will exceed a GitHub runner's
> 6-hour job limit (several models take over an hour to build on their own). Use
> it deliberately, and expect sweeping changes to need self-hosted runners or
> validation in batches.

The [bump-models-pin.yml](.github/workflows/bump-models-pin.yml) workflow keeps
the `models` submodule pin in step with proteingym-models' `main`. A submodule
records an exact commit, so a merge there does not reach this repository on its
own; the workflow fast-forwards the pin, regenerates `models.json`, and opens a
pull request so the bump is validated before it lands on `main` and reaches the
website. It polls daily, and proteingym-models can dispatch it on merge for an
immediate bump instead. Both opening that pull request and dispatching it need a
token — see
[CONTRIBUTING.md](CONTRIBUTING.md#keeping-the-models-pin-up-to-date).

> [!NOTE]
> Submitting a model requires no change in this repository. CI synthesises
> `models.json` from the model cards with
> [scripts/generate_models_list.py](scripts/generate_models_list.py), so a new
> model folder is picked up automatically. The committed `models.json` files are
> the published set and are regenerated from the cards after the submodule is
> bumped.

Scores on the dummy dataset are meaningless; CI only checks that each model
builds, runs and produces a scorable prediction set.

[benchmark/metrics.csv](benchmark/metrics.csv) holds the benchmark results shown on the website.

## Website

The SvelteKit website in [src/](src/) is deployed to GitHub Pages on every push to
`main` ([deploy.yml](.github/workflows/deploy.yml)). It reads the model cards in
`models/*/README.md`, `benchmark/metrics.csv`, and the dataset metadata in
`static/manifests.json` and `static/histograms.json`, which
[sync-datasets.yml](.github/workflows/sync-datasets.yml) pulls from Hugging Face
every six hours. See [CONTRIBUTING.md](CONTRIBUTING.md#static-website) for local development.
