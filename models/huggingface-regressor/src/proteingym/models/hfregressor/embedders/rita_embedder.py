from pathlib import Path

import numpy as np
import polars as pl

from sklearn.base import BaseEstimator, TransformerMixin
from transformers import AutoTokenizer, AutoModelForCausalLM

import torch

from proteingym.base.model import ModelCard


class RITAEmbedder(TransformerMixin, BaseEstimator):
    def __init__(self, model_card: ModelCard, data: pl.DataFrame, cache_dir: str):
        self.model_card = model_card
        self.data = data
        self.cache_dir = cache_dir
        self.model = AutoModelForCausalLM.from_pretrained(
            f"lightonai/{self.model_card.hyper_params['embedder_model_name']}",
            trust_remote_code=True,
        )
        self.tokenizer = AutoTokenizer.from_pretrained(
            f"lightonai/{self.model_card.hyper_params['embedder_model_name']}"
        )
        self.embed_dim = {
            "RITA_s": 768,
            "RITA_m": 1024,
            "RITA_l": 1536,
            "RITA_xl": 2048,
        }[self.model_card.hyper_params["embedder_model_name"]]
        self.model.to(torch.get_default_device())

    # noinspection PyUnusedLocal
    def fit(self, x, y=None):
        return self

    def transform(self, transform_data: pl.DataFrame):
        """
        The tokenizer creates a tokenized sequence of length len(sequence) + 1.
        No BOS token is created, but there is an EOS token. When using pooling option
        'last', embeddings are extracted by taking hidden_states[:, -2, :]
        (so only the hidden state of the last residue). When using option 'mean',
        embeddings are extracting by taking the mean of the second hidden states' axis.
        """
        if (Path(self.cache_dir) / "embeddings.npy").is_file():
            embeddings = np.load(Path(self.cache_dir) / "embeddings.npy")
        else:
            embeddings_shape = (len(self.data), 2 * self.embed_dim)
            embeddings = np.empty(embeddings_shape)
            for i, sequence in enumerate(self.data["sequence"]):
                with torch.no_grad():
                    for j, p in enumerate([sequence, sequence[::-1]]):
                        tokenized_sequence = torch.tensor(
                            [self.tokenizer.encode(p)], device=torch.get_default_device()
                        )
                        outputs = self.embed_tokenized_sequence(tokenized_sequence)
                        embeddings[i, self.embed_dim * j : self.embed_dim * (j + 1)] = (
                            outputs
                        )
            np.save(Path(self.cache_dir) / "embeddings.npy", embeddings)
        indices = transform_data["embedding_index"].to_numpy()
        return embeddings[indices]

    def embed_tokenized_sequence(self, tokenized_sequence: torch.tensor):
        return {
            "mean": self.extract_mean_pooled_embeddings,
            "last": self.extract_last_hidden_state_embeddings,
        }[self.model_card.hyper_params["embedder_pooling"]](tokenized_sequence)

    def extract_mean_pooled_embeddings(self, tokenized_sequence: torch.tensor):
        return torch.mean(self.model(tokenized_sequence).hidden_states, dim=1).cpu()

    def extract_last_hidden_state_embeddings(self, tokenized_sequence: torch.tensor):
        return self.model(tokenized_sequence).hidden_states[:, -2, :].cpu()
