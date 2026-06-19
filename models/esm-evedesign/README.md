---
# Model identifier used for referencing this model in the benchmark system
name: esm-evedesign

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "evedesign.models.esm2:ESM2"

tags: ["zero-shot", "embeddable"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the evedesign ESM2 wrapper
# constructor (evedesign.models.esm2.ESM2), so every key here must be a valid
# ESM2.__init__ argument.
hyper_parameters:
    # ESM-2 checkpoint identifier (loaded from facebook/<model_name> on the Hub)
    model_name: "esm2_t33_650M_UR50D"
    # Number of sequences scored per forward pass
    batch_size: 64
    # Device to run the model on ("cpu", "cuda", or "mps")
    device: "cuda"
---

# Model Card for ESM-2 (evedesign-wrapped)

This model scores protein sequences with the [evedesign](https://github.com/evedesignbio/evedesign)
`ESM2` wrapper, which loads an ESM-2 masked language model via HuggingFace
`transformers`. Scoring is zero-shot: each sequence is assigned the sum of the
per-position log-likelihoods of its residues under the model.

The benchmark entrypoint converts the ProteinGym subset into evedesign
datatypes with `evedesign.proteingym.dataset_to_evedesign`, builds the wrapper
on the resulting `System`, and scores the requested test fold. Because the
wrapper exposes the full evedesign model API, additional capabilities such as
`transform()` (embeddings), `generate()` (Gibbs sampling), and mutation scoring
come along with the import for free.

ESM-2 is a state-of-the-art protein model trained on a masked language modelling
objective. For detailed information on the model architecture and training data,
please refer to the [accompanying paper](https://www.biorxiv.org/content/10.1101/2022.07.20.500902v2).

Several ESM-2 checkpoints are available in the Hub with varying sizes. Larger
sizes generally have somewhat better accuracy, but require much more memory and
time to run:

| Checkpoint name | Num layers | Num parameters |
|------------------------------|----|----------|
| [esm2_t48_15B_UR50D](https://huggingface.co/facebook/esm2_t48_15B_UR50D) | 48 | 15B     |
| [esm2_t36_3B_UR50D](https://huggingface.co/facebook/esm2_t36_3B_UR50D) | 36 | 3B      |
| [esm2_t33_650M_UR50D](https://huggingface.co/facebook/esm2_t33_650M_UR50D) | 33 | 650M    |
| [esm2_t30_150M_UR50D](https://huggingface.co/facebook/esm2_t30_150M_UR50D) | 30 | 150M    |
| [esm2_t12_35M_UR50D](https://huggingface.co/facebook/esm2_t12_35M_UR50D) | 12 | 35M     |
| [esm2_t6_8M_UR50D](https://huggingface.co/facebook/esm2_t6_8M_UR50D)  | 6  | 8M      |
