import logging

import polars as pl
import numpy as np

from proteingym.base import Dataset

logger = logging.getLogger(__name__)


def prepare_dataframe(dataset: Dataset, test_size: float = 0.2) -> pl.DataFrame:
    """
    Load a dataframe from a dataset archive file for a specified split.

    This function applies the configured split strategy for the dataset,
    and returns the features (X) and targets (Y)
    for the requested data split.

    Args:
        dataset: The dataset object loaded by proteingym.base.Dataset.from_path.
        split: The data split to load (train, validation, or test).

    Returns:
        pl.DataFrame: A dataframe storing the sequence, target and embedding index,
        so that the huggingface-regressor can slice precomputed embeddings using those indices

    Note:
        - Currently only supports single target and single feature column (index 0).
        - The split strategy is determined by the dataset's metadata configuration.
        - Extra features are WIP
    """
    data = pl.DataFrame(
        {
            "sequence": [str(seq.value) for seq, _ in dataset.assays[0].records],
            "target": [target["target"] for _, target in dataset.assays[0].records],
        }
    )
    embedding_indices = np.arange(len(data))
    data = data.with_columns(pl.Series("embedding_index", embedding_indices))

    # TODO: Update split below
    # dataset.assays.add_split(
    #     split_strategy=SPLIT_STRATEGY_MAPPING[dataset.assays.meta.split_strategy](),
    #     targets=targets,
    # )

    # TODO: Replace this dummy split with above split method
    n_test = int(test_size * len(data))
    n_train = len(data) - n_test
    splits = np.concatenate((n_train * ["train"], n_test * ["test"]))
    np.random.shuffle(splits)
    data = data.with_columns(pl.Series("split", splits))

    logger.info("Parsed dataset file into dataframe.")

    return data
