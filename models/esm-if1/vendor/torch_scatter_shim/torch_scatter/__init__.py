"""
Minimal pure-PyTorch stand-in for the real `torch-scatter` package.

Why this exists:
PyPI release of fair-esm (2.0.0) hard-imports torch_scatter at module
level in esm/inverse_folding/gvp_modules.py, even though modern
torch_geometric only treats torch_scatter as optional
and works fine without it. The real torch-scatter package is a compiled 
CUDA/C++ extension distributed only as prebuilt wheels pinned to exact 
(torch version, CUDA version) combinations via PyG's own wheel index 
(https://data.pyg.org/whl/)
"""
from typing import Optional

import torch
from torch import Tensor

__version__ = "0.0.0+esm_if1_shim"


def broadcast(src: Tensor, other: Tensor, dim: int) -> Tensor:
    """
    Broadcast `src` (typically the index tensor) to match `other`'s shape,
    mirroring the real torch_scatter package's own `torch_scatter.utils.
    broadcast` helper: unsqueeze `src` up to `dim`, then unsqueeze trailing
    dims to match `other`'s rank, then expand.
    """
    if dim < 0:
        dim = other.dim() + dim
    if src.dim() == 1:
        for _ in range(0, dim):
            src = src.unsqueeze(0)
    for _ in range(src.dim(), other.dim()):
        src = src.unsqueeze(-1)
    return src.expand(other.size())


def scatter_sum(
    src: Tensor,
    index: Tensor,
    dim: int = -1,
    out: Optional[Tensor] = None,
    dim_size: Optional[int] = None,
) -> Tensor:
    index = broadcast(index, src, dim)
    if out is not None:
        return out.scatter_add_(dim, index, src)

    size = list(src.size())
    if dim_size is not None:
        size[dim] = dim_size
    elif index.numel() == 0:
        size[dim] = 0
    else:
        size[dim] = int(index.max()) + 1
    out = torch.zeros(size, dtype=src.dtype, device=src.device)
    return out.scatter_add_(dim, index, src)


# torch_scatter's own top-level API exposes scatter_add as a direct alias of
# scatter_sum (no separate "add" reduction, same op) - matching that here.
scatter_add = scatter_sum


def scatter_mean(
    src: Tensor,
    index: Tensor,
    dim: int = -1,
    out: Optional[Tensor] = None,
    dim_size: Optional[int] = None,
) -> Tensor:
    out_sum = scatter_sum(src, index, dim, out, dim_size)
    ones = torch.ones_like(src)
    count = scatter_sum(ones, index, dim, None, out_sum.size(dim if dim >= 0 else out_sum.dim() + dim))
    count = count.clamp(min=1)
    return out_sum / count


def scatter(
    src: Tensor,
    index: Tensor,
    dim: int = -1,
    out: Optional[Tensor] = None,
    dim_size: Optional[int] = None,
    reduce: str = "sum",
) -> Tensor:
    if reduce in ("sum", "add"):
        return scatter_sum(src, index, dim, out, dim_size)
    elif reduce == "mean":
        return scatter_mean(src, index, dim, out, dim_size)
    else:
        raise NotImplementedError(
            f"reduce={reduce!r} is not implemented in this minimal torch_scatter "
            "shim (only 'sum'/'add'/'mean' are ever needed by esm.inverse_folding "
            "- see this module's docstring)."
        )


__all__ = ["scatter", "scatter_add", "scatter_sum", "scatter_mean", "broadcast"]
