---
name: cytolexmuta
model_class: "proteingym.models.cytolexmuta.model:Cytolexmuta"
tags: ["zero-shot"]
multi_y: false
hyper_parameters:
    esmc_model_id: "biohub/ESMC-600M"
    esmc_revision: "a7e82012c83126b9eedb055fea9fa84b6c02f094"
    prosst_model_id: "AI4Protein/ProSST-2048"
    prosst_revision: "e94ffee7846d7f55c1bf5efa8ec7372a336ac4b8"
    prosst_repo: "/opt/ProSST"
    gemme_path: "/opt/GEMME"
    jet_path: "/opt/JET2"
    device: "auto"
    position_batch_size: 8
    sequence_batch_size: 8
    max_residues: 1022
    gemme_nseqs: 20000
---

# cytolexmuta

`cytolexmuta` is a label-free, prediction-only baseline that combines four
independently computed protein energy maps: ESM-C, ProSST, GEMME, and ESM-IF1.
For every assay, the runtime applies the fixed path

```text
0.50 * robust_z(0.50 * ESM-C + 0.50 * ProSST)
+ 0.25 * robust_z(GEMME)
+ 0.25 * robust_z(ESM-IF1)
```

where `robust_z(x) = (x - median(x)) / (1.4826 * MAD(x))`. The model does not
read fitness measurements, labels, benchmark folds, or target columns, and it
does not fit assay-specific parameters.

## Executable scoring pipeline

The evedesign wrapper requires one protein entity with a query-first aligned
MSA and a single-chain structure. It computes every expert at prediction time:

- **ESM-C 600M:** centered-window masked-marginal log-odds, summed across the
  substitutions in a variant.
- **ProSST-2048:** structure tokens are generated from the supplied PDB with
  the pinned ProSST quantizer; WT-conditioned residue log-odds are summed
  across substitutions.
- **GEMME:** the query-first match-state MSA and requested variants are passed
  to the official GEMME/JET2 runtime, retaining GEMME's combination-mutation
  treatment.
- **ESM-IF1:** teacher-forced mean log-likelihood of each complete mutated
  sequence under the supplied backbone coordinates.

Sequences longer than 1,022 residues use deterministic windows for ESM-C and
ProSST. Insertions, deletions, multi-chain systems, and PDB/WT sequence
mismatches fail explicitly instead of producing fallback predictions. CUDA is
used automatically when available; CPU execution is supported but much slower.

## Pinned sources and released regression data

- ESM-C checkpoint:
  [`biohub/ESMC-600M`](https://huggingface.co/biohub/ESMC-600M), revision
  `a7e82012c83126b9eedb055fea9fa84b6c02f094`.
- ProSST checkpoint:
  [`AI4Protein/ProSST-2048`](https://huggingface.co/AI4Protein/ProSST-2048),
  revision `e94ffee7846d7f55c1bf5efa8ec7372a336ac4b8`; structure quantizer source
  [`ai4protein/ProSST`](https://github.com/ai4protein/ProSST) at commit
  `0bcfae91356d70e0da56fdd9bade4a3e7944769c`.
- ESM-IF1 implementation and checkpoint loader: `fair-esm==2.0.0`.
- GEMME/JET2: the authors' `elodielaine/gemme:gemme` image, pinned by digest
  `sha256:a35099f7dee9411842e3e04318f4a9b435541bce16aff1c9ef5baa507baf5e7d`.

The ProSST checkout is used unmodified under its upstream
CC BY-NC-ND 4.0 license. GEMME is MIT-licensed; JET2 is consumed only through
the GEMME authors' published image rather than redistributed separately.

The public [`cytolex/cytolexmuta`](https://huggingface.co/datasets/cytolex/cytolexmuta)
Full217 prediction release is retained as a numerical regression reference.
It is not consulted by the production scorer.

## Resource note

The four experts are loaded sequentially so their neural checkpoints do not
occupy accelerator memory simultaneously. GEMME runs concurrently on CPU.
Model downloads require network access on the first execution and are then
served from the standard Hugging Face and torch caches.
