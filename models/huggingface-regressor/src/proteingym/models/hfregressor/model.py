"""
Ridge regression on pooled embeddings from HuggingFace protein language models
(currently RITA, https://huggingface.co/lightonai)
"""
from typing import Any, Literal, Self, Sequence

import numpy as np
import torch
from loguru import logger
from sklearn.linear_model import Ridge
from transformers import AutoModelForCausalLM, AutoTokenizer

from evedesign.constants import GAP
from evedesign.dataset import LabeledInstanceDataset
from evedesign.model import BaseModel, Transformer
from evedesign.models.supervised import SklearnPredictorOnEmbeddingsScores
from evedesign.system import System, SystemInstance
from evedesign.types import BatchSize, StatusCallback


def _resolve_device(device: str) -> str:
    """Fall back to CPU if CUDA is requested but unavailable."""
    if device == "cuda" and not torch.cuda.is_available():
        logger.warning("No cuda enabled GPUs available, falling back to CPU")
        return "cpu"
    return device


class RITAEmbedder(BaseModel, Transformer):
    """
    Transforms protein sequences into fixed-size RITA embeddings.

    Each sequence and its reverse are embedded separately and pooled across
    positions; the two pooled vectors are concatenated into one vector of size
    2 x hidden size, stored as EntityInstance.embedding.
    """
    name: str = "RITA"
    citations: list[str] = [
        # RITA: a Study on Scaling Up Generative Protein Sequence Models
        "10.48550/arXiv.2205.05789",
    ]

    requires_target: bool = False
    requires_fixed_length: bool = False
    handles_deletions: bool = True
    handles_insertions: bool = True
    requires_gpu: bool = False
    supports_gpu: bool = True
    supports_gpu_parallel: bool = False
    supports_cpu_parallel: bool = False

    required_entity_attributes: list[str] | None = []
    optional_entity_attributes: list[str] | None = []

    def __init__(
        self,
        model_name: Literal["RITA_s", "RITA_m", "RITA_l", "RITA_xl"] = "RITA_s",
        pooling: Literal["mean", "last"] = "mean",
        device: str = "cpu",
    ):
        """
        Parameters
        ----------
        model_name
            RITA checkpoint to load from huggingface (lightonai/<model_name>)
        pooling
            "mean" averages hidden states over all tokens; "last" takes the
            hidden state of the last residue (the token before EOS)
        device
            Torch device to run the model on; "cuda" falls back to CPU if no
            GPU is available
        """
        self.model_name = model_name
        self.pooling = pooling
        self.device = device

        self._system: System | None = None
        self._model = None
        self._tokenizer = None
        # sequence -> embedding, so sequences scored more than once (e.g.
        # training folds at build and score time) are only embedded once
        self._cache: dict[str, np.ndarray] = {}

    @property
    def system(self) -> System | None:
        return self._system

    @property
    def ready(self) -> bool:
        return self._system is not None and self._model is not None

    @classmethod
    def can_model(cls, system: System, data: Any = None) -> tuple[bool, str]:
        if data is not None:
            return False, "Model does not take data"

        if len(system) == 0 or not all(entity.type == "protein" for entity in system):
            return False, "All entities in the system must be proteins"

        return True, ""

    def build(
        self,
        system: System,
        data: None = None,
        status_callback: StatusCallback | None = None,  # noqa
    ) -> Self:
        self.can_model_or_raise(system, data)
        self._system = system
        self._load()

        return self

    def _load(self) -> None:
        """Load the pretrained tokenizer and model from huggingface."""
        self.device = _resolve_device(self.device)
        self._tokenizer = AutoTokenizer.from_pretrained(f"lightonai/{self.model_name}")
        self._model = AutoModelForCausalLM.from_pretrained(
            f"lightonai/{self.model_name}",
            trust_remote_code=True,
        )
        self._model.to(self.device)
        self._model.eval()

    def __getstate__(self) -> dict[str, Any]:
        # RITA's remote code contains TorchScript functions that cannot be
        # pickled; the weights are pretrained and unchanged, so drop them and
        # reload from huggingface on unpickling
        state = self.__dict__.copy()
        state["_model"] = None
        state["_tokenizer"] = None
        return state

    def __setstate__(self, state: dict[str, Any]) -> None:
        self.__dict__.update(state)
        if self._system is not None:
            self._load()

    def _embed(self, sequence: str) -> np.ndarray:
        """
        Embed a sequence and its reverse. The tokenizer adds an EOS token but no
        BOS token, so "last" pooling takes hidden_states[:, -2, :].
        """
        parts = []
        with torch.no_grad():
            for seq in (sequence, sequence[::-1]):
                tokens = torch.tensor(
                    [self._tokenizer.encode(seq)], device=self.device
                )
                hidden_states = self._model(tokens).hidden_states
                if self.pooling == "mean":
                    pooled = torch.mean(hidden_states, dim=1)
                elif self.pooling == "last":
                    pooled = hidden_states[:, -2, :]
                else:
                    raise ValueError(f"Invalid pooling strategy: {self.pooling!r}")
                parts.append(pooled.squeeze(0).float().cpu().numpy())

        return np.concatenate(parts).astype(np.float64)

    def transform(
        self,
        instances: Sequence[SystemInstance],
        entity: int | None = None,
        status_callback: StatusCallback | None = None,
    ) -> list[SystemInstance]:
        self.ready_or_raise()
        self._validate_instances(instances)

        target_entities = range(len(self.system)) if entity is None else [entity]

        transformed_instances = []
        for inst_idx, instance in enumerate(instances):
            # shallow copy to avoid mutating input
            new_instance = instance.copy()

            for entity_idx in target_entities:
                entity_instance = new_instance[entity_idx]
                # RITA has no gap token: deleted residues are simply absent,
                # and insert states are embedded as regular residues
                sequence = "".join(entity_instance.rep).replace(GAP, "").upper()
                if sequence not in self._cache:
                    self._cache[sequence] = self._embed(sequence)
                entity_instance.embedding = self._cache[sequence]

            transformed_instances.append(new_instance)

            if status_callback is not None:
                progress = ((inst_idx + 1) / len(instances)) * 100
                status_callback(
                    "running", progress, f"Embedded instance {inst_idx + 1}/{len(instances)}"
                )

        return transformed_instances


class hfregressor(SklearnPredictorOnEmbeddingsScores):
    """
    Ridge regression on pooled HuggingFace protein language model embeddings.
    """
    name: str = "hfregressor"
    citations: list[str] = [
        # RITA: a Study on Scaling Up Generative Protein Sequence Models
        "10.48550/arXiv.2205.05789",
        # Scikit-learn: Machine Learning in Python
        "10.48550/arXiv.1201.0490",
    ]

    _EMBEDDERS: dict[str, type] = {"RITA": RITAEmbedder}

    def __init__(
        self,
        huggingface_model_name: Literal["RITA"] = "RITA",
        embedder_model_name: str = "RITA_s",
        embedder_pooling: Literal["mean", "last"] = "mean",
        alpha: float = 1.0,
        device: str = "cuda",
        batch_size: BatchSize = 128,
    ):
        """
        Instantiate a HuggingFace embedding + ridge regression model.

        Parameters
        ----------
        huggingface_model_name
            Embedder family to use (currently only "RITA")
        embedder_model_name
            Checkpoint of that family to load from huggingface
        embedder_pooling
            How per-residue embeddings are pooled across the sequence ("mean"
            or "last")
        alpha
            Ridge regularization strength
        device
            Torch device for computing embeddings; "cuda" falls back to CPU if
            no GPU is available
        batch_size
            Number of instances to embed at a time
        """
        if huggingface_model_name not in self._EMBEDDERS:
            raise ValueError(
                f"Unsupported huggingface_model_name {huggingface_model_name!r}, "
                f"valid options are: {', '.join(self._EMBEDDERS)}"
            )

        embedder = self._EMBEDDERS[huggingface_model_name](
            model_name=embedder_model_name,
            pooling=embedder_pooling,
            device=device,
        )

        super().__init__(
            predictor=Ridge(alpha=alpha),
            embedder=embedder,
            scorer=None,
            use_embeddings=True,
            use_scores=False,
            target_name=None,
            batch_size=batch_size,
        )

        self.huggingface_model_name = huggingface_model_name
        self.embedder_model_name = embedder_model_name
        self.embedder_pooling = embedder_pooling
        self.alpha = alpha
        self.device = device

    def build(
        self,
        system: System,
        data: LabeledInstanceDataset,
        status_callback: StatusCallback | None = None,
    ) -> Self:
        # the embedder is pretrained and holds no learned state, so it is built
        # here on the same system rather than outside as evedesign's convention
        # expects
        self.embedder.build(system)

        return super().build(system, data=data, status_callback=status_callback)
