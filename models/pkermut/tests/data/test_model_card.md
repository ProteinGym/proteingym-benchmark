---
# Model identifier used for referencing this model in the benchmark system
name: pkermut

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.pkermut.model:pkermut"

tags: ["supervised"]

# Multi-target support flag
multi_y: false

hyper_parameters:
    # Number of AdamW steps used to maximise the GP marginal likelihood
    n_steps: 2
    # Learning rate of the GP hyperparameter optimisation
    lr: 0.1
    # True -> train in preferential mode (PKermut), False -> train the original
    # version of Kermut on the labels (use Kermut for this)
    preferential: true
    # Average degree to which to uniformly subsample the preference graph when
    # training in preferential mode. In this case 2.5
    preference_sampling_strategy: "uniform_2.5"
    # Standardize training labels before fitting (preferential: false only)
    standardize: true
    # Random seed set before sampling preferences and fitting the GP
    seed: 2024
    # fair-esm model used for the sequence embeddings and the zero-shot mean
    esm_model_name: "esm2_t6_8M_UR50D"
    # Number of sequences per ESM2 forward pass
    batch_size: 16
    # Key into the entity's structures mapping to select a structure, if more
    # than one is present leave null
    structure_name: null
    # "cuda" or "cpu"; falls back to CPU if CUDA is not available
    device: "cpu"
---

# Model Card for PKermut (evedesign-wrapped)

This model scores protein sequences with the
`proteingym.models.pkermut.model.pkermut` wrapper around PKermut, reported in
[Acquiring Improved Protein Variants With Probabilistic Preferential Learning](https://doi.org/10.64898/2026.06.22.733688),
a preferential version of the Kermut Gaussian process reported in
[Kermut: Composite kernel regression for protein variant effects](https://doi.org/10.48550/arXiv.2407.00002).
The implementation is a [fork](https://github.com/florisvdf/kermut-package) of
the [original repository](https://github.com/petergroth/kermut) that adds a
[preferential objective](https://botorch.readthedocs.io/en/latest/models.html#module-botorch.models.pairwise_gp)
([Preference learning with Gaussian processes](https://doi.org/10.1145/1102351.1102369)):
instead of regressing the labels, the GP is fitted to pairwise comparisons
("variant A scores higher than variant B") sampled from the training labels

The wrapper's `build()` computes the structure artifacts (CA coordinates and
ProteinMPNN conditional probabilities) and the ESM2 masked-marginal
log-probabilities of the reference once, featurizes the labelled training
folds, samples preference pairs from their labels and fits the GP. `score()`
featurizes the instances and returns the posterior mean of the latent
utility: higher scores mean more preferred (higher training labels), but the
scores are **not** on the label scale. With `preferential: false` the model
falls back to standard Kermut and returns predictions on the label scale

## Architecture notes

- **Pretrained components, GP fitted per dataset.** ESM2 (650M by default,
  downloaded by `fair-esm` on first use) and ProteinMPNN (vendored under
  `software/`) are used frozen; the only learned parameters are the GP
  kernel/mean hyperparameters
- **Same kernel and mean as Kermut.** A structure kernel (ProteinMPNN
  site/mutation similarity and CA-distance between mutated sites) combined
  with a sequence kernel on ESM2 embeddings, and ESM2 zero-shot scores as a
  linear mean function
- **Preference sampling.** `uniform_{d}` samples `d x n_train` pairs
  uniformly from all ordered pairs of training variants; kermut's
  `top_vs_all_{k}` and `all` samplers are also accepted. The `batch` sampler
  needs per-variant batch labels and is not supported
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
  GPU is available, otherwise on CPU. The preferential GP runs in double
  precision and its cost grows with both the number of training variants and
  the number of sampled pairs

## Citation

```
@article{vanderFlier2026PKermut,
  author = {van der Flier, Floris J. and de Ridder, Dick and Probst, Daniel and Redestig, Henning},
  title = {Acquiring Improved Protein Variants With Probabilistic Preferential Learning},
  journal = {bioRxiv},
  year = {2026},
  doi = {10.64898/2026.06.22.733688}
}

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
