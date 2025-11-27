# ProteinGym2 Benchmark

## Models

The models are included in the [models](models/) folder, where each model occupies a subfolder as its repo.

A model repo contains its README.md as a model card, which comes in two parts:
- Metadata, which is a YAML section at the top, i.e., front matter.
- Text descriptions, which is a Markdown file, including summary and descriptions of the model.

For more information, you can reference Hugging Face's [model cards](https://huggingface.co/docs/hub/en/model-cards).

## Datasets

The datasets are included in the [dataset](datasets/) folder, where each dataset is an archived file with suffix `pgdata`.

In order to build the archived file for each dataset, [proteingym-base](https://github.com/ProteinGym/proteingym-base) is used.

You can reference [this guide](https://github.com/ProteinGym/proteingym-base?tab=readme-ov-file#archive-data) to build the archived dataset.

## Benchmark

The benchmark is defined in the [benchmark](benchmark/) folder, where there exist two games: supervised and zero-shot. Each game has its selected list of models and datasets defined in `dvc.yaml`.

- Supervised game is defined in this [dvc.yaml](benchmark/supervised/dvc.yaml).
- Zero-shot game is defined in this [dvc.yaml](benchmark/zero_shot/dvc.yaml).

The models and datasets are defined in `vars` at the top, and DVC translates `vars` into a matrix, which is namely a loop defined as the following pseudo-code:

```python
for dataset in datasets:
    for model in models:
        predict()

for dataset in datasets:
    for model in models:
        calculate_metric()
```

### Prerequisites

In order to benchmark a selected list of models and datasets, it depends on the following criteria:
1. Generate your own `datasets.json`.
2. Have Docker model images locally.
3. Create your own `models.json`.

#### Step 1: Generate `datasets.json`

To generate the `datasets.json` , you need to use the `proteingym-base` command:

* `proteingym-base list-datasets datasets` will list all datasets under the folder `datasets`.
* `jq` is used to filter the datasets.

```shell
proteingym-base list-datasets datasets | jq ... > benchmark/supervised/local/datasets.json
```

For more information, you can check out [CONTRIBUTING.md](CONTRIBUTING.md).

#### Step 2: Build a Docker model image

The DVC pipelines run based on the local Docker images of models. The local images can be either built from Dockerfile or pulled from a remote Docker registry.

To build an image from Dockerfile:

```shell
docker build \
  -f models/pls/Dockerfile \
  -t pls:latest \
  models/pls
```

To pull an image from a remote Docker registry:

```shell
docker pull <repo>/pls:latest
docker tag <repo>/pls:latest pls:latest
```

#### Step 3: Create `models.json`

The example `models.json` looks like below, with a list of models defined by its `name` and local `image` name:

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

### Getting started

With `datasets.json` and `models.json` present in each game's folder: namely [supervised](benchmark/supervised/) and [zero_shot](benchmark/zero_shot/), and Docker is running with the local model images, you can start to benchmark.

#### Supervised

You can benchmark a group of supervised models:
```shell
dvc repro benchmark/supervised/dvc.yaml -s
```

#### Zero-shot

You can benchmark a group of zero-shot models:
```shell
dvc repro benchmark/zero_shot/dvc.yaml -s
```

> [!NOTE]
> By default, all pipelines configured by `dvc.yaml` will be recursively checked when executing `dvc repro`. As a result, if either `datasets.json` or `models.json` are missing in any pipelines, an error will be thrown. So the command option `--single-item` (`-s`) is used to restrict what gets checked by turning off the recursive search for changed dependencies of all pipelines.
>
> For example, if you run `dvc repro ... -s` in `supervised` folder, only `datasets.json` and `models.json` in `supervised` folder are checked for its `dvc.yaml` dependencies, excluding the `zero_shot` folders.

> [!TIP]
> To run specific parts of the pipeline with DVC, you can run `dvc repro --downstream <stage_name>`. For example, `dvc repro --downstream calculate_metric`.

> [!TIP]
> To ignore cache and run anew, you can run `dvc repro --force`.

> [!TIP]
> By default, DVC will stop execution when any stage fails. If one dataset-model pair's metric calculation fails (e.g., due to a missing prediction file, script error, or invalid data), DVC will halt the entire pipeline run. In order to prevent this blocking behavior, you can use: `dvc repro --keep-going`. This flag tells DVC to continue executing other stages even if some fail.

## CML pipeline

The CML (Continuous Machine Learning) pipeline is configured in [cml.yaml](.github/workflows/cml.yaml), which will be triggered every time there is a PR submitted.

> [!IMPORTANT]
> If you add a new dataset in [datasets](datasets/) or add a new model in [models](models/), please also update the `datasets.json` and `models.json` respectively in either [supervised](benchmark/supervised/) folder or [zero_shot](benchmark/zero_shot/) folder.

You can find the latest metrics result in either [supervised](benchmark/supervised) folder or [zero_shot](benchmark/zero_shot/) folder, as the latest CML pipeline will commit the metrics back in the main branch, once it is merged.