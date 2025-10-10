# Scripts

This directory contains utility scripts for the ProteinGym benchmark.

## Dependencies

Make sure to install the required dependencies:

```shell
pip install -r requirements.txt
```

## metric.py

The [metric.py](metric.py) script calculates performance metrics for machine learning models by comparing actual and predicted values. It computes both classification metrics (via confusion matrix) and correlation metrics (Spearman correlation) from CSV output files.

### Arguments

- `--output`: Path to the CSV file containing prediction results
- `--metric`: Path where the calculated metrics CSV will be saved
- `--actual-vector-col`: Column name containing actual/ground truth values
- `--predict-vector-col`: Column name containing predicted values

### Example

```shell
python metric.py \
    --output predictions.csv \
    --metric metrics.csv \
    --actual-vector-col "true_values" \
    --predict-vector-col "predicted_values"
```