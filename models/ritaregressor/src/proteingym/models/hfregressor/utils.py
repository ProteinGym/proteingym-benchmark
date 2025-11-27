from pathlib import Path
from typing import List, Union
from loguru import logger

import polars as pl
from sklearn.base import BaseEstimator, TransformerMixin
from sklearn.compose import ColumnTransformer
from sklearn.exceptions import NotFittedError
from sklearn.preprocessing import OneHotEncoder, StandardScaler


class ExtraFeatures(TransformerMixin, BaseEstimator):
    def __init__(
        self,
        numerical_extra_features: List[str],
        categorical_extra_features: List[str],
        categories: Union[str, List[List[str]]] = "auto",
    ):
        self.numerical_extra_features = numerical_extra_features
        self.categorical_extra_features = categorical_extra_features
        self.categories = categories
        self._n_features_after_transformation = None

        self._pipeline = ColumnTransformer(
            [
                ("numerical", StandardScaler(), numerical_extra_features),
                (
                    "categorical",
                    OneHotEncoder(categories=self.categories, sparse=False),
                    categorical_extra_features,
                ),
            ]
        )

    # noinspection PyUnusedLocal
    def fit(self, x: pl.DataFrame, y=None):
        x_transformed = self._pipeline.fit_transform(x)
        self._n_features_after_transformation = x_transformed.shape[1]
        return self

    def transform(self, x: pl.DataFrame):
        return self._pipeline.transform(x)

    @property
    def n_output_features(self):
        try:
            return self._n_features_after_transformation
        except AttributeError as e:
            raise NotFittedError("ExtraFeatures") from e
