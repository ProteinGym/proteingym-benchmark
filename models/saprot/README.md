---
# Model identifier used for referencing this model in the benchmark system
name: saprot

# Import string for the evedesign model class to run. The class is
# instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.saprot.model:SaProt"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to SaProt.__init__
hyper_parameters:
    # HF checkpoint (local copy of westlake-repl/SaProt_35M_AF2, fetched at
    # build time); other SaProt checkpoints also work
    model_name: "/opt/program/data/SaProt_35M_AF2"
    # Foldseek binary used to compute 3Di structure tokens
    foldseek_path: "/opt/program/bin/foldseek"
    # Key into entity.structures; null = use the only structure present
    structure_name: null
    # Give target positions the structure doesn't cover the masked "#" 3Di
    # token instead of failing
    allow_missing_structure: true
    # Number of masked sequences per forward pass
    batch_size: 8
    # Device to run the model on ("cpu" or "cuda"; null = cuda if available)
    device: "cpu"
---

# Model Card for SaProt (evedesign-wrapped)

SaProt is a masked protein language model with the ESM-2 architecture and a
structure-aware vocabulary: each token combines an amino acid with that
residue's Foldseek 3Di structural state. It needs the target sequence and a 3D
structure (3Di tokens are computed with Foldseek); no MSA is used. A variant
is scored by masking the amino-acid half of its mutated positions and summing
`log P(mutant) - log P(wild type)` over them. Unlike the published ProteinGym
protocol, low-pLDDT residues are not masked, since archives carry no
per-residue pLDDT. Substitutions only; insertions and deletions are not
scored.

For details on the architecture and training data, see the
[paper](https://doi.org/10.1101/2023.10.01.560349).

## Citation

```
@inproceedings{su2024saprot,
    title={{SaProt}: Protein Language Modeling with Structure-aware Vocabulary},
    author={Jin Su and Chenchen Han and Yuyang Zhou and Junjie Shan and Xibin Zhou and Fajie Yuan},
    booktitle={The Twelfth International Conference on Learning Representations},
    year={2024}
}
```

## License

SaProt's code and the `westlake-repl/SaProt_*` checkpoints are released under
the **MIT license** - see `LICENSE`. Foldseek, included as a separate
executable, is GPL-3.0.
