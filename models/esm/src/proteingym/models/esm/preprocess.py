import logging
from typing import Any

import polars as pl
import torch
from esm.data import Alphabet
from proteingym.base import Subsets

logger = logging.getLogger(__name__)


def has_indels(mutant_seq: str, wt_seq: str) -> bool:
    """Check if mutant sequence has insertions or deletions."""
    return len(mutant_seq) != len(wt_seq)


def sequence_to_mutation(mutant_seq: str, wt_seq: str) -> str:
    """Convert mutant sequence to mutation string format.
    
    Args:
        mutant_seq: Mutant protein sequence
        wt_seq: Wild-type protein sequence
        
    Returns:
        Mutation string in format 'WT{position}MT' (e.g., 'A123V')
        
    Raises:
        ValueError: If sequences have different lengths (indels present)
    """
    if has_indels(mutant_seq, wt_seq):
        raise ValueError("Cannot convert sequences with indels to mutation strings")
    
    for i, (wt_aa, mt_aa) in enumerate(zip(wt_seq, mutant_seq)):
        if wt_aa != mt_aa:
            return f"{wt_aa}{i+1}{mt_aa}"
    return ""


def load_x_and_y(
    subset: Subsets,
    split: str,
    test_fold: int,
    target: str,
    wt_seq: str = None,
    return_sequences: bool = False,
) -> tuple[list[Any], list[Any], list[Any], list[Any], bool]:
    """Load train/test splits for k-fold cross-validation.
    
    Args:
        subset: a split dataset in the Subsets format
        split: name of the split
        test_fold: Which kfold split to take as test set
        target: name of the target we are classifying
        wt_seq: Wild-type sequence for converting mutant sequences to mutation strings
        return_sequences: If True, return full sequences; if False, return mutation strings
        
    Returns:
        Tuple of (train_X, train_Y, test_X, test_Y, has_indels_flag)
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
    
    test_sequences = test_df['sequence'].to_list()
    
    # Check for indels
    has_indels_flag = False
    if wt_seq:
        has_indels_flag = any(has_indels(seq, wt_seq) for seq in test_sequences)
    
    # Return sequences or mutation strings based on flag
    if return_sequences or has_indels_flag:
        test_X = test_sequences
    elif wt_seq:
        test_X = [sequence_to_mutation(seq, wt_seq) for seq in test_sequences]
    else:
        test_X = test_sequences
    
    test_Y = test_df[target].to_list()
    
    return train_X, train_Y, test_X, test_Y, has_indels_flag


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
