---
# Model identifier used for referencing this model in the benchmark system
name: pls

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.pls.model:pls"

tags: ["supervised"]

# Multi-target support flag
multi_y: false

# This is a CPU-only model; there is no "device"/GPU hyperparameter
hyper_parameters:
    # Number of PLS components to extract (dimensionality of the reduced space)
    n_components: 2
    # Scale X and Y to unit variance before fitting
    scale: true
    # Maximum number of iterations of the NIPALS inner loop
    max_iter: 500
    # Convergence tolerance of the NIPALS inner loop
    tol: 1.0e-06
    # Aggregation of the per-residue one-hot matrix across positions. null
    # flattens it (one feature per position x symbol, requires fixed-length
    # sequences); "mean"/"max" reduce each sequence to its composition
    pooling: null
    # If false, lowercase insert states are folded into match states before
    # one-hot encoding; if true they get their own alphabet columns
    independent_insertion_alphabet: false
    # Number of instances to one-hot encode at a time
    batch_size: 128
---

# Model Card for PLS Regression (evedesign-wrapped)

This model scores protein sequences with the
`proteingym.models.pls.model.pls` wrapper, an evedesign
`SklearnPredictorOnEmbeddingsScores` that fits scikit-learn's
[`PLSRegression`](https://scikit-learn.org/stable/modules/generated/sklearn.cross_decomposition.PLSRegression.html)
on one-hot encoded sequences. Partial Least Squares (PLS) regression finds
linear combinations of the input features that best explain the covariance
between predictors and response, which makes it well-suited to protein
sequence data where the number of features (positions x amino acids) is large
relative to the number of labelled variants

The wrapper's `build()` one-hot encodes the training instances and fits the
regressor on the labelled training folds; `score()` encodes and predicts the
test instances. Scores are on the same scale and orientation as the training
labels

## Architecture notes

- **No GPU, no pretrained checkpoint.** Features are a fixed one-hot encoding
  computed by evedesign's `OneHotEmbedder` (20 canonical amino acids + gap,
  21 columns per position); the only learned weights are the PLS loadings
- **Positional features.** With the default `pooling: null` the
  `(length x 21)` one-hot matrix is flattened, so every instance must have
  the same length as the target sequence (`requires_fixed_length=True`).
  Setting `pooling` to `"mean"`/`"max"` lifts this restriction but reduces
  each sequence to its amino-acid composition
- **Indels.** Gaps are encoded as their own one-hot column
  (`handles_deletions=True`). Insertions are only meaningful with a pooling
  strategy, since flattened features require fixed length
- **One-hot features only.** `use_scores` is fixed to
  `False`; the model uses one-hot features only

## Citation

```
@article{Pedregosa2011Scikit,
  author = {Pedregosa, F. and Varoquaux, G. and Gramfort, A. and Michel, V. and Thirion, B. and Grisel, O. and Blondel, M. and Prettenhofer, P. and Weiss, R. and Dubourg, V. and Vanderplas, J. and Passos, A. and Cournapeau, D. and Brucher, M. and Perrot, M. and Duchesnay, E.},
  title = {Scikit-learn: Machine Learning in Python},
  journal = {Journal of Machine Learning Research},
  volume = {12},
  pages = {2825--2830},
  year = {2011},
  doi = {10.48550/arXiv.1201.0490}
}
```

## License

scikit-learn (depended on directly, not vendored) is released under the
BSD 3-Clause license. There are no pretrained model weights to separately
license
