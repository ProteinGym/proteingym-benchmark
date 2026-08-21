---
# Model identifier used for referencing this model in the benchmark system
name: evmutation

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "evedesign.models.evcouplings:EVcouplingsPLM"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the evedesign EVcouplingsPLM wrapper
# constructor (evedesign.models.evcouplings.EVcouplingsPLM), so every key here
# must be a valid EVcouplingsPLM.__init__ argument. This is a CPU-only model-
# there is no "device" hyperparameter.
hyper_parameters:
    # Alignment columns whose (unweighted) gap frequency strictly exceeds this
    # threshold are excluded from the fitted model
    max_gap_fraction: 0.5
    # Sequence reweighting identity threshold, used as a fallback when the
    # provided Sequences carry no precomputed weights. Leave null to require
    # precomputed weights on the input. (will fail if no weights + null)
    theta: 0.8
    # L2 regularisation strength on fields h_i
    lambda_h: 0.01
    # L2 regularisation strength on couplings J_ij, before scaling
    lambda_J: 0.01
    # Scale lambda_J by the number of states and modelled positions, as in
    # standard EVcouplings/plmc practice
    lambda_J_times_Lq: true
    # Group L1 regularisation strength on couplings (null = plmc default)
    lambda_group: null
    # Scale weights of sequence clusters by this value (null = plmc default)
    scale_clusters: null
    # Maximum L-BFGS iterations
    iterations: 100
    # If true, exclude gaps from parameter inference (gaps then cannot be scored)
    ignore_gaps: false
    # If true, fit an independent-site (no couplings) model instead
    independent_model: false
    # Path to / name of the plmc binary; "plmc" resolves via PATH
    plmc_binary: "plmc"
    # Number of CPU cores for plmc to use ("max" or an integer). Requires an
    # OpenMP-compiled plmc, which is how this model's container builds it
    cpu: "max"
---

# Model Card for EVmutation (evedesign-wrapped)

This model scores protein sequences with the [evedesign](https://github.com/evedesignbio/evedesign)
`EVcouplingsPLM` wrapper, which fits a pairwise Potts model of a protein family
from a multiple sequence alignment using the plmc pseudo-likelihood solver. 
Scoring is zero-shot: each sequence is assigned its statistical energy 
(Hamiltonian) under the fitted model.

The wrapper also exposes `single_mutation_scan` and `score_conditional` for 
mutation-effect and conditional scoring, but does not implement the `Transformer` 
interface, so this card is not tagged `embeddable`.

For detailed information on the underlying method, please refer to
the [EVmutation paper](https://doi.org/10.1038/nbt.3769) (Hopf et al., *Nature
Biotechnology* 2017) and the [EVcouplings framework paper](https://doi.org/10.1093/bioinformatics/bty862)
(Hopf et al., *Bioinformatics* 2019).

The wrapper implements two fitting engines: `EVcouplingsMeanField` (mean-field
DCA, pure Python/numpy, no external dependency) and `EVcouplingsPLM` (used by
this card), which fits with the pseudo-likelihood solver via the external
`plmc` binary ([debbiemarkslab/plmc](https://github.com/debbiemarkslab/plmc)).

Regularisation strengths (`lambda_h`, `lambda_J`, `lambda_group`) and sequence
reweighting (`theta`, or precomputed weights on the input) govern the
statistical quality of the fit; `max_gap_fraction` controls which alignment
columns are modelled at all. `iterations` and `independent_model` trade fit
quality/runtime against a simpler, couplings-free baseline.
