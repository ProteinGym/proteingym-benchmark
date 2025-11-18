# Scripts

This directory contains utility scripts for the ProteinGym benchmark.

## Dependencies

Make sure to install the required dependencies:

```shell
pip install -r requirements.txt
```

## metric.py

The [metric.py](metric.py) script calculates performance metrics for machine learning models by comparing actual and predicted values.

### Arguments

- `--prediction-path`: Path to the CSV file containing prediction results
- `--metric-path`: Path where the calculated metrics CSV will be saved
- `--selected-metrics`:The list of metrics to calculate in order to evaluate a model

### Example

```shell
python metric.py \\
    --prediction-path predictions.csv \\
    --metric-path metrics.json \\
    --selected-metrics spearman
```
