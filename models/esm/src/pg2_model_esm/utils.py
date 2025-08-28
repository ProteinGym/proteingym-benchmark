import torch


def label_row(
    row: str,
    sequence: str,
    token_probs: torch.Tensor,
    alphabet: object,
    offset_idx: int,
) -> float:
    """Calculate the log probability difference between a mutant and wildtype amino acid at a specific position.

    This function computes the mutation effect score by comparing the log probabilities of the
    mutant amino acid versus the wildtype amino acid at a given sequence position.

    Args:
        row: A mutation string in the format "WT{position}MT" where:
                  - WT is the wildtype amino acid (single letter)
                  - {position} is the 1-indexed position in the sequence
                  - MT is the mutant amino acid (single letter)
                  Example: "A123V" means Alanine at position 123 mutated to Valine
        sequence: The reference protein sequence used to validate the wildtype amino acid
        token_probs: Log probability tensor with shape (batch_size, seq_len + 1, vocab_size)
                                   where seq_len + 1 accounts for the BOS (Beginning of Sequence) token
        alphabet: An alphabet object with a get_idx() method that returns the vocabulary index
                 for a given amino acid character
        offset_idx: Offset to convert from 1-indexed position notation to 0-indexed sequence indexing

    Returns:
        float: The mutation effect score calculated as:
               log_prob(mutant_aa) - log_prob(wildtype_aa) at the specified position
               Positive values indicate the mutation is more likely than wildtype,
               negative values indicate the mutation is less likely than wildtype

    Raises:
        AssertionError: If the wildtype amino acid in the mutation string doesn't match
                       the amino acid at that position in the provided sequence

    Example:
        >>> row = "A50V"  # Alanine at position 50 to Valine
        >>> sequence = "MKTAYIAKQRQISFVKSHFSRQLEERLGLIEVQAPILSRVGDGTQDNLSGAEKAVQVKVKALPDAQFEVVHSLAKWKRQTLGQHDFSAGEGLYTHMKALRPDEDRLSLEVKNEQKQIAYIKLAQLPLEVQEKQGLTQVVK"
        >>> score = label_row(row, sequence, token_probs, alphabet, offset_idx=1)
        >>> print(f"Mutation effect score: {score}")
    """
    wt, idx, mt = row[0], int(row[1:-1]) - offset_idx, row[-1]
    assert sequence[idx] == wt, (
        "The listed wildtype does not match the provided sequence"
    )

    wt_encoded, mt_encoded = alphabet.get_idx(wt), alphabet.get_idx(mt)

    # add 1 for BOS
    score = token_probs[0, 1 + idx, mt_encoded] - token_probs[0, 1 + idx, wt_encoded]
    return score.item()


def compute_pppl(
    row: str, sequence: str, model: object, alphabet: object, offset_idx: int
) -> float:
    """Compute the pseudo-perplexity (PPPL) score for a protein sequence with a specific mutation.

    This function calculates the pseudo-perplexity by applying a mutation to the sequence,
    then computing the sum of log probabilities for each position when that position is
    masked and predicted by the model. This provides a measure of how well the model
    can predict the mutated sequence.

    Args:
        row: A mutation string in the format "WT{position}MT" where:
                  - WT is the wildtype amino acid (single letter)
                  - {position} is the 1-indexed position in the sequence
                  - MT is the mutant amino acid (single letter)
                  Example: "A123V" means Alanine at position 123 mutated to Valine
        sequence: The reference protein sequence to be mutated
        model: A protein language model (e.g., ESM) that can predict masked amino acids.
               Must have a forward method that returns a dictionary with "logits" key
        alphabet: An alphabet object with methods:
                 - get_batch_converter(): returns a batch converter for tokenization
                 - get_idx(aa): returns vocabulary index for amino acid
                 - mask_idx: attribute containing the mask token index
        offset_idx: Offset to convert from 1-indexed position notation to 0-indexed sequence indexing

    Returns:
        float: The sum of log probabilities across all positions in the mutated sequence.
               Higher (less negative) values indicate the mutated sequence is more likely
               according to the model. Lower (more negative) values indicate lower likelihood.

    Raises:
        AssertionError: If the wildtype amino acid in the mutation string doesn't match
                       the amino acid at that position in the provided sequence

    Example:
        >>> row = "A50V"  # Alanine at position 50 to Valine
        >>> sequence = "MKTAYIAKQRQISFVKSHFSRQLEERLGLIEVQAPILSRVGDGTQDNLSGAEKAVQVKVKALPDAQFEVVHSLAKWKRQTLGQHDFSAGEGLYTHMKALRPDEDRLSLEVKNEQKQIAYIKLAQLPLEVQEKQGLTQVVK"
        >>> pppl_score = compute_pppl(row, sequence, model, alphabet, offset_idx=1)
        >>> print(f"Pseudo-perplexity score: {pppl_score}")

    Notes:
        - The function creates a mutated version of the input sequence
        - For each position (excluding start/end tokens), it masks that position and
            computes the log probability of the actual amino acid at that position
        - The pseudo-perplexity is the sum of these log probabilities
        - This approach differs from traditional perplexity calculation and is commonly
            used in protein language model evaluation
    """
    wt, idx, mt = row[0], int(row[1:-1]) - offset_idx, row[-1]
    assert sequence[idx] == wt, (
        "The listed wildtype does not match the provided sequence"
    )

    # modify the sequence
    sequence = sequence[:idx] + mt + sequence[(idx + 1) :]

    # encode the sequence
    data = [
        ("protein1", sequence),
    ]

    batch_converter = alphabet.get_batch_converter()

    _, _, batch_tokens = batch_converter(data)

    _, _ = alphabet.get_idx(wt), alphabet.get_idx(mt)

    # compute probabilities at each position
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
        )  # vocab size

    return sum(log_probs)
