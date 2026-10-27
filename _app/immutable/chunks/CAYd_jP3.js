const e=`---
# Model identifier used for referencing this model in the benchmark system
name: pottsmpnn

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.pottsmpnn.model:PottsMPNN"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Forwarded verbatim to PottsMPNN.__init__
hyper_parameters:
    # Checkpoint file; default is upstream's recommended vanilla 0.2A+MSA model.
    # Also shipped: vanilla pottsmpnn_{20,30}.pt, soluble sol_pottsmpnn_{20,30,msa_20}.pt
    checkpoint_path: "/opt/program/data/vanilla_model_weights/pottsmpnn_msa_20.pt"
    # Key into entity.structures; null = use the only structure present
    structure_name: null
    # Token budget per energy batch (upstream inference.max_tokens)
    max_tokens: 20000
    # "cpu" or "cuda"; null = cuda if available, else cpu
    device: "cpu"
---

# Model Card for PottsMPNN (evedesign-wrapped)

PottsMPNN is an inverse-folding GNN with the ProteinMPNN architecture whose encoder outputs a
structure-conditioned Potts model (per-residue fields plus pairwise couplings to each residue's
48 nearest neighbours). It needs a structure (\`entity.structures\`) and no MSA at inference.
Scoring is zero-shot: the score is the energy difference E(wild type) - E(variant) computed from
the Potts energy table of the wild-type structure, so higher scores mean fitter variants.
Substitutions only; insertions and deletions are not scored.

For details on the architecture and training data, see the [paper](https://doi.org/10.1073/pnas.2535494123).

## Citation

\`\`\`
@article{birnbaum2026pottsmpnn,
  title={Beyond native sequence recovery: Improved modeling of the sequence-energy landscape of protein structures},
  author={Birnbaum, Foster and Keating, Amy E.},
  journal={Proceedings of the National Academy of Sciences},
  year={2026},
  doi={10.1073/pnas.2535494123}
}
\`\`\`

## License

PottsMPNN code and weights are released under the MIT license (see \`LICENSE\`, from the
[upstream repository](https://github.com/KeatingLab/PottsMPNN)).
`;export{e as default};
