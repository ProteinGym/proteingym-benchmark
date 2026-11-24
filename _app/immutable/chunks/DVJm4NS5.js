const e=`---
# Model identifier used for referencing this model in the benchmark system
name: prosst

# Import string for the evedesign model class to run. The class is
# instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.prosst.model:ProSST"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to ProSST.__init__
hyper_parameters:
    # HF checkpoint (local copy of AI4Protein/ProSST-2048, fetched at build time)
    model_name: "/opt/program/data/ProSST-2048"
    # Structure token vocabulary size; must match the checkpoint and codebook
    structure_vocab_size: 2048
    # Key into entity.structures; null = use the only structure present
    structure_name: null
    # CA-CA distance cutoff (Angstrom) for the quantizer's residue graph
    max_distance: 10
    # Node budget per quantizer encoder batch
    max_batch_nodes: 10000
    # Pad target positions the structure doesn't cover instead of failing
    allow_missing_structure: true
    # Device to run the model on ("cpu" or "cuda"; null = cuda if available)
    device: "cpu"
---

# Model Card for ProSST (evedesign-wrapped)

ProSST is a structure-aware masked protein language model that reads the
residue sequence alongside a sequence of discrete structure tokens, combined
through disentangled attention. It needs the target sequence and a 3D
structure, which a GVP encoder and k-means codebook quantize into structure
tokens; no MSA is used. A variant is scored as the sum over its mutated
positions of \`log P(mutant) - log P(wild type)\` from a single forward pass on
the wild-type sequence. Substitutions only; insertions and deletions are not
scored.

For details on the architecture and training data, see the
[paper](https://doi.org/10.1101/2024.04.15.589672).

## Citation

\`\`\`
@inproceedings{li2024prosst,
    title={{ProSST}: Protein Language Modeling with Quantized Structure and Disentangled Attention},
    author={Mingchen Li and Yang Tan and Xinzhu Ma and Bozitao Zhong and Huiqun Yu and Ziyi Zhou and Wanli Ouyang and Bingxin Zhou and Pan Tan and Liang Hong},
    booktitle={The Thirty-eighth Annual Conference on Neural Information Processing Systems},
    year={2024}
}
\`\`\`

## License

ProSST's code, vendored \`structure/\` quantizer and its static weights are
licensed **CC-BY-NC-ND-4.0** (non-commercial) - see \`LICENSE\`. The
\`AI4Protein/ProSST-*\` checkpoints state no license and are assumed to fall
under the same terms; the vendored \`torch_scatter\` shim is MIT.
`;export{e as default};
