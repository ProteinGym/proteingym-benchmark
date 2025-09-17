# ProteinGym2 Benchmark

## Getting started

Before you start, you need to create a `git-auth.txt` file in two folders respectively - [supervised](supervised) and [zero_shot](zero_shot):

```
https://username:token@github.com
```

## Models

The models are included in the [models](models/) folder, where each model occupies a subfolder as its repo.

A model repo contains its README.md as a model card, which comes in two parts:
- Metadata, which is a YAML section at the top, i.e., front matter.
- Text descriptions, which is a Markdown file, including summary and descriptions of the model.

For more information, you can reference Hugging Face's [model cards](https://huggingface.co/docs/hub/en/model-cards).

### Model validation

You can validate if your model will work with Protein Gym benchmark:

```shell
$ uv run pg2-benchmark validate <your_model_root_path>
```

## Datasets

The datasets are included in the [dataset](datasets/) folder, where each dataset goes into a subfolder.

In order to build the archived file for each dataset, [pg2-dataset](https://github.com/ProteinGym2/pg2-dataset) is used.

You can reference [this guide](https://github.com/ProteinGym2/pg2-dataset?tab=readme-ov-file#archive-data) to build the archived dataset.

## Benchmark

The benchmark is defined in the [benchmark](benchmark/) folder, where there exist two games: supervised and zero-shot.

First of all, you need to select the models and datasets for each game as below command before running DVC:

- `-g` or `--game`: You can choose either `supervised` or `zero_shot` for the game.
- `-e` or `--env`: You can choose either `local` or `aws` environment to run the benchmarking.

```
$ uv run pg2-benchmark select models datasets -g supervised -e local
```

It is an interactive tool for you to choose the model and dataset permutations. Once you confirm your choice, `dvc.yaml` and `params.yaml`, which are standard DVC configurations, will be generated in the defined location for different environments, e.g., `benchmark/<game>/<env>` folder.

### Local environment

There are two games to benchmark: supervised and zero-shot. Each game has its selected list of models and datasets defined in [benchmark/models](benchmark/models) and [benchmark/datasets](benchmark/datasets).

- Supervised game is defined in this Jinja template: [dvc.yaml.jinja](benchmark/supervised/local/dvc.yaml.jinja)
- Zero-shot game is defined in this Jinja template: [dvc.yaml.jinja](benchmark/zero_shot/local/dvc.yaml.jinja))

The models and datasets are defined in `vars` at the top, and DVC translates `vars` into a matrix, which is namely a loop defined as the following pseudo-code:

```python
for dataset in datasets:
    for model in models:
        predict()

for dataset in datasets:
    for model in models:
        calculate_metric()
```

#### Supervised

You can benchmark a group of supervised models:
```shell
uv run dvc repro benchmark/supervised/local/dvc.yaml
```

#### Zero-shot

You can benchmark a group of zero-shot models:
```shell
uv run dvc repro benchmark/zero_shot/local/dvc.yaml
```

### AWS environment

There are two environments in which to run benchmark: one is the local environment, the other is the AWS environment.

The difference of the AWS environment is that:
* You need to upload the dataset and model files to S3.
* You need to build and push your Docker image to ECR.
* You need to use SageMaker training job to either train or score a model.

> [!IMPORTANT]
> In order to use the AWS environment, you need to set up your AWS profile with the below steps:
> 1. Execute `aws configure sso`.
> 2. Fill in the required fields, especially: "Default client Region" is "us-east-1".
>   a. SSO session name: `pg2benchmark`.
>   b. SSO start URL: https://d-90674355f1.awsapps.com/start
>   c. SSO region: `us-east-1`.
>   d. SSO registration scopes: Leave empty.
>   e. Login via browser.
> 2. Select the account: `ifflabdev`.
>   a. Default client Region is `us-east-1`.
>   b. CLI default ouptut: Leave empty.
>   c. Profile name: `pg2benchmark`.
> 4. You can find your account ID and profile by executing `cat ~/.aws/config`.
> 5. Finally, you can run `dvc repro` with environment variables in each game: `AWS_ACCOUNT_ID=xxx AWS_PROFILE=yyy dvc repro`

#### Supervised

You can benchmark a group of supervised models:
```shell
AWS_ACCOUNT_ID=xxx AWS_PROFILE=yyy uv run dvc repro benchmark/supervised/aws/dvc.yaml
```

#### Zero-shot

You can benchmark a group of zero-shot models:
```shell
AWS_ACCOUNT_ID=xxx AWS_PROFILE=yyy uv run dvc repro benchmark/zero_shot/aws/dvc.yaml
```

## Generate dummy data

You can generate dummy data by the following command:
```shell
uv run pg2-benchmark dataset generate-dummy-data supervised/data/dummy/charge_ladder.csv --n-rows 5 --sequence-length 100
```



