from pathlib import Path

import hydra
import numpy as np
import torch
from omegaconf import DictConfig


from kermut.data import (
    prepare_GP_inputs,
    prepare_GP_kwargs,
    split_inputs,
    standardize,
)
from kermut.gp import instantiate_gp, optimize_gp, predict
from pg_model.constants import HYDRA_TEMP_CONFIG_PATH
from pg_model.pair_sampling import pair_sampling_factory


def _evaluate_dms(cfg: DictConfig) -> None:
    DMS_id = cfg.DMS_id
    target_seq = cfg.target_seq
    device = "cuda" if cfg.use_gpu and torch.cuda.is_available() else "cpu"
    df, y, x_toks, x_embed, x_zero_shot = prepare_GP_inputs(
        cfg, DMS_id
    )  # Set dtype to make it nice
    gp_inputs = prepare_GP_kwargs(
        cfg, DMS_id, target_seq, dtype=torch.get_default_dtype()
    )

    df_out = df.copy()
    df_out = df_out.assign(fold=np.nan, y=np.nan, y_pred=np.nan, y_var=np.nan)

    test_fold = (
        -1
    )  # Predict needs test fold, only for fold tracking in results, not necessary here
    torch.manual_seed(cfg.seed)
    np.random.seed(cfg.seed)

    train_idx = (df["split"] == "train").tolist()
    test_idx = (df["split"] == "test").tolist()

    y_train, y_test = split_inputs(train_idx, test_idx, y)
    y_train, y_test = (
        standardize(y_train, y_test) if cfg.data.standardize else (y_train, y_test)
    )

    x_toks_train, x_toks_test = split_inputs(train_idx, test_idx, x_toks)
    x_embed_train, x_embed_test = split_inputs(train_idx, test_idx, x_embed)
    x_zero_shot_train, x_zero_shot_test = split_inputs(train_idx, test_idx, x_zero_shot)

    train_inputs = (x_toks_train, x_embed_train, x_zero_shot_train)
    test_inputs = (x_toks_test, x_embed_test, x_zero_shot_test)
    train_targets = y_train
    test_targets = y_test

    if cfg.preferential:
        torch.set_default_dtype(torch.float64)
        train_inputs = tuple(
            [x.to(device=device, dtype=torch.get_default_dtype()) for x in train_inputs]
        )
        test_inputs = tuple(
            [x.to(device=device, dtype=torch.get_default_dtype()) for x in test_inputs]
        )
        sampler = pair_sampling_factory(
            cfg.preference_sampling_strategy,
            split=df.get("split"),
            batch_labels=df.get("batch_label"),
        )
        train_targets = torch.tensor(
            sampler.sample(train_targets.cpu().numpy()), device=device
        )

    gp, likelihood = instantiate_gp(
        cfg=cfg,
        train_inputs=train_inputs,
        train_targets=train_targets,
        gp_inputs=gp_inputs,
    )

    gp, likelihood = optimize_gp(
        gp=gp,
        likelihood=likelihood,
        train_inputs=train_inputs,
        train_targets=train_targets,
        lr=cfg.optim.lr,
        n_steps=cfg.optim.n_steps,
        progress_bar=cfg.optim.progress_bar,
        preferential=cfg.preferential,
    )

    test_df_out = predict(
        gp=gp,
        likelihood=likelihood,
        test_inputs=test_inputs,
        test_targets=test_targets,
        test_fold=test_fold,
        test_idx=test_idx,
        df_out=df_out,
        preferential=cfg.preferential,
    )

    train_df_out = predict(
        gp=gp,
        likelihood=likelihood,
        test_inputs=train_inputs,
        test_targets=y_train,  # Make sure to not accidentally set preferences
        test_fold=test_fold,
        test_idx=train_idx,
        df_out=df_out,
        preferential=cfg.preferential,
        training_data=True,
    )
    df_out = train_df_out.combine_first(test_df_out)

    out_path = Path(cfg.data.paths.output_folder) / f"{DMS_id}.csv"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    df_out.to_csv(out_path, index=False)


@hydra.main(
    version_base=None,
    config_path=str(HYDRA_TEMP_CONFIG_PATH),
    config_name="benchmark",
)
def main(cfg: DictConfig) -> None:
    _evaluate_dms(cfg)


if __name__ == "__main__":
    main()
