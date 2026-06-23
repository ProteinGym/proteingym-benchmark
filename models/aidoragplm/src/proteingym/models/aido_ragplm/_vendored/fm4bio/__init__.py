"""Vendored FM4Bio model and tokenizer (genbio-ai/ModelGenerator @ c562a20).

These files are copied verbatim from
``modelgenerator/huggingface_models/fm4bio/`` at commit
``c562a20b2bb45e10655ac1de1ff48f7c8cc015e3``. They import only ``torch``,
``transformers`` and each other and are sufficient to load and run inference
with the ``genbio-ai/AIDO.Protein-RAG-3B`` checkpoint without installing the
full ModelGenerator package.

One local patch lives in ``modeling_fm4bio.py`` (marked ``# PATCH
(AIDO_RAGPLM):``) so ``RotaryEmbedding.inv_freq`` is a non-persistent buffer
instead of a CUDA-pinned attribute — this is required for non-CUDA devices
(MPS, CPU-only) and is behavior-preserving on CUDA.
"""

from proteingym.models.aido_ragplm._vendored.fm4bio.configuration_fm4bio import (
    FM4BioConfig,
)
from proteingym.models.aido_ragplm._vendored.fm4bio.modeling_fm4bio import (
    FM4BioForMaskedLM,
    FM4BioModel,
)
from proteingym.models.aido_ragplm._vendored.fm4bio.tokenization_fm4bio import (
    FM4BioTokenizer,
)

__all__ = [
    "FM4BioConfig",
    "FM4BioForMaskedLM",
    "FM4BioModel",
    "FM4BioTokenizer",
]
