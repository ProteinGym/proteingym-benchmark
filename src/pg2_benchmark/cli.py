import typer
from pg2_benchmark.commands import train

app = typer.Typer(
    help="ProteinGym2 - Benchmark CLI",
    add_completion=True,
)

app.add_typer(train.app, name="train", help="Train various models")

if __name__ == "__main__":
    app()