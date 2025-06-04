import typer
import os
import shutil
from typing import Any
import tempfile
from pg2_benchmark.utils.git_utils import git_clone
from pg2_benchmark.io.data import load_data
from python_on_whales import docker
from pycm import ConfusionMatrix
import pandas as pd


app = typer.Typer(
    help="ProteinGym2 - Benchmark CLI",
    add_completion=True,
)

@app.command()
def predict(
    toml_folder: str = typer.Option(help="Path to the folder containing TOML files"),
    dataset_toml_file: str = typer.Option(help="Path to the dataset TOML file"),
    model_toml_file: str = typer.Option(help="Path to the model TOML file"),
    git_repo: str = typer.Option(help="Git repository URI"),
    git_branch: str = typer.Option(help="Git branch name"),
) -> tuple[list[Any], list[Any]]:
    
    curr_dir = os.getcwd()
    
    with tempfile.TemporaryDirectory() as temp_dir:

        typer.echo(f"Git clone the repo: {git_repo}")
        git_clone(repo_url=git_repo, target_dir=temp_dir, branch_name=git_branch)
        os.chdir(temp_dir)

        shutil.copy(f"{curr_dir}/git-auth.txt", "git-auth.txt")

        typer.echo("Building the Docker image...")
        docker.build(
            context_path=".",
            tags=["test-model:latest"],
            secrets="id=git_auth,src=git-auth.txt",
            load=True,
        )

        typer.echo(f"{docker.image.list()}")

        typer.echo("Running the Docker container...")
        docker.run(
            image="test-model:latest",
            volumes=[(f"{curr_dir}/{toml_folder}", "/data")], 
            remove=True,
            command=[
                "predict",
                "--dataset-toml-file", dataset_toml_file,
                "--model-toml-file", model_toml_file,
            ],
        )

        docker.image.remove("test-model:latest", force=False, prune=True)

        typer.echo("Calculationg metrics...")
        df = pd.read_csv(f"{curr_dir}/{toml_folder}/output.csv")

        cm = ConfusionMatrix(actual_vector=df["test"].tolist(), predict_vector=df["pred"].tolist())

        df = pd.DataFrame(list(cm.overall_stat.items()), columns=["Metric", "Value"])
        df.to_csv(f"{curr_dir}/{toml_folder}/metrics.csv", index=False)
        typer.echo(f"Metrics saved to {curr_dir}/{toml_folder}/metrics.csv.")


@app.command()
def ping():
    typer.echo("pong")


if __name__ == "__main__":
    app()