# Metric

The metric defines the evaluation result for a model.

## Overview

Metrics are implemented in [proteingym-base](https://github.com/ProteinGym/proteingym-base),
in `src/proteingym/base/metrics.py`. The DVC pipelines call them through the
`proteingym-base` CLI:

- `proteingym-base evaluate-subset` for the supervised benchmark (one call per fold),
- `proteingym-base evaluate-zero-shot` for the zero-shot benchmark.

Which metrics are computed is set by `metrics` in each benchmark's `default.yaml`
([supervised](../benchmark/supervised/default.yaml), [zero-shot](../benchmark/zero_shot/default.yaml)).
The supervised benchmark also sets `score_modes` (`test`, `train_available`, `per_fold`).

## How to add a metric

### Step 1: Add a `metric_<name>` function to proteingym-base

Any function in `proteingym/base/metrics.py` whose name starts with `metric_` is
discovered automatically; the part after `metric_` is the metric name. It takes a
`DatasetScoringContext`, whose `scoring_df` holds the ground truth (`<target>`)
and predictions (`<target>_pred`) aligned row by row. Use `metric_spearman` as the
example:

```python
def metric_<name>(context: DatasetScoringContext) -> float:
    df = context.scoring_df
    return custom_calculation(
        df[context.target].to_numpy(),
        df[f"{context.target}_pred"].to_numpy(),
    )
```

Open the PR against proteingym-base. This repository installs it from its `main`
branch (see [requirements.txt](../requirements.txt)).

### Step 2: Add the metric to `default.yaml`

Add the name to `metrics` in the `default.yaml` of each benchmark that should
report it:

```yaml
metrics: ["spearman", "<name>"]
```

### Step 3: Run the benchmark

```shell
dvc repro benchmark/zero_shot/dvc.yaml --single-item
dvc metrics show
```

## Output

Each `calculate_metric` stage writes a JSON file under
`benchmark/<game>/metric/<dataset>/<model>/<target>/<split>[/fold<k>].json`.
`aggregate_metrics` then combines the folds into
`<dataset>_<model>_<split>_<target>_aggregated.json` (mean and `_std` per metric
and score mode), and `generate_metrics_csv` merges those into
[benchmark/metrics.csv](../benchmark/metrics.csv), which the website reads.
