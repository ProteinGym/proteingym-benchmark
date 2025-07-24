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

The models and datasets are defined in `vars` at the top, and DVC translates `vars` into a matrix, which is namely a loop defined as the following pseudo-code:

```python
for dataset in datasets:
    for model in models:
        predict()

for dataset in datasets:
    for model in models:
        calculate_metric()
```

### Supervised

You can benchmark a group of supervised models:
```shell
cd supervised && dvc repo
```

### Zero-shot

You can benchmark a group of zero-shot models:
```shell
cd zero_shot && dvc repo
```

## AWS

There are two environments in which to run benchmark: one is the local environment, the other is the AWS environment.

The difference of the AWS environment is that:
* You need to upload the data and model TOML files and the actual data to S3.
* You need to build and push your Docker image to ECR.
* You need to use SageMaker training job to either train or score a model.

> [!IMPORTANT]
> In order to use the AWS environment, you need to set up your AWS profile with the below steps:
> 1. Execute `aws configure sso`.
> 2. Fill in the required fields, especially: "Default client Region" is "us-east-1".
> 3. You can find your account ID and profile by executing `cat ~/.aws/config`.
> 4. Finally, you can run `dvc repro` with environment variables in each game: `AWS_ACCOUNT_ID=xxx AWS_PROFILE=yyy dvc repro`

## Generate dummy data

You can generate dummy data by the following command:
```shell
uv run pg2-benchmark dataset generate-dummy-data supervised/data/dummy/charge_ladder.csv --n-rows 5 --sequence-length 100
```
