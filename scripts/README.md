# Scripts

Utility scripts for the ProteinGym benchmark. Install the Python dependencies
from the repository root first:

```shell
pip install -r requirements.txt
```

## generate_datasets_list.py

Builds `benchmark/supervised/datasets.json` and `benchmark/zero_shot/datasets.json`
with one entry per dataset, split and target found in `datasets/splits/*.splits.pgdata`.
Run it from the repository root:

```shell
python scripts/generate_datasets_list.py
```

> [!NOTE]
> It also writes `static/datasets-list.json` and `static/datasets/*.lock`. The
> website no longer reads those files; dataset pages use `static/manifests.json`
> and `static/histograms.json`, which the
> [sync-datasets](../.github/workflows/sync-datasets.yml) workflow pulls from Hugging Face.

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
