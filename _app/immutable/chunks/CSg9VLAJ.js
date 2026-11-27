const e=`---
# Model identifier used for referencing this model in the benchmark system
name: mif-st

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.mif_st.model:MIFST"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the MIFST wrapper constructor, so
# every key here must be a valid MIFST.__init__ argument.
hyper_parameters:
    # Checkpoint loaded through sequence_models.pretrained: "mifst" is MIF-ST,
    # "mif" the same architecture trained without CARP-640M sequence transfer
    checkpoint_name: "mifst"
    # Key into the entity's structures mapping to select a structure, if more
    # than one is present. Leave null (every real ProteinGym2 archive checked
    # so far provides exactly one structure per entity).
    structure_name: null
    # Number of sequences scored per forward pass
    batch_size: 8
    device: "cpu"
---

# Model Card for MIF-ST (evedesign-wrapped)

This model scores protein sequences with the
\`proteingym.models.mif_st.model.MIFST\` wrapper around
[MIF-ST](https://github.com/microsoft/protein-sequence-models) (Masked Inverse
Folding with Sequence Transfer). It is a message-passing GNN over a fixed
backbone graph (N, CA, C coordinates only) whose sequence input is the output
of CARP-640M, a pretrained convolutional masked language model - the "sequence
transfer" the name refers to, and the reason MIF-ST outperforms the otherwise
identical \`mif\` checkpoint. Scoring is zero-shot: each sequence is assigned the
sum of the per-position (unmasked) log-likelihoods of its residues under the
model, conditioned on the structure.

**Two checkpoints.** MIF-ST is the 13 MB inverse-folding GNN *plus* the 2.4 GB
CARP-640M sequence model it reads from. \`sequence_models.pretrained\` resolves
both through \`torch.hub\`'s cache, and fetches CARP unconditionally even when
the GNN is supplied as a local path, so the image pre-populates that cache and
sets \`TORCH_HOME\` to keep the model offline at run time.

**Cost.** Every sequence is scored by running CARP-640M over the whole
target, so cost scales with assay size: 400 variants of the 465-residue HXK4
target take ~8 min on 8 CPU cores (~1.2 s per variant). \`device: "cuda"\` is
worthwhile for large assays.

**Fixed-length, substitutions only.** \`requires_fixed_length=True\`,
\`handles_deletions=False\`, \`handles_insertions=False\` - the structure features
built once in \`build()\` align \`target_seq[i]\` with \`coords[i]\` one-to-one, so
an insertion or deletion would desynchronize every downstream position's
structural context.

**Missing coordinates.** Positions the structure does not cover are passed
through as \`np.nan\`, which is what MIF-ST's own collater expects: NaN
distances sort last in the k-nearest-neighbour selection, so unresolved
residues drop out of the graph, and their edge features are masked and zeroed.
Such positions are still scored, from the sequence side alone.

**Single chain.** Unlike [esm-if1](../esm-if1/), MIF-ST builds its graph from
one backbone and has no multi-chain context path, so this wrapper raises if an
entity's structure resolves to more than one chain.

## Citation

\`\`\`
@article{yang2022masked,
  title={Masked inverse folding with sequence transfer for protein representation learning},
  author={Yang, Kevin K and Zanichelli, Niccol{\\\`o} and Yeh, Hugh},
  journal={bioRxiv},
  year={2022},
  doi={10.1101/2022.05.25.493516}
}
\`\`\`

## License

The \`sequence-models\` package this wrapper depends on (upstream:
https://github.com/microsoft/protein-sequence-models) is released by Microsoft
under a BSD-style license (see \`LICENSE\`, copied verbatim from the upstream
repository). The MIF-ST and CARP-640M checkpoints are distributed under the BSD
license on Zenodo ([6573779](https://zenodo.org/record/6573779) and
[6564798](https://zenodo.org/record/6564798) respectively).
`;export{e as default};
