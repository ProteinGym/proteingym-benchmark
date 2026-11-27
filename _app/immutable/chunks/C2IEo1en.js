const e=`---
# Model identifier used for referencing this model in the benchmark system
name: vespag

# Import string for the evedesign model class to run. The class is
# instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.vespag.model:VespaG"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the wrapper constructor
# (proteingym.models.vespag.model.VespaG)
hyper_parameters:
    # Upstream's v2 ESM-2 head (model_weights/v2/esm2.pt), baked at build time
    head_checkpoint_path: "/opt/program/data/vespag/esm2.pt"
    # ESM-2 checkpoint supplying the embeddings. The head was trained on
    # 2560-dimensional embeddings, so only the 3B model fits it
    embedder_model_name: "esm2_t36_3B_UR50D"
    # Upstream's min-max rescaling of the landscape to [0, 1]. Monotonic, so it
    # cannot change rankings; off by default so a variant's score does not
    # depend on the rest of the landscape
    normalize: false
    # Sequences per embedder forward pass (only the target is embedded)
    batch_size: 1
    # Device for both the embedder and the head
    device: "cpu"
---

# Model Card for VespaG (evedesign-wrapped)

This model scores substitutions with [VespaG](https://github.com/jschlensok/vespag),
a small feed-forward head over ESM-2 embeddings distilled from GEMME. The head
has one 256-unit hidden layer and emits 20 scores per residue, so a single
embedder pass yields the protein's whole mutational landscape and scoring is a
lookup afterwards. A variant's score is the sum of its substitutions' values;
the wild type scores 0, because upstream zeroes each position's wild-type column.

**Zero-shot in benchmark terms, although the model was trained supervised.**
VespaG's head was fitted on ~39M human variants using GEMME scores as
pseudo-labels, but that head is published pretrained and is frozen here: no
assay labels are used and no training fold is needed, so the card is tagged
\`zero-shot\` and the benchmark scores the whole dataset at once. Tagging it
\`supervised\` would wrongly imply it fits on the training split.

Only substitutions are scored. The landscape is indexed by position against the
target, so variants must align to it residue for residue; insertions and
deletions are rejected rather than scored.

Its teacher, [gemme](../gemme/), is also in this zoo, so student-versus-teacher
comparison is possible in principle.

For the method and its evaluation, see the
[paper](https://doi.org/10.1093/bioinformatics/btae621).

## Implementation notes

- The head's four layers are rebuilt in \`model.py\` from upstream's published
  architecture rather than importing upstream's package, which is **GPL-3.0** —
  the most restrictive licence in this zoo. The weights are downloaded from that
  repository at build time and are covered by it.
- Only upstream's **v2** checkpoint is supported. Its v1 has no dropout and so a
  different layer layout; loading it would put the output weights on the wrong
  module, so the wrapper rejects it explicitly.
- The embedder is evedesign's \`ESM2\`, whose \`transform()\` returns the final
  hidden states per residue with no pooling — the same embedding space upstream
  uses via \`AutoModel\`. The wrapper checks the embedding width against the head
  and fails with a clear message on a mismatch.
- The 3B embedder (~11 GB) is fetched from the Hub on first use rather than baked
  into the image, as [esm2](../esm2/) does. The head itself is only 2.6 MB and is
  baked.
`;export{e as default};
