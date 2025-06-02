# ProteinGym2 Benchmark

## Getting started

1. Run the benchmarking script:
```
uv run pg2-benchmark supervise \
    --dataset-toml-file /data/dataset.toml \
    --model-toml-file /data/model.toml \
    --git-repo "https://github.com/ProteinGym2/pg2-model-pls.git" \
    --git-branch "main"
```

2. Display the metrics:
```
uv run streamlit run app.py
```

## Todo

- [ ] add docker health check for the container
