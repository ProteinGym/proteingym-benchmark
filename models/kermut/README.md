---
# Model identifier used for referencing this model in the benchmark system
name: kermut

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.kermut.model:kermut"

tags: ["supervised"]

# Multi-target support flag
multi_y: false

hyper_parameters:
    # Number of AdamW steps used to maximise the GP marginal likelihood
    n_steps: 150
    # Learning rate of the GP hyperparameter optimisation
    lr: 0.1
    # Standardize training labels before fitting; predictions are mapped back
    # to the original label scale
    standardize: true
    # Random seed set before fitting the GP
    seed: 2024
    # fair-esm model used for the sequence embeddings and the zero-shot mean
    esm_model_name: "esm2_t33_650M_UR50D"
    # Number of sequences per ESM2 forward pass
    batch_size: 16
    # Key into the entity's structures mapping to select a structure, if more
    # than one is present leave null
    structure_name: null
    # "cuda" or "cpu"; falls back to CPU if CUDA is not available
    device: "cuda"
---

# Model Card for Kermut (evedesign-wrapped)

This model scores protein sequences with the
`proteingym.models.kermut.model.kermut` wrapper around
[Kermut](https://github.com/florisvdf/kermut-package), the Gaussian process
reported in
[Kermut: Composite kernel regression for protein variant effects](https://doi.org/10.48550/arXiv.2407.00002).
Kermut combines a structure kernel (ProteinMPNN site/mutation similarity and
CA-distance between mutated sites) with a sequence kernel on ESM2
embeddings, and uses ESM2 zero-shot scores as a linear mean function

The wrapper's `build()` computes the structure artifacts (CA coordinates and
ProteinMPNN conditional probabilities) and the ESM2 masked-marginal
log-probabilities of the reference once, featurizes the labelled training
folds and fits the GP hyperparameters. `score()` featurizes the instances and
returns the posterior predictive mean, on the same scale and orientation as
the training labels

## Architecture notes

- **Pretrained components, GP fitted per dataset.** ESM2 (650M by default,
  downloaded by `fair-esm` on first use) and ProteinMPNN (vendored under
  `software/`) are used frozen; the only learned parameters are the GP
  kernel/mean hyperparameters
- **Substitutions only.** Kermut's kernels compare one-hot sequences
  position-by-position against the reference, so every instance must have
  the target's length (`requires_fixed_length=True`,
  `handles_deletions=False`, `handles_insertions=False`). Sequences are
  limited to the 20 standard amino acids and to ESM2's 1022-residue context
- **Structure required.** A single-chain structure whose residues match the
  reference sequence is required for the structure kernel
- **Reference sequence.** Kermut's kernels require at least one mutation per
  sequence, so the reference itself is scored as a pseudo variant with its
  final residue mutated (same convention as the kermut package)
- **GPU optional.** ESM2 and the GP run on CUDA when `device: "cuda"` and a
  GPU is available, otherwise on CPU. The GP is exact, so memory grows
  quadratically with the number of training variants
- **Standard Kermut only.** The kermut package's preferential (PKermut) mode
  is not exposed by this wrapper; see the `pkermut` model

## Citation

```
@article{Groth2024Kermut,
  author = {Groth, Peter M{\o}rch and Kerrn, Mads Herbert and Olsen, Lars and Salomon, Jesper and Boomsma, Wouter},
  title = {Kermut: Composite kernel regression for protein variant effects},
  journal = {Advances in Neural Information Processing Systems},
  volume = {37},
  year = {2024},
  doi = {10.48550/arXiv.2407.00002}
}
```

## License

Kermut (the `kermut-package` fork, vendored under `software/`) is released
under the MIT license, Copyright (c) 2024 Peter Mørch Groth and Mads Herbert
Kerrn. ProteinMPNN (vendored under `software/`) and ESM2 (`fair-esm`,
depended on directly) are released under the MIT license
