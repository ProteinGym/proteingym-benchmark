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

### Generate `datasets.json` and `models.json`

In order to create your own benchmark and generate your own `datasets.json` and `models.json`, you can use the `proteingym-base` command as below:

* `proteingym-base list-datasets datasets` will list all datasets under the folder `datasets`.
* `proteingym-base list-models models` will list all models under the folder `models`. Pay attention that in order for a model to be listed, it needs to define its model card as `README.md` with YAML front matter in its root folder.
* `jq` is used to filter the datasets and models.

```shell
proteingym-base list-datasets datasets | jq ... > benchmark/supervised/local/datasets.json
proteingym-base list-models models | jq ... > benchmark/supervised/local/models.json
```

For more information, you can check out [CONTRIBUTING.md](CONTRIBUTING.md) to reference the detailed commands. Also in [cml.yaml](.github/workflows/cml.yaml), you can check out the detailed commands which run in the CI pipeline.

### Supervised

You can benchmark a group of supervised models:
```shell
dvc repro benchmark/supervised/dvc.yaml -s
```

### Zero-shot

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

In the CML pipeline, it will check whether there exist `datasets.json` and `models.json` in the [supervised](benchmark/supervised/) and [zero_shot](benchmark/zero_shot/) folder. If there are no required files, it will generate them using `proteingym-base list-datasets` and `proteingym-base list-models` as mentioned above, otherwise CML pipeline will start based on the existing `datasets.json` and `models.json`.

Besides, `dvc.lock` and `.dvc/cache` will be tracked in Git repo, so if the files, namely `datasets.json` and `models.json`, don't change (e.g., no new datasets added, no new models added), the DVC stages to run the training jobs and calculate the metrics will be skipped, because DVC will compare the cached files with the present `dvc.lock`. It will make the CML pipeline efficient to skip already-run datasets and models.

> [!IMPORTANT]
> If you add a new dataset in [datasets](datasets/) or add a new model in [models](models/), please also update the `datasets.json` and `models.json` respectively in either [supervised](benchmark/supervised/) folder or [zero_shot](benchmark/zero_shot/) folder.

You can find the latest metrics result in either [supervised](benchmark/supervised) folder or [zero_shot](benchmark/zero_shot/) folder, as the latest CML pipeline will commit the metrics back in the main branch, once it is merged.