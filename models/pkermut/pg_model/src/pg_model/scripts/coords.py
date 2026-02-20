from pathlib import Path
from typing import Annotated
import typer

import numpy as np
from Bio.PDB import PDBParser


app = typer.Typer(
    help="Extract 3D coordinates from a PDB file",
    add_completion=True,
)


@app.command()
def extract_3d_coords(
    dataset_name: Annotated[
        str,
        typer.Option(
            help="Name of the dataset. Used for naming the dataframe containing zero shot scores"
        )
    ],
    pdb_file: Annotated[
        str,
        typer.Option(
            help="PDB file to extract 3D coordinates from"
        )
    ],
    coords_dir: Annotated[
        str,
        typer.Option(
            help="Directory to save the coordinates to"
        )
    ]
) -> None:
    coords_dir = Path(coords_dir)
    coords_dir.mkdir(exist_ok=True, parents=True)
    out_path = coords_dir / f"{dataset_name}.npy"
    structure_name = Path(pdb_file).stem
    structure = PDBParser().get_structure(structure_name, pdb_file)

    coords = []
    for model in structure:
        for chain in model:
            for residue in chain:
                for atom in residue:
                    if atom.get_name() == "CA":
                        coords.append(atom.get_coord())
    coords = np.array(coords)
    np.save(out_path, coords)


if __name__ == "__main__":
    app()
