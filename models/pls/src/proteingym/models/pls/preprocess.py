import logging
from typing import Any

import numpy as np
from proteingym.base import Dataset

logger = logging.getLogger(__name__)


def load_x_and_y(dataset: Dataset, split) -> tuple[list[Any], list[Any]]:
    """Load feature and target data from a dataset archive file for a specified split.

    This function applies the configured split strategy for the dataset,
    and returns the features (X) and targets (Y)
    for the requested data split.

    Args:
        dataset: The dataset object loaded by proteingym.base.Dataset.from_path.
        split: The data split to load (train, validation, or test).

    Returns:
        tuple[list[Any], list[Any]]: A tuple containing:
            - split_X (list[Any]): Feature data for the specified split, where each
                inner list represents features for a single sample.
            - split_Y (list[Any]): Target values for the specified split.

    Note:
        - Currently only supports single target and single feature column (index 0).
        - The split strategy is determined by the dataset's metadata configuration.
        - Multiple targets and features support is planned for future implementation.
    """

    # TODO: Update split below
    # dataset.assays.add_split(
    #     split_strategy=SPLIT_STRATEGY_MAPPING[dataset.assays.meta.split_strategy](),
    #     targets=targets,
    # )

    # TODO: Replace this dummy split with above split method
    split_size = len(dataset.assays[0].records) // 3

    match split:
        case 1:
            split_X = [
                str(seq.value) for seq, _ in dataset.assays[0].records[:split_size]
            ]
            split_Y = [
                target["target"] for _, target in dataset.assays[0].records[:split_size]
            ]

        case 2:
            split_X = [
                str(seq.value)
                for seq, _ in dataset.assays[0].records[split_size : split_size * 2]
            ]
            split_Y = [
                target["target"]
                for _, target in dataset.assays[0].records[split_size : split_size * 2]
            ]

        case 3:
            split_X = [
                str(seq.value)
                for seq, _ in dataset.assays[0].records[split_size * 2 : split_size * 3]
            ]
            split_Y = [
                target["target"]
                for _, target in dataset.assays[0].records[
                    split_size * 2 : split_size * 3
                ]
            ]

    logger.info("Loaded the dataset with splits X and Y.")

    return split_X, split_Y


def encode(spit_X: list[Any], hyper_params: dict[str, Any]) -> np.ndarray:
    """Encode protein sequences into one-hot encoded numerical arrays.

    This function converts a list of protein sequences into a numerical representation
    using one-hot encoding, where each amino acid is represented as a binary vector
    based on its position in the amino acid alphabet.

    Args:
        spit_X: List of protein sequences to encode. Each sequence should
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

    sequence_length = len(spit_X[0])

    encodings = np.empty(
        (
            len(spit_X),
            sequence_length * hyper_params["aa_alphabet_length"],
        )
    )

    for idx, sequence in enumerate(spit_X):
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
