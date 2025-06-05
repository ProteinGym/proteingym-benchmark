# ProteinGym2 Benchmark

## Benchmarking

Before you start, you need to create a `git-auth.txt` file in the root path with the following content:

```
https://username:token@github.com
```

You can run a benchmarking script for a supervised model:
```
uv run pg2-benchmark predict \
    --toml-folder data/supervise \
    --dataset-toml-file /data/dataset.toml \
    --model-toml-file /data/model.toml \
    --git-repo "https://github.com/ProteinGym2/pg2-model-pls.git" \
    --git-branch "main"
```

You can also run a benchmarking script for a zero-shot model:
```
uv run pg2-benchmark predict \
    --toml-folder data/zero_shot \
    --dataset-toml-file /data/dataset.toml \
    --model-toml-file /data/model.toml \
    --git-repo "https://github.com/ProteinGym2/pg2-model-esm.git" \
    --git-branch "main"
```

## Metrics

Display the metrics:
```
uv run streamlit run app.py
```

## Todo

- [ ] add docker health check for the container
