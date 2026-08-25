---
name: cytolexmuta
model_class: "proteingym.models.cytolexmuta.model:Cytolexmuta"
tags: ["zero-shot"]
multi_y: false
hyper_parameters:
    score_bundle_url: "https://huggingface.co/datasets/cytolex/cytolexmuta/resolve/main/cytolexmuta_scores_full217.tar.gz?download=true"
    cache_dir: "/tmp/cytolexmuta"
    allow_diagnostic_fallback: true
---

# cytolexmuta

`cytolexmuta` is a zero-shot, prediction-only map-fusion baseline for
ProteinGym DMS substitutions. It combines four independently produced energy
maps—ESM-C, ProSST, GEMME, and ESM-IF1—after assay-local robust normalization,
with the fixed convex path:

```text
0.50 × robust_z(0.50 × ESM-C + 0.50 × ProSST)
+ 0.25 × robust_z(GEMME)
+ 0.25 × robust_z(ESM-IF1)
```

The released full-217 prediction bundle is public at
[`cytolex/cytolexmuta`](https://huggingface.co/datasets/cytolex/cytolexmuta).
The container downloads it lazily, selects the CSV matching the current
ProteinGym dataset/assay identifier, and maps the released `mutant` scores to
the sequences in the input archive. It does not read assay targets, labels,
splits, or any other supervised signal.

For development datasets outside the full-217 release, the card enables a
transparent deterministic sequence-only diagnostic fallback so the container
can be smoke-tested without labels or a second hidden artifact. The fallback
is not a leaderboard result and is reported in the container log. Full-217
assays use the released cytolexmuta scores whenever the identifier is present.

## Reproducibility and provenance

- Method output: `full217_cytolexmuta_scores.csv.gz` and
  `cytolexmuta_scores_full217.tar.gz` in the public Hugging Face dataset.
- Source component runs and the fixed fusion rule are documented in the
  [method card](https://huggingface.co/datasets/cytolex/cytolexmuta/blob/main/METHOD_CARD.md).
- The ProteinGym2 entrypoint is prediction-only and does not fit parameters on
  the benchmark archive.

## Limitations

The released score bundle covers the 217 DMS assays used for the original
cytolexmuta result. A dataset identifier outside that release is supported
only for diagnostic smoke testing through the explicit fallback described
above; it must not be reported as the published cytolexmuta benchmark score.
