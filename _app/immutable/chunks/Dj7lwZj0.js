const e=`---
# Model identifier used for referencing this model in the benchmark system
name: msa-transformer

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.msa_transformer.model:MSATransformer"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the MSATransformer wrapper
# constructor, so every key here must be a valid MSATransformer.__init__
# argument. There is only one released ESM-MSA-1b checkpoint
# (esm_msa1b_t12_100M_UR50S, 100M params) - no size-family/sweep logic.
hyper_parameters:
    checkpoint_path: "/opt/program/data/esm_msa1b_t12_100M_UR50S.pt"
    # Alignment rows passed to the model, target included; the published
    # zero-shot protocol subsamples the MSA
    n_seqs: 384
    # Seed for the row subsample, so a run is reproducible
    seed: 0
    # Masked positions per forward pass; each carries its own copy of the
    # alignment, so raising this trades memory for speed
    positions_per_batch: 1
    device: "cpu"
---

# Model Card for MSA Transformer (evedesign-wrapped)

This model scores protein sequences with
[MSA Transformer](https://github.com/facebookresearch/esm) (ESM-MSA-1b), a
masked language model that attends over both the residue and the sequence
dimension of a multiple sequence alignment. Scoring is zero-shot by masked
marginals: the target's insertions are removed so the alignment spans its
columns, the target becomes the query row, the remaining rows are subsampled to
\`n_seqs\`, and one masked forward pass per target position yields that position's
amino-acid log-probabilities. A variant's score is the summed log-probability
ratio to the target, so the target scores 0.

The profile is computed once during \`build()\` and cached, so scoring a large
variant set afterwards is a table lookup rather than another pass over the
model. Because the score is a per-position difference, only substitutions are
modelled; the runner leaves indels without a prediction.

The checkpoint is baked into the image at build time. fair-esm's local loader
also requires the matching \`-contact-regression.pt\` weights to sit beside it
(it expects them for every model except ESM-1v and ESM-IF), so both files are
fetched.

For detailed information on the model architecture and training data, please
refer to the [accompanying paper](https://proceedings.mlr.press/v139/rao21a.html).

| Checkpoint name | Num layers | Num parameters |
|------------------------------|----|----------|
| esm_msa1b_t12_100M_UR50S | 12 | 100M |
`;export{e as default};
