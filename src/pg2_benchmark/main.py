import typer
import os
import shutil
from typing import Any
import tempfile
from pg2_benchmark.utils.git_utils import git_clone
from python_on_whales import docker


app = typer.Typer(
    help="ProteinGym2 - Benchmark CLI",
    add_completion=True,
)

@app.command()
def supervise(
    dataset_toml_file: str = typer.Option(help="Path to the dataset TOML file"),
    model_toml_file: str = typer.Option(help="Path to the model TOML file"),
    git_repo: str = typer.Option(help="Git repository URI"),
    git_branch: str = typer.Option(help="Git branch name"),
) -> tuple[list[Any], list[Any]]:
    
    curr_dir = os.getcwd()
    
    with tempfile.TemporaryDirectory() as temp_dir:
        
        git_clone(repo_url=git_repo, target_dir=temp_dir, branch_name=git_branch)
        os.chdir(temp_dir)

        shutil.copy(f"{curr_dir}/git-auth.txt", "git-auth.txt")

        typer.echo("Building the Docker image...")
        docker.build(
            context_path="./",
            tags=["test-model:latest"],
            secrets="id=git_auth,src=git-auth.txt",
            load=True,
        )

        typer.echo(f"{docker.image.list()}")

        typer.echo("Running the Docker container...")
        docker.run(
            image="test-model:latest",
            volumes=[(f"{curr_dir}/data", "/data")], 
            remove=True,
            command=[
                "supervise",
                "--dataset-toml-file", dataset_toml_file,
                "--model-toml-file", model_toml_file,
            ],
        )

        docker.image.remove("test-model:latest", force=False, prune=True)


@app.command()
def ping():
    typer.echo("pong")


if __name__ == "__main__":
    app()