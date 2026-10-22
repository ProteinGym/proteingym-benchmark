---
# Model identifier used for referencing this model in the benchmark system
name: blosum62

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored
model_class: "proteingym.models.blosum62.model:SubstitutionMatrixScorer"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the wrapper constructor
# (proteingym.models.blosum62.model.SubstitutionMatrixScorer)
hyper_parameters:
    # Substitution matrix name, any provided by Biopython's
    # substitution_matrices.load() (e.g. BLOSUM62, BLOSUM80, PAM250)
    matrix_name: "BLOSUM62"
    # Score added per deleted position, relative to the wild-type residue
    # (-4 is BLASTP's BLOSUM62 gap-open cost)
    gap_penalty: -4.0
---

# Model Card for BLOSUM62 (evedesign-wrapped)

This is a trivial, untrained baseline that scores variants with a fixed
amino-acid substitution matrix (BLOSUM62 by default, read from Biopython).
It needs only the target sequence: no MSA, no structure and no weights.
Each position contributes the matrix score of the variant residue relative to
the wild-type residue, summed over the sequence, so the wild type scores 0.
Scores deletions (a fixed `gap_penalty` per deleted position); insertions are
not scored.

## Citation

```
@article{henikoff1992blosum,
  title={Amino acid substitution matrices from protein blocks},
  author={Henikoff, Steven and Henikoff, Jorja G.},
  journal={Proceedings of the National Academy of Sciences},
  volume={89}, number={22}, pages={10915--10919}, year={1992},
  doi={10.1073/pnas.89.22.10915}
}
```

## License

The wrapper code is MIT (see `LICENSE`). The substitution matrices are NCBI
data files distributed with Biopython (Biopython License Agreement); no model
weights are involved.
