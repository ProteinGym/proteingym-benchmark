---
# Model identifier used for referencing this model in the benchmark system
name: gemme

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.gemme.model:gemme"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the gemme.model.gemme wrapper
# constructor
hyper_parameters:
    # Number of JET2 (iJET) resampling iterations used to compute conservation.
    n_iter: 10
    # Maximum number of alignment sequences (including the query) handed to
    # JET2 for conservation computation, truncated from the start of the
    # alignment (matching GEMME's own -N/--NSeqs behaviour). The full alignment
    # is still used for the R-side sequence-count/epistatic computations
    max_sequences: 20000
    # Path to the GEMME directory (containing computePred.R, pred.R,
    # blosum62p.txt, default.conf)
    gemme_path: "/opt/program"
    # Path to the JET2 directory (containing jet/JET.class, jet/extLibs/vecmath.jar)
    jet_path: "/opt/program/JET2"
    # Path to / name of the java binary used to run JET2
    java_binary: "java"
    # Path to / name of the Rscript binary (needs seqinr and RColorBrewer installed)
    rscript_binary: "Rscript"
    # Path to / name of muscle 3.8.x 
    muscle_binary: "muscle"
    # -Xmx value passed to the JET2 JVM invocation
    java_max_heap: "4096m"
---

# Model Card for GEMME (evedesign-wrapped)

This model scores protein sequences with [GEMME](http://www.lcqb.upmc.fr/GEMME/)
(Global Epistasis Model for predicting Mutational Effects), which combines
per-position evolutionary conservation with a "global epistasis" model of
evolutionary distance to homologs to predict mutational effects from a multiple
sequence alignment. Scoring is zero-shot: each position/substitution is assigned
a score from the fitted model, so no labelled training fold is required.
**This is a CPU-only model; it does not use a GPU.**

Because the model conditions on a family alignment, every entity in the benchmark
subset must carry aligned sequences (`required_entity_attributes: ["sequences"]`).
The benchmark entrypoint converts the ProteinGym subset into evedesign datatypes
with `evedesign.proteingym.dataset_to_evedesign`, builds the wrapper on the
resulting `System` (fitting the model from the MSA), and scores the requested test
fold. The wrapper also exposes `single_mutation_scan` and `score_conditional` for
mutation-effect scoring, but does not implement the `Transformer` interface, so
this card is not tagged `embeddable`.

GEMME is described in Laine E, Karami Y, Carbone A. *GEMME: A Simple and Fast
Global Epistatic Model Predicting Mutational Effects*, Molecular Biology and
Evolution, 2019 (https://doi.org/10.1093/molbev/msz179).