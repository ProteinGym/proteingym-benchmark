import torch
from esm.data import Alphabet


def encode(sequence: str, alphabet: Alphabet) -> torch.Tensor:
    """Encode a protein sequence into tokens using the ESM alphabet.

    Args:
        sequence: Protein sequence to encode
        alphabet: ESM alphabet for tokenization

    Returns:
        Batch tokens tensor for the sequence
    """
    data = [
        ("protein1", sequence),
    ]

    batch_converter = alphabet.get_batch_converter()

    _, _, batch_tokens = batch_converter(data)

    return batch_tokens
