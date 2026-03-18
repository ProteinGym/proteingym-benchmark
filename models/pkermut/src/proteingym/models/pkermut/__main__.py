import tempfile
from pathlib import Path
from typing import Annotated
import typer
from rich.console import Console
from loguru import logger

import polars as pl
from hydra import initialize_config_dir, compose

import torch

from proteingym.base import Dataset
from proteingym.base.model import ModelCard
from proteingym.base.sequence import SequenceType

from .pg_model.kermut_run import main as kermut_run
from .pg_model.scripts.precompute_artifacts import precompute_artifacts
from .pg_model.utils import prepare_hydra_configs, log_and_save_metrics, is_container
from .pg_model.utils import (
    variant_sequence_to_mutations,
    prepare_dataframe,
    dump_pg_structure,
)
from .pg_model.constants import HYDRA_CONFIG_PATH, HYDRA_TEMP_CONFIG_PATH


CUDA_AVAILABLE = torch.cuda.is_available()


app = typer.Typer(
    help="Kermut model CLI",
    add_completion=True,
)

console = Console()


class ContainerTrainingJobPath:
    PREFIX = Path("/opt/program")
    MODEL_CARD_PATH = PREFIX / "README.md"
    OUTPUT_PATH = PREFIX / "output"


@app.command()
def train(
    dataset_file: Annotated[
        Path,
        typer.Option(
            help="Path to the dataset file",
        ),
    ],
    model_card_file: Annotated[
        Path,
        typer.Option(
            help="Path to the model card markdown file",
        ),
    ] = ContainerTrainingJobPath.MODEL_CARD_PATH,
):
    console.print(f"Loading {dataset_file} and {model_card_file}...")

    dataset = Dataset.from_path(dataset_file)
    model_card = ModelCard.from_path(model_card_file)
    reference_sequence = str(
        next(
            seq.value for seq in dataset.sequences if seq.type == SequenceType.WILD_TYPE
        )
    )

    torch.set_default_dtype(torch.float32)
    if model_card.hyper_parameters["preferential"]:
        torch.set_default_dtype(torch.float64)

    with tempfile.TemporaryDirectory() as temp_dir:
        # TODO: Read splits from dataset object
        data_path = str(Path(temp_dir) / f"{dataset.name}.csv")
        if is_container():
            output_path = str(ContainerTrainingJobPath.OUTPUT_PATH)
        else:
            output_path = str(Path(temp_dir) / "output")
        df = prepare_dataframe(dataset, test_size=0.2)

        if "mutant" not in df.columns:
            logger.info(
                "No mutant column in the dataframe. Creating a copy with mutation information"
            )
            df = df.with_columns(
                pl.col("sequence")
                .map_elements(
                    lambda x: variant_sequence_to_mutations(x, reference_sequence),
                    return_dtype=pl.String,
                )
                .alias("mutant")
            )
            variant_matches_reference = df["sequence"] == reference_sequence
            if variant_matches_reference.any():
                logger.error("Dataframe contains variants identical to reference!")
                logger.error(df[variant_matches_reference])
            df.write_csv(data_path)

        # TODO: Parse structure form dataset object
        pdb_path = str(Path(temp_dir) / "structure.pdb")
        structure = dataset.structures[0]
        dump_pg_structure(pdb_path, structure)

        if pdb_path is not None:
            console.print("PDB file passed, computing necessary artifacts")
            _ = precompute_artifacts(
                dataset_name=dataset.name,
                data_path=data_path,
                pdb_file=pdb_path,
                reference_sequence=reference_sequence,
                artifact_dir=temp_dir,
                device=model_card.hyper_parameters["device"],
            )

        params_to_update = {
            "dataset_name": dataset.name,
            # TODO: Parse target from Dataset object
            "target": "target",
            "data_artifact_path": temp_dir,
            "DMS_input_folder": temp_dir,
            "embedding_path": str(Path(temp_dir) / "embeddings"),
            "conditional_probs_path": str(Path(temp_dir) / "conditional_probs"),
            "reference_sequence": reference_sequence,
            "output_path": output_path,
            "n_steps": model_card.hyper_parameters["n_steps"],
            "use_gpu": True
            if model_card.hyper_parameters["device"] == "cuda"
            else False,
            "preferential": model_card.hyper_parameters["preferential"],
            "preference_sampling_strategy": model_card.hyper_parameters[
                "preference_sampling_strategy"
            ],
        }
        prepare_hydra_configs(
            HYDRA_CONFIG_PATH, HYDRA_TEMP_CONFIG_PATH, params_to_update
        )

        with initialize_config_dir(config_dir=str(HYDRA_TEMP_CONFIG_PATH)):
            cfg = compose(config_name="benchmark")
            kermut_run(cfg)

        results = pl.read_csv(Path(output_path) / f"{dataset.name}.csv")
        results = results.rename({"y_var": "y_pred_var"})
        results.select(["sequence", "split", "y", "y_pred", "y_pred_var"]).write_csv(
            Path(output_path) / "predictions.csv"
        )
        log_and_save_metrics(results, str(output_path))

        # TODO: pg-benchmark expects a dataframe with only test gt and predictions,
        # This should be removed later
        test_data = results.filter(pl.col("split") == "test")
        df = pl.DataFrame(
            {
                "sequence": test_data["sequence"],
                "test": test_data["y"],
                "pred": test_data["y_pred"],
            }
        )

        df.write_csv(
            f"{output_path}/{dataset.name}_{model_card.name}.csv"
        )

        console.print(
            f"Saved the metrics in CSV in {output_path}/{dataset.name}_{model_card.name}.csv"
        )


@app.command()
def ping():
    console.print("pong")


if __name__ == "__main__":
    app()
