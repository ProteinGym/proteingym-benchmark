import logging

import polars as pl
import torch
from esm import pretrained
from esm.data import Alphabet
from proteingym.base import Subsets
from proteingym.base.model import ModelCard
from proteingym.base.sequence import SequenceType
from tqdm import tqdm

from .preprocess import encode, load_x_and_y
from .utils import compute_pppl, score_sequence_difference

logger = logging.getLogger(__name__)



def load(model_card: ModelCard) -> tuple[torch.nn.Module, Alphabet]:
    """Load and configure an ESM model and its alphabet.

    Loads a pretrained ESM model from the location specified in the model card,
    sets it to evaluation mode, and optionally transfers it to an accelerator device
    (MPS on macOS, CUDA on other platforms) if available and not disabled.

    Device priority: MPS > CUDA > CPU

    Args:
        model_card: Configuration object containing model location and GPU settings
                   (nogpu parameter applies to all accelerators including MPS)

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
    sequences: list[str],
    dataset,
    target: str,
    model_card: ModelCard,
    model: torch.nn.Module,
    alphabet: Alphabet,
) -> pl.DataFrame:
    """Generate predictions for protein mutations using an ESM model.

    Computes fitness scores for protein mutations using one of three scoring
    strategies: wild-type marginals, masked marginals, or pseudo-perplexity.
    The scoring strategy is determined by the model card.

    Args:
        sequences: List of protein sequences to score
        dataset: Dataset object containing reference sequence
        target: Target column name (unused, kept for compatibility)
        model_card: Configuration object specifying scoring strategy and parameters
        model: The loaded ESM model for computing predictions
        alphabet: ESM alphabet for token encoding/decoding

    Returns:
        pl.DataFrame: Polars DataFrame with sequences and predictions in 'pred' column

    Raises:
        ValueError: If scoring strategy is incompatible with data type
    """
    reference_sequence = str(next(
        seq.value for seq in dataset.sequences if seq.type == SequenceType.WILD_TYPE
    ))

    scoring_strategy = model_card.hyper_parameters["scoring_strategy"]

    model_device = next(model.parameters()).device

    match scoring_strategy:
        case "wt-marginals":
            batch_tokens = encode(reference_sequence, alphabet).to(model_device)
            with torch.no_grad():
                token_probs = torch.log_softmax(model(batch_tokens)["logits"], dim=-1)

            predictions = [
                score_sequence_difference(
                    seq,
                    reference_sequence,
                    token_probs,
                    alphabet,
                )
                for seq in tqdm(sequences, desc="Scoring mutations")
            ]

        case "masked-marginals":
            batch_tokens = encode(reference_sequence, alphabet).to(model_device)
            all_token_probs = []

            for i in tqdm(range(batch_tokens.size(1)), desc="Computing marginals"):
                batch_tokens_masked = batch_tokens.clone()
                batch_tokens_masked[0, i] = alphabet.mask_idx

                with torch.no_grad():
                    token_probs = torch.log_softmax(
                        model(batch_tokens_masked)["logits"], dim=-1
                    )

                all_token_probs.append(token_probs[:, i])

            token_probs = torch.cat(all_token_probs, dim=0).unsqueeze(0)

            predictions = [
                score_sequence_difference(
                    seq,
                    reference_sequence,
                    token_probs,
                    alphabet,
                )
                for seq in tqdm(sequences, desc="Scoring mutations")
            ]

        case "pseudo-ppl":
            predictions = [
                compute_pppl(
                    seq,
                    model,
                    alphabet,
                )
                for seq in tqdm(sequences, desc="Computing pseudo-perplexity")
            ]

        case _:
            raise ValueError(
                f"Unrecognized scoring strategy: {scoring_strategy}"
            )

    df = pl.DataFrame(
        {
            "sequence": sequences,
            "pred": predictions,
        }
    )

    return df
