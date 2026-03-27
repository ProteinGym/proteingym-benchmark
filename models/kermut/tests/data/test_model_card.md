---
# Model identifier used for referencing this model in the benchmark system
name: "kermut"

tags: ["supervised"]

hyper_parameters:
    # Number of training iterations
    n_steps: 2
    # Device to use 
    device: cpu
---

# Model Card for kermut


The Kermut Gaussian process model as reported in 
[Kermut: Composite kernel regression for protein variant effects](https://doi.org/10.48550/arXiv.2407.00002).

Implementation: https://github.com/florisvdf/kermut-package