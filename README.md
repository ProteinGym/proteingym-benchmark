# ProteinGym2 Benchmark

## Getting started

Before you start, you need to create a `git-auth.txt` file in two folders respectively - [supervised](supervised) and [zero_shot](zero_shot):

```
https://username:token@github.com
```

## Benchmark

There are two games to benchmark: supervised and zero-shot. Each game has its selected list of models and datasets defined in `dvc.yaml`.

- Supervised game is defined in this [dvc.yaml](supervised/dvc.yaml)
- Zero-shot game is defined in this [dvc.yaml](zero_shot/dvc.yaml)

The models and datasets are defined in `vars` at the top, and DVC translates `vars` into a matrix, which is namely a loop defined as the below pseudo-code:

```python
for dataset in datasets:
    for model in models:
        predict()

for dataset in datasets:
    for model in models:
        calculate_metric()
```

### Supervised

You can benchmark for a group of supervised models:
```shell
cd supervised && dvc repo
```

### Zero-shot

You can benchmark for a group of zero-shot models:
```shell
cd zero_shot && dvc repo
```

## Generate dummy data

You can generate dummy data by the following command:
```shell
uv run pg2-benchmark dataset generate-dummy-data supervised/data/dummy/charge_ladder.csv --n-rows 5 --sequence-length 100
```