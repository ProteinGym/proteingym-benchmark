import logging

import pandas as pd
import torch
from esm import pretrained
from esm.data import Alphabet
from proteingym.base import Dataset
from proteingym.benchmark.model import ModelCard
from tqdm import tqdm

from .preprocess import encode
from .utils import compute_pppl, label_row


logger = logging.getLogger(__name__)


def load(model_card: ModelCard) -> tuple[torch.nn.Module, Alphabet]:
    """Load and configure an ESM model and its alphabet.

    Loads a pretrained ESM model from the location specified in the model card,
    sets it to evaluation mode, and optionally transfers it to GPU if available
    and not disabled.

    Args:
        model_card: Configuration object containing model location and GPU settings

    Returns:
        tuple: The loaded ESM model and its corresponding alphabet
    """
    model, alphabet = pretrained.load_model_and_alphabet(
        model_card.hyper_params["location"]
    )
    model.eval()

    if torch.cuda.is_available() and not model_card.hyper_params["nogpu"]:
        model = model.cuda()
        print("Transferred model to GPU")

    return model, alphabet


def infer(
    dataset: Dataset,
    model_card: ModelCard,
    model: torch.nn.Module,
    alphabet: Alphabet,
) -> pd.DataFrame:
    """Generate predictions for protein mutations using an ESM model.

    Computes fitness scores for protein mutations using one of three scoring
    strategies: wild-type marginals, masked marginals, or pseudo-perplexity.
    The scoring strategy is determined by the model card.

    Args:
        dataset: Dataset containing assay data with mutations to score
        model_card: Configuration object specifying scoring strategy and parameters
        model: The loaded ESM model for computing predictions
        alphabet: ESM alphabet for token encoding/decoding

    Returns:
        pd.DataFrame: DataFrame with predictions added in 'pred' column and
                     target column renamed to 'test'

    Raises:
        ValueError: If an unrecognized scoring strategy is specified
    """
    assays = dataset.assays.meta.assays
    targets = list(dataset.assays.meta.assays.keys())

    sequence = assays[targets[0]].constants["sequence"]
    mutation_col = assays[targets[0]].constants["mutation_col"]

    df = dataset.assays.data_frame

    batch_tokens = encode(sequence, alphabet)

    match model_card.hyper_params["scoring_strategy"]:
        case "wt-marginals":
            with torch.no_grad():
                token_probs = torch.log_softmax(model(batch_tokens)["logits"], dim=-1)

            df["pred"] = df.apply(
                lambda row: label_row(
                    row[mutation_col],
                    sequence,
                    token_probs,
                    alphabet,
                    model_card.hyper_params["offset_idx"],
                ),
                axis=1,
            )

        case "masked-marginals":
            all_token_probs = []

            for i in tqdm(range(batch_tokens.size(1))):
                batch_tokens_masked = batch_tokens.clone()
                batch_tokens_masked[0, i] = alphabet.mask_idx

                with torch.no_grad():
                    token_probs = torch.log_softmax(
                        model(batch_tokens_masked)["logits"], dim=-1
                    )

                all_token_probs.append(token_probs[:, i])  # vocab size

            token_probs = torch.cat(all_token_probs, dim=0).unsqueeze(0)

            df["pred"] = df.apply(
                lambda row: label_row(
                    row[mutation_col],
                    sequence,
                    token_probs,
                    alphabet,
                    model_card.hyper_params["offset_idx"],
                ),
                axis=1,
            )

        case "pseudo-ppl":
            tqdm.pandas()

            df["pred"] = df.progress_apply(
                lambda row: compute_pppl(
                    row[mutation_col],
                    sequence,
                    model,
                    alphabet,
                    model_card.hyper_params["offset_idx"],
                ),
                axis=1,
            )

        case _:
            raise ValueError(
                f"Unrecognized scoring strategy: {model_card.hyper_params['scoring_strategy']}"
            )

    df.rename(columns={targets[0]: "test"}, inplace=True)

    return df
