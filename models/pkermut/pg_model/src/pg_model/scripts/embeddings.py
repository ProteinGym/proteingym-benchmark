"""Script to extract ESM-2 embeddings for ProteinGym DMS assays.
Adapted from https://github.com/facebookresearch/esm/blob/main/scripts/extract.py"""

from pathlib import Path
from loguru import logger

import h5py
from typing import Annotated
import typer
import pandas as pd
import torch
from esm import FastaBatchedDataset, pretrained
from omegaconf import DictConfig
from tqdm import tqdm


app = typer.Typer(
    help="Extract ESM2 embeddings for each sequence in a protein variant dataset",
    add_completion=True
)


def _filter_datasets(cfg: DictConfig, embedding_dir: Path) -> pd.DataFrame:
    df_ref = pd.read_csv(cfg.data.paths.reference_file)
    match cfg.dataset:
        case "all":
            if cfg.data.embedding.mode == "multiples":
                df_ref = df_ref[df_ref["includes_multiple_mutants"]]
                df_ref = df_ref[df_ref["DMS_total_number_mutants"] < 7500]
        case "single":
            if cfg.single.use_id:
                df_ref = df_ref[df_ref["DMS_id"] == cfg.single.id]
            else:
                df_ref = df_ref.iloc[[cfg.single.id]]
        case _:
            raise ValueError(f"Invalid dataset: {cfg.dataset}")

    if not cfg.overwrite:
        existing_results = []
        for DMS_id in df_ref["DMS_id"]:
            output_file = embedding_dir / f"{DMS_id}.h5"
            if output_file.exists():
                existing_results.append(DMS_id)
        df_ref = df_ref[~df_ref["DMS_id"].isin(existing_results)]

    return df_ref


@app.command()
def extract_esm2_embeddings(
    data_path: Annotated[
        str,
        typer.Option(
            help="Path the the variant dataset."
        )
    ],
    dataset_name: Annotated[
        str,
        typer.Option(
            help="Name of the dataset. Used for naming the dataframe containing zero shot scores"
        )
    ],
    embedding_dir: Annotated[
        str,
        typer.Option(
            help="Directory to store the embeddings"
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
    ] = "cpu"
) -> None:
    data_path = Path(data_path)
    model_name = "esm2_t33_650M_UR50D"
    embedding_dir = Path(embedding_dir)
    embedding_dir.mkdir(parents=True, exist_ok=True)

    model, alphabet = pretrained.load_model_and_alphabet(model_name)
    model.eval()

    if device == "cuda" and torch.cuda.is_available():
        model = model.cuda()
        logger.info("Computing embeddings using GPU")

    df = pd.read_csv(data_path)

    mutants = df["mutant"].tolist()
    sequences = df["sequence"].tolist()
    batched_dataset = FastaBatchedDataset(sequence_strs=sequences, sequence_labels=mutants)

    batches = batched_dataset.get_batch_indices(
        toks_per_batch, extra_toks_per_seq=1
    )
    data_loader = torch.utils.data.DataLoader(
        batched_dataset,
        collate_fn=alphabet.get_batch_converter(truncation_seq_length=1022),
        batch_sampler=batches,
    )

    repr_layers = [33]
    all_labels = []
    all_representations = []

    with torch.no_grad():
        for batch_idx, (labels, strs, toks) in enumerate(data_loader):
            if device == "cuda" and torch.cuda.is_available():
                toks = toks.to(device="cuda", non_blocking=True)

            out = model(toks, repr_layers=repr_layers, return_contacts=False)
            representations = {
                layer: t.to(device="cpu") for layer, t in out["representations"].items()
            }

            for i, label in enumerate(labels):
                truncate_len = min(1022, len(strs[i]))
                all_labels.append(label)
                all_representations.append(
                    representations[33][i, 1 : truncate_len + 1].mean(axis=0).clone().numpy()
                )

    assert mutants == all_labels
    embeddings_dict = {
        "embeddings": all_representations,
        "mutants": mutants,
    }

    # Store data as HDF5
    with h5py.File(embedding_dir / f"{dataset_name}.h5", "w") as h5f:
        for key, value in embeddings_dict.items():
            h5f.create_dataset(key, data=value, dtype=h5py.string_dtype() if key == "mutant" else None)


if __name__ == "__main__":
    app()
