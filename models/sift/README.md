---
# Model identifier used for referencing this model in the benchmark system
name: sift

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

hyper_parameters:
    # Path to the SIFT 6.x install directory (must contain `bin/info_on_seqs`
    # and `blimps/docs/`). Defaults to the canonical install location used by
    # the upstream Ensembl Docker image (`ensemblorg/sift:6.2.1`).
    sift_home: "/opt/sift"
    # Path to the BLIMPS directory used by SIFT for default matrices and
    # frequency tables. The `docs/` subfolder must contain `default.qij` and
    # related files. Defaults to `<sift_home>/blimps`.
    blimps_dir: "/opt/sift/blimps"
    # Keep the parsed PSSM associated with the model instance after build().
    # Set to false to reduce memory when serializing.
    keep_pssm_after_build: true
---

# Model Card for SIFT

SIFT (Sorting Intolerant From Tolerated;
[Ng & Henikoff, *Nucleic Acids Research*, 2003](https://doi.org/10.1093/nar/gkg509);
[Kumar, Henikoff & Ng, *Nat. Protoc.*, 2009](https://doi.org/10.1038/nprot.2009.86))
predicts whether an amino acid substitution affects protein function based on
sequence conservation in a multiple sequence alignment of homologs.

This wrapper drives the academic `info_on_seqs` binary distributed with the
standalone SIFT 6.x release (http://sift-dna.org). Given an aligned FASTA,
SIFT emits a position-specific scoring matrix (PSSM) of normalized amino
acid probabilities. The wrapper parses that PSSM into a `(L, 20)` numpy
matrix in `VALID_AA_SORTED` column order and uses it to score
substitutions, conditional positions, and full sequences (as a tractable
product-of-marginals lower bound on the joint log-likelihood).

SIFT is zero-shot, requires no experimental labels, and does not support
insertions or deletions: each test sequence must be aligned to and the same
length as the wildtype.

## Reference

- Kumar, Henikoff & Ng, "Predicting the effects of coding non-synonymous
  variants on protein function using the SIFT algorithm",
  *Nature Protocols*, 2009.
  doi:[10.1038/nprot.2009.86](https://doi.org/10.1038/nprot.2009.86)
- Ng & Henikoff, "Predicting deleterious amino acid substitutions",
  *Genome Research*, 2001.
  doi:[10.1101/gr.176601](https://doi.org/10.1101/gr.176601)
- Source distribution: http://sift-dna.org
- Upstream container: `ensemblorg/sift:6.2.1`

## Runtime requirements

SIFT's reference implementation depends on:

- The `info_on_seqs` binary from the SIFT 6.x release
- The BLIMPS data directory (`blimps/docs/default.qij`, etc.)
- An `x86_64` runtime (the upstream binary is not built for ARM)

The provided `Dockerfile` extends `ensemblorg/sift:6.2.1`, which ships
SIFT 6.2.1 with `info_on_seqs` at `/opt/sift/bin/info_on_seqs` and the
BLIMPS data at `/opt/sift/blimps`. On Apple Silicon hosts the image runs
under Rosetta/QEMU emulation via `--platform=linux/amd64`.

## Notes

- The companion `SIFTINDEL` tool is **not** wrapped: it is a human-exome
  indel pipeline (requires Ensembl coding tables and a GRCh37/38
  reference) and is not a general protein indel predictor.
- Probabilities below `1e-4` are floored before taking `log(p)` to keep
  predictions finite. SIFT's deleterious/tolerated decision threshold is
  `0.05`, comfortably above the floor.
- Positions with fewer than two distinct amino acids in the MSA are
  reported as `NOT SCORED` and surface as all-NaN rows in the PSSM.
- The implementation at `src/proteingym/models/sift/model.py` is the
  original wrapper around SIFT built for the `evedesign` framework. The
  ProteinGym CLI entrypoint (`__main__.py`, plus `preprocess.py` /
  `utils.py`) that adapts this wrapper to the benchmark contract will be
  added in a follow-up.
