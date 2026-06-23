"""Benchmark-harness adapter and evedesign wrapper for AIDO.RAGPLM.

Two related classes / functions live here:

* :class:`AIDORAGPLM` — an ``evedesign.model.BaseModel`` / ``Scorer`` that
  wraps the inference layer in :mod:`._inference` so it plays nicely with
  any evedesign-aware pipeline.
* :func:`load` / :func:`infer` — the proteingym-benchmark contract that the
  typer CLI in :mod:`.__main__` calls. ``load`` builds an ``AIDORAGPLM``
  from a model card; ``infer`` runs ``build`` + ``score`` against the test
  fold of a ``Subsets`` archive and returns a polars ``DataFrame`` with
  ``mutation_col`` / ``test`` / ``pred`` columns.

The checkpoint itself is downloaded lazily on the first ``score`` call so
that ``load`` is cheap and any hyper-parameter errors surface early without
paying the ~11 GB download cost.
"""

from __future__ import annotations

import logging
from os import PathLike
from typing import Any, Self, Sequence

import numpy as np
import polars as pl

from evedesign.model import BaseModel, Scorer
from evedesign.system import System, SystemInstance
from evedesign.types import DeviceType, StatusCallback
from evedesign.utils import model_param_context
from proteingym.base import Subsets
from proteingym.base.model import ModelCard

from ._inference import (
    DEFAULT_MODEL_NAME,
    PreparedContext,
    compute_position_logits,
    load_model,
    load_tokenizer,
    positions_changed_between,
    prepare_context,
    score_variant_sequences,
)
from .preprocess import (
    build_evedesign_system,
    build_instances,
    extract_msa_rows,
    extract_reference_sequence,
    load_x_and_y,
)

logger = logging.getLogger(__name__)

try:
    import torch  # noqa: F401

    IMPORT_AVAILABLE = True
except ImportError:
    IMPORT_AVAILABLE = False


# --------------------------------------------------------------------------
# evedesign wrapper
# --------------------------------------------------------------------------


class AIDORAGPLM(BaseModel, Scorer):
    """AIDO.RAGPLM (AIDO.Protein-RAG-3B) zero-shot mutation scorer.

    The wrapper assumes a single-component protein ``System`` with an aligned
    MSA attached to the entity's ``sequences`` attribute. ``build()`` caches
    the tokenized MSA windows; ``score()`` evaluates one or more
    ``SystemInstance`` representations against the cached context and returns
    a 1-D NumPy vector of zero-shot scores in the same order as the inputs.

    The first MSA row must equal the reference sequence (after removing
    insertions); we follow the ProteinGym AIDO convention there. If the user
    forgets, ``prepare_context`` transparently prepends the reference.
    """

    available = IMPORT_AVAILABLE
    name: str = "AIDO.RAGPLM"
    citations: list[str] = ["doi:10.1101/2024.12.02.626519"]

    # core capability flags
    requires_target: bool = True
    requires_fixed_length: bool = True
    handles_deletions: bool = False
    handles_insertions: bool = False
    requires_gpu: bool = False
    supports_gpu: bool = True
    supports_gpu_parallel: bool = False
    supports_cpu_parallel: bool = False

    required_entity_attributes: list[str] | None = ["sequences"]
    optional_entity_attributes: list[str] | None = []

    # ProteinGym 16B baseline uses 12,800 tokens for max_context and 768 for
    # the sliding window. Both are tunable; defaults match the 16B baseline.
    DEFAULT_MAX_CONTEXT: int = 12_800
    DEFAULT_SLIDING_WINDOW: int = 768

    def __init__(
        self,
        model_name: str = DEFAULT_MODEL_NAME,
        model_revision: str | None = None,
        model_dir_path: str | PathLike[str] | None = None,
        cache_dir: str | PathLike[str] | None = None,
        device: DeviceType = "cpu",
        dtype: "str | None" = None,
        max_context: int = DEFAULT_MAX_CONTEXT,
        sliding_window: int = DEFAULT_SLIDING_WINDOW,
        sliding_step: int | None = None,
        msa_token_budget: int = DEFAULT_MAX_CONTEXT,
        max_msa_sequences: int | None = None,
        msa_selection_seed: int | None = 0,
        mutant_temperature: float = 1.0,
        wildtype_temperature: float = 1.0,
        keep_model_after_build: bool = False,
        keep_model_after_pred: bool = True,
    ) -> None:
        if not self.available:
            raise ImportError(
                "PyTorch is required to use AIDORAGPLM. Install with "
                "`uv sync` inside models/aidoragplm/."
            )
        if max_context <= 0:
            raise ValueError("max_context must be positive")
        if sliding_window <= 0:
            raise ValueError("sliding_window must be positive")
        if mutant_temperature <= 0 or wildtype_temperature <= 0:
            raise ValueError("temperatures must be positive")

        self.model_name = model_name
        self.model_revision = model_revision
        self.model_dir_path = model_dir_path
        self.cache_dir = cache_dir
        self.device = device
        self.dtype = dtype
        self.max_context = int(max_context)
        self.sliding_window = int(sliding_window)
        self.sliding_step = int(
            sliding_step if sliding_step is not None else sliding_window
        )
        self.msa_token_budget = int(msa_token_budget)
        self.max_msa_sequences = max_msa_sequences
        self.msa_selection_seed = msa_selection_seed
        self.mutant_temperature = float(mutant_temperature)
        self.wildtype_temperature = float(wildtype_temperature)
        self.keep_model_after_build = bool(keep_model_after_build)
        self.keep_model_after_pred = bool(keep_model_after_pred)

        self._system: System | None = None
        self.model = None
        self.tokenizer = None
        self._reference_sequence: str | None = None
        self._prepared_context: PreparedContext | None = None
        self._position_logits_cache: dict[int, np.ndarray] = {}

    # ----- evedesign properties ----------------------------------------

    @property
    def ready(self) -> bool:
        return self._system is not None and self._prepared_context is not None

    @property
    def system(self) -> System | None:
        return self._system

    @classmethod
    def can_model(
        cls, system: System, data: Any = None
    ) -> tuple[bool, str]:
        if data is not None:
            return False, "AIDO.RAGPLM does not support a `data` argument (must be None)"
        if len(system) != 1:
            return False, "AIDO.RAGPLM only supports single-entity systems"

        target = system[0]
        if target.type != "protein":
            return False, "AIDO.RAGPLM only supports protein entities"
        if not target.defined_sequence():
            return False, "Target entity must have a defined reference sequence"
        if target.sequences is None or len(target.sequences.seqs) == 0:
            return False, "AIDO.RAGPLM requires an aligned MSA on entity.sequences"
        if not target.sequences.aligned:
            return False, "AIDO.RAGPLM requires the MSA to be aligned"
        return True, ""

    # ----- lazy model loading ------------------------------------------

    def _resolve_dtype(self) -> "Any | None":
        if self.dtype is None:
            return None
        import torch  # noqa: PLC0415

        if isinstance(self.dtype, str):
            mapping = {
                "float32": torch.float32,
                "fp32": torch.float32,
                "float16": torch.float16,
                "fp16": torch.float16,
                "bfloat16": torch.bfloat16,
                "bf16": torch.bfloat16,
            }
            try:
                return mapping[self.dtype]
            except KeyError as err:
                raise ValueError(f"Unsupported dtype: {self.dtype!r}") from err
        return self.dtype

    def _load_model(self) -> None:
        if self.model is not None and self.tokenizer is not None:
            return
        if self.tokenizer is None:
            self.tokenizer = load_tokenizer()
        if self.model is None:
            source = (
                self.model_dir_path
                if self.model_dir_path is not None
                else self.model_name
            )
            self.model = load_model(
                model_name=str(source),
                revision=self.model_revision,
                cache_dir=self.cache_dir,
                device=self.device,
                dtype=self._resolve_dtype(),
            )

    def _release_cache(self) -> None:
        try:
            import torch  # noqa: PLC0415
        except ImportError:
            return
        if self.device == "cuda" and torch.cuda.is_available():
            torch.cuda.empty_cache()
        elif self.device == "mps" and getattr(torch.backends, "mps", None) is not None:
            try:
                torch.mps.empty_cache()
            except (AttributeError, RuntimeError):
                pass

    def _delete_model(self) -> None:
        self.model = None
        self.tokenizer = None
        self._release_cache()

    # ----- build --------------------------------------------------------

    def build(
        self,
        system: System,
        data: Any = None,
        status_callback: StatusCallback | None = None,
    ) -> Self:
        self.can_model_or_raise(system, data)

        if status_callback is not None:
            status_callback("running", 0.0, "Preparing AIDO.RAGPLM context")

        self._system = system
        target = system[0]
        reference_sequence = "".join(target.rep)
        self._reference_sequence = reference_sequence

        msa_a3m = target.sequences.to_a3m()
        msa_rows = [seq.seq for seq in msa_a3m.seqs]

        # Tokenizer is tiny and has no download cost.
        if self.tokenizer is None:
            self.tokenizer = load_tokenizer()

        self._prepared_context = prepare_context(
            reference_sequence,
            msa_rows,
            self.tokenizer,
            max_context=self.max_context,
            sliding_window=self.sliding_window,
            sliding_step=self.sliding_step,
            msa_token_budget=self.msa_token_budget,
            max_msa_sequences=self.max_msa_sequences,
            msa_selection_seed=self.msa_selection_seed,
        )
        self._position_logits_cache = {}

        if status_callback is not None:
            status_callback(
                "running",
                100.0,
                f"Prepared {len(self._prepared_context._windows)} MSA window(s)",
            )

        if self.keep_model_after_build:
            self._load_model()

        return self

    # ----- instance validation -----------------------------------------

    def _validate_instances(self, instances: Sequence[SystemInstance]) -> None:
        if not self.ready:
            raise ValueError("AIDORAGPLM.score() requires a previous build()")
        ref = self._reference_sequence
        assert ref is not None
        ref_len = len(ref)
        for i, instance in enumerate(instances):
            self.system.valid_instance(
                instance,
                validate_reps=True,
                require_reps=True,
                fixed_length=True,
                allow_deletions=False,
                raise_invalid=True,
            )
            seq = "".join(instance[0].rep)
            if len(seq) != ref_len:
                raise ValueError(
                    f"Instance #{i} length ({len(seq)}) does not match reference"
                    f" length ({ref_len})"
                )
            if any(symbol == "-" for symbol in seq):
                raise ValueError(
                    f"Instance #{i} contains a gap symbol; AIDO.RAGPLM does not"
                    " score deletions"
                )

    # ----- score --------------------------------------------------------

    def score(
        self,
        instances: Sequence[SystemInstance],
        status_callback: StatusCallback | None = None,
    ) -> np.ndarray:
        self.ready_or_raise()
        self._validate_instances(instances)

        if len(instances) == 0:
            return np.empty(0, dtype=float)

        if status_callback is not None:
            status_callback("running", 0.0, "Collecting mutated positions")

        variant_sequences = ["".join(inst[0].rep) for inst in instances]
        positions = positions_changed_between(
            self._reference_sequence, variant_sequences
        )

        missing_positions = [
            p for p in positions if p not in self._position_logits_cache
        ]

        if missing_positions:
            if status_callback is not None:
                status_callback(
                    "running",
                    10.0,
                    f"Loading model for {len(missing_positions)} masked position(s)",
                )

            with model_param_context(
                self._load_model, self._delete_model, self.keep_model_after_pred
            ):
                if status_callback is not None:
                    status_callback(
                        "running", 30.0, "Running masked-LM forward passes"
                    )

                new_logits = compute_position_logits(
                    self.model,
                    self._prepared_context,
                    missing_positions,
                    self.tokenizer,
                    device=self.device,
                )
                self._position_logits_cache.update(new_logits)

        if status_callback is not None:
            status_callback("running", 80.0, "Assembling variant scores")

        scores = score_variant_sequences(
            self._reference_sequence,
            variant_sequences,
            self._position_logits_cache,
            self.tokenizer,
            mutant_temperature=self.mutant_temperature,
            wildtype_temperature=self.wildtype_temperature,
        )

        if status_callback is not None:
            status_callback("done", 100.0, "AIDO.RAGPLM scoring complete")

        return np.asarray(scores, dtype=float)


# --------------------------------------------------------------------------
# proteingym-benchmark adapter (load / infer)
# --------------------------------------------------------------------------


_WRAPPER_INIT_KEYS = {
    "model_name",
    "model_revision",
    "model_dir_path",
    "cache_dir",
    "device",
    "dtype",
    "max_context",
    "sliding_window",
    "sliding_step",
    "msa_token_budget",
    "max_msa_sequences",
    "msa_selection_seed",
    "mutant_temperature",
    "wildtype_temperature",
    "keep_model_after_build",
    "keep_model_after_pred",
}


def _wrapper_kwargs(hyper_parameters: dict[str, Any]) -> dict[str, Any]:
    """Pick out only those hyper_parameters that map to ``AIDORAGPLM.__init__``."""
    return {k: v for k, v in hyper_parameters.items() if k in _WRAPPER_INIT_KEYS}


def load(model_card: ModelCard) -> AIDORAGPLM:
    """Instantiate the AIDO.RAGPLM evedesign wrapper from the model card.

    Constructor arguments are read from ``model_card.hyper_parameters``;
    unknown keys are ignored so the model card can also carry harness-only
    metadata. The checkpoint itself is downloaded only on the first ``score``
    call.
    """
    kwargs = _wrapper_kwargs(model_card.hyper_parameters)
    logger.info("Instantiating AIDORAGPLM with kwargs: %s", kwargs)
    return AIDORAGPLM(**kwargs)


def infer(
    split_dataset: Subsets,
    split: str,
    test_fold: int,
    target: str,
    model_card: ModelCard,
    model: AIDORAGPLM,
) -> pl.DataFrame:
    """Score the test fold of ``split_dataset[split]`` with AIDO.RAGPLM.

    Returns one row per test-fold variant with columns ``mutation_col``,
    ``test``, ``pred``. Row order matches the dataset's row order.
    """
    reference_sequence = extract_reference_sequence(split_dataset, split)
    msa_rows = extract_msa_rows(split_dataset, split, reference_sequence)
    system = build_evedesign_system(
        reference_sequence,
        msa_rows,
        entity_id=split_dataset[split].dataset.name,
    )

    _, _, test_X, test_Y = load_x_and_y(
        subset=split_dataset,
        split=split,
        test_fold=test_fold,
        target=target,
    )

    logger.info(
        "Scoring %d test variants (split=%s, fold=%d, target=%s)",
        len(test_X), split, test_fold, target,
    )

    model.build(system)
    instances = build_instances(test_X)
    scores = model.score(instances)

    return pl.DataFrame(
        {
            "mutation_col": test_X,
            "test": [float(v) for v in test_Y],
            "pred": [float(s) for s in scores],
        }
    )
