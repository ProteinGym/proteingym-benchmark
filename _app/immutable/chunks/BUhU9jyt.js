const e=`---
# Model identifier used for referencing this model in the benchmark system
name: protssn

# Import string for the evedesign model class to run. The class is
# instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.protssn.model:ProtSSN"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to ProtSSN.__init__
hyper_parameters:
    # One of upstream's nine protssn_k<k>_h<h>.pt checkpoints, baked at build
    # time. The k and h below must match the ones in this filename.
    gnn_model_path: "/opt/program/data/protssn_k20_h512.pt"
    # Neighbours per node (k) when building the CA residue graph; also selects
    # the matching cath_k<k>_mean_attr.pt normalization statistics
    c_alpha_max_neighbors: 20
    # EGNN hidden width (h) of the checkpoint
    hidden_channels: 512
    # Key into entity.structures; null = use the only structure present
    structure_name: null
    # Score uncovered target positions as 0 instead of failing
    allow_missing_structure: true
    # Device to run the model on ("cpu" or "cuda"; null = cuda if available)
    device: "cpu"
---

# Model Card for ProtSSN (evedesign-wrapped)

ProtSSN encodes a protein as a residue graph and denoises its sequence from
structure. Residue embeddings from a frozen ESM-2 650M model are the graph's
node features; an equivariant graph neural network over the k-nearest-neighbour
CA graph then predicts the amino acid at every position. It needs the target
sequence and a 3D structure, and uses no MSA.

A variant is scored as the sum over its mutated positions of
\`log P(mutant) - log P(wild type)\`, so the wild type scores exactly 0.0 — the
same log-ratio convention as [msa-transformer](../msa-transformer/) rather than
the absolute log-likelihood sums most models here emit. Substitutions only;
insertions and deletions are not scored.

One pass over the wild type yields all twenty log-probabilities at every
position, so each variant is a table lookup rather than a forward pass and the
cost is flat in the number of variants.

Upstream publishes nine checkpoints spanning k in {10, 20, 30} and h in
{512, 768, 1280}, and reports its headline numbers from the ensemble of all
nine. This card runs the single k20_h512 model; a different one means baking a
different checkpoint and setting \`c_alpha_max_neighbors\` and \`hidden_channels\`
to match, which the wrapper checks against the filename.

Positions the structure does not cover contribute 0 rather than failing the run
(\`allow_missing_structure\`). Upstream's featurizer accepts only the twenty
standard residues with a complete N/CA/C backbone, so the wrapper drops
anything else before building the graph; without that a single modified residue
would reject the whole structure.

For details on the architecture and training data, see the
[paper](https://doi.org/10.7554/eLife.98033).

## Citation

\`\`\`
@article{tan2024protssn,
    title={Semantical and geometrical protein encoding toward enhanced bioactivity and thermostability},
    author={Yang Tan and Bingxin Zhou and Lirong Zheng and Guisheng Fan and Liang Hong},
    journal={eLife},
    year={2024},
    doi={10.7554/eLife.98033}
}
\`\`\`

## License

ProtSSN's code and checkpoints are licensed **CC-BY-NC-ND-4.0**
(non-commercial) - see \`LICENSE\`. The vendored \`upstream/\` tree is upstream's
own code under those terms, with the EGNN layers inside it carrying Facebook's
MIT header.
`;export{e as default};
