const e=`---
# Model identifier used for referencing this model in the benchmark system
name: s3f

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.s3f.model:S3F"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Forwarded verbatim to S3F.__init__
hyper_parameters:
    # "s3f" (sequence + structure + surface) or "s2f" (sequence + structure)
    variant: "s3f"
    # Checkpoint from Zenodo record 14257708, downloaded at container build time
    checkpoint_path: "/opt/program/data/s3f.pth"
    # Directory with esm2_t33_650M_UR50D.pt; null = /opt/program/data/esm-model-weights
    esm_dir: null
    # Key into entity.structures; null = use the only structure present
    structure_name: null
    # Masked sequences per forward pass; null = upstream default (2 for S3F, 4 for S2F)
    batch_size: null
    # Seed for the random surface point sampling (S3F only)
    surface_seed: 0
    # Device for surface generation
    surface_device: "cpu"
    # "cpu" or "cuda"; null = cuda if available, else cpu
    device: "cpu"
---

# Model Card for S3F (evedesign-wrapped)

S3F (Sequence-Structure-Surface Fitness) is a multi-scale model that adds GVP graph networks
over the backbone structure and a protein surface point cloud on top of frozen ESM-2-650M
embeddings; the S2F variant drops the surface branch. It needs a structure for the target
(\`entity.structures\`) and no MSA. Scoring is zero-shot masked marginals: mutated positions are
masked and each variant is scored by the sum of log p(mutant) - log p(wild type) over them.
Substitutions only; insertions and deletions are not scored. A GPU is strongly recommended.

For details on the architecture and training data, see the [paper](https://arxiv.org/abs/2412.01108).

## Citation

\`\`\`
@inproceedings{zhang2024s3f,
  title={Multi-Scale Representation Learning for Protein Fitness Prediction},
  author={Zhang, Zuobai and Notin, Pascal and Huang, Yining and Lozano, Aurelie and Chenthamarakshan, Vijil and Marks, Debora and Das, Payel and Tang, Jian},
  booktitle={Advances in Neural Information Processing Systems},
  year={2024}
}
\`\`\`

## License

The S3F code, including the vendored \`s3f/\` package, is MIT (see \`LICENSE\`). The S3F/S2F
checkpoints are CC-BY-4.0 (Zenodo record 14257708) and ESM-2 weights are MIT.
`;export{e as default};
