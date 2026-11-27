const e=`---
# Model identifier used for referencing this model in the benchmark system
name: proteinnpt

# Import string for the evedesign model class to run. The class is
# instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.proteinnpt.model:ProteinNPT"

tags: ["supervised"]

# Multi-target support flag
multi_y: false

# Only the values deliberately set for this benchmark. Every other upstream
# argument is left at its published value by the wrapper's own defaults
# (proteingym.models.proteinnpt.model.ProteinNPT), which is also where the flat
# 35-field namespace upstream reads its config from gets assembled.
hyper_parameters:
    embedder_checkpoint_path: "/opt/program/data/esm2_t33_650M_UR50D.pt"
    aa_embeddings: "ESM2_650M"
    # default step count is 10k
    num_total_training_steps: 100
    training_num_assay_sequences_per_batch: 425
    device: "cpu"
---

# Model Card for ProteinNPT (evedesign-wrapped)

ProteinNPT is a semi-supervised non-parametric transformer for protein property
prediction ([Notin et al., NeurIPS 2023](https://papers.nips.cc/paper_files/paper/2023/hash/6a4d5d85f7a52f062d23d98d544a5578-Abstract-Conference.html)).
Unlike a per-sequence regressor it learns a joint representation of a whole
batch of sequences and their labels, attending across both the residue and the
sequence dimension, so a prediction for one variant is conditioned on the other
labelled variants in the batch. Residue embeddings come from a frozen protein
language model; only the NPT layers and target head are trained — 4.9 M
parameters, against the embedder's 651 M.

It is supervised and trained from scratch per assay — there is no pretrained
ProteinNPT checkpoint — so the benchmark must pass \`--split\` and \`--test-fold\`.

This wrapper drives upstream's \`ProteinNPTModel\` and \`Trainer\` directly, over
embeddings computed in-wrapper from the evedesign \`System\`. Upstream's own
pipeline is script- and filesystem-driven (CSV assay files, a
\`proteinnpt_data_location\` tree); that layout is not used here, apart from the
embedding cache, which is upstream's own \`.h5\` format — see Runtime.

Two deviations from upstream's published configuration are worth noting:

- **No zero-shot augmentation.** Upstream's best config passes zero-shot fitness
  predictions as auxiliary labels, which requires per-variant scores from a
  separate model. Upstream states these "are not strictly required", and the
  benchmark supplies no such scores, so this wrapper runs without them. Expect
  slightly lower accuracy than the published numbers, particularly when
  extrapolating to unseen positions.
- **ESM-2 embeddings rather than MSA Transformer.** Upstream ships final configs
  for both; ESM-2 needs only the target sequence, so it is used here. An MSA
  Transformer variant would additionally consume the dataset's alignment.

## Embedding cache

Embeddings are computed once per sequence under \`no_grad\` into an \`.h5\` that
upstream reads via \`sequence_embeddings_location\`. That argument is what keeps
the embedder frozen rather than merely described as such: with it set,
\`forward()\` uses the cached tensors and never calls the embedder. Left unset,
upstream embeds inside \`forward()\` and — since \`create_optimizer\` collects
\`named_parameters()\` with no \`requires_grad\` filter — trains all 651 M embedder
parameters: 85 GB, OOM-killed at a batch of 32, where the cached path runs the
published 425 in 26 GB. Cached embeddings are zeroed at masked positions
upstream, so the reconstruction loss is unaffected.

## Runtime

Measured on 8 CPU threads, 400 variants of a 465-residue target, at the
committed hyperparameters:

| | peak memory | per step | 10000 steps |
|---|---|---|---|
| 465-residue target, batch 425 | 26 GB | 22.5 s | ~2.6 days per fold |
| 5-residue CI dummy, 49 variants | 5.9 GB | ~0.14 s | ~25 min |

So the committed card is CI-feasible — the dummy fits a 16 GB runner and
finishes all 10000 steps well inside a 6 h cap — but a real assay at these
settings is GPU territory, and \`device: "cpu"\` is there for CI rather than for
use. The remaining cost is the NPT's own axial attention over the batch, which
is architectural rather than an artefact. A short run is correspondingly
under-trained: 100 of the 10000 steps gives a held-out-fold Spearman of +0.385
on 400 variants of HXK4.

Upstream is MIT licensed.
`;export{e as default};
