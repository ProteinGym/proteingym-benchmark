const e=`---
# Model identifier used for referencing this model in the benchmark system
name: prott5

# Import string for the evedesign model class to run. The class is
# instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.prott5.model:ProtT5"

tags: ["supervised"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the wrapper constructor
# (proteingym.models.prott5.model.ProtT5)
hyper_parameters:
    # Any scikit-learn regressor by name, as listed by
    # sklearn.utils.all_estimators(type_filter=["regressor"])
    predictor: "Ridge"
    # Keyword arguments forwarded to that regressor's constructor
    predictor_kwargs:
        alpha: 1.0
    # ProtT5 checkpoint on the Hub; only the encoder is loaded, so the
    # encoder-only "-enc" variants also work and are smaller
    model_name: "Rostlab/prot_t5_xl_uniref50"
    # Reduction of per-residue embeddings to one vector per sequence
    pooling: "mean"
    # Number of sequences embedded per forward pass
    batch_size: 8
    # Device the embedder runs on ("cpu", "cuda", or "mps")
    device: "cpu"
---

# Model Card for ProtT5 (evedesign-wrapped)

This model embeds sequences with the [ProtT5](https://doi.org/10.1109/TPAMI.2021.3095381)
encoder, mean-pools the per-residue embeddings to one vector per sequence, and
fits a scikit-learn regressor on the training fold's assay values. It needs a
labelled training split, so the benchmark passes \`--split\` and \`--test-fold\`.

**Why supervised rather than zero-shot.** ProtT5 is an encoder-decoder trained
with span corruption, so it has no masked-marginal convention like ESM's and no
canonical zero-shot variant-effect protocol. Its published purpose, and what the
literature cites it for, is as a sequence encoder. Scoring it zero-shot would
mean inventing a protocol whose numbers are not comparable to anything, so this
wrapper uses it the way the field does: as frozen features under a supervised
head, the same shape as [embedding-regressor](../embedding-regressor/) with
ESM-2. Scores are therefore the head's predictions in assay units, not
log-likelihoods.

**Only the encoder is loaded.** The decoder is never used, so the wrapper loads
\`T5EncoderModel\`, which is upstream's own recommendation for embedding and
halves the weights. The checkpoint is fetched from the Hub on first use rather
than baked into the image, as [esm2](../esm2/) does.

Two details that silently corrupt results if got wrong, both covered by tests:
ProtT5's tokenizer expects residues **space-separated**, and the ambiguity
codes \`U/Z/O/B\` must be folded to \`X\`. They do tokenize to real vocabulary
entries, so leaving them unmapped raises nothing — but ProtT5 was trained with
ProtTrans' preprocessing, which maps them to \`X\`, leaving those entries
effectively untrained. And T5 appends \`</s>\` but has **no** start token,
so the ESM convention of slicing \`[1:L+1]\` would shift every embedding by one
position; the wrapper derives the residue positions from the tokenizer's own
special-token ids instead of assuming an offset.

Unlike ESM, T5 uses relative position embeddings, so there is no 1022-token
context limit and long targets are scored without truncation.
`;export{e as default};
