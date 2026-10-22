---
# Model identifier used for referencing this model in the benchmark system
name: esmc

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.esmc.model:ESMC"

tags: ["zero-shot", "embeddable"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the ESMC wrapper constructor
# (proteingym.models.esmc.model.ESMC)
hyper_parameters:
    # ESMC checkpoint (biohub/{model_name}): ESMC-300M, ESMC-600M or ESMC-6B.
    # Ignored when model_dir_path is set
    model_name: "ESMC-300M"
    # Local checkpoint directory, overrides model_name (downloaded at image build time)
    model_dir_path: "/opt/program/data/esmc-300m"
    # Number of sequences scored per forward pass
    batch_size: 64
    # Accepted for parity with the ESM2 wrapper (unused)
    keep_model_after_build: false
    # Device to run the model on ("cpu", "cuda", or "mps")
    device: "cpu"
---

# Model Card for ESMC-300M (evedesign-wrapped)

This model scores protein sequences with
[ESMC](https://huggingface.co/biohub/esmc-model-family), Biohub's masked
protein language model family, loaded through HuggingFace `transformers`. It needs only the target sequence (no MSA or structure). Each variant is
scored with masked marginals: the sum of `logP(mut) - logP(wt)` at each
mutated position, masked independently in the wild-type sequence.
Substitutions only; insertions and deletions are not scored.

For details on the architecture and training data, see the
[paper](https://biohub.ai/papers/esm_protein.pdf).

| Checkpoint name | Num parameters |
|---|---|
| [ESMC-300M](https://huggingface.co/biohub/ESMC-300M) | 300M |
| [ESMC-600M](https://huggingface.co/biohub/ESMC-600M) | 600M |
| [ESMC-6B](https://huggingface.co/biohub/ESMC-6B) | 6B |

## Citation

```
@misc{candido2026language,
  title  = {Language Modeling Materializes a World Model of Protein Biology},
  author = {Candido, Salvatore and Hayes, Thomas and Derry, Alexander and Rao, Roshan
            and Lin, Zeming and Verkuil, Robert and Wu, Bryan and Lee, Jin Sub
            and Bruguera, Elise S. and Keval, Jehan A. and Kopylov, Mykhailo
            and Pak, John E. and Wu, Wesley and Thomas, Neil and Mataraso, Samson
            and Hsu, Alvin and Trotman-Grant, Ashton C. and Fatras, Kilian
            and dos Santos Costa, Allan and Badkundri, Rohil and Ak{\i}n, Halil
            and Oktay, Deniz and Deaton, Jonathan and Montabana, Elizabeth
            and Sitwala, Hrishita and Yu, Yue and Wiggert, Marius
            and Carlin, Dylan Alexander and Goering, Anthony W. and Blazejewski, Tomasz
            and Sandora, McCullen and Hla, Michael and Jia, Tina Z.
            and Kloker, Leon H. and Sofroniew, Nicholas J. and Uehara, Masatoshi
            and Pannu, Jassi and Bachas, Sharrol and Liu, Daniel S.
            and Sercu, Tom and Rives, Alexander},
  year   = {2026},
  url    = {https://biohub.ai/papers/esm_protein.pdf},
  note   = {Preprint}
}
```

## License

The ESMC weights are MIT (see `LICENSE`) plus Biohub's
[Acceptable Use Policy](https://biohub.org/acceptable-use-policy/).
