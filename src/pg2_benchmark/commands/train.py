import typer
from loguru import logger
from pg2_dataset.backends.records import RecordsDataset
from python_on_whales import docker

app = typer.Typer(help="Training commands")

@app.command("model")
def train(
    toml_file: str = typer.Option("--toml-file", help="Configuration TOML file"),
):
    ds = RecordsDataset(
        toml_file=toml_file,
        include_records=True,
    )

    df = ds.data_frame()

    logger.info(f"loaded dataset: {df[:3]}")
    logger.info(f"loaded params: {ds.settings.assays['assay_one'].constants}")

    git.clone()
    docker.build()

    docker.run(image="pls-model", volumes=[("data", "/data")], command=["--input-file", "data/A0A140D2T1_ZIKV_Sourisseau_2019.parquet", "--output-file", "data/output.json"])

if __name__ == "__main__":
    app()
