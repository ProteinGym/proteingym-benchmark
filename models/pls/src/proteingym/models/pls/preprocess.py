import logging
from typing import Any

import numpy as np
import polars as pl
from proteingym.base import Subsets

logger = logging.getLogger(__name__)

def load_x_and_y(subset: Subsets, split: str, test_fold: int, target: str) -> tuple[list[Any], list[Any], list[Any], list[Any]]:
        """Load train/test splits for k-fold cross-validation.
        
        Args:
            subset: a split dataset in the Subsets format
            split: name of the split
            test_fold: Which kfold split to take as test set
            target: name of the target we are classifying
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

def encode(split_X: list[Any], hyper_params: dict[str, Any]) -> np.ndarray:
    """Encode protein sequences into one-hot encoded numerical arrays.

    This function converts a list of protein sequences into a numerical representation
    using one-hot encoding, where each amino acid is represented as a binary vector
    based on its position in the amino acid alphabet.

    Args:
        split_X: List of protein sequences to encode. Each sequence should
            be a string or iterable of amino acid residues.
        hyper_params: Dictionary containing encoding parameters:
            - "aa_alphabet_length": Number of amino acids in the alphabet
            - "aa_alphabet": Ordered amino acid alphabet used for encoding

    Returns:
        np.ndarray: 2D numpy array of shape (n_sequences, sequence_length * aa_alphabet_length)
            containing one-hot encoded sequences. Each row represents one sequence,
            flattened from its original (sequence_length, aa_alphabet_length) matrix form.

    Example:
        >>> sequences = ["ACG", "AGC"]
        >>> params = {
        ...     "aa_alphabet_length": 20,
        ...     "aa_alphabet": "ACDEFGHIKLMNPQRSTVWY"
        ... }
        >>> encoded = encode(sequences, params)
        >>> encoded.shape
        (2, 60)  # 2 sequences, each 3 * 20 = 60 features

    Note:
        - Sequences are assumed to match the specified sequence_length
        - All amino acids in the sequences must be present in the aa_alphabet
        - The output is flattened; each sequence becomes a 1D array of length
            sequence_length * aa_alphabet_length
    """

    sequence_length = len(split_X[0])

    encodings = np.empty(
        (
            len(split_X),
            sequence_length * hyper_params["aa_alphabet_length"],
        )
    )

    for idx, sequence in enumerate(split_X):
        encoding = np.concatenate(
            [
                np.eye(hyper_params["aa_alphabet_length"])[
                    hyper_params["aa_alphabet"].index(res)
                ]
                for res in sequence
            ]
        )

        encodings[idx] = encoding

    return encodings
