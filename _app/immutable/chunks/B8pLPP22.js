const e=`---
# Model identifier used for referencing this model in the benchmark system
name: poet

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.poet.model:PoET"

tags: ["zero-shot"]

# NOTE: PoET is GPU-only. The wrapper calls .cuda()/.half() directly and takes
# no \`device\` hyperparameter, so unlike every other model in this zoo it cannot
# run on CPU and will fail in CPU-only CI. Run it with \`docker run --gpus all\`
# (or \`singularity run --nv\`).

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to PoET.__init__
hyper_parameters:
    # Path to the PoET checkpoint, baked into the image at build time
    ckpt_path: "/opt/program/data/poet.ckpt"
    # Number of sequences scored per forward pass against a fixed MSA context
    batch_size: 8
---

# Model Card for PoET (evedesign-wrapped)

> [!IMPORTANT]
> **PoET requires a GPU.** Unlike the other models in this zoo it takes no
> \`device\` hyperparameter: the wrapper moves the model and its tensors with
> \`.cuda()\` and runs in half precision, so there is no CPU code path. Run it
> with \`docker run --gpus all ...\` or \`singularity run --nv ...\`. It therefore
> cannot pass the CPU-only CI validation run.

PoET is an autoregressive generative model of protein families that treats an
MSA as a "sequence of sequences", using a Transformer with tied within- and
between-sequence attention. It needs an MSA of the target family, which is
subsampled into the conditioning context. Each sequence is scored by its
log-likelihood given the context, averaged over forward and reverse directions
and over an ensemble of 15 context-subsampling settings. Scores indels.
Requires a CUDA GPU.

For details on the architecture and training data, see the [paper](https://arxiv.org/abs/2306.06156).

## Citation

\`\`\`
@inproceedings{NEURIPS2023_f4366126,
 author = {Truong Jr, Timothy and Bepler, Tristan},
 booktitle = {Advances in Neural Information Processing Systems},
 editor = {A. Oh and T. Neumann and A. Globerson and K. Saenko and M. Hardt and S. Levine},
 pages = {77379--77415},
 publisher = {Curran Associates, Inc.},
 title = {PoET: A generative model of protein families as sequences-of-sequences},
 url = {https://proceedings.neurips.cc/paper_files/paper/2023/file/f4366126eba252699b280e8f93c0ab2f-Paper-Conference.pdf},
 volume = {36},
 year = {2023}
}
\`\`\`

## License

The code is MIT-licensed (see \`LICENSE\`). The
[PoET weights](https://zenodo.org/records/10061322) are under CC BY-NC-SA 4.0
for academic use only; contact NE47 Bio (contact@ne47.bio) for commercial use.
`;export{e as default};
