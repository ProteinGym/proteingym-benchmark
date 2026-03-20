import os

from loguru import logger

import numpy as np
import polars as pl
from Bio.PDB.PDBIO import PDBIO

from proteingym.base import Dataset
from proteingym.base.structure import Structure


def prepare_dataframe(dataset: Dataset, test_size: float = 0.2) -> pl.DataFrame:
    # Dummy function for loading data. Should be removed with new version of proteingym-base
    data = pl.DataFrame(
        {
            "sequence": [str(seq.value) for seq, _ in dataset.assays[0].records],
            "target": [
                target[dataset.assay_targets[0].name]
                for _, target in dataset.assays[0].records
            ],
        }
    )
    embedding_indices = np.arange(len(data))
    data = data.with_columns(pl.Series("embedding_index", embedding_indices))

    n_test = int(test_size * len(data))
    n_train = len(data) - n_test
    splits = np.concatenate((n_train * ["train"], n_test * ["test"]))
    np.random.shuffle(splits)
    data = data.with_columns(pl.Series("split", splits))

    logger.info("Parsed dataset file into dataframe.")

    return data


def dump_pg_structure(path: str, structure: Structure) -> None:
    io = PDBIO()
    io.set_structure(structure.value)
    io.save(path)


def is_container() -> bool:
    return os.path.exists("/opt/program")
