---
# Model identifier used for referencing this model in the benchmark system
name: gemme

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.gemme.model:GEMME"

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

# Hyperparameters are forwarded verbatim to the GEMME wrapper constructor
# (proteingym.models.gemme.model.GEMME). CPU-only; there is no "device" option.
hyper_parameters:
    # Number of JET2 (iJET) resampling iterations used to compute conservation
    # (10 matches GEMME's published usage)
    n_iter: 10
    # Maximum number of alignment sequences (including the query) passed to JET2
    max_sequences: 20000
    # Path to the GEMME directory (computePred.R, pred.R, default.conf, ...)
    gemme_path: "/opt/program/GEMME"
    # Path to the JET2 directory (jet/JET.class, jet/extLibs/vecmath.jar)
    jet_path: "/opt/program/JET2"
    # Path to / name of the java binary used to run JET2
    java_binary: "java"
    # Path to / name of the Rscript binary (needs seqinr and RColorBrewer)
    rscript_binary: "Rscript"
    # Path to / name of muscle 3.8.x (muscle 5.x does not work with JET2)
    muscle_binary: "muscle"
    # -Xmx value passed to the JET2 JVM
    java_max_heap: "4096m"
---

# Model Card for GEMME (evedesign-wrapped)

GEMME (Global Epistatic Model for predicting Mutational Effects) is an MSA-based
model that combines per-position conservation from JET2 with a "global epistasis"
model of evolutionary distance to homologs. It needs a multiple sequence alignment
of the target family; no structure is used. Each substitution gets GEMME's evolEpi
score, and a sequence is scored as the sum over its residues. Substitutions only;
insertions and deletions are not scored. CPU-only.

For details on the method, see the [paper](https://doi.org/10.1093/molbev/msz179).

## Citation

```
@article{Laine2019GEMME,
  author = {Laine, Elodie and Karami, Yasaman and Carbone, Alessandra},
  title = {GEMME: A Simple and Fast Global Epistatic Model Predicting Mutational Effects},
  journal = {Molecular Biology and Evolution},
  volume = {36},
  number = {11},
  pages = {2604--2619},
  year = {2019},
  doi = {10.1093/molbev/msz179}
}
```

## License

GEMME is released under the MIT license (see `LICENSE`). JET2 and muscle are
downloaded at build time and are not redistributed in this folder.
