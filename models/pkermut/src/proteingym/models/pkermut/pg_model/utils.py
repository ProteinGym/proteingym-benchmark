import shutil
import os

import pandas as pd
import yaml
import json
from pathlib import Path
from loguru import logger

import numpy as np
import polars as pl
from scipy.stats import spearmanr
from sklearn.metrics import r2_score
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


def prepare_hydra_configs(
    hydra_src_config_path: str, hydra_dest_config_path: str, params_to_update: dict
) -> None:
    shutil.copytree(hydra_src_config_path, hydra_dest_config_path, dirs_exist_ok=True)
    with open(Path(hydra_dest_config_path) / "data/paths.yaml", "r") as fp:
        hydra_paths_config = yaml.safe_load(fp)
    with open(Path(hydra_dest_config_path) / "data/dataset.yaml", "r") as fp:
        hydra_datasets_config = yaml.safe_load(fp)
    with open(Path(hydra_dest_config_path) / "kernel/kermut.yaml", "r") as fp:
        hydra_kernel_config = yaml.safe_load(fp)
    with open(Path(hydra_dest_config_path) / "benchmark.yaml", "r") as fp:
        hydra_benchmark_config = yaml.safe_load(fp)

    hydra_paths_config["data_dir"] = params_to_update["data_artifact_path"]
    hydra_paths_config["sequence_col"] = "sequence"
    hydra_paths_config["paths"]["embeddings"] = params_to_update["embedding_path"]
    hydra_paths_config["paths"]["conditional_probs"] = params_to_update[
        "conditional_probs_path"
    ]
    hydra_paths_config["paths"]["DMS_input_folder"] = params_to_update[
        "DMS_input_folder"
    ]
    hydra_paths_config["paths"]["output_folder"] = params_to_update["output_path"]
    hydra_paths_config["target_col"] = params_to_update["target"]

    # WIP: Setting other Kermut params (types of kernels, etc)
    hydra_benchmark_config["optim"]["n_steps"] = params_to_update["n_steps"]
    hydra_benchmark_config["DMS_id"] = params_to_update["dataset_name"]
    hydra_benchmark_config["target_seq"] = params_to_update["reference_sequence"]
    hydra_benchmark_config["use_gpu"] = params_to_update["use_gpu"]
    hydra_benchmark_config["preferential"] = params_to_update["preferential"]
    hydra_benchmark_config["preference_sampling_strategy"] = params_to_update[
        "preference_sampling_strategy"
    ]

    hydra_kernel_config["structure_kernel"]["_target_"] = (
        "proteingym.models.pkermut.kermut.kernels.StructureKernel"
    )
    hydra_kernel_config["sequence_kernel"]["_target_"] = (
        "proteingym.models.pkermut.kermut.kernels.SequenceKernel"
    )

    with open(Path(hydra_dest_config_path) / "data/paths.yaml", "w") as fp:
        yaml.dump(hydra_paths_config, fp)
    with open(Path(hydra_dest_config_path) / "data/dataset.yaml", "w") as fp:
        yaml.dump(hydra_datasets_config, fp)
    with open(Path(hydra_dest_config_path) / "kernel/kermut.yaml", "w") as fp:
        yaml.dump(hydra_kernel_config, fp)
    with open(Path(hydra_dest_config_path) / "benchmark.yaml", "w") as fp:
        yaml.dump(hydra_benchmark_config, fp)


def mse(y_true: np.ndarray, y_pred: np.ndarray) -> np.ndarray:
    return np.mean(np.square((y_true - y_pred)))


def r_square(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    try:
        return r2_score(y_true=y_true, y_pred=y_pred)
    except ValueError:
        return np.nan


def spearman(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    if len(np.unique(y_true)) <= 1 or len(np.unique(y_pred)) <= 1:
        return np.float64(0.0)
    return spearmanr(y_true, y_pred).correlation


def log_and_save_metrics(results: pd.DataFrame, output_dir: str) -> None:
    metrics = {
        "mse": mse,
        "r_square": r_square,
        "spearman": spearman,
    }
    metric_stats = {}
    for split in results["split"].unique():
        split_df = results.filter(pl.col("split") == split)
        for metric_name, metric in metrics.items():
            value = metric(split_df["y"].to_numpy(), split_df["y_pred"].to_numpy())
            metric_stats[f"{split}_{metric_name}"] = value
            logger.info(f"{split}_{metric_name}: {value:.4f}")
    with open(Path(output_dir) / "metrics.json", "w") as fh:
        json.dump(metric_stats, fh)


def variant_sequence_to_mutations(variant: str, reference: str) -> str:
    return ":".join(
        [
            f"{aa_ref}{pos + 1}{aa_var}"
            for pos, (aa_ref, aa_var) in enumerate(zip(reference, variant))
            if aa_ref != aa_var
        ]
    )


def is_container() -> bool:
    return os.path.exists("/opt/program")
