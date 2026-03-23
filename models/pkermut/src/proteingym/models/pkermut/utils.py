import os

from loguru import logger

import numpy as np
import polars as pl
from Bio.PDB.PDBIO import PDBIO

from proteingym.base import Dataset, Subsets
from proteingym.base.structure import Structure


def prepare_dataframe(
    subset: Subsets, target: str, split: str, test_fold: int
) -> pl.DataFrame:
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
    train_df = train_df.with_columns(pl.lit("train").alias("split"))
    test_df = test_df.with_columns(pl.lit("test").alias("split"))
    df = pl.concat([train_df, test_df])
    df = df.rename({target: "target"}).sample(fraction=1)
    return df


def add_pseudo_if_variant_matches_reference(
    df: pl.DataFrame, reference_sequence: str
) -> pl.DataFrame:
    matches_reference = df.filter(pl.col("sequence") == reference_sequence)
    if len(matches_reference) > 0:
        if reference_sequence[-1] == "G":
            replacement_aa = "A"
        else:
            replacement_aa = "G"
        modified_sequence = reference_sequence[:-1] + replacement_aa
        logger.warning(
            f"Found {len(matches_reference)} sequence(s) matching the reference sequence,"
            f"replacing with pseudo variant: \n {modified_sequence}\n"
            f"(mutating final residue to {replacement_aa})."
        )
        return df.with_columns(
            pl.when(pl.col("sequence") == reference_sequence)
            .then(pl.lit(modified_sequence))
            .otherwise(pl.col("sequence"))
            .alias("sequence")
        )
    else:
        return df


def dump_pg_structure(path: str, structure: Structure) -> None:
    io = PDBIO()
    io.set_structure(structure.value)
    io.save(path)


def is_container() -> bool:
    return os.path.exists("/opt/program")
