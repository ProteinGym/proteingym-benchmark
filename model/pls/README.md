# pg2-model-pls

This is the model built from the cookie-cutter template: https://github.com/ProteinGym2/pg2-model

You can create your own model repo by: 
```
uvx cookiecutter https://github.com/ProteinGym2/pg2-model.git
```

> [!TIP]
> You can install `uv` by this guide: https://docs.astral.sh/uv/getting-started/installation/

## Getting started

1. Create a `git-auth.txt` file in the root path with the following content:

```
https://username:token@github.com
```

2. After you've created your project, you can run the following commands to build and score your model:

Build a model:
```shell
docker build \
--secret id=git_auth,src=git-auth.txt \
-t test-model .
```

Score a model:
```shell
docker run --rm -v $(pwd)/data:/data test-model \
    predict \
    --dataset-toml-file /data/dataset.toml \
    --model-toml-file /data/model.toml
```

> [!TIP]
> You can check the commands with help: `docker run --rm pls-model --help`