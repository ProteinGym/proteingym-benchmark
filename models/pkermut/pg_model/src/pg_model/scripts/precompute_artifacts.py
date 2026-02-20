from pathlib import Path

from loguru import logger
import typer
from typing import Annotated

from pg_model.scripts.coords import extract_3d_coords
from pg_model.scripts.conditionals import extract_proteinmpnn_conditional_probabilities
from pg_model.scripts.process_conditionals import process_probabilities
from pg_model.scripts.embeddings import extract_esm2_embeddings
from pg_model.scripts.zero_shot import extract_esm2_zero_shots


app = typer.Typer(
    help="Precompute artifacts for Kermut using the configuration as presented in the paper.",
    add_completion=True
)


@app.command()
def precompute_artifacts(
    dataset_name: Annotated[
        str,
        typer.Option(
            help="Name of the dataset. Used for naming the dataframe containing zero shot scores"
        )
    ],
    data_path: Annotated[
        str,
        typer.Option(
            help="Path the the variant dataset."
        )
    ],
    pdb_file: Annotated[
        str,
        typer.Option(
            help="PDB file to extract 3D coordinates from"
        )
    ],
    reference_sequence: Annotated[
        str,
        typer.Option(
            help="Reference sequence",
        ),
    ],
    artifact_dir: Annotated[
        str,
        typer.Option(
            help="Directory to save the precomputed artifacts to"
        )
    ],
    toks_per_batch: Annotated[
        int,
        typer.Option(
            help="Number of tokens to process per batch"
        )
    ] = 16384,
    device: Annotated[
        str,
        typer.Option(
            help="PyTorch backend device"
        )
    ] = "cpu",
) -> dict:
    path_conf = {
        "coords_dir": Path(artifact_dir) / "structures/coords",
        "conditional_probs_dir": Path(artifact_dir) / "conditional_probs",
        "embedding_dir": Path(artifact_dir) / "embeddings",
        "zero_shot_dir": Path(artifact_dir) / "zero_shot_fitness_predictions",
    }
    for path_name in path_conf.values():
        path_name.mkdir(parents=True, exist_ok=True)

    logger.info(f"Extracting 3D coordinates")
    extract_3d_coords(
        dataset_name=dataset_name,
        pdb_file=pdb_file,
        coords_dir=str(path_conf["coords_dir"])
    )
    logger.info(f"Obtaining conditional probabilities from ProteinMPNN")
    extract_proteinmpnn_conditional_probabilities(
        pdb_file=pdb_file,
        dataset_name=dataset_name,
        conditional_probs_dir=str(path_conf["conditional_probs_dir"])
    )
    logger.info(f"Processing conditional probabilities")
    process_probabilities(
        dataset_name=dataset_name,
        reference_sequence=reference_sequence,
        pdb_file=pdb_file,
        conditional_probs_dir=str(path_conf["conditional_probs_dir"])
    )
    logger.info(f"Extracting embeddings from ESM2")
    extract_esm2_embeddings(
        data_path=data_path,
        dataset_name=dataset_name,
        embedding_dir=str(path_conf["embedding_dir"]),
        toks_per_batch=toks_per_batch,
        device=device
    )
    logger.info(f"Extracting zeroshot scores from ESM2")
    extract_esm2_zero_shots(
        data_path=data_path,
        dataset_name=dataset_name,
        zero_shot_dir=str(path_conf["zero_shot_dir"]),
        reference_sequence=reference_sequence,
        device=device,
    )
    logger.success("Done!")
    return path_conf


if __name__ == "__main__":
    _ = app()
