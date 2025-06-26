# ProteinGym2 Benchmark

## Getting started

Before you start, you need to create a `git-auth.txt` file in the root path with the following content:

```
https://username:token@github.com
```

## Benchmark

You can benchmark for a group of supervised models:
```
uv run dvc repro supervise_predict supervise_metric
```

You can benchmark for a group of zero-shot models:
```
uv run dvc repro supervise_predict supervise_metric
```

> [!NOTE]
> You can add your data and model for each group, either supervised or zero-shot in [params.yaml](params.yaml)