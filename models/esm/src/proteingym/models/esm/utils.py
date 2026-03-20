import torch


def score_sequence_difference(
    mutant_seq: str,
    wt_seq: str,
    token_probs: torch.Tensor,
    alphabet: object,
) -> float:
    """Calculate the log probability difference between mutant and wildtype sequences.

    This function identifies positions where the mutant differs from wildtype and computes
    the sum of log probability differences at those positions.

    Args:
        mutant_seq: The mutant protein sequence
        wt_seq: The wildtype protein sequence
        token_probs: Log probability tensor with shape (batch_size, seq_len + 1, vocab_size)
                     where seq_len + 1 accounts for the BOS (Beginning of Sequence) token
        alphabet: An alphabet object with a get_idx() method that returns the vocabulary index
                 for a given amino acid character

    Returns:
        float: The mutation effect score calculated as the sum of:
               log_prob(mutant_aa) - log_prob(wildtype_aa) at each differing position
               Positive values indicate mutations are more likely than wildtype,
               negative values indicate mutations are less likely than wildtype

    Raises:
        ValueError: If sequences have different lengths (indels not supported for this method)

    Example:
        >>> mutant_seq = "MKTAYIAK...QVVK"  # Mutant sequence
        >>> wt_seq = "MKTAYIAK...QVVK"      # Wildtype sequence
        >>> score = score_sequence_difference(mutant_seq, wt_seq, token_probs, alphabet)
        >>> print(f"Mutation effect score: {score}")
    """
    if len(mutant_seq) != len(wt_seq):
        raise ValueError(
            "Sequences must have the same length. Indels are not supported for marginal scoring strategies."
        )

    total_score = 0.0
    for idx, (wt_aa, mt_aa) in enumerate(zip(wt_seq, mutant_seq)):
        if wt_aa != mt_aa:
            wt_encoded = alphabet.get_idx(wt_aa)
            mt_encoded = alphabet.get_idx(mt_aa)
            # add 1 for BOS token
            score = token_probs[0, 1 + idx, mt_encoded] - token_probs[0, 1 + idx, wt_encoded]
            total_score += score.item()

    return total_score


def compute_pppl(
    sequence: str, model: object, alphabet: object
) -> float:
    """Compute the pseudo-perplexity (PPPL) score for a protein sequence.

    This function calculates the pseudo-perplexity by computing the sum of log
    probabilities for each position when that position is masked and predicted
    by the model.

    Args:
        sequence: The protein sequence to score
        model: A protein language model (e.g., ESM) that can predict masked amino acids.
               Must have a forward method that returns a dictionary with "logits" key
        alphabet: An alphabet object with methods:
                 - get_batch_converter(): returns a batch converter for tokenization
                 - get_idx(aa): returns vocabulary index for amino acid
                 - mask_idx: attribute containing the mask token index

    Returns:
        float: The sum of log probabilities across all positions in the sequence.
               Higher (less negative) values indicate the sequence is more likely
               according to the model.
    """
    data = [
        ("protein1", sequence),
    ]

    batch_converter = alphabet.get_batch_converter()
    _, _, batch_tokens = batch_converter(data)

    # Move batch_tokens to same device as model
    model_device = next(model.parameters()).device
    batch_tokens = batch_tokens.to(model_device)

    log_probs = []

    for i in range(1, len(sequence) - 1):
        batch_tokens_masked = batch_tokens.clone()
        batch_tokens_masked[0, i] = alphabet.mask_idx
        with torch.no_grad():
            token_probs = torch.log_softmax(
                model(batch_tokens_masked)["logits"], dim=-1
            )
        log_probs.append(
            token_probs[0, i, alphabet.get_idx(sequence[i])].item()
        )

    return sum(log_probs)


