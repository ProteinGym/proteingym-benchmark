---
# Model identifier used for referencing this model in the benchmark system
name: "pkermut"

tags: ["supervised"]

hyper_parameters:
    # Number of training iterations
    n_steps: 150
    # Device to use 
    device: gpu
    # True -> Train in preferential mode, False -> Train original version of Kermut
    preferential: True
    # Average degree to which to uniformly subsample the preference graph when training in preferential mode. In this case 2.5
    preference_sampling_strategy: uniform_2.5
---
> [!WARNING]
> Splitting?
> Extra features?

> [!NOTE]
> Note? 
# Model Card for pkermut

PKermut is a preferential version of the Kermut Gaussian process model as reported in 
[Kermut: Composite kernel regression for protein variant effects](https://doi.org/10.48550/arXiv.2407.00002). 
This repository is a fork of the [original repository](https://github.com/petergroth/kermut) 
and contains some modifications that allows users to train and run inference with PKermut: 
Kermut trained with a [preferential objective](https://botorch.readthedocs.io/en/latest/models.html#module-botorch.models.pairwise_gp).