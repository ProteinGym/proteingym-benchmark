const e=`---
# Model identifier used for referencing this model in the benchmark system
name: esm1v

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.esm1v.model:ESM1v"

tags: ["zero-shot", "embeddable"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the ESM1v wrapper constructor
# (proteingym.models.esm1v.model.ESM1v)
hyper_parameters:
    # Checkpoints to average, each loaded from facebook/<name> on the Hub. The
    # published ESM-1v is the mean over all five; a shorter list scores
    # proportionally faster
    model_names:
        - "esm1v_t33_650M_UR90S_1"
        - "esm1v_t33_650M_UR90S_2"
        - "esm1v_t33_650M_UR90S_3"
        - "esm1v_t33_650M_UR90S_4"
        - "esm1v_t33_650M_UR90S_5"
    # Number of sequences scored per forward pass
    batch_size: 64
    # Device to run the model on ("cpu", "cuda", or "mps")
    device: "cpu"
---

# Model Card for ESM-1v (evedesign-wrapped)

This model scores protein sequences with ESM-1v, the 650M-parameter transformer
of Meier et al. trained on UniRef90 and introduced specifically for zero-shot
variant effect prediction. Scoring is zero-shot: each sequence is assigned the
sum of the per-position (unmasked) log-likelihoods of its residues, and the
reported score is the **mean over the five released checkpoints**, which is the
form the paper and the ProteinGym baselines report.

ESM-1v shares ESM-2's architecture and tokenizer, so every member loads through
the [evedesign](https://github.com/evedesignbio/evedesign) \`ESM2\` wrapper's
\`transformers\` path. This model therefore adds only the ensembling: it selects
each checkpoint in turn, delegates to \`ESM2.score\`, and averages. Members are
loaded one at a time and released in between, so peak memory is that of a
single 650M model rather than five.

The benchmark entrypoint converts the ProteinGym subset into evedesign
datatypes with \`evedesign.proteingym.dataset_to_evedesign\`, builds the wrapper
on the resulting \`System\`, and scores the requested test fold. Inherited
single-checkpoint capabilities (\`transform()\` embeddings, mutation scoring) use
the first member.

For detailed information on the model architecture and training data, please
refer to the [accompanying paper](https://doi.org/10.1101/2021.07.09.450648).

| Checkpoint name | Num layers | Num parameters |
|------------------------------|----|----------|
| [esm1v_t33_650M_UR90S_1](https://huggingface.co/facebook/esm1v_t33_650M_UR90S_1) | 33 | 650M |
| [esm1v_t33_650M_UR90S_2](https://huggingface.co/facebook/esm1v_t33_650M_UR90S_2) | 33 | 650M |
| [esm1v_t33_650M_UR90S_3](https://huggingface.co/facebook/esm1v_t33_650M_UR90S_3) | 33 | 650M |
| [esm1v_t33_650M_UR90S_4](https://huggingface.co/facebook/esm1v_t33_650M_UR90S_4) | 33 | 650M |
| [esm1v_t33_650M_UR90S_5](https://huggingface.co/facebook/esm1v_t33_650M_UR90S_5) | 33 | 650M |

The five checkpoints total roughly 13 GB and are fetched from the Hub on first
use rather than baked into the image, matching [esm2](../esm2/).
`;export{e as default};
