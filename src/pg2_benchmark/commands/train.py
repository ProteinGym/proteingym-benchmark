import os
import typer
from loguru import logger
import tempfile
import pandas as pd
from pg2_dataset.backends.records import RecordsDataset
from pg2_benchmark.models.git_utils import git_clone
from python_on_whales import docker

app = typer.Typer(help="Training commands")

@app.command("model")
def train(
    toml_file: str = typer.Option("--toml-file", help="Configuration TOML file"),
    git_repo: str = typer.Option("--git-repo", help="Git repository URI"),
    git_branch: str = typer.Option("--git-branch", help="Git branch name"),
):  
    # 1. Load the dataset
    ds = RecordsDataset(
        toml_file=toml_file,
        include_records=True,
    )
    
    # 2. Load the parameters
    logger.info(f"Loaded params: {ds.settings}")

    target_col_name = ds.settings.assays['assay_one'].constants["target_col_name"]
    sequence_length = ds.settings.assays['assay_one'].constants["sequence_length"]
    n_components = ds.settings.assays['assay_one'].constants["n_components"]

    with tempfile.TemporaryDirectory() as temp_dir:
        # 3. Clone the git repository
        git_clone(repo_url=git_repo, target_dir=temp_dir, branch_name=git_branch)
        os.chdir(temp_dir)

        # 4. Build the Docker image
        logger.info("Building Docker image...")
        docker.build(
            context_path="./",
            tags=["pls-model:latest"],
            load=True,
        )

        logger.info(f"{docker.image.list()}")

        # 5. Load the dataset
        os.makedirs(f"{temp_dir}/data", exist_ok=True)

        ds.data_frame()[:7].to_csv(f"{temp_dir}/data/train.csv", index=False)
        ds.data_frame()[7:10].to_csv(f"{temp_dir}/data/test.csv", index=False)

        logger.info(f"Loaded data frame: {ds.data_frame()[:10]}")

        # 6. Run the Docker container
        logger.info("Running Docker container...")
        docker.run(
            image="pls-model:latest",
            volumes=[(f"{temp_dir}/data", "/data")], 
            remove=True,
            command=[
                "--train-file-path", "/data/train.csv",
                "--test-file-path", "/data/test.csv",
                "--predict-file-path", "/data/predict.csv",
                "--sequence-length", sequence_length,
                "--sequence-col-name", "sequence",
                "--target-col-name", target_col_name,
                "--n-components", n_components,
            ],
        )

        result_df = pd.read_csv(f"{temp_dir}/data/predict.csv", encoding="utf-8")
        logger.info(f"Result is: {result_df}")

        # Calculate metrics here

        # 7. Clean up
        docker.image.remove("pls-model:latest", force=False, prune=True)

if __name__ == "__main__":
    app()
