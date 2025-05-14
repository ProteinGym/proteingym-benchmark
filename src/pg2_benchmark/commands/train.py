import typer
from loguru import logger
from pg2_dataset.backends.records import RecordsDataset

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

if __name__ == "__main__":
    app()
