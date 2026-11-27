const e=`---
# Model identifier used for referencing this model in the benchmark system
name: progen2

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.progen2.model:ProGen2"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to ProGen2.__init__
hyper_parameters:
    # Official checkpoint name; reporting only, model_dir_path is what loads
    model_name: "progen2-small"
    # Local checkpoint baked in at build time (config.json + pytorch_model.bin)
    model_dir_path: "/opt/program/data/progen2-small"
    # Number of sequences between status callbacks
    batch_size: 8
    # Average the N->C and C->N decoding directions, as upstream does; false
    # scores N->C only and halves the cost
    bidirectional: true
    # Device to run the model on ("cpu" or "cuda")
    device: "cpu"
---

# Model Card for ProGen2 (evedesign-wrapped)

ProGen2 is Salesforce's family of autoregressive protein language models
(151M-6.4B parameters). It needs only the sequence: no MSA or structure. Each
sequence is scored by its summed log-likelihood, averaged over the N->C and
C->N decoding directions, which is the protocol in upstream's \`likelihood.py\`.
Scores indels. Runs on CPU.

Scoring restricts the logits to the amino-acid slice of the vocabulary (token
ids 5-29) before normalizing, as upstream does. Skipping that step renormalizes
over the special tokens too and shifts every score, so the wrapper factors it
into \`canonical_log_likelihood\`, which the unit tests cover directly.

The wrapper vendors upstream's \`modeling_progen.py\`, \`configuration_progen.py\`
and \`tokenizer.json\` (BSD-3-Clause, from
[salesforce/progen](https://github.com/salesforce/progen)) rather than depending
on the community HuggingFace mirrors: those advertise a "slightly modified
configuration and forward pass" and drop the \`mp_num\` argument that controls how
the fused QKV projection is reshaped, which could change scores. Their configs
match upstream's exactly, so the mirrors may well be faithful — but that was not
verified here, and a reference baseline is the wrong place to guess.

| Checkpoint name | Num parameters | Size |
|---|---|---|
| [progen2-xlarge](https://github.com/salesforce/progen/tree/main/progen2) | 6.4B | 11.9 GB |
| progen2-large | 2.7B | 5.1 GB |
| progen2-medium | 764M | 2.8 GB |
| progen2-base | 764M | 1.4 GB |
| progen2-small | 151M | 561 MB |

\`progen2-small\` is baked into the image. A larger checkpoint means changing both
the recipe's download and \`model_dir_path\`; note \`n_positions\` is 1024, so
targets longer than 1022 residues cannot be scored by any of them.
`;export{e as default};
