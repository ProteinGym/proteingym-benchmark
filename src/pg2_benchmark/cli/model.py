import typer
from rich.console import Console
from typing import Any
from pathlib import Path
from python_on_whales import docker

model_app = typer.Typer()

console = Console()


@model_app.command()
def predict(
    type: str = typer.Option(help="Type of benchmarking game"),
    dataset_manifest: str = typer.Option(help="Path to the dataset TOML file"),
    model_manifest: str = typer.Option(help="Path to the model TOML file"),
    model_dockerfile_folder: str = typer.Option(
        help="Path to the model Dockerfile folder"
    ),
    model_name: str = typer.Option(help="Model name"),
) -> tuple[list[Any], list[Any]]:
    # TODO: To be removed after pg2-dataset is public and 'git-auth.txt' is not required.

    console.print("Building the Docker image...")
    docker.build(
        context_path=model_dockerfile_folder,
        tags=[f"{model_name}:latest"],
        secrets="id=git_auth,src=git-auth.txt",
        load=True,
        cache=False,
    )

    console.print(f"{docker.image.list()}")

    console.print("Running the Docker container...")
    docker.run(
        image=f"{model_name}:latest",
        volumes=[
            (f"{Path(dataset_manifest).parent.parent.resolve()}", "/data"),
            (f"{Path(model_manifest).parent.resolve()}", "/model"),
            (
                f"{Path(model_manifest).parent.parent.parent.resolve()}/{type}/output",
                "/output",
            ),
        ],
        remove=True,
        command=[
            "predict",
            "--dataset-toml-file",
            f"/data/{str(Path(dataset_manifest).relative_to(Path(dataset_manifest).parent.parent))}",
            "--model-toml-file",
            f"/model/{str(Path(model_manifest).name)}",
        ],
    )

    docker.image.remove(f"{model_name}:latest", force=False, prune=True)
    console.print("Removed the Docker image.")
