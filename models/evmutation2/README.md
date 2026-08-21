---
# Model identifier used for referencing this model in the benchmark system
name: evmutation2

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored
model_class: "evedesign.models.evmutation2:EVmutation2"

tags: ["zero-shot", "embeddable"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the evedesign EVmutation2 wrapper
# constructor (evedesign.models.evmutation2.EVmutation2)
hyper_parameters:
    # (fetching https://huggingface.co/thomashopf/evmutation2/resolve/main/
    # msa-only-small.ckpt at build() time)
    model_file_path: "/opt/program/data/msa-only-small.ckpt"
    # Number of encoder samples (single/pair representations) drawn from the
    # MSA encoder and averaged over
    encoder_num_samples: 16
    # Recycling steps run when computing each MSA encoding.
    encoder_num_recycling_steps: 4
    # Number of sequences sampled from the MSA when computing the encoding.
    encoder_max_num_msa: 2048
    # Maximum number of sequences decoded concurrently.
    decoder_batch_size: 64
    # Sampled decoding orders averaged over when computing full-sequence
    # scores
    decoder_num_full_samples: 16
    # Sampled decoding orders averaged over when computing single-mutant/
    # conditional scores
    decoder_num_mutant_samples: 16
    # Device to run the model on ("cpu", "cuda", or "mps")
    device: "cuda"
---

# Model Card for EVmutation2 (evedesign-wrapped)

This model scores protein sequences with the [evedesign](https://github.com/evedesignbio/evedesign)
`EVmutation2` wrapper. EVmutation2 encodes the evolutionary context of a target protein
family from a multiple sequence alignment (an axial-attention-style encoder
producing single/pair representations), then autoregressively decodes 
sequence and mutant log likelihoods from those representations.
Scoring is zero-shot - each sequence is assigned a log-likelihood-derived
score.

For detailed information on the model architecture and training data, please
refer to the [accompanying preprint](https://doi.org/10.64898/2026.03.17.712115).

## Citation

```
@article{Hopf2026evedesign,
  doi = {10.64898/2026.03.17.712115},
  url = {https://doi.org/10.64898/2026.03.17.712115},
  year = {2026},
  publisher = {bioRxiv},
  title = {evedesign: accessible biosequence design with a unified framework},
  author = {Hopf, Thomas A. and Belahsen, Khaoula and others}
}
```

## License

Both the `evmutation2` PyPI package's source
([evedesignbio/evmutation2](https://github.com/evedesignbio/evmutation2)) and
the [`thomashopf/evmutation2`](https://huggingface.co/thomashopf/evmutation2)
checkpoint on HuggingFace Hub are released under the **MIT license** - see
`LICENSE` in this directory.
