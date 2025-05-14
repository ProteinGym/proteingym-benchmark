# ProteinGym2 Benchmark

<img width="972" alt="image" src="https://github.com/user-attachments/assets/68328a2b-82c9-44b8-ab1b-ff9d97bc8bbc" />

## Getting started

1. Build the image:

```
git clone -b test/pg2-benchmark https://github.com/ProteinGym2/pg2-model-pls.git

cd pg2-model-pls
docker build -t pls-model .
```

2. Run the benchmark:

```
uv run pg2-benchmark train model --toml-file config/pls.toml
```
