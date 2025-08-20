# Containerise model in Docker

## Structure

The basic structure for a supervised and a zero-shot model is listed respectively as below. The difference is that a supervised model has `train.py`, whereas a zero-shot model doesn't have it.

To explain:

* `__main__.py` contains the glue code to load the dataset using `pg2-dataset` and the model manifest using `pg2-benchmark`. Namely, the following two classes are imported and used: `from pg2_dataset.dataset import Dataset` and `from pg2_benchmark.manifest import Manifest`.

* `preprocess.py` contains the data preprocessing code, like encoding and load training or test split of the dataset.

* `train.py` contains the training code, which might use `preprocess.py`'s `encode()` function to encode the data before feeding into the model and the model's `Manifest` to load hyper parameters.

* `predict.py` contains the scoring code, which might use `preprocess.py`'s `encode()` function and model's `Manifest` as well.

### Supervised model

For a supervised model, since it needs to be trained with the training dataset, its source code structure is as below:

```shell
├── __main__.py
├── predict.py
├── preprocess.py
└── train.py
```

### Zero-shot model

For a zero-shot model, since it does not need training, its source code structure is as below:

```shell
├── __main__.py
├── predict.py
├── preprocess.py
```

