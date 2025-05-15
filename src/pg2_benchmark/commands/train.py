import os
import typer
from loguru import logger
import tempfile
from pg2_dataset.backends.records import RecordsDataset
from pg2_benchmark.models.git_utils import git_clone
from python_on_whales import docker

app = typer.Typer(help="Training commands")

@app.command("model")
def train(
    toml_file: str = typer.Option("--toml-file", help="Configuration TOML file"),
):  
    
    # 1. Load the dataset
    ds = RecordsDataset(
        toml_file=toml_file,
        include_records=True,
    )
    
    # 2. Load the parameters
    logger.info(f"Loaded params: {ds.settings}")

    git_repo = ds.settings.assays['assay_one'].constants["git_repo"]
    git_branch = ds.settings.assays['assay_one'].constants["git_branch"]

    label_col_name = ds.settings.assays['assay_one'].target
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
                "--sequence-length", sequence_length,
                "--sequence-col-name", "sequence",
                "--label-col-name", label_col_name,
                "--n-components", n_components,
            ],
        )

if __name__ == "__main__":
    app()
