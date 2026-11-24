const e=`---
# Model identifier used for referencing this model in the benchmark system
name: proteinmpnn

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "evedesign.models.mpnn:LigandMPNN"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the evedesign MPNN wrapper
# constructor (evedesign.models.mpnn.LigandMPNN), so every key here must be a
# valid LigandMPNN.__init__ argument.
hyper_parameters:
    # MPNN checkpoint name ("proteinmpnn_v_48_{002,010,020,030}" = training noise
    # in units of 0.01A); must match model_file_path
    model_name: "proteinmpnn_v_48_020"
    # Checkpoint downloaded at container build time
    model_file_path: "/opt/program/data/proteinmpnn_v_48_020.pt"
    # Batch size for generation only; scoring runs one sequence at a time
    batch_size: 1
    # Device to run the model on ("cpu", "cuda", or "mps")
    device: "cpu"
---

# Model Card for ProteinMPNN (evedesign-wrapped)

ProteinMPNN is an inverse-folding GNN conditioned on backbone structure, run here through the
[evedesign](https://github.com/evedesignbio/evedesign) \`LigandMPNN\` wrapper with the original
ProteinMPNN weights. It needs a structure for the target (\`entity.structures\`) and no MSA.
Scoring is zero-shot: each sequence is assigned the sum of the per-position log-likelihoods of
its residues given the target backbone. Substitutions only; insertions and deletions are not scored.

For details on the architecture and training data, see the [paper](https://doi.org/10.1126/science.add2187).

| Checkpoint name | Training backbone noise |
|------------------------|--------|
| \`proteinmpnn_v_48_002\` | 0.02 Å |
| \`proteinmpnn_v_48_010\` | 0.10 Å |
| \`proteinmpnn_v_48_020\` | 0.20 Å |
| \`proteinmpnn_v_48_030\` | 0.30 Å |

## Citation

\`\`\`
@article{Dauparas2022ProteinMPNN,
  author = {Dauparas, J. and Anishchenko, I. and Bennett, N. and Bera, H. and Ragotte, R. J. and Milles, L. F. and Wicky, B. I. M. and Courbet, A. and de Haas, R. J. and Bethel, N. and others},
  title = {Robust deep learning-based protein sequence design using ProteinMPNN},
  journal = {Science},
  volume = {378},
  number = {6615},
  pages = {49--56},
  year = {2022},
  doi = {10.1126/science.add2187}
}
\`\`\`

## License

ProteinMPNN code and weights are released under the MIT license (see \`LICENSE\`, copied from the
upstream repository).
`;export{e as default};
