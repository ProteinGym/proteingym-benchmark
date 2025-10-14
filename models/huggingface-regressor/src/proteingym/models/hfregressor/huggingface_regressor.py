import polars as pl

from sklearn.base import BaseEstimator
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.linear_model import Ridge

from proteingym.base.model import ModelCard

from .embedders.rita_embedder import RITAEmbedder


class HuggingFaceRegressor(BaseEstimator):
    def __init__(
        self,
        model_card: ModelCard,
        data: pl.DataFrame,
        cache_dir: str,
    ):
        self.model_card = model_card
        self.data = data
        self.cache_dir = cache_dir
        self.pipeline = self.build_pipeline()

    def build_pipeline(self):
        embedder = {"RITA": RITAEmbedder}[
            self.model_card.hyper_params["huggingface_model_name"]
        ](model_card=self.model_card, data=self.data, cache_dir=self.cache_dir)
        column_transformer = ColumnTransformer(
            [
                (
                    "sequence",
                    embedder,
                    ["sequence", "embedding_index"],
                ),
                # WIP
                # (
                #     "extra_features",
                #     ExtraFeatures(
                #         self.numerical_extra_features,
                #         self.categorical_extra_features,
                #     ),
                #     self.numerical_extra_features + self.categorical_extra_features,
                # ),
            ]
        )
        regressor = Ridge(alpha=self.model_card.hyper_params["alpha"])
        pipeline = Pipeline(
            steps=[
                ("column_transformer", column_transformer),
                ("regressor", regressor),
            ]
        )
        return pipeline

    def fit(self, data: pl.DataFrame):
        self.pipeline.fit(data, data["target"])

    def predict(self, data: pl.DataFrame):
        return self.pipeline.predict(data)
