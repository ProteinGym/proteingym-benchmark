---
# Model identifier used for referencing this model in the benchmark system
name: poet2

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.poet2.model:PoET2"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to PoET2.__init__
hyper_parameters:
    # Path to the PoET-2 checkpoint, baked into the image at build time
    ckpt_path: "/opt/program/data/poet-2.ckpt"
    # Token budget for the MSA prompt. More context helps, but attention is
    # quadratic in this without flash-attn, so this is the plateau rather than
    # the maximum -- see "Context budget" below
    max_tokens: 4096
    # Drop MSA sequences more similar than this to the target before sampling
    # (1.0 keeps all)
    max_similarity: 1.0
    # Identity threshold for the homology reweighting that biases prompt
    # sampling towards diverse sequences
    theta: 0.2
    # Number of variants scored per forward pass against the prompt
    batch_size: 8
    # Device to run the model on ("cpu" or "cuda")
    device: "cpu"
    # Seed for prompt sampling, so a run is reproducible
    seed: 0
---

# Model Card for PoET-2 (evedesign-wrapped)

This model scores protein sequences with
[PoET-2](https://github.com/OpenProteinAI/PoET-2), a retrieval-augmented
protein language model that conditions on a prompt assembled from the target's
MSA. Prompt sequences are drawn by homology-reweighted sampling and packed up to
a token budget; each variant is then scored as
`log P(variant | prompt) - log P(wildtype | prompt)`, so scoring is zero-shot
and needs no labelled fold.

Reps are normalized (gaps dropped, insertions uppercased) before encoding, so
indels are scored as the actual shorter or longer sequence.

For architecture and training details see the
[accompanying paper](https://doi.org/10.48550/arXiv.2508.04724) (182M
parameters; 12 layers, 1024 hidden).

## Context budget

More prompt context helps, with diminishing returns, but attention is quadratic
in `max_tokens` without upstream's flash-attention fork. Measured on one
465-residue target, 100 variants, CPU:

| `max_tokens` | prompt seqs | HXK4 Spearman | peak RSS | s/variant |
|---|---|---|---|---|
| 2048 | 4 | +0.466 | 3.6 GB | 0.59 |
| 4096 | 9 | +0.512 | 4.6 GB | 0.79 |
| 8192 | 20 | +0.521 | 11.1 GB | 1.20 |
| 16384 | 40 | +0.489 | 35.9 GB | 1.84 |

The curve peaks in the middle: 4096 is the default because 8192 buys +0.009 --
noise on a single assay -- for 2.4x the memory, which would make this the
heaviest model in the zoo, and 16384 is both worse and unusable in CI. So this
is near the measured optimum rather than a memory-forced compromise, and even a
GPU run has little reason to exceed 8192.

## Licence

The inference code is Apache-2.0, but **the weights are not**: they are released
under the PoET Non-Commercial License Agreement, which incorporates
[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/) and
restricts use to non-commercial internal research and education. The licence
names **benchmarking and performance evaluation as permitted uses**, which is
what this model is here for. Commercial use — including incorporation into a
commercial product, providing services for a fee, and *sponsored research* —
requires a separate written licence from NE47 Bio, Inc. No patent rights are
granted. The licence is baked into the image at `/opt/program/data/MODEL_LICENSE`
alongside the checkpoint. The existing [poet](../poet/) model ships
academic-only weights on comparable terms.

## Build-time network access

| Method | URL | Why |
|---|---|---|
| GET | `https://zenodo.org/records/16815645/files/poet-2.ckpt?download=1` | the 400 MB checkpoint |
| GET | `https://zenodo.org/records/16815645/files/MODEL_LICENSE?download=1` | its licence, shipped with it |

## Deviations from upstream

Upstream requires Python 3.10 and a custom fork of flash-attention published
only as a `cp310`/cu118 wheel. Neither fits this zoo, which runs Python 3.12,
nor CI, which has no GPU. Two changes make it work:

- `poet_2` is vendored and shipped as a top-level package rather than installed,
  so upstream's `requires-python = ">=3.10,<3.11"` does not apply. Its internal
  imports are absolute, hence top-level rather than nested.
- One `xformers` import is moved into the branch that uses it.
  `_maybe_fused_swiglu_apply_feedforward` imported `xformers.ops` *above* its own
  `if not use_fused_swiglu: return _swiglu_apply_feedforward(...)` early return,
  so the import ran even when the fused path was not taken. Since
  `use_fused_swiglu` is itself `XFORMERS_MODULE_INSTALLED and ...`, the fused
  branch is unreachable without xformers, and upstream's unfused path is
  correct — the import was simply misplaced. No behaviour changes; adding
  xformers as a dependency would have bloated the image with a CUDA-oriented
  package for a branch that never runs.
- Its four unconditional `flash_attn` imports are wrapped in a `try`/`except`
  falling back to `src/poet_2/models/_flash_compat.py`. None is needed to score:
  two are pure-torch packing helpers, one is einops' `rearrange`, and the
  generation-only sampler raises if called. Attention needs no shim — upstream
  already returns False from `is_flash_attn_avail` when the module is missing or
  the tensors are not on CUDA, and falls back to standard attention.

`MSASampler` keeps upstream's `force_include_first=False`. This is worth
stating because the obvious choice is wrong: forcing the target in as
`prompt[0]` lets the model copy it, so `log P(wildtype | prompt)` collapses from
about -930 to about -10 for a 465-residue target. Every variant score is a delta
against that term, so the target must not appear in its own prompt -- on HXK4 it
cost 0.06 Spearman at 2048 tokens and 0.19 at 8192, and it inverted the
context-scaling: scores fell as homologs were added instead of rising. A prompt
of the target alone scores +0.297, worse than any real-homolog prompt.

Unlike [poet](../poet/), PoET-2 therefore runs on CPU: upstream selects float32
when the device is CPU, and the similarity calculation's `.cuda()` path is
reached only when `can_use_torch` is set, which this wrapper ties to the device.

The prompt is sequence-only. PoET-2 is multimodal and accepts per-prompt-sequence
structure, but ProteinGym datasets carry a structure for the target rather than
for its MSA, so the structure-conditioned prompt is not exercised here.
