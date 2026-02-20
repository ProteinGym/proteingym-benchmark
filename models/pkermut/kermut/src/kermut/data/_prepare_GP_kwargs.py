from pathlib import Path
from typing import Dict

import numpy as np
import torch

from omegaconf import DictConfig

from ._tokenizer import Tokenizer


def prepare_GP_kwargs(cfg: DictConfig, DMS_id: str, wt_sequence: str, dtype) -> Dict:
    if not cfg.kernel.use_structure_kernel:
        return {}

    if DMS_id == "BRCA2_HUMAN_Erwood_2022_HEK293T":
        cfg.kernel.structure_kernel.use_distance_comparison = False

    inputs = {}
    tokenizer = Tokenizer()
    wt_toks = tokenizer(wt_sequence)
    inputs["wt_sequence"] = wt_toks
    if (
        cfg.kernel.structure_kernel.use_site_comparison
        or cfg.kernel.structure_kernel.use_mutation_comparison
    ):
        conditional_probs = np.load(Path(cfg.data.paths.conditional_probs) / f"{DMS_id}.npy")
        inputs["conditional_probs"] = torch.tensor(
            conditional_probs, dtype=torch.get_default_dtype()
        )
    if cfg.kernel.structure_kernel.use_distance_comparison:
        coords = np.load(Path(cfg.data.paths.coords) / f"{DMS_id}.npy")
        inputs["coords"] = torch.tensor(coords, dtype=torch.get_default_dtype())

    return inputs
