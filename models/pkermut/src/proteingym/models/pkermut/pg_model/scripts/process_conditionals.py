from pathlib import Path
import typer
from typing import Annotated

import numpy as np

# raw_proteinmpnn_dir from root: "data/conditional_probs/raw_ProteinMPNN_outputs"
# proteinmpnn_dir from root: "data/conditional_probs/ProteinMPNN"

app = typer.Typer(
    help="Process conditional probabilities from raw",
    add_completion=True,
)


@app.command()
def process_probabilities(
    dataset_name: Annotated[
        str,
        typer.Option(
            help="Name of the dataset",
        ),
    ],
    reference_sequence: Annotated[
        str,
        typer.Option(
            help="Reference sequence",
        ),
    ],
    pdb_file: Annotated[
        str,
        typer.Option(
            help="PDB file to extract 3D coordinates from"
        )
    ],
    conditional_probs_dir: Annotated[
        str,
        typer.Option(
            help="Where to store the processed probabilities",
        ),
    ]
) -> None:
    proteinmpnn_dir = Path(conditional_probs_dir)
    raw_proteinmpnn_dir = Path(conditional_probs_dir) / "raw_ProteinMPNN_outputs"
    proteinmpnn_alphabet = "ACDEFGHIKLMNPQRSTVWYX"
    proteinmpnn_tok_to_aa = {i: aa for i, aa in enumerate(proteinmpnn_alphabet)}
    proteinmpnn_dir.mkdir(parents=True, exist_ok=True)

    structure_name = Path(pdb_file).stem
    file_path = (
        raw_proteinmpnn_dir
        / dataset_name
        / f"proteinmpnn/conditional_probs_only/{structure_name}.npz"
    )
    # Load and unpack
    raw_file = np.load(file_path)
    log_p = raw_file["log_p"]
    wt_toks = raw_file["S"]

    # Process logits ("X" is included as 21st AA in ProteinMPNN alphabet)
    log_p_mean = log_p.mean(axis=0)
    p_mean = np.exp(log_p_mean)
    p_mean = p_mean[:, :20]
    wt_seq_from_toks = "".join([proteinmpnn_tok_to_aa[tok] for tok in wt_toks])
    if len(wt_seq_from_toks) != len(reference_sequence):
        raise ValueError("Sequences don't have equal length!")
    if wt_seq_from_toks != reference_sequence:
        raise ValueError("Sequences don't match!")

    np.save(proteinmpnn_dir / dataset_name, p_mean)


if __name__ == "__main__":
    app()
