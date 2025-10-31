# Metric

The metric defines the evaluation result for a model.

## Overview

The ProteinGym benchmark uses the [scripts/metric.py](../scripts/metric.py) script to calculate performance metrics for model predictions. The script computes any custom metrics like Spearman correlation.

## How to add a custom metric

Adding a new custom metric is straightforward and requires the following steps:

### Step 1: Add your metric with a `metric_[your_metric_name]` function

Open [scripts/metric.py](../scripts/metric.py) and take the `metric_spearman()` function as an example.

To add a new metric, define a function following this pattern:
```python
def metric_<your_metric_name>(actual_values: list[float], predicted_values: list[float]) -> float:
    result = custom_calculation(actual_values, predicted_values)
    return result
```

> [!IMPORTANT]
> - Your metric function should accept `actual_values` and `predicted_values` as inputs
> - The metric name (dictionary key) will appear in the metric JSON files
> - Import any required libraries at the top of the file
> - The metric value can be a number, string, or tuple (it will be converted to string in the output)

### Step 2: Add your metric to `default.yaml`

To include your metric in the benchmark output, add it to the metrics list in the DVC configuration file: `default.yaml` for each game under each environment:

```yaml
metrics: 
  - spearman
  - your_metric_name  # Add your new metric here
```

### Step 3: Update `requirements.txt`

If you have extra package installed for your metric, don't forget to add the dependencies in [requirements.txt](../requirements.txt).

### Step 4: Verify your metric

Run the metric calculation script to verify your metric is computed correctly:

```bash
python scripts/metric.py \
  --prediction-path path/to/predictions.csv \
  --metric-path path/to/output/metrics.json \
  --selected-metrics "your_metric_name"
```

### Step 5: Run the benchmark

The new metric will automatically be included in all metric outputs, when you run below command for each game under each environment.

```shell
dvc repro -s
```

You can show your metrics by the following command:

```shell
dvc metrics show
```

## Output Format

Metrics are saved in the JSON formats:

### 1. Metrics JSON (`metric.json`)
```json
{
  "spearman": "0.72",
  "your_metric_name": "0.91"
}
```

> [!TIP]
> You can check out https://dvc.org/doc/command-reference/metrics#supported-file-formats for the format and hierarchies of metrics.
