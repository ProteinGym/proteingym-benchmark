"""Small PyTorch-native subset used by the pinned ProSST structure encoder."""

from __future__ import annotations

import torch


def _expanded_index(index: torch.Tensor, src: torch.Tensor, dim: int) -> torch.Tensor:
    if index.dim() == 1:
        shape = [1] * src.dim()
        shape[dim] = index.numel()
        index = index.view(shape)
    return index.expand_as(src)


def scatter_sum(
    src: torch.Tensor,
    index: torch.Tensor,
    dim: int = -1,
    out: torch.Tensor | None = None,
    dim_size: int | None = None,
) -> torch.Tensor:
    dim %= src.dim()
    index = _expanded_index(index, src, dim)
    if out is None:
        size = list(src.shape)
        size[dim] = dim_size or (int(index.max()) + 1 if index.numel() else 0)
        out = torch.zeros(size, dtype=src.dtype, device=src.device)
    return out.scatter_add_(dim, index, src)


scatter_add = scatter_sum


def scatter_mean(
    src: torch.Tensor,
    index: torch.Tensor,
    dim: int = -1,
    out: torch.Tensor | None = None,
    dim_size: int | None = None,
) -> torch.Tensor:
    result = scatter_sum(src, index, dim=dim, out=out, dim_size=dim_size)
    ones = torch.ones_like(src)
    count = scatter_sum(ones, index, dim=dim, dim_size=result.shape[dim])
    return result / count.clamp_min_(1)


def scatter_max(
    src: torch.Tensor,
    index: torch.Tensor,
    dim: int = -1,
    out: torch.Tensor | None = None,
    dim_size: int | None = None,
) -> tuple[torch.Tensor, torch.Tensor]:
    dim %= src.dim()
    index = _expanded_index(index, src, dim)
    if out is None:
        size = list(src.shape)
        size[dim] = dim_size or (int(index.max()) + 1 if index.numel() else 0)
        out = torch.full(size, -torch.inf, dtype=src.dtype, device=src.device)
    out.scatter_reduce_(dim, index, src, reduce="amax", include_self=True)
    argmax = torch.full(out.shape, -1, dtype=torch.long, device=src.device)
    return out, argmax
