---
# Model identifier used for referencing this model in the benchmark system
name: "pkermut"

tags: ["supervised"]

hyper_parameters:
    # Number of training iterations
    n_steps: 2
    # Device to use 
    device: cpu
    # Whether to train in preferential mode or not
    preferential: False
---

# Model Card for pkermut

PKermut is a preferential version of the Kermut Gaussian process model as reported in 
[Kermut: Composite kernel regression for protein variant effects](https://doi.org/10.48550/arXiv.2407.00002). 
This repository is a fork of the [original repository](https://github.com/petergroth/kermut) 
and contains some modifications that allows users to train and run inference with PKermut: 
Kermut trained with a [preferential objective](https://botorch.readthedocs.io/en/latest/models.html#module-botorch.models.pairwise_gp).