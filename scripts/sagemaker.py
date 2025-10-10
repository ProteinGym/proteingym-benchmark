"""
SageMaker Training Job Management Script

This script provides utilities for managing AWS SageMaker training jobs.

Commands:
    create  - Create and start a new SageMaker training job
    monitor - Monitor an existing training job until completion

Usage:
    python sagemaker.py create --model-name <name> --region-name <region> [options...]
    python sagemaker.py monitor --region-name <region> --job-name <job> [options...]
    python sagemaker.py --help
"""

import argparse
import sys
import time
from datetime import datetime

import boto3


def create_training_job(
    model_name: str,
    region_name: str,
    sagemaker_role_name: str,
    ecr_repository_uri: str,
    s3_training_data_prefix: str,
    s3_output_prefix: str,
    instance_type: str,
    volume_size: int,
    dataset_prefix: str,
    model_prefix: str,
):
    """
    Create and start a SageMaker training job with specified configuration.

    This function sets up a SageMaker training job with custom ECR image, configures
    input data channels for both training datasets and model cards, and starts the
    training process with the specified compute resources.

    Args:
        model_name: Name of the model, used to generate unique job names
        region_name: AWS region where the training job will be executed
        sagemaker_role_name: IAM role name with SageMaker permissions
        ecr_repository_uri: URI of the ECR repository containing training image
        s3_training_data_prefix: S3 bucket/prefix containing training data
        s3_output_prefix: S3 bucket/prefix where training outputs will be stored
        instance_type: EC2 instance type for training (e.g., 'ml.p3.2xlarge')
        volume_size: EBS volume size in GB for the training instance
        dataset_prefix: Prefix path for dataset files within the S3 bucket
        model_prefix: Prefix path for model card files within the S3 bucket

    Returns:
        None: Function starts the training job but doesn't wait for completion
    """

    sagemaker = boto3.client("sagemaker", region_name=region_name)

    training_job_name = f"{model_name}-{datetime.now().strftime('%Y%m%d-%H%M%S')}"

    iam = boto3.client("iam")

    response = iam.get_role(RoleName=sagemaker_role_name)

    training_job_params = {
        "TrainingJobName": training_job_name,
        "ResourceConfig": {
            "InstanceCount": 1,
            "InstanceType": instance_type,
            "VolumeSizeInGB": volume_size,
        },
        "AlgorithmSpecification": {
            "TrainingImage": ecr_repository_uri,
            "TrainingInputMode": "File",
        },
        "OutputDataConfig": {"S3OutputPath": f"s3://{s3_output_prefix}"},
        "StoppingCondition": {
            "MaxRuntimeInSeconds": 86400  # 24 hours
        },
        "RoleArn": response["Role"]["Arn"],
        "InputDataConfig": [
            {
                "ChannelName": "training",
                "DataSource": {
                    "S3DataSource": {
                        "S3DataType": "S3Prefix",
                        "S3Uri": f"s3://{s3_training_data_prefix}/datasets/{dataset_prefix}",
                        "S3DataDistributionType": "FullyReplicated",
                    }
                },
            },
            {
                "ChannelName": "model_card",
                "DataSource": {
                    "S3DataSource": {
                        "S3DataType": "S3Prefix",
                        "S3Uri": f"s3://{s3_training_data_prefix}/models/{model_prefix}",
                        "S3DataDistributionType": "FullyReplicated",
                    }
                },
            },
        ],
    }

    response = sagemaker.create_training_job(**training_job_params)

    print(training_job_name)


def monitor_training_job(
    region_name: str,
    job_name: str,
    poll_interval: int,
    timeout: int,
):
    """
    Monitor a SageMaker training job until completion or timeout.

    This function continuously polls the SageMaker API to check the status of a training
    job and returns detailed information about the job's final state. It handles all
    possible job statuses including success, failure, stopping, and timeout scenarios.

    Args:
        region_name: AWS region where the training job is running
        job_name: Name of the SageMaker training job to monitor
        poll_interval Time in seconds between status checks
        timeout: Maximum time in seconds to wait before timing out

    Returns:
        dict: Job completion information with the following structure:
            - For completed jobs: {
                'status': 'Completed',
                'job_name': str,
                'model_artifacts': dict,
                'training_time': int,
                'billable_time': int
              }
            - For failed jobs: {
                'status': 'Failed',
                'job_name': str,
                'failure_reason': str
              }
            - For stopped jobs: {
                'status': 'Stopped',
                'job_name': str
              }
            - For timed out jobs: {
                'status': 'Timeout',
                'job_name': str
              }
    """

    sagemaker = boto3.client("sagemaker", region_name=region_name)
    start_time = time.time()

    print(f"Monitoring SageMaker training job: {job_name}")
    print(f"Poll interval: {poll_interval}s, Timeout: {timeout}s")

    while True:
        response = sagemaker.describe_training_job(TrainingJobName=job_name)
        status = response["TrainingJobStatus"]

        elapsed = int(time.time() - start_time)
        print(f"[{elapsed}s] Job status: {status}")

        if status == "Completed":
            print("✅ Training job completed successfully!")
            return {
                "status": "Completed",
                "job_name": job_name,
                "model_artifacts": response.get("ModelArtifacts", {}),
                "training_time": response.get("TrainingTimeInSeconds", 0),
                "billable_time": response.get("BillableTimeInSeconds", 0),
            }

        elif status == "Failed":
            failure_reason = response.get("FailureReason", "Unknown")
            print(f"❌ Training job failed: {failure_reason}", file=sys.stderr)

            return {
                "status": "Failed",
                "job_name": job_name,
                "failure_reason": failure_reason,
            }

        elif status == "Stopped":
            print("Training job was stopped")
            return {"status": "Stopped", "job_name": job_name}

        elif status in ["InProgress", "Stopping"]:
            if time.time() - start_time > timeout:
                print(f"Timeout reached ({timeout}s)")
                return {"status": "Timeout", "job_name": job_name}

            time.sleep(poll_interval)

        else:
            print(f"Unexpected status: {status}", file=sys.stderr)
            time.sleep(poll_interval)


def main():
    parser = argparse.ArgumentParser(description="SageMaker operations")
    subparsers = parser.add_subparsers(dest="command", help="Available commands")

    create_parser = subparsers.add_parser("create", help="Create a training job")
    create_parser.add_argument("--model-name", required=True, help="Model name")
    create_parser.add_argument("--region-name", required=True, help="AWS region")
    create_parser.add_argument(
        "--sagemaker-role-name", required=True, help="SageMaker role name"
    )
    create_parser.add_argument(
        "--ecr-repository-uri", required=True, help="ECR repository URI"
    )
    create_parser.add_argument(
        "--s3-training-data-prefix", required=True, help="S3 training data prefix"
    )
    create_parser.add_argument(
        "--s3-output-prefix", required=True, help="S3 output prefix"
    )
    create_parser.add_argument("--instance-type", required=True, help="Instance type")
    create_parser.add_argument(
        "--volume-size", type=int, required=True, help="Volume size in GB"
    )
    create_parser.add_argument("--dataset-prefix", required=True, help="Dataset prefix")
    create_parser.add_argument("--model-prefix", required=True, help="Model prefix")

    monitor_parser = subparsers.add_parser("monitor", help="Monitor a training job")
    monitor_parser.add_argument("--region-name", required=True, help="AWS region")
    monitor_parser.add_argument("--job-name", required=True, help="Training job name")
    monitor_parser.add_argument(
        "--poll-interval", type=int, default=30, help="Poll interval in seconds"
    )
    monitor_parser.add_argument(
        "--timeout", type=int, default=86400, help="Timeout in seconds"
    )

    args = parser.parse_args()

    if args.command == "create":
        create_training_job(
            model_name=args.model_name,
            region_name=args.region_name,
            sagemaker_role_name=args.sagemaker_role_name,
            ecr_repository_uri=args.ecr_repository_uri,
            s3_training_data_prefix=args.s3_training_data_prefix,
            s3_output_prefix=args.s3_output_prefix,
            instance_type=args.instance_type,
            volume_size=args.volume_size,
            dataset_prefix=args.dataset_prefix,
            model_prefix=args.model_prefix,
        )
        print("Training job created successfully")

    elif args.command == "monitor":
        result = monitor_training_job(
            region_name=args.region_name,
            job_name=args.job_name,
            poll_interval=args.poll_interval,
            timeout=args.timeout,
        )
        print(f"Training job result: {result}")

    else:
        parser.print_help()


if __name__ == "__main__":
    main()
