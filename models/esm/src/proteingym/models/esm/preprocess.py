import logging
from typing import Any

import polars as pl
import torch
from esm.data import Alphabet
from proteingym.base import Subsets

logger = logging.getLogger(__name__)


def load_x_and_y(
    subset: Subsets,
    split: str,
    test_fold: int,
    target: str,
) -> tuple[list[str], list[float], list[str], list[float]]:
    """Load train/test splits for k-fold cross-validation.

    Args:
        subset: a split dataset in the Subsets format
        split: name of the split
        test_fold: Which kfold split to take as test set
        target: name of the target we are classifying

    Returns:
        Tuple of (train_X, train_Y, test_X, test_Y) where X values are sequences
    """

    test_dataset = subset[split].slices[test_fold]
    test_df = subset[split].dataset[test_dataset].to_df()

    train_dfs = []
    for i in range(len(subset[split].slices)):
        if i != test_fold:
            train_slice = subset[split].slices[i]
            train_dfs.append(subset[split].dataset[train_slice].to_df())

    train_df = pl.concat(train_dfs)

    train_X = train_df['sequence'].to_list()
    train_Y = train_df[target].to_list()

    test_X = test_df['sequence'].to_list()
    test_Y = test_df[target].to_list()

    return train_X, train_Y, test_X, test_Y


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
