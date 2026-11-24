const e=`---
# Model identifier used for referencing this model in the benchmark system
name: aido-rag-16b

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.aido_rag_16b.model:AidoProteinRAG16B"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to AidoProteinRAG16B.__init__
hyper_parameters:
    # HuggingFace Hub repo id of the zero-shot checkpoint
    model_name: "genbio-ai/AIDO.Protein-RAG-16B-proteingym-dms-zeroshot"
    # Cache directory holding the checkpoint, baked in at build time
    hf_cache_dir: "/opt/program/data/hf_cache"
    # Device to run the model on (GPU required)
    device: "cpu"
    # Sliding-window size and stride (positions) for long sequences
    sliding_window: 768
    sliding_step: 768
    # If true, zero out the structure embedding (ablation)
    mask_str: false
    # Temperatures for the mutant and wildtype log-softmax terms
    temp_mt: 1.0
    temp_wt: 1.5
---

# Model Card for AIDO.Protein-RAG-16B (evedesign-wrapped)

AIDO.Protein-RAG-16B is a 16B-parameter mixture-of-experts protein language
model with retrieval augmentation, conditioned on an MSA and a 3D structure of
the target. The wrapper uses GenBio AI's dedicated zero-shot checkpoint
(\`genbio-ai/AIDO.Protein-RAG-16B-proteingym-dms-zeroshot\`) and needs both an
aligned MSA and a structure. Each position is masked in turn, and a variant is
scored by summing asymmetric-temperature log-odds of mutant vs. wildtype over
its mutated positions. Substitutions only; insertions and deletions are not
scored. Requires a GPU (~32GB of bf16 weights).

For details on the architecture and training data, see the [paper](https://doi.org/10.1101/2024.11.29.625425).

## Citation

\`\`\`
@inproceedings{sun_mixture_2024,
    title = {Mixture of Experts Enable Efficient and Effective Protein Understanding and Design},
    url = {https://www.biorxiv.org/content/10.1101/2024.11.29.625425v1},
    doi = {10.1101/2024.11.29.625425},
    publisher = {bioRxiv},
    author = {Sun, Ning and Zou, Shuxian and Tao, Tianhua and Mahbub, Sazan and Li, Dian and Zhuang, Yonghao and Wang, Hongyi and Cheng, Xingyi and Song, Le and Xing, Eric P.},
    year = {2024},
    booktitle={NeurIPS 2024 Workshop on AI for New Drug Modalities},
}

@article {Li2024.12.02.626519,
    author = {Li, Pan and Cheng, Xingyi and Song, Le and Xing, Eric},
    title = {Retrieval Augmented Protein Language Models for Protein Structure Prediction},
    url = {https://www.biorxiv.org/content/10.1101/2024.12.02.626519v1},
    year = {2024},
    doi = {10.1101/2024.12.02.626519},
    publisher = {bioRxiv},
    booktitle={NeurIPS 2024 Workshop on Machine Learning in Structural Biology},
}
\`\`\`

## License

The wrapper and the adapted ProteinGym baseline code are MIT-licensed (see
\`LICENSE\`); \`vendor/protein.py\` and \`vendor/residue_constants.py\` are from
AlphaFold under Apache 2.0. The weights are under the GenBio AI Community
License Agreement (see \`GENBIO_LICENSE\`). Non-commercial use only.

Powered by GenBio AI.
`;export{e as default};
