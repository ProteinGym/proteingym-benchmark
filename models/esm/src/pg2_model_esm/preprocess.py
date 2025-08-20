import torch
from esm.data import Alphabet


def encode(sequence: str, alphabet: Alphabet) -> torch.Tensor:
    data = [
        ("protein1", sequence),
    ]

    batch_converter = alphabet.get_batch_converter()

    _, _, batch_tokens = batch_converter(data)

    return batch_tokens
