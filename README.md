# ProteinGym2 Benchmark

## Getting started

Before you start, you need to create a `git-auth.txt` file in the root path with the following content:

```
https://username:token@github.com
```

## Benchmark

You can benchmark for a group of supervised models:
```shell
uv run dvc repro supervised_predict supervised_metric
```

You can benchmark for a group of zero-shot models:
```shell
uv run dvc repro zero_shot_predict zero_shot_metric
```

> [!NOTE]
> You can add your data and model for each group, either supervised or zero-shot, in [params.yaml](params.yaml)

## Generate dummy data

You can generate dummy data by the following command:
```shell
uv run pg2-benchmark dataset generate-dummy-data data/supervised/data/ --n-rows 5 --sequence-length 100
```