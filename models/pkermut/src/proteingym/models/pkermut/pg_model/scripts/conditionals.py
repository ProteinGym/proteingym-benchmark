import os
import subprocess
import typer
from typing import Annotated
from pathlib import Path


app = typer.Typer(
    help="Compute the raw conditional probabilities of a structure with proteinmpnn",
    add_completion=True
)


@app.command()
def extract_proteinmpnn_conditional_probabilities(
    pdb_file: Annotated[
        str,
        typer.Option(
            help="PDB file to extract 3D coordinates from"
        )
    ],
    dataset_name: Annotated[
        str,
        typer.Option(
            help="Name of the dataset. Used for naming the dataframe containing zero shot scores"
        )
    ],
    conditional_probs_dir: Annotated[
        str,
        typer.Option(
            help="Directory to store the proteinmpnn conditional probabilities"
        )
    ],
) -> None:
    PROTEINMPNN_DIR = os.environ.get("PROTEINMPNN_DIR")

    output_dir = Path(conditional_probs_dir) / f"raw_ProteinMPNN_outputs/{dataset_name}/proteinmpnn"
    output_dir.mkdir(parents=True, exist_ok=True)

    command = [
        "python",
        f"{PROTEINMPNN_DIR}/protein_mpnn_run.py",
        "--pdb_path", f"{pdb_file}",
        "--save_score", "1",
        "--conditional_probs_only", "1",
        "--num_seq_per_target", "10",
        "--batch_size", "1",
        "--out_folder", f"{output_dir}",
        "--seed", "37",
        "--suppress_print", "1",
    ]
    subprocess.run(command)


if __name__ == "__main__":
    app()
