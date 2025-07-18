import typer
from typing import Annotated
from rich.console import Console
import boto3
from datetime import datetime
import time

aws_app = typer.Typer()

console = Console()
err_console = Console(stderr=True)


@aws_app.command()
def create_training_job(
    model_name: Annotated[str, typer.Option(help="Unique model name")],
    region_name: Annotated[str, typer.Option(help="AWS region name")],
    role_name: Annotated[str, typer.Option(help="AWS SageMaker role name")],
    ecr_repository_uri: Annotated[str, typer.Option(help="AWS ECR repository URI")],
    s3_training_data_bucket: Annotated[
        str, typer.Option(help="S3 training data bucket")
    ],
    s3_output_bucket: Annotated[str, typer.Option(help="S3 output bucket")],
    instance_type: Annotated[str, typer.Option(help="EC2 instance type")],
    volume_size: Annotated[int, typer.Option(help="EC2 volume size in Gigabytes")],
    dataset_toml_file: Annotated[
        str, typer.Option(help="Dataset TOML file as hyperparamters")
    ],
    model_toml_file: Annotated[
        str, typer.Option(help="Model TOML file as hyperparamters")
    ],
):
    """Create and start SageMaker training job"""

    # Initialize SageMaker client
    sagemaker = boto3.client("sagemaker", region_name=region_name)

    training_job_name = f"{model_name}-{datetime.now().strftime('%Y%m%d-%H%M%S')}"

    # Get SageMaker role
    iam = boto3.client("iam")

    response = iam.get_role(RoleName=role_name)

    # Prepare training job parameters
    training_job_params = {
        "TrainingJobName": training_job_name,
        "ResourceConfig": {
            "InstanceCount": 1,
            "InstanceType": instance_type,
            "VolumeSizeInGB": volume_size,
        },
        "HyperParameters": {
            "dataset_toml_file": dataset_toml_file,
            "model_toml_file": model_toml_file,
        },
        "AlgorithmSpecification": {
            "TrainingImage": ecr_repository_uri,
            "TrainingInputMode": "File",
        },
        "OutputDataConfig": {"S3OutputPath": f"s3://{s3_output_bucket}"},
        "StoppingCondition": {
            "MaxRuntimeInSeconds": 86400  # 24 hours
        },
        "RoleArn": response["Role"]["Arn"],
        "InputDataConfig": [
            {
                "ChannelName": "training",
                "ContentType": "text/csv",
                "DataSource": {
                    "S3DataSource": {
                        "S3DataType": "S3Prefix",
                        "S3Uri": f"s3://{s3_training_data_bucket}",
                        "S3DataDistributionType": "FullyReplicated",
                    }
                },
            }
        ],
    }

    # Create training job
    response = sagemaker.create_training_job(**training_job_params)

    console.print(training_job_name)


@aws_app.command()
def monitor_training_job(
    region_name: Annotated[str, typer.Option(help="AWS region name")],
    job_name: Annotated[str, typer.Option(help="AWS SageMaker training job name")],
    poll_interval: Annotated[
        int, typer.Option(default=30, help="Poll interval in seconds")
    ],
    timeout: Annotated[int, typer.Option(default=3600, help="Timeout in seconds")],
):
    """Monitor SageMaker training job until completion"""

    sagemaker = boto3.client("sagemaker", region_name=region_name)
    start_time = time.time()

    console.print(f"Monitoring SageMaker training job: {job_name}")
    console.print(f"Poll interval: {poll_interval}s, Timeout: {timeout}s")

    while True:
        response = sagemaker.describe_training_job(TrainingJobName=job_name)
        status = response["TrainingJobStatus"]

        # Log current status
        elapsed = int(time.time() - start_time)
        console.print(f"[{elapsed}s] Job status: {status}")

        if status == "Completed":
            console.print("✅ Training job completed successfully!")
            return {
                "status": "Completed",
                "job_name": job_name,
                "model_artifacts": response.get("ModelArtifacts", {}),
                "training_time": response.get("TrainingTimeInSeconds", 0),
                "billable_time": response.get("BillableTimeInSeconds", 0),
            }

        elif status == "Failed":
            failure_reason = response.get("FailureReason", "Unknown")
            err_console.print(f"❌ Training job failed: {failure_reason}")
            return {
                "status": "Failed",
                "job_name": job_name,
                "failure_reason": failure_reason,
            }

        elif status == "Stopped":
            console.print("Training job was stopped")
            return {"status": "Stopped", "job_name": job_name}

        elif status in ["InProgress", "Stopping"]:
            # Check timeout
            if time.time() - start_time > timeout:
                console.print(f"Timeout reached ({timeout}s)")
                return {"status": "Timeout", "job_name": job_name}

            # Wait before next check
            time.sleep(poll_interval)

        else:
            err_console.print(f"Unexpected status: {status}")
            time.sleep(poll_interval)
