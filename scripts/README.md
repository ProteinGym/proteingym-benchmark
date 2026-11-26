# Scripts

Utility scripts for the ProteinGym benchmark. Install the Python dependencies
from the repository root first:

```shell
pip install -r requirements.txt
```

## generate_models_list.py

Builds `benchmark/supervised/models.json` and `benchmark/zero_shot/models.json`
from the model cards in `models/`. The card is the single source of truth: its
`name` is the model id used in results, its `tags` decide the game (`zero-shot`
or `supervised`), and the image is always `<model-folder>:latest`.

```shell
python -m scripts.generate_models_list                   # both games, every model
python -m scripts.generate_models_list --only esm2,pls   # restrict to these folders
python -m scripts.generate_models_list --check           # non-zero if lists have drifted
```

Regenerate after bumping the `models` submodule. `--only` is what
[validate-model.yml](../.github/workflows/validate-model.yml) uses to synthesise
an ephemeral list covering just the models a pull request touched -- which is why
submitting a model to
[proteingym-models](https://github.com/ProteinGym/proteingym-models) needs no
change in this repository.

Requires the `models` submodule to be checked out
(`git submodule update --init models`). `scripts/test_generate_models_list.py`
covers the derivation.

## generate_datasets_list.py

Builds `benchmark/supervised/datasets.json` and `benchmark/zero_shot/datasets.json`
with one entry per dataset, split and target found in `datasets/splits/*.splits.pgdata`.
Run it from the repository root:

```shell
python scripts/generate_datasets_list.py
```

> [!NOTE]
> It also writes `static/datasets-list.json` and `static/datasets/*.lock`. The
> website no longer reads those files; dataset pages use `static/manifests.json`,
> which the [sync-datasets](../.github/workflows/sync-datasets.yml) workflow
> pulls from Hugging Face, and `static/histograms.json`, which nothing generates
> yet (see [CONTRIBUTING.md](../CONTRIBUTING.md#static-website)).

## histograms.py

`dataset_histogram(inner_zip, toml.loads(manifest_text))` — the score histograms
the website's dataset pages want in `static/histograms.json`. Nothing generates
that file yet; see [CONTRIBUTING.md](../CONTRIBUTING.md#static-website) for where
this belongs and why it is here. `scripts/test_histograms.py` covers it.

## utils.py

Typer CLI used by the DVC pipelines after metrics are calculated (see
[docs/metric.md](../docs/metric.md)):

- `aggregate` – combines the per-fold metric JSON files of one dataset/model/split/target
  into a single `*_aggregated.json` (mean and standard deviation per score mode).
- `generate-csv` – merges all `*_aggregated.json` files into
  [benchmark/metrics.csv](../benchmark/metrics.csv).

```shell
PYTHONPATH=. python -m scripts.utils generate-csv \
  --metric-dir benchmark/zero_shot/metric \
  --output-path benchmark/metrics.csv \
  --game zero_shot
```

## Website tests

`normalize.test.ts` and `stats.test.ts` test the website's statistics code in
`src/lib/`. Run them with Node:

```shell
node scripts/normalize.test.ts
node scripts/stats.test.ts
```
