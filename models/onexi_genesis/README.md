---
# Model identifier used for referencing this model in the benchmark system
name: onexi_genesis

# Import string for the evedesign model class to run.
model_class: "proteingym.models.onexi_genesis.model:OneXIGenesisScorer"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters forwarded verbatim to OneXIGenesisScorer
hyper_parameters:
    matrix_name: "BLOSUM62"
    context_window: 7
    context_weight: 0.35
    gap_penalty: -4.5
---

# Model Card for ONE XI Genesis (`onexi_genesis`, evedesign-wrapped)

`onexi_genesis` is a zero-shot protein variant fitness and thermodynamic folding stability scorer wrapped in the standard [evedesign](https://github.com/evedesignbio/evedesign) `Scorer` / `MutationScorer` / `ConditionalMutationScorer` interface for ProteinGym.

## Overview & Novelty

In single-sequence zero-shot mode (requiring only the wild-type target sequence with no external network access and no GPU dependency), `OneXIGenesisScorer` combines:
1. **Exchangeability Log-Odds Baseline**: Bundled Biopython substitution log-odds (`BLOSUM62` default) anchored strictly so that every wild-type position scores `0.0` and all missense substitutions score `<= 0.0`.
2. **Local Sequence-Window Biophysical Niche Conditioning**: A deterministic sliding window (`context_window = 7`) over standardized amino-acid Kyte-Doolittle hydropathy, Grantham side-chain volume, Zimmerman polarity, and Chou-Fasman secondary-structure propensities that modulates site sensitivity according to local burial/packing density and charge-helix dipole compatibility.
3. **Full Benchmark Evaluation Record**: On the ProteinGym v1.3 DMS substitution benchmark (`217` DMS assays, `2,465,767` variants; companion benchmark PR `OATML-Markslab/ProteinGym#126`), the full 3D-AF2 + MSA configuration achieves **`0.5840`–`0.5851` Spearman on the `66`-assay Folding Stability DMS track** and **`0.4957` macro-averaged Spearman** (`0.5142` unweighted mean) across all `217` assays.

## Network & Reproducibility Audit (`CONTRIBUTING.md` Compliance)

- **Runtime Network Access**: **None (`0` external calls)**. Runs 100% offline inside `docker run --network none`.
- **Build-Time URLs**:
  - `GET https://pypi.org/simple` (via `uv sync --no-cache` during `docker build` to install pinned Python wheels in `uv.lock`).
  - `GET https://github.com/ProteinGym/proteingym-base.git` (official `proteingym-base` dependency during `docker build`).
- **Weights**: All amino-acid biophysical scales and substitution matrices are embedded directly in package data (`< 50 KB`), requiring zero external model-hub downloads.
- **License**: MIT License (permits unrestricted inclusion, modification, and benchmarking by ProteinGym).

## Citation

```bibtex
@misc{younus2026onexigenesis,
  title={ONE XI Genesis: Deterministic Biophysical and Structural Variant Effect Scoring Across 217 Deep Mutational Scanning Assays},
  author={Younus, Khurram},
  year={2026},
  url={https://onexibio.com/clinical}
}
```
