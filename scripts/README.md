# Scripts

This directory contains utility scripts for the ProteinGym benchmark.

## Dependencies

Make sure to install the required dependencies:

```shell
pip install -r requirements.txt
```

## metric.py

The [metric.py](metric.py) script calculates performance metrics for machine learning models by comparing actual and predicted values. It computes both classification metrics (via confusion matrix) and correlation metrics (Spearman correlation) from CSV output files.

### Arguments

- `--output`: Path to the CSV file containing prediction results
- `--metric`: Path where the calculated metrics CSV will be saved
- `--actual-vector-col`: Column name containing actual/ground truth values
- `--predict-vector-col`: Column name containing predicted values

### Example

```shell
python metric.py \
    --output predictions.csv \
    --metric metrics.csv \
    --actual-vector-col "true_values" \
    --predict-vector-col "predicted_values"
```

## sagemaker.py

The [sagemaker.py](sagemaker.py) script provides utilities for managing AWS SageMaker training jobs. It supports two main commands: `create` for starting new training jobs and `monitor` for tracking existing jobs.

### Arguments

#### Create Command
- `--model-name`: Name of the model (used to generate unique job names)
- `--region-name`: AWS region where the training job will be executed
- `--sagemaker-role-name`: IAM role name with SageMaker permissions
- `--ecr-repository-uri`: URI of the ECR repository containing training image
- `--s3-training-data-prefix`: S3 bucket/prefix containing training data
- `--s3-output-prefix`: S3 bucket/prefix where training outputs will be stored
- `--instance-type`: EC2 instance type for training (e.g., 'ml.p3.2xlarge')
- `--volume-size`: EBS volume size in GB for the training instance
- `--dataset-prefix`: Prefix path for dataset files within the S3 bucket
- `--model-prefix`: Prefix path for model card files within the S3 bucket

#### Monitor Command
- `--region-name`: AWS region where the training job is running
- `--job-name`: Name of the SageMaker training job to monitor
- `--poll-interval`: Time in seconds between status checks (default: 30)
- `--timeout`: Maximum time in seconds to wait before timing out (default: 86400)

### Examples

#### Create a training job
```shell
python sagemaker.py create \
    --model-name "model-v1" \
    --region-name "us-west-2" \
    --sagemaker-role-name "SageMakerExecutionRole" \
    --ecr-repository-uri "xxx.dkr.ecr.us-west-2.amazonaws.com/my-training-image:latest" \
    --s3-training-data-prefix "my-bucket/training-data" \
    --s3-output-prefix "my-bucket/model-outputs" \
    --instance-type "ml.p3.2xlarge" \
    --volume-size 100 \
    --dataset-prefix "proteingym/datasets" \
    --model-prefix "proteingym/models"
```

#### Monitor a training job
```shell
python sagemaker.py monitor \
    --region-name "us-west-2" \
    --job-name "model-v1-20231010-143000" \
    --poll-interval 60 \
    --timeout 7200
```
