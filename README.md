# ProteinGym2 Benchmark

This repository holds the ProteinGym2 model zoo, the DVC pipelines that benchmark
those models, and the website that shows model cards, datasets and results.

## Models

The models are in the [models](models/) folder. Each
model is a small Python package with a Dockerfile, run through
[evedesign](https://github.com/evedesignbio/evedesign), which we use
to standardize model building and scoring.

A model's `README.md` (modelcard) contains:
- Metadata: a YAML front matter section at the top (name, evedesign model class, tags, hyper parameters)
- A Markdown description of the model

See [models/README.md](models/README.md) for the full structure, and
[CONTRIBUTING.md](CONTRIBUTING.md) for the submission policy.

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
2. Generate `datasets.json`.
3. Have the model Docker images locally.
4. List the models in `models.json`.

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

#### Create `models.json`

Each entry has the model `name` and its local `image`:

```json
{
  "models": [
    {
      "name": "pls",
      "image": "pls:latest"
    }
  ]
}
```

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

The [cml.yaml](.github/workflows/cml.yaml) workflow runs on every pull request to
`main` that changes something under `models/`. It builds (or reuses from the
GitHub Container Registry) the images of the models changed in the PR, or of all
models if `models.json`, a `dvc.yaml` or `scripts/` changed, runs both games
on a small dummy dataset ([ci_datasets.json](.github/workflows/ci_datasets.json)),
and posts the resulting metrics as a PR comment.

> [!IMPORTANT]
> When you add a model, also add it to `models.json` in the
> [supervised](benchmark/supervised/models.json) or [zero_shot](benchmark/zero_shot/models.json)
> folder, with `image` set to `<model-folder>:latest`. CI only runs models listed there.

[benchmark/metrics.csv](benchmark/metrics.csv) holds the benchmark results shown on the website.

## Website

The SvelteKit website in [src/](src/) is deployed to GitHub Pages on every push to
`main` ([deploy.yml](.github/workflows/deploy.yml)). It reads the model cards in
`models/*/README.md`, `benchmark/metrics.csv`, and the dataset metadata in
`static/manifests.json` and `static/histograms.json`, which
[sync-datasets.yml](.github/workflows/sync-datasets.yml) pulls from Hugging Face
every six hours. See [CONTRIBUTING.md](CONTRIBUTING.md#static-website) for local development.
