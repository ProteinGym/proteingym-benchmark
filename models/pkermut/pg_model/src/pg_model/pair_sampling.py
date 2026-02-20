from abc import ABC, abstractmethod
from typing import Union
from loguru import logger
import random

import networkx as nx

import numpy as np
import pandas as pd


def sample_preferences(labels: np.ndarray, strategy: Union[str, float]) -> np.ndarray:
    preferences = list(zip(*np.where(labels[:, None] > labels)))
    n_initial_preferences = len(preferences)

    if strategy == "prune":
        preferences = prune_comparisons(preferences)
    elif isinstance(strategy, float):
        preferences = subsample_preferences(preferences, fraction=strategy)
    else:
        raise ValueError("No valid preference sampling strategy provided.")
    logger.info(f"Reduced {n_initial_preferences} to {len(preferences)} preferences.")
    return np.vstack(preferences)


def prune_comparisons(preferences: list) -> list:
    logger.info(f"Pruning preferences.")
    graph = nx.DiGraph()
    graph.add_edges_from(preferences)

    if not nx.is_directed_acyclic_graph(graph):
        raise ValueError("preferences graph is not a directed acyclic graph.")
    graph_reduced = nx.transitive_reduction(graph)
    reduced_preferences = list(graph_reduced.edges())
    return reduced_preferences


def subsample_preferences(preferences: list, fraction: float) -> list:
    subsample = int(fraction * len(preferences))
    logger.info(f"Subsampling {subsample} preferences.")
    subsampled_preferences = random.sample(preferences, subsample)
    return subsampled_preferences


class PairSampler(ABC):
    @abstractmethod
    def sample(self, labels: np.array) -> list:
        pass

    @staticmethod
    def get_preference_matrix(labels: np.array) -> np.array:
        matrix = labels[:, None] > labels
        return matrix


class UniformPairSampler(PairSampler):
    """Samples preferences uniformly to reach an average degree, i.e., the average number
    of comparisons per variant."""

    def __init__(self, average_degree: int):
        self.average_degree = average_degree

    def sample(self, labels: np.array) -> list:
        n_samples = int(len(labels) * self.average_degree)
        preference_matrix = self.get_preference_matrix(labels)
        preferences = list(zip(*np.where(preference_matrix)))
        logger.info(f"Subsampling {n_samples} preferences.")
        subsampled_preferences = random.sample(preferences, n_samples)
        return subsampled_preferences


class TopVsAllSampler(PairSampler):
    """Sample only preference pairs that include the top k variants"""

    def __init__(self, top_k: int):
        self.top_k = top_k

    def sample(self, labels: np.array) -> list:
        threshold = np.sort(labels)[::-1][self.top_k]
        preference_matrix = self.get_preference_matrix(labels)
        mask = (labels > threshold).reshape(-1, 1)
        masked_matrix = preference_matrix * mask
        subsampled_preferences = list(zip(*np.where(masked_matrix)))
        logger.info(f"Subsampling {len(subsampled_preferences)} preferences.")
        return subsampled_preferences


class FullSampler(PairSampler):
    def sample(self, labels: np.array) -> list:
        preference_matrix = self.get_preference_matrix(labels)
        all_preferences = list(zip(*np.where(preference_matrix)))
        logger.info("Returning all preferences.")
        return all_preferences


class BatchSampler(PairSampler):
    def __init__(self, batch_labels: pd.Series, split: pd.Series) -> None:
        self.batch_labels = batch_labels
        self.split = split
        self.average_degree = 10

    def sample(self, labels: np.array) -> list:
        n_samples = int(len(labels) * self.average_degree)
        batch_labels_train = self.batch_labels[self.split == "train"].values
        all_preferences = self.get_preference_matrix(labels)
        comparison_allowed = batch_labels_train[:, None] == batch_labels_train
        preference_matrix = comparison_allowed * all_preferences
        preferences = list(zip(*np.where(preference_matrix)))
        logger.info(f"Subsampling {n_samples} preferences.")
        subsampled_preferences = random.sample(preferences, n_samples)
        return subsampled_preferences


def pair_sampling_factory(strategy: str, **kwargs) -> PairSampler:
    if strategy.startswith("uniform_"):
        param = eval(strategy.strip("uniform_"))
        return UniformPairSampler(average_degree=param)
    elif strategy.startswith("top_vs_all_"):
        param = eval(strategy.strip("top_vs_all_"))
        return TopVsAllSampler(top_k=param)
    elif strategy == "all" or strategy.startswith("bo"):
        return FullSampler()
    elif strategy == "batch":
        return BatchSampler(**kwargs)
