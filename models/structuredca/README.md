---
# Model identifier used for referencing this model in the benchmark system
name: structuredca

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.structuredca.model:StructureDCA"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the structuredca StructureDCA
# wrapper constructor, so every key here must be a valid StructureDCA.__init__
# argument. This is a CPU-only model
hyper_parameters:
    # Use StructureDCA's RSA-complement reweighting for all scores
    reweight_by_rsa: false
    # Distance threshold (Angstrom) for a residue-residue contact to be kept in
    # the sparse DCA model
    distance_cutoff: 8.0
    # Remove contacts at positions with low structure confidence
    use_contacts_plddt_filter: false
    contacts_plddt_cutoff: 70.0
    contacts_plddt_keep_window: 1
    # Drop contacts at MSA positions with gap-ratio above this threshold
    contacts_gap_cutoff: null
    # L2 regularisation strength on fields h_i and couplings J_ij
    lambda_h: 1.0
    lambda_J: 1.0
    # Asymptotic correction to the L2 regularisation (as Neff -> +inf)
    lambda_asymptotic: 0.001
    # Frequency-level regularisation, only used to initialise fields h
    theta_regularization: 0.10
    # If true, gap is excluded as a modelled state (cannot be scored then)
    exclude_gaps: true
    # Discard MSA sequences below this identity to the target (null = no filter)
    min_seqid: 0.25
    # Sequence-identity clustering threshold for reweighting (null = no reweighting)
    weights_seqid: 0.90
    count_target_sequence: true
    # Solver settings
    num_threads: 4
    max_iterations: 2000
    use_sparse_J: true
    # Caching paths (null = no caching, always refit)
    distance_cache_path: null
    rsa_cache_path: null
    weights_cache_path: null
    dca_cache_path: null
    # Logging
    verbose: false
    log_gd_steps: false
    disable_warnings: false
    disable_log_colors: false
    disable_solver_logs: false
---

# Model Card for StructureDCA (evedesign-wrapped)

This model scores protein sequences with the [structuredca](.)
`StructureDCA` wrapper around the
[StructureDCA](https://github.com/3BioCompBio/StructureDCA) package, which fits a
sparse, structure-informed pairwise Potts model of a protein family: couplings 
between residue pairs that are not in structural contact (per a residue distance 
cutoff on a provided 3D structure) are constrained to zero. Scoring is zero-shot: 
each sequence is assigned its (negative) statistical energy under the fitted model, 
no labelled training fold is required.

## License

StructureDCA's code (the `structuredca` PyPI package, depended on directly, not
vendored) is released under the MIT license, Copyright (c), 2026, Matsvei
Tsishyn, Hugo Talibart, Marianne Rooman and Fabrizio Pucci (see `LICENSE`,
copied verbatim from the upstream repository). There are no model weights to
separately license
