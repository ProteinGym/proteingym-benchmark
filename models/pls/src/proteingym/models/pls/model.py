"""
Wrapper class around scikit-learn's PLSRegression
(https://scikit-learn.org/stable/modules/generated/sklearn.cross_decomposition.PLSRegression.html)
on one-hot encoded sequences
"""
from typing import Literal, Self

from sklearn.cross_decomposition import PLSRegression

from evedesign.dataset import LabeledInstanceDataset
from evedesign.models.embedders import OneHotEmbedder
from evedesign.models.supervised import SklearnPredictorOnEmbeddingsScores
from evedesign.system import System
from evedesign.types import BatchSize, StatusCallback


class pls(SklearnPredictorOnEmbeddingsScores):
    """
    Partial Least Squares regression on per-residue one-hot sequence encodings.
    """
    name: str = "pls"
    citations: list[str] = [
        # Scikit-learn: Machine Learning in Python
        "10.48550/arXiv.1201.0490",
    ]

    def __init__(
        self,
        n_components: int = 2,
        scale: bool = True,
        max_iter: int = 500,
        tol: float = 1e-06,
        pooling: Literal["mean", "max"] | None = None,
        independent_insertion_alphabet: bool = False,
        batch_size: BatchSize = 128,
    ):
        """
        Instantiate a PLS model.

        Parameters
        ----------
        n_components
            Number of PLS components to extract (dimensionality of the reduced
            space)
        scale
            Whether to scale X and Y to unit variance before fitting
        max_iter
            Maximum number of iterations of the NIPALS inner loop
        tol
            Convergence tolerance of the NIPALS inner loop
        pooling
            Aggregation of the per-residue one-hot matrix across positions. None
            (default) flattens it, keeping one feature per (position, symbol)
            pair; this requires every instance to have the target's length.
            "mean"/"max" reduce each sequence to its composition and drop all
            positional information
        independent_insertion_alphabet
            Forwarded to OneHotEmbedder. If False (default), lowercase insert
            states are folded into match states before encoding
        batch_size
            Number of instances to one-hot encode at a time
        """
        embedder = OneHotEmbedder(
            merge_alphabets=True,
            independent_insertion_alphabet=independent_insertion_alphabet,
        )

        # flattened per-position features only line up if every instance has
        # the same length - let the embedder's instance validation enforce it
        if pooling is None:
            embedder.requires_fixed_length = True

        super().__init__(
            predictor=PLSRegression(
                n_components=n_components,
                scale=scale,
                max_iter=max_iter,
                tol=tol,
            ),
            embedder=embedder,
            scorer=None,
            use_embeddings=True,
            use_scores=False,
            target_name=None,
            pooling=pooling,
            batch_size=batch_size,
        )

        self.n_components = n_components
        self.scale = scale
        self.max_iter = max_iter
        self.tol = tol
        self.independent_insertion_alphabet = independent_insertion_alphabet

    def build(
        self,
        system: System,
        data: LabeledInstanceDataset,
        status_callback: StatusCallback | None = None,
    ) -> Self:
        # the embedder holds no learned state, so it is built here on the same
        # system rather than outside as evedesign's convention expects
        self.embedder.build(system)

        return super().build(system, data=data, status_callback=status_callback)
