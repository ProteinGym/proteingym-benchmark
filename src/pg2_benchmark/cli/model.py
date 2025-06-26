import typer
import os
import shutil
from typing import Any
import tempfile
from pathlib import Path
from pg2_benchmark.repo import clone
from pg2_benchmark.meta import ModelManifest
from python_on_whales import docker

model_app = typer.Typer()


@model_app.command()
def predict(
    dataset_manifest: str = typer.Option(help="Path to the dataset TOML file"),
    model_manifest: str = typer.Option(help="Path to the model TOML file"),
) -> tuple[list[Any], list[Any]]:
        
    # TODO: To be removed after pg2-dataset is public and 'git-auth.txt' is not required.
    curr_dir = os.getcwd()

    manifest = ModelManifest.from_path(model_manifest)
    
    with tempfile.TemporaryDirectory() as temp_dir:

        typer.echo(f"Git clone the repo: {manifest.repo_url}")
        clone(repo_url=manifest.repo_url, target_dir=temp_dir, branch_name=manifest.branch_name)

        shutil.copy(f"{curr_dir}/git-auth.txt", f"{temp_dir}/git-auth.txt")

        typer.echo("Building the Docker image...")
        docker.build(
            context_path=temp_dir,
            tags=["test-model:latest"],
            secrets="id=git_auth,src=git-auth.txt",
            load=True,
        )

        typer.echo(f"{docker.image.list()}")

        typer.echo("Running the Docker container...")
        docker.run(
            image="test-model:latest",
            volumes=[
                (f"{Path(dataset_manifest).parent.resolve()}", "/data"),
                (f"{Path(model_manifest).parent.resolve()}", "/model"),
                (f"{Path(model_manifest).parent.parent.resolve()}/output", "/output"),
            ], 
            remove=True,
            command=[
                "predict",
                "--dataset-toml-file", f"/data/{str(Path(dataset_manifest).name)}",
                "--model-toml-file", f"/model/{str(Path(model_manifest).name)}",
            ],
        )

        docker.image.remove("test-model:latest", force=False, prune=True)
