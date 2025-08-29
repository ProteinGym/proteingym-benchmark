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

## Datasets

The datasets are included in the [dataset](datasets/) folder.

## Benchmark

The benchmark is defined in the [benchmark](benchmark/) folder, where each dataset occupies a subfolder.

In order to build the archived file for each dataset, [pg2-dataset](https://github.com/ProteinGym2/pg2-dataset) is used.

You can reference [this guide](https://github.com/ProteinGym2/pg2-dataset?tab=readme-ov-file#archive-data) to build the archived dataset.

### Local environment

There are two games to benchmark: supervised and zero-shot. Each game has its selected list of models and datasets defined in `dvc.yaml`.

- Supervised game is defined in this [dvc.yaml](supervised/local/dvc.yaml)
- Zero-shot game is defined in this [dvc.yaml](zero_shot/local/dvc.yaml)

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
dvc repro benchmark/supervised/local/dvc.yaml
```

#### Zero-shot

You can benchmark a group of zero-shot models:
```shell
dvc repro benchmark/zero_shot/local/dvc.yaml
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
AWS_ACCOUNT_ID=xxx AWS_PROFILE=yyy dvc repro benchmark/supervised/aws/dvc.yaml
```

#### Zero-shot

You can benchmark a group of zero-shot models:
```shell
AWS_ACCOUNT_ID=xxx AWS_PROFILE=yyy dvc repro benchmark/zero_shot/aws/dvc.yaml
```

## Generate dummy data

You can generate dummy data by the following command:
```shell
uv run pg2-benchmark dataset generate-dummy-data supervised/data/dummy/charge_ladder.csv --n-rows 5 --sequence-length 100
```
