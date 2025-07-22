import typer
from pg2_benchmark.cli.dataset import dataset_app
from pg2_benchmark.cli.metric import metric_app
from pg2_benchmark.cli.aws import aws_app

app = typer.Typer(
    name="benchmark",
    help="ProteinGym2 - Benchmark CLI",
    add_completion=False,
)

app.add_typer(dataset_app, name="dataset", help="Dataset operations")
app.add_typer(metric_app, name="metric", help="Metric operations")
app.add_typer(aws_app, name="aws", help="AWS operations")


@app.command()
def ping():
    typer.echo("pong")


if __name__ == "__main__":
    app()
