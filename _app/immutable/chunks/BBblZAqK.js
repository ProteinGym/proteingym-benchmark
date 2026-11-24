const e=`---
# Model identifier used for referencing this model in the benchmark system
name: progen3

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.progen3.model:ProGen3"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to ProGen3.__init__
hyper_parameters:
    # HuggingFace Hub checkpoint, used if model_dir_path is not set
    # (Profluent-Bio/progen3-{112m,219m,339m,762m,1b,3b})
    model_name: "Profluent-Bio/progen3-112m"
    # Local checkpoint baked in at build time; takes precedence over model_name
    model_dir_path: "/opt/program/data/progen3-112m"
    # Number of sequences passed to the scorer at once
    batch_size: 64
    # Maximum number of tokens per forward pass - bounds GPU memory
    max_batch_tokens: 65536
    # Device to run the model on (GPU required)
    device: "cpu"
---

# Model Card for ProGen3 (evedesign-wrapped)

ProGen3 is Profluent Bio's family of order-agnostic autoregressive protein
language models with a sparse mixture-of-experts architecture (112M-3B
parameters). It needs only the sequence: no MSA or structure. Each sequence is
scored by its summed log-likelihood, averaged over the N->C and C->N decoding
directions. Scores indels. Requires a GPU.

| Checkpoint name | Num parameters |
|---|---|
| [progen3-3b](https://huggingface.co/Profluent-Bio/progen3-3b) | 3B |
| [progen3-1b](https://huggingface.co/Profluent-Bio/progen3-1b) | 1B |
| [progen3-762m](https://huggingface.co/Profluent-Bio/progen3-762m) | 762M |
| [progen3-339m](https://huggingface.co/Profluent-Bio/progen3-339m) | 339M |
| [progen3-219m](https://huggingface.co/Profluent-Bio/progen3-219m) | 219M |
| [progen3-112m](https://huggingface.co/Profluent-Bio/progen3-112m) | 112M |

## Citation

\`\`\`
@misc{progen3,
  author = {Profluent Bio},
  title = {ProGen3},
  year = {2025},
  publisher = {GitHub},
  url = {https://github.com/Profluent-AI/progen3}
}
\`\`\`

## License

The code is Apache 2.0-licensed (see \`LICENSE\`, matching upstream \`progen3\`).
The [ProGen3 weights](https://huggingface.co/Profluent-Bio) are under
CC BY-NC-SA 4.0, non-commercial use only.
`;export{e as default};
