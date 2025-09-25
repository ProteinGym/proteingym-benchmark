from pg2_dataset.dataset import Dataset
from pg2_dataset.backends.assays import SPLIT_STRATEGY_MAPPING
from pg2_dataset.backends import Assays


def load_assays(
    dataset: Dataset,
) -> Assays:
    
    dataset.assays.add_split(
        split_strategy=SPLIT_STRATEGY_MAPPING[
            dataset.assays.meta.split_strategy
        ](),
        targets=list(dataset.assays.meta.assays.keys()),
    )

    return dataset.assays
