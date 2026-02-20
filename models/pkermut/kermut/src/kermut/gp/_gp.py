import hydra
import torch
from typing import List, Tuple

from gpytorch.models import ExactGP
from gpytorch.means import LinearMean
from omegaconf import DictConfig

from kermut.kernels import CompositeKernel

from botorch.models.pairwise_gp import (
    PairwiseGP,
    _ensure_psd_with_jitter,
    _scaled_psd_safe_cholesky,
)

from gpytorch.distributions.multivariate_normal import MultivariateNormal
from gpytorch.means.constant_mean import ConstantMean
from gpytorch import settings
from torch import Tensor

from linear_operator.operators import LinearOperator, RootLinearOperator


class KermutGP(ExactGP):
    """Gaussian Process regression model for supervised variant effects predictions.

    A specialized Gaussian Process implementation that combines sequence and structural
    information for predicting the effects of protein mutations. It extends gpytorch's
    ExactGP class and supports both composite and single kernel architectures, as well
    as zero-shot prediction capabilities through its mean function.

    Args:
        train_inputs: Training input data for the GP model. Default expects tuple of
            (one-hot sequences, sequence_embeddings, zero-shot scores).
        train_targets: Target values corresponding to the training inputs.
        likelihood: Gaussian likelihood function for the GP model.
        kernel_cfg (DictConfig): Configuration dictionary for kernel specifications,
            containing settings for sequence_kernel and structure_kernel if composite
            is True, or a single kernel configuration if composite is False.
        use_zero_shot_mean (bool, optional): Whether to use a linear mean function
            for zero-shot predictions. If True, uses LinearMean; if False, uses
            ConstantMean. Defaults to True.
        composite (bool, optional): Whether to use a composite kernel combining
            sequence and structure information. If False, uses a single kernel
            specified in kernel_cfg. Defaults to True.
        **kwargs: Additional keyword arguments passed to the kernel initialization.

    Attributes:
        covar_module: The kernel (covariance) function, either a CompositeKernel
            or a single kernel as specified by kernel_cfg.
        mean_module: The mean function, either LinearMean for zero-shot predictions
            or ConstantMean for standard GP regression.
        use_zero_shot_mean (bool): Flag indicating whether zero-shot mean function
            is being used.
    """

    def __init__(
        self,
        train_inputs,
        train_targets,
        likelihood,
        kernel_cfg: DictConfig,
        use_zero_shot_mean: bool = True,
        composite: bool = True,
        **kwargs,
    ):
        super().__init__(train_inputs, train_targets, likelihood)
        if composite:
            self.covar_module = CompositeKernel(
                sequence_kernel=kernel_cfg.sequence_kernel,
                structure_kernel=kernel_cfg.structure_kernel,
                **kwargs,
            )
        else:
            self.covar_module = hydra.utils.instantiate(kernel_cfg.kernel, **kwargs)

        self.use_zero_shot_mean = use_zero_shot_mean
        if self.use_zero_shot_mean:
            self.mean_module = LinearMean(input_size=1, bias=True)
        else:
            self.mean_module = ConstantMean()

    def forward(self, x_toks, x_embed, x_zero=None) -> MultivariateNormal:
        if x_zero is None:
            x_zero = x_toks
        mean_x = self.mean_module(x_zero)
        covar_x = self.covar_module((x_toks, x_embed))
        return MultivariateNormal(mean_x, covar_x)


class PKermutGP(PairwiseGP):
    """Minimally modified version of the class above to make the GP preferential.
    A number of the PairwiseGP methods are overwritten conform with Kermut's composite kernel
    and to implement the linear mean module."""
    def __init__(
        self,
        train_inputs,
        train_targets,
        input_shapes: List[Tuple],
        kernel_cfg: DictConfig,
        use_zero_shot_mean: bool = True,
        composite: bool = True,
        device: str = "cpu",
        **kwargs,
    ) -> None:
        self.input_shapes = input_shapes
        self.device = device
        super().__init__(train_inputs, train_targets)
        if composite:
            self.covar_module = CompositeKernel(
                sequence_kernel=kernel_cfg.sequence_kernel,
                structure_kernel=kernel_cfg.structure_kernel,
                **kwargs,
            )
        else:
            self.covar_module = hydra.utils.instantiate(kernel_cfg.kernel, **kwargs)

        self.use_zero_shot_mean = use_zero_shot_mean
        self.mean_module = LinearMean(input_size=1, bias=False)
        # Overwrite mean module
        if self.use_zero_shot_mean:
            for param in self.mean_module.parameters():
                param.requires_grad = True
        else:
            self.mean_module = ConstantMean()

    def _calc_covar(self, X1: Tensor, X2: Tensor) -> Tensor | LinearOperator:
        # Return spherical covar (unit variance, zero covar) when covar module not initialized yet
        if self.covar_module.__class__.__name__ != "CompositeKernel":
            return torch.eye(X1.shape[0], device=self.device, dtype=torch.get_default_dtype())
        X1_split = self.reconstruct_input_tuple(X1)[
            :2
        ]  # only first two for now since zeroshot is ignored
        X2_split = self.reconstruct_input_tuple(X2)[:2]
        covar = self.covar_module(X1_split, X2_split).to_dense()
        # making sure covar is PSD when it's a covariance matrix
        if X1 is X2:
            covar = _ensure_psd_with_jitter(
                matrix=covar,
                jitter=self._jitter,
            )
        return covar

    def _update_covar(self, datapoints: Tensor) -> None:
        # Overwriting because of output scale
        self.covar = self._calc_covar(datapoints, datapoints)
        self.covar_chol = _scaled_psd_safe_cholesky(
            matrix=self.covar,
            scale=torch.tensor(1, device=self.device),
            jitter=self._jitter,
        )
        self.covar_inv = torch.cholesky_inverse(self.covar_chol)

    def _prior_mean(self, X: Tensor) -> Tensor | LinearOperator:
        # Overwriting to use zeroshot linear mean
        zeroshot_score = self.reconstruct_input_tuple(X)[-1]
        # This method is called in fsolve, which has to be performed on cpu
        if not X.is_cuda and self.device == "cuda":
            result = self.mean_module(zeroshot_score.to("cuda")).to("cpu")
        else:
            result = self.mean_module(zeroshot_score)
        return result

    def forward(self, datapoints: Tensor) -> MultivariateNormal:
        # Overwriting forward method since kermut kernel has no output scale parameter.
        # Original method uses scale to scale the jitter
        if self.training:
            if self._has_no_data():
                raise RuntimeError(
                    "datapoints and comparisons cannot be None in training mode. "
                    "Call .eval() for prior predictions, "
                    "or call .set_train_data() to add training data."
                )

            if datapoints is not self.unconsolidated_datapoints:
                raise RuntimeError("Must train on training data")

            self.set_train_data(
                datapoints=datapoints,
                comparisons=self.unconsolidated_comparisons,
                update_model=True,
            )

            hl = self.likelihood_hess
            covar = self.covar
            hl_cov = hl @ covar
            eye = torch.eye(
                hl_cov.size(-1),
                dtype=self.datapoints.dtype,
                device=self.datapoints.device,
            ).expand(hl_cov.shape)
            hl_cov_I = hl_cov + eye  # add I to hl_cov
            output_covar = covar - covar @ torch.linalg.solve(hl_cov_I, hl_cov)
            output_mean = self.utility

        # Prior mode
        elif settings.prior_mode.on() or self._has_no_data():
            transformed_new_dp = self.transform_inputs(datapoints)
            output_mean, output_covar = self._prior_predict(transformed_new_dp)

        else:
            transformed_dp = self.transform_inputs(self.datapoints)
            transformed_new_dp = self.transform_inputs(datapoints).to(transformed_dp)

            if self.utility is None:
                self._update(transformed_dp)

            if self.pred_cov_fac_need_update:
                self._update_utility_derived_values()

            X, X_new = self._transform_batch_shape(transformed_dp, transformed_new_dp)
            covar_chol, _ = self._transform_batch_shape(self.covar_chol, X_new)
            hl, _ = self._transform_batch_shape(self.likelihood_hess, X_new)
            hlcov_eye, _ = self._transform_batch_shape(self.hlcov_eye, X_new)

            covar_xnew_x = self._calc_covar(X_new, X)
            covar_x_xnew = covar_xnew_x.transpose(-1, -2)
            covar_xnew = self._calc_covar(X_new, X_new)
            p = self.utility - self._prior_mean(X)

            covar_inv_p = torch.cholesky_solve(p.unsqueeze(-1), covar_chol)
            pred_mean = (covar_xnew_x @ covar_inv_p).squeeze(-1)
            pred_mean = pred_mean + self._prior_mean(X_new)

            fac = torch.linalg.solve(hlcov_eye, hl @ covar_x_xnew)
            pred_covar = covar_xnew - (covar_xnew_x @ fac)

            output_mean, output_covar = pred_mean, pred_covar

        post = MultivariateNormal(
            mean=output_mean,
            covariance_matrix=RootLinearOperator(
                _scaled_psd_safe_cholesky(
                    matrix=output_covar,
                    scale=torch.tensor(1, device=self.device),
                    jitter=self._jitter,
                )
            ),
        )
        return post

    def reconstruct_input_tuple(self, concatenated_inputs: torch.Tensor):
        arrays = []
        idx = 0
        for shape in self.input_shapes:
            input_dim = shape[1]
            slc = [slice(None)] * concatenated_inputs.ndim
            slc[1] = slice(idx, idx + input_dim)
            arrays.append(concatenated_inputs[tuple(slc)])
            idx += input_dim
        return tuple(arrays)
