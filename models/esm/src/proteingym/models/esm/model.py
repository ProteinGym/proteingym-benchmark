import logging

import pandas as pd
import torch
from esm import pretrained
from esm.data import Alphabet
from proteingym.base import Subsets
from proteingym.base.model import ModelCard
from tqdm import tqdm

from .preprocess import encode, load_x_and_y
from .utils import compute_pppl, compute_pppl_from_mutation, label_row

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
    split_dataset: Subsets,
    split: str,
    test_fold: int,
    target: str,
    model_card: ModelCard,
    model: torch.nn.Module,
    alphabet: Alphabet,
) -> pd.DataFrame:
    """Generate predictions for protein mutations using an ESM model.

    Computes fitness scores for protein mutations using one of three scoring
    strategies: wild-type marginals, masked marginals, or pseudo-perplexity.
    The scoring strategy is determined by the model card.

    Args:
        split_dataset: Subsets object containing protein sequences and targets
        split: Name of the split to use
        test_fold: Which fold to use as test set
        target: Target column name
        model_card: Configuration object specifying scoring strategy and parameters
        model: The loaded ESM model for computing predictions
        alphabet: ESM alphabet for token encoding/decoding

    Returns:
        pd.DataFrame: DataFrame with predictions added in 'pred' column and
                     target column renamed to 'test'

    Raises:
        ValueError: If scoring strategy is incompatible with data type
    """
    dataset = split_dataset[split].dataset
    reference_sequence = str(next(
        seq.value for seq in dataset.sequences if seq.type == 'wild_type'
    ))
    
    scoring_strategy = model_card.hyper_parameters["scoring_strategy"]
    
    # Determine if we need sequences or mutation strings
    return_sequences = scoring_strategy == "pseudo-ppl"
    
    _, _, test_X, test_Y, has_indels = load_x_and_y(
        subset=split_dataset,
        split=split,
        test_fold=test_fold,
        target=target,
        wt_seq=reference_sequence,
        return_sequences=return_sequences,
    )

    # Validate scoring strategy compatibility
    if has_indels and scoring_strategy in ["wt-marginals", "masked-marginals"]:
        raise ValueError(
            f"Scoring strategy '{scoring_strategy}' cannot handle indels. "
            "Use 'pseudo-ppl' for sequences with insertions/deletions."
        )

    df = pd.DataFrame(
        {
            "mutation_col": test_X,
            "target": test_Y,
        }
    )

    match scoring_strategy:
        case "wt-marginals":
            batch_tokens = encode(reference_sequence, alphabet)
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
            batch_tokens = encode(reference_sequence, alphabet)
            all_token_probs = []

            for i in tqdm(range(batch_tokens.size(1))):
                batch_tokens_masked = batch_tokens.clone()
                batch_tokens_masked[0, i] = alphabet.mask_idx

                with torch.no_grad():
                    token_probs = torch.log_softmax(
                        model(batch_tokens_masked)["logits"], dim=-1
                    )

                all_token_probs.append(token_probs[:, i])

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

            if has_indels:
                # Direct sequence scoring for indels
                df["pred"] = df.progress_apply(
                    lambda row: compute_pppl(
                        row["mutation_col"],
                        model,
                        alphabet,
                    ),
                    axis=1,
                )
            else:
                # Mutation string scoring for substitutions
                df["pred"] = df.progress_apply(
                    lambda row: compute_pppl_from_mutation(
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
                f"Unrecognized scoring strategy: {scoring_strategy}"
            )

    df.rename(columns={"target": "test"}, inplace=True)

    return df
