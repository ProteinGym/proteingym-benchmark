"""Adapted from https://github.com/facebookresearch/esm/blob/main/examples/variant-prediction/predict.py"""

from pathlib import Path
from loguru import logger

import typer
from typing import Annotated

import pandas as pd
import torch
from esm import pretrained
from omegaconf import DictConfig


app = typer.Typer(
    help="Extract ESM2 zero shot scores from variants sequences. "
         "The dataset needs to include a row named 'mutant' for computing likelihoods.",
    add_completion=True,
)


def _label_row(row, sequence, token_probs, alphabet, offset_idx):
    mutations = row.split(":")
    score = 0
    for mutation in mutations:
        wt, idx, mt = mutation[0], int(mutation[1:-1]) - offset_idx, mutation[-1]
        assert sequence[idx] == wt, "The listed wildtype does not match the provided sequence"

        wt_encoded, mt_encoded = alphabet.get_idx(wt), alphabet.get_idx(mt)

        # add 1 for BOS
        score += (token_probs[0, 1 + idx, mt_encoded] - token_probs[0, 1 + idx, wt_encoded]).item()

    return score


# zeroshot_path = "data/zero_shot_fitness_predictions"
@app.command()
def extract_esm2_zero_shots(
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
    zero_shot_dir: Annotated[
        str,
        typer.Option(
            help="Directory to store the zeroshot predictions"
        )
    ],
    reference_sequence: Annotated[
        str,
        typer.Option(
            help="Reference sequence",
        ),
    ],
    device: Annotated[
        str,
        typer.Option(
            help="PyTorch backend device"
        )
    ] = "cpu"
) -> None:
    data_path = Path(data_path)
    model_name = "esm2_t33_650M_UR50D"

    model, alphabet = pretrained.load_model_and_alphabet(model_name)
    model.eval()

    if device == "cuda" and torch.cuda.is_available():
        model = model.cuda()
        logger.info("Computing zero shot scores using GPU")

    df = pd.read_csv(data_path)
    batch_converter = alphabet.get_batch_converter()
    data = [
        ("protein1", reference_sequence),
    ]
    _, _, batch_tokens = batch_converter(data)

    all_token_probs = []
    for i in range(batch_tokens.size(1)):
        batch_tokens_masked = batch_tokens.clone()
        batch_tokens_masked[0, i] = alphabet.mask_idx
        with torch.no_grad():
            if device == "cuda" and torch.cuda.is_available():
                batch_tokens_masked = batch_tokens_masked.cuda()
            token_probs = torch.log_softmax(model(batch_tokens_masked)["logits"], dim=-1)
        all_token_probs.append(token_probs[:, i])
    token_probs = torch.cat(all_token_probs, dim=0).unsqueeze(0)
    df[model_name] = df.apply(
        lambda row: _label_row(
            row["mutant"],
            reference_sequence,
            token_probs,
            alphabet,
            1,
        ),
        axis=1,
    )

    output_dir = Path(zero_shot_dir) / "ESM2" / "650M"
    output_dir.mkdir(parents=True, exist_ok=True)
    df.to_csv(output_dir / f"{dataset_name}.csv", index=False)


if __name__ == "__main__":
    app()
