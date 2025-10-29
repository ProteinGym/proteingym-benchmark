# Metric

The metric defines the evaluation result for a model.

## Overview

The ProteinGym benchmark uses the [scripts/metric.py](../scripts/metric.py) script to calculate performance metrics for model predictions. The script computes both standard classification metrics (via confusion matrix) and custom metrics like Spearman correlation.

## How to add a custom metric

Adding a new custom metric is straightforward and requires two steps:

### Step 1: Add your metric to `calculate_all_metrics()` function

Open [scripts/metric.py](../scripts/metric.py) and locate the `calculate_all_metrics()` function. Add your custom metric calculation in the designated section:

```python
def calculate_all_metrics(
    actual_values: list,
    predicted_values: list,
) -> dict:
    """Calculate all metrics including confusion matrix metrics and custom metrics.

    This function computes both standard classification metrics via confusion matrix
    and custom metrics like Spearman correlation. Add new custom metrics here.
    """
    # Calculate confusion matrix metrics
    cm = ConfusionMatrix(
        actual_vector=actual_values,
        predict_vector=predicted_values,
    )

    all_metrics = dict(cm.overall_stat.items())

    # Calculate custom metrics
    # Add your custom metrics below this line

    # Add your new metric here:
    your_metric_value = your_calculation(actual_values, predicted_values)
    all_metrics["Your Metric Name"] = your_metric_value

    return all_metrics
```

> [!IMPORTANT]
> - Your metric function should accept `actual_values` and `predicted_values` as inputs
> - The metric name (dictionary key) will appear in the metric and plot JSON files
> - Import any required libraries at the top of the file
> - The metric value can be a number, string, or tuple (it will be converted to string in the output)

### Step 2: Add your metric to `default.yaml`

To include your metric in the benchmark output, add it to the metrics list in the DVC configuration file: `default.yaml` for each game under each environment:

```yaml
metrics: '"Overall RACCU" "Average Spearman" "Your Metric Name"'  # Add your new metric here
```

### Step 3: Update `requirements.txt`

If you have extra package installed for your metric, don't forget to add the dependencies in [requirements.txt](../requirements.txt).

### Step 4: Verify your metric

Run the metric calculation script to verify your metric is computed correctly:

```bash
python scripts/metric.py \
  --output path/to/predictions.csv \
  --metric path/to/output/metrics.json \
  --plot path/to/output/plot.json \
  --actual-vector-col "test" \
  --predict-vector-col "pred" \
  --selected-metrics "Your Metric Name"
```

### Step 5: Run the benchmark

The new metric will automatically be included in all metric outputs and visualizations, when you run below command for each game under each environment.

```shell
dvc repro -s
```

You can show your metrics by the following command:

```shell
dvc metrics show
```

You can also plot your metrics by:

```shell
dvc plots show
```

## Output Format

Metrics are saved in two formats:

### 1. Metrics JSON (`metric.json`)
```json
{
  "Overall ACC": "0.85",
  "Average Spearman": "0.72",
  "Your Metric Name": "0.91"
}
```

> [!TIP]
> You can check out https://dvc.org/doc/command-reference/metrics#supported-file-formats for the format and hierarchies of metrics.

### 2. Plot JSON (`plot.json`)
```json
[
  {"metric": "Overall ACC", "value": "0.85"},
  {"metric": "Average Spearman", "value": "0.72"},
  {"metric": "Your Metric Name", "value": "0.91"}
]
```

> [!TIP]
> You can check out https://dvc.org/doc/command-reference/plots/show#example-hierarchical-data for the format and hierarchies of plots.

## Available confusion matrix metrics

The script automatically includes all metrics from [PyCM](https://github.com/sepandhaghighi/pycm) (Python Confusion Matrix library). Some commonly used metrics include:

- Overall ACC (Accuracy)
- Overall RACCU (Random Accuracy Unbiased)
- Kappa (Cohen's Kappa)
- F1 Macro
- PPV Macro (Precision)
- TPR Macro (Recall/Sensitivity)

See the [PyCM documentation](https://www.pycm.io/doc/) for a complete list of available metrics.
