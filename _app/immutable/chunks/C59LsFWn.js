const e=`---
# Model identifier used for referencing this model in the benchmark system
name: tranception

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.tranception.model:Tranception"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the Tranception wrapper
# constructor (proteingym.models.tranception.model.Tranception)
hyper_parameters:
    # Directory with one subdirectory per checkpoint size (only Small is in the image)
    checkpoint_dir: "/opt/program/data"
    # Checkpoint to load ("Small", "Medium" or "Large")
    checkpoint_size: "Small"
    # Blend transformer log-probabilities with an MSA-derived prior
    # (requires an MSA)
    inference_time_retrieval: true
    # Weight (alpha) of the retrieval prior
    retrieval_inference_weight: 0.6
    # Score each sequence in both directions and average
    scoring_mirror: true
    # Batch size for scoring (forced to 1 for indels with retrieval)
    batch_size_inference: 20
    # Number of workers for the scoring data loader
    num_workers: 4
    # Clustal Omega binary, only used for indels with retrieval
    clustal_omega_binary: "clustalo"
    # Device to run the model on ("cuda" or "cpu")
    device: "cpu"
---

# Model Card for Tranception (evedesign-wrapped)

Tranception is an autoregressive protein language model (GPT2-style
transformer with grouped-ALiBi position embeddings and convolution-augmented
attention). It needs only the target sequence; with inference-time retrieval
enabled (the default here), it also uses an MSA, whose per-position
amino-acid frequencies are blended with the transformer's predictions. Each
sequence is scored as its log-likelihood ratio to the wild type, averaged over
left-to-right and right-to-left passes. Scores indels.

For details on the architecture and training data, see the
[paper](https://arxiv.org/abs/2205.13760).

| \`checkpoint_size\` | Num parameters |
|---|---|
| [Small](https://huggingface.co/OATML-Markslab/Tranception_Small) | ~85M |
| [Medium](https://huggingface.co/OATML-Markslab/Tranception_Medium) | ~300M |
| [Large](https://huggingface.co/OATML-Markslab/Tranception_Large) | ~700M |

## Citation

\`\`\`
@inproceedings{notin2022tranception,
    title={Tranception: Protein Fitness Prediction with Autoregressive Transformers and Inference-time Retrieval},
    author={Notin, Pascal and Dias, Mafalda and Frazer, Jonathan and Marchena-Hurtado, Javier and Gomez, Aidan N. and Marks, Debora S. and Gal, Yarin},
    booktitle={Proceedings of the 39th International Conference on Machine Learning},
    year={2022}
}
\`\`\`

## License

The wrapper and the vendored Tranception code are released under the **MIT
license** - see \`LICENSE\` in this directory. The Tranception checkpoints are
also MIT-licensed.
`;export{e as default};
