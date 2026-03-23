---
# Model identifier used for referencing this model in the benchmark system
name: "kermut"

tags: ["supervised"]

hyper_parameters:
    # Number of training iterations
    n_steps: 150
    # Device to use 
    device: gpu
    # Whether to train in preferential mode or not
    preferential: False
---

# Model Card for pkermut

The Kermut Gaussian process model as reported in 
[Kermut: Composite kernel regression for protein variant effects](https://doi.org/10.48550/arXiv.2407.00002).