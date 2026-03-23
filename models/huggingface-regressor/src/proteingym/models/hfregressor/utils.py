import os

import polars as pl
import numpy as np

from proteingym.base import Dataset, Subsets


def prepare_dataframe(
    subset: Subsets, split: str, test_fold: int
) -> pl.DataFrame:
    """Load train/test splits for k-fold cross-validation.

    Args:
        subset: a split dataset in the Subsets format
        split: name of the split
        test_fold: Which kfold split to take as test set
    """

    test_dataset = subset[split].slices[test_fold]
    test_df = subset[split].dataset[test_dataset].to_df()

    train_dfs = []
    for i in range(len(subset[split].slices)):
        if i != test_fold:
            train_slice = subset[split].slices[i]
            train_dfs.append(subset[split].dataset[train_slice].to_df())

    train_df = pl.concat(train_dfs)
    train_df = train_df.with_columns(pl.lit("train").alias("split"))
    test_df = test_df.with_columns(pl.lit("test").alias("split"))
    df = pl.concat([train_df, test_df]).sample(fraction=1)
    return df

def is_container() -> bool:
    return os.path.exists("/opt/program")