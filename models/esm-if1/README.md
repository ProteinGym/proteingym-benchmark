---
# Model identifier used for referencing this model in the benchmark system
name: esm-if1

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.esm_if1.model:ESMIF1"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the evedesign ESMIF1 wrapper
# constructor, so every key here must be a valid ESMIF1.__init__ argument.
# There is only one officially released ESM-IF1 checkpoint size
# (esm_if1_gvp4_t16_142M_UR50, 142M params) - no size-family/sweep logic.
hyper_parameters:
    checkpoint_path: "/opt/program/data/esm_if1_gvp4_t16_142M_UR50.pt"
    # Key into the entity's structures mapping to select a structure, if
    # more than one is present. Leave null (every real ProteinGym2 archive
    # checked so far provides exactly one structure per entity).
    structure_name: null
    device: "cpu"
---

# Model Card for ESM-IF1 (evedesign-wrapped)

This model scores protein sequences with the
`proteingym.models.esm_if1.model.ESMIF1` wrapper around
[ESM-IF1](https://github.com/facebookresearch/esm/blob/main/examples/inverse_folding/README.md)
(ESM Inverse Folding). It is a GVP + transformer encoder-decoder that 
conditions on a fixed backbone structure (N, CA, C coordinates only) and assigns
an autoregressive log-likelihood to a given sequence under that structure.
There is only one officially released ESM-IF1 checkpoint
(`esm_if1_gvp4_t16_142M_UR50`, 142M params), so this wrapper has no
size-family/sweep logic the way ESM2/ESMC-style wrappers do.

**Fixed-length, substitutions only.** `requires_fixed_length=True`,
`handles_deletions=False`, `handles_insertions=False` - the fixed
`(length, 3, 3)` coordinate array built once in `build()` conditions
`target_seq[i]` on `coords[i]` one-to-one; an insertion/deletion would
desynchronize every downstream position's structural context.

**Multichain handling.** Some ProteinGym2 archives attach more than one
chain to a single entity's structure (`entity.structures[key]` is then a list
of `Structure` objects - the same situation `LigandMPNN`'s wrapper in this
package also handles). If exactly one chain is present, this wrapper
conditions on just that chain's own backbone

## Citation

```
@article{hsu2022learning,
  title={Learning inverse folding from millions of predicted structures},
  author={Hsu, Chloe and Verkuil, Robert and Liu, Jason and Lin, Zeming and Hie, Brian and Sercu, Tom and Lerer, Adam and Rives, Alexander},
  journal={bioRxiv},
  year={2022},
  doi={10.1101/2022.04.10.487779}
}
```

## License

This wrapper's own code, and the `fair-esm` package it depends on (upstream:
https://github.com/facebookresearch/esm), are both released under the MIT
license (see `LICENSE`, copied verbatim from the upstream repository). The
ESM-IF1 checkpoint weights are also distributed under MIT
