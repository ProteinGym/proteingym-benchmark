const e=`---
# Model identifier used for referencing this model in the benchmark system
name: esm1b

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
    # ESM-1b checkpoint identifier (loaded from facebook/<model_name> on the
    # Hub). ESM-1b predates ESM-2 but is the same architecture family and loads
    # through the same transformers EsmForMaskedLM path, so it needs no wrapper
    # of its own
    model_name: "esm1b_t33_650M_UR50S"
    # Number of sequences scored per forward pass
    batch_size: 64
    # Device to run the model on ("cpu", "cuda", or "mps")
    device: "cpu"
---

# Model Card for ESM-1b (evedesign-wrapped)

This model scores protein sequences with the [evedesign](https://github.com/evedesignbio/evedesign)
\`ESM2\` wrapper pointed at the ESM-1b checkpoint, loaded via HuggingFace
\`transformers\`. Scoring is zero-shot: each sequence is assigned the sum of the
per-position (unmasked) log-likelihoods of its residues under the model.

ESM-1b is the 650M-parameter transformer from Rives et al., trained on UniRef50
with masked language modelling. It is included as a baseline in its own right:
it predates ESM-2 but remains a widely reported reference point, and it is the
model behind genome-wide missense effect prediction in
[Brandes et al.](https://www.nature.com/articles/s41588-023-01465-0). Because it
shares ESM-2's architecture and tokenizer, evedesign's \`ESM2\` wrapper loads it
unchanged and this model contributes no Python of its own.

The benchmark entrypoint converts the ProteinGym subset into evedesign
datatypes with \`evedesign.proteingym.dataset_to_evedesign\`, builds the wrapper
on the resulting \`System\`, and scores the requested test fold. Because the
wrapper exposes the full evedesign model API, additional capabilities such as
\`transform()\` (embeddings), \`generate()\` (Gibbs sampling), and mutation scoring
are also available.

For detailed information on the model architecture and training data, please
refer to the [accompanying paper](https://www.pnas.org/doi/10.1073/pnas.2016239118).

| Checkpoint name | Num layers | Num parameters |
|------------------------------|----|----------|
| [esm1b_t33_650M_UR50S](https://huggingface.co/facebook/esm1b_t33_650M_UR50S) | 33 | 650M |

The checkpoint is fetched from the Hub on first use rather than baked into the
image, matching [esm2](../esm2/).
`;export{e as default};
