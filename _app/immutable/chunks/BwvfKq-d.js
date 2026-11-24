const e=`---
# Model identifier used for referencing this model in the benchmark system
name: msa-pairformer

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.msa_pairformer.model:MSAPairformerModel"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the wrapper constructor
# (proteingym.models.msa_pairformer.model.MSAPairformerModel)
hyper_parameters:
    # HuggingFace hub cache dir holding yakiyama/MSA-Pairformer (filled at build time)
    ckpt_dir: "/opt/program/data"
    # Number of MSA sequences (including the query) sampled as model context
    n_seqs: 512
    # Sequence-reweighting threshold (1 - identity); 0.2 for most families,
    # 0.01 for viruses
    theta: 0.2
    # Random seed for the weighted MSA subsampling
    seed: 42
    # Device to run the model on ("cuda" recommended; CPU is impractical)
    device: "cpu"
    # Number of masked positions scored per forward pass
    batch_size: 1
---

# Model Card for MSA Pairformer (evedesign-wrapped)

MSA Pairformer is an MSA-conditioned protein language model that iteratively
updates MSA and pairwise representations to model coevolution. It needs a multiple
sequence alignment of the target family, from which a reweighted subsample is drawn
as context. Scores are masked marginals: each query position is masked and a
variant scores the sum of log P(mutant) - log P(WT) over its mutated positions.
Substitutions only; insertions and deletions are not scored. Needs a GPU.

For details on the architecture and training data, see the [paper](https://doi.org/10.1016/j.cell.2026.06.029).

## Citation

\`\`\`
@article {Akiyama2026,
	author = {Akiyama, Yo and Zhang, Zhidian and Tang, Olivia and Kim, Rachel Seongeun and Mirdita, Milot and Steinegger, Martin and Ovchinnikov, Sergey},
	title = {Expanding the scope of protein language modeling to protein-protein interactions with MSA Pairformer},
	year = {2026},
	doi = {10.1016/j.cell.2026.06.029},
	publisher = {Cell Press},
	URL = {https://doi.org/10.1016/j.cell.2026.06.029},
	journal = {Cell}
}
\`\`\`

## License

MSA Pairformer code and weights are released under a modified, permissive MIT
license, free for academic and commercial use (see \`LICENSE\` and \`MIT_LICENSE\`).
`;export{e as default};
