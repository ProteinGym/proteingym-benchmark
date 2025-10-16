import logging

import pandas as pd
import torch
from esm import pretrained
from esm.data import Alphabet
from proteingym.base import Dataset
from proteingym.base.model import ModelCard
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
        model_card.hyper_parameters["location"]
    )
    model.eval()

    if torch.cuda.is_available() and not model_card.hyper_parameters["nogpu"]:
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
    assays = dataset.assays[0]

    df = pd.DataFrame(
        {
            "mutation_col": [str(seq.value) for seq, _ in assays.records],
            "target": [target["target"] for _, target in assays.records],
        }
    )

    # For ESM zero-shot model, the schema is defined as ["mutation_col", "target"]
    # with a reference sequence.
    # For example: ["A2C", "-0.03"] with a reference sequence "AABB" refers to:
    # in position 2, "A" is mutated to "C", which ends as "ACBB".
    # But this reference sequence cannot be represented in the current proteingym-base dataset.
    # As a workaround, variables are used to store this constant reference sequence.
    reference_sequence = assays.variables["R"]

    batch_tokens = encode(reference_sequence, alphabet)

    match model_card.hyper_parameters["scoring_strategy"]:
        case "wt-marginals":
            with torch.no_grad():
                token_probs = torch.log_softmax(model(batch_tokens)["logits"], dim=-1)

            df["pred"] = df.apply(
                lambda row: label_row(
                    row["mutation_col"],
                    reference_sequence,
                    token_probs,
                    alphabet,
                    model_card.hyper_parameters["offset_idx"],
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
                    row["mutation_col"],
                    reference_sequence,
                    token_probs,
                    alphabet,
                    model_card.hyper_parameters["offset_idx"],
                ),
                axis=1,
            )

        case "pseudo-ppl":
            tqdm.pandas()

            df["pred"] = df.progress_apply(
                lambda row: compute_pppl(
                    row["mutation_col"],
                    reference_sequence,
                    model,
                    alphabet,
                    model_card.hyper_parameters["offset_idx"],
                ),
                axis=1,
            )

        case _:
            raise ValueError(
                f"Unrecognized scoring strategy: {model_card.hyper_parameters['scoring_strategy']}"
            )

    df.rename(columns={"target": "test"}, inplace=True)

    return df
