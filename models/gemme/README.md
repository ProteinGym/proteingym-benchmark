---
# Model identifier used for referencing this model in the benchmark system
name: gemme

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

hyper_parameters:
    # Path to the GEMME source root containing `gemme.py`. Defaults to the
    # canonical install location used by the upstream Docker image.
    gemme_path: "/opt/GEMME"
    # Path to the JET2 source root used by GEMME for conservation analysis.
    jet_path: "/opt/JET2"
    # Python interpreter used to invoke GEMME. The reference implementation
    # is Python 2.7 only and is preinstalled on the upstream Docker image.
    python_command: "python2.7"
    # Number of JET iterations used to compute conservation levels. The
    # paper recommends 1 (default); bump to 7-10 to mitigate JET's
    # stochasticity at higher runtime cost.
    n_iter: 1
    # Maximum number of MSA sequences passed to JET. Larger MSAs are
    # subsampled. 20000 matches the upstream web server.
    n_seqs: 20000
    # GEMME output table to load:
    # - "combi": combined independent + epistatic (recommended)
    # - "epi": epistatic only
    # - "ind": independent / per-position only
    model_variant: "combi"
    # Keep the temporary GEMME working directory around for inspection.
    keep_workdir: false
---

# Model Card for GEMME

GEMME (Global Epistatic Model for predicting Mutational Effects;
[Laine, Karami, Carbone, MBE 2019](https://doi.org/10.1093/molbev/msz179))
predicts the mutational landscape of a protein from a single multiple
sequence alignment. It combines per-position evolutionary conservation
(estimated with JET2) with a global epistatic term that summarises how far
each homologous sequence is from the query in conservation-weighted Hamming
distance. The output is a 20 x L matrix of log-odds-like scores normalised
so that the wildtype residue at each position has score 0.

GEMME is zero-shot: it does not require any experimental labels and scores
substitutions directly from sequence-only homology information. It does not
support insertions or deletions and requires a fixed-length MSA aligned to
the wildtype.

## Reference

- Laine et al., "GEMME: A Simple and Fast Global Epistatic Model Predicting
  Mutational Effects", *Molecular Biology and Evolution*, 2019.
  doi:[10.1093/molbev/msz179](https://doi.org/10.1093/molbev/msz179)
- Source distribution: https://www.lcqb.upmc.fr/GEMME/download.html
- Upstream container: `elodielaine/gemme:gemme`

## Runtime requirements

GEMME's reference implementation depends on:

- Python 2.7 (drives `gemme.py` itself)
- R (used internally by GEMME for the epistatic computation)
- Java (used by JET2 for conservation analysis)
- A copy of the GEMME source tree at `GEMME_PATH` (default `/opt/GEMME`)
- A copy of the JET2 source tree at `JET_PATH` (default `/opt/JET2`)

The provided `Dockerfile` extends the upstream `elodielaine/gemme:gemme`
image, which ships all of the above pre-installed at the canonical paths.

## Model variants

| `model_variant` | Output file               | Description                                   |
|-----------------|---------------------------|-----------------------------------------------|
| `combi`         | `normPred_evolCombi.txt`  | Combined independent + epistatic (default).   |
| `epi`           | `normPred_evolEpi.txt`    | Epistatic component only.                     |
| `ind`           | `normPred_evolInd.txt`    | Per-position (independent) component only.    |

## Notes

The implementation at `src/proteingym/models/gemme/model.py` is the original
wrapper around GEMME built for the `evedesign` framework. The ProteinGym
CLI entrypoint (`__main__.py`, plus `preprocess.py`/`utils.py`) that adapts
this wrapper to the benchmark contract will be added in a follow-up.
