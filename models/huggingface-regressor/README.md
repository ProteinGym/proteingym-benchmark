---
# Model identifier used for referencing this model in the benchmark system
name: "ritaregressor"

# Import string for the evedesign model class to run. Accepts entry-point style
# ("package.module:ClassName") or fully dotted style ("package.module.ClassName").
# The class is instantiated with hyper_parameters, then built and scored.
model_class: "proteingym.models.hfregressor.model:hfregressor"

tags: ["supervised"]

# Multi-target support flag
multi_y: false

hyper_parameters:
    # Which embedder family to use (currently only "RITA")
    huggingface_model_name: RITA
    # Which checkpoint of that family to load from huggingface
    # (RITA_s, RITA_m, RITA_l or RITA_xl)
    embedder_model_name: RITA_s
    # How to pool per-residue embeddings across the sequence ("mean" or "last")
    embedder_pooling: mean
    # Alpha regularization parameter for the ridge regression model
    alpha: 1.0
    # Torch device for computing embeddings; "cuda" falls back to CPU if no
    # GPU is available
    device: cuda
    # Number of instances to embed at a time
    batch_size: 128
---

> [!NOTE]
> To fit within the compute budget of the CI runner, this model uses RITA_s instead of the more powerful RITA_xl.

# Model Card for ritaregressor (evedesign-wrapped)

The ritaregressor is a supervised VEP model that produces sequence embeddings
and fits those to properties by ridge regression. It runs through the
`proteingym.models.hfregressor.model.hfregressor` wrapper, an evedesign
`SklearnPredictorOnEmbeddingsScores` that fits scikit-learn's
[`Ridge`](https://scikit-learn.org/stable/modules/generated/sklearn.linear_model.Ridge.html)
on embeddings from the `RITAEmbedder` transformer. The sequence embeddings are
obtained using [RITA](https://huggingface.co/lightonai/RITA_xl), which was
first described in
[RITA: a Study on Scaling Up Generative Protein Sequence Models](https://arxiv.org/abs/2205.05789)

The wrapper's `build()` embeds the training instances and fits the regressor on
the labelled training folds; `score()` embeds and predicts the given instances.
Scores are on the same scale and orientation as the training labels

## Architecture notes

- **Pretrained embedder, learned ridge head.** RITA weights are downloaded from
  huggingface (`lightonai/<embedder_model_name>`) at `build()` time and are not
  fine-tuned; the only learned weights are the ridge coefficients
- **Embedding.** Each sequence and its reverse are embedded separately and
  pooled across positions (`embedder_pooling`); the two pooled vectors are
  concatenated into one feature vector of size 2 x hidden size (768 for
  RITA_s up to 2048 for RITA_xl)
- **Variable length / indels.** Embeddings are pooled, so sequences may differ
  in length (`requires_fixed_length=False`). RITA has no gap token, so gap
  symbols are removed and insert states are upper-cased before embedding
- **GPU optional.** `device: cuda` uses a GPU if one is visible and otherwise
  falls back to CPU (`requires_gpu=False`, `supports_gpu=True`). With
  Singularity, run the container with `--nv` to expose the GPU
- **Embedding cache.** Embeddings are cached in memory per sequence, so
  sequences scored more than once in a run are only embedded once

## Citation

```
@article{Hesslow2022RITA,
  author = {Hesslow, Daniel and Zanichelli, Niccol{\'o} and Notin, Pascal and Poli, Iacopo and Marks, Debora},
  title = {RITA: a Study on Scaling Up Generative Protein Sequence Models},
  journal = {arXiv preprint arXiv:2205.05789},
  year = {2022},
  doi = {10.48550/arXiv.2205.05789}
}

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

The RITA model weights are downloaded from huggingface (`lightonai`) at run
time and are not redistributed here; see the model pages for their license.
scikit-learn (depended on directly, not vendored) is released under the BSD
3-Clause license
