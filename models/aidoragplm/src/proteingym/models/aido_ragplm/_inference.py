"""Self-contained AIDO.RAGPLM zero-shot mutation scoring.

Combines three concerns that used to live in separate modules:

* checkpoint / tokenizer loading,
* query + MSA tokenization and prepared-context construction
  (mirrors ``proteingym/baselines/AIDO/utils/misc.py`` at ProteinGym commit
  ``144fe22b``, functions ``tokenize`` and ``greedy_select``),
* masked-LM forward pass and zero-shot log-ratio scoring
  (functions ``get_logits_table_sliding`` and ``get_scores_from_table`` in
  the same upstream file, adapted for the 3B MSA-only path).

See ``README.md`` for the algorithm-to-upstream mapping. The 3B model uses
``[MASK]`` (not ``tMASK``), has no structural input (``str_embedding_in:
null``), and defaults to ``temp_mt = temp_wt = 1.0`` — those are the only
substantive differences from the 16B ProteinGym baseline that this code
mirrors.
"""

from __future__ import annotations

import importlib.resources
import random
import string
from dataclasses import dataclass, field
from os import PathLike
from typing import TYPE_CHECKING, Any, Iterable, Mapping, Sequence

import numpy as np

if TYPE_CHECKING:
    import torch
    from proteingym.models.aido_ragplm._vendored.fm4bio import (
        FM4BioForMaskedLM,
        FM4BioTokenizer,
    )


# --------------------------------------------------------------------------
# Loading
# --------------------------------------------------------------------------

DEFAULT_MODEL_NAME = "genbio-ai/AIDO.Protein-RAG-3B"

_DEFAULT_TOKENIZER_RESOURCE = (
    "proteingym.models.aido_ragplm._vendored.fm4bio",
    "vocab_protein.txt",
)


def load_tokenizer(
    vocab_file: str | PathLike[str] | None = None,
    **tokenizer_kwargs: Any,
) -> "FM4BioTokenizer":
    """Build the ``FM4BioTokenizer`` from the vendored protein vocabulary.

    Parameters
    ----------
    vocab_file
        Path to an alternative ``vocab_protein.txt``. When ``None`` (the
        default) the file packaged inside
        ``proteingym.models.aido_ragplm._vendored.fm4bio`` is used.
    tokenizer_kwargs
        Forwarded to ``FM4BioTokenizer.__init__``.
    """
    from proteingym.models.aido_ragplm._vendored.fm4bio import FM4BioTokenizer

    if vocab_file is None:
        package, resource = _DEFAULT_TOKENIZER_RESOURCE
        with importlib.resources.as_file(
            importlib.resources.files(package).joinpath(resource)
        ) as vocab_path:
            return FM4BioTokenizer(vocab_file=str(vocab_path), **tokenizer_kwargs)
    return FM4BioTokenizer(vocab_file=str(vocab_file), **tokenizer_kwargs)


def load_model(
    model_name: str = DEFAULT_MODEL_NAME,
    revision: str | None = None,
    cache_dir: str | PathLike[str] | None = None,
    device: str = "cpu",
    dtype: "torch.dtype | None" = None,
    **from_pretrained_kwargs: Any,
) -> "FM4BioForMaskedLM":
    """Load ``FM4BioForMaskedLM`` from the Hugging Face hub.

    The HF repo doesn't ship modeling code or tokenizer files, so this routes
    through the vendored model class; the tokenizer is built separately by
    :func:`load_tokenizer`.
    """
    import torch  # noqa: F401  (used below)

    from proteingym.models.aido_ragplm._vendored.fm4bio import FM4BioForMaskedLM

    kwargs: dict[str, Any] = dict(from_pretrained_kwargs)
    if revision is not None:
        kwargs.setdefault("revision", revision)
    if cache_dir is not None:
        kwargs.setdefault("cache_dir", str(cache_dir))
    if dtype is not None:
        kwargs.setdefault("torch_dtype", dtype)

    model = FM4BioForMaskedLM.from_pretrained(model_name, **kwargs)
    model.to(device)
    model.eval()
    return model


# --------------------------------------------------------------------------
# MSA tokenization & prepared-context construction
# --------------------------------------------------------------------------

_REMOVE_INSERTIONS = str.maketrans("", "", string.ascii_lowercase + ".")
GAP_SYMBOL = "-"
MASK_SYMBOL = "[MASK]"


def remove_insertions(seq: str) -> str:
    """Strip lowercase columns and dots, leaving the aligned uppercase columns."""
    return seq.translate(_REMOVE_INSERTIONS)


def _hamming_distance_argselect(
    array: np.ndarray,
    n_to_keep: int | None,
    token_budget: int | None,
    mode: str,
) -> list[int]:
    if mode not in ("max", "min"):
        raise ValueError(f"mode must be 'max' or 'min', got {mode!r}")

    optfunc = np.argmax if mode == "max" else np.argmin
    n_rows, n_cols = array.shape
    indices = [0]
    pairwise = np.zeros((0, n_rows), dtype=np.float64)
    selected: list[bytes] = []
    all_indices = np.arange(n_rows)
    for _ in range(n_rows - 1):
        diff = (
            (array[indices[-1:]] != array)
            .astype(np.float64)
            .mean(axis=1, keepdims=True)
            .T
        )
        pairwise = np.concatenate([pairwise, diff])
        shifted = np.delete(pairwise, indices, axis=1).mean(0)
        shifted_index = optfunc(shifted)
        index = int(np.delete(all_indices, indices)[shifted_index])
        indices.append(index)
        selected.append(bytes(array[index]))
        if n_to_keep is not None and len(indices) >= n_to_keep:
            break
        if token_budget is not None:
            non_gap_total = sum(len(s) - s.count(ord(GAP_SYMBOL)) for s in selected)
            if non_gap_total >= token_budget:
                break
    return indices


def greedy_select_msa(
    msa: Sequence[str],
    num_seqs: int | None = None,
    num_tokens: int | None = None,
    mode: str = "max",
    seed: int | None = 0,
) -> list[str]:
    """Hamming-distance-based diverse MSA row selection.

    Equivalent to the ``greedy_select`` reference function in ProteinGym AIDO
    ``misc.py`` (line 204). Exactly one of ``num_seqs`` / ``num_tokens`` must
    be provided. ``num_tokens`` counts non-gap tokens across the selected
    rows.
    """
    if (num_seqs is None) == (num_tokens is None):
        raise ValueError("Exactly one of num_seqs / num_tokens must be specified")

    msa_list = list(msa)
    if seed is not None:
        random.Random(seed).shuffle(msa_list)

    if num_seqs is not None and len(msa_list) <= num_seqs:
        return msa_list
    if num_tokens is not None:
        non_gap = sum(len(s) - s.count(GAP_SYMBOL) for s in msa_list)
        if non_gap <= num_tokens:
            return msa_list

    array = np.array([list(seq) for seq in msa_list], dtype=np.bytes_).view(np.uint8)
    indices = _hamming_distance_argselect(array, num_seqs, num_tokens, mode)
    return [msa_list[i] for i in indices[1:]]


def tokenize_query_and_msa(
    query: str,
    msa: Sequence[str],
    tokenizer: "FM4BioTokenizer",
    max_context: int = 12_800,
) -> tuple[np.ndarray, np.ndarray]:
    """Tokenize the query + MSA into a flat token array with a 2-row position map.

    Mirrors ProteinGym AIDO's ``tokenize`` function: flat tokens (``query |
    row_1 | row_2 | ...``); a ``(2, N)`` position tensor where row 0 cycles
    residue positions and row 1 carries the sequence index (``0`` for the
    query); gap tokens stripped; result truncated to ``max_context`` tokens.
    """
    if max_context <= 0:
        raise ValueError(f"max_context must be positive, got {max_context}")

    len_query = len(query)
    tokens: list[int] = list(tokenizer.encode(query, add_special_tokens=False))
    if len(tokens) != len_query:
        raise ValueError(
            "Query tokenization produced a different number of tokens "
            f"({len(tokens)}) than residues ({len_query}). This usually means "
            "the tokenizer is not the AIDO FM4Bio protein tokenizer."
        )
    num_seqs = 1

    for row in msa:
        if len(row) != len_query:
            raise ValueError(
                f"MSA row has length {len(row)} but query has length {len_query};"
                " MSA rows must be aligned to the query."
            )
        row_ids = tokenizer.encode(row, add_special_tokens=False)
        if len(row_ids) != len_query:
            raise ValueError(
                "MSA row tokenization produced a different number of tokens "
                f"({len(row_ids)}) than residues ({len_query})."
            )
        tokens.extend(row_ids)
        num_seqs += 1

    residue_axis = np.tile(np.arange(len_query, dtype=np.int64), num_seqs)
    sequence_axis = np.repeat(np.arange(num_seqs, dtype=np.int64), len_query)
    pos_encoding = np.stack([residue_axis, sequence_axis])

    tokens_arr = np.asarray(tokens, dtype=np.int64)
    gap_id = tokenizer.convert_tokens_to_ids(GAP_SYMBOL)
    keep_mask = tokens_arr != gap_id
    tokens_arr = tokens_arr[keep_mask][:max_context]
    pos_encoding = pos_encoding[..., keep_mask][..., :max_context]
    return tokens_arr, pos_encoding


@dataclass
class PreparedContext:
    """Cached, model-independent inputs for masked-LM scoring."""

    query: str
    msa_rows: tuple[str, ...]
    max_context: int
    sliding_window: int
    sliding_step: int
    msa_token_budget: int
    max_msa_sequences: int | None = None
    msa_selection_seed: int | None = 0
    _windows: list[dict[str, np.ndarray]] = field(default_factory=list, repr=False)

    @property
    def query_length(self) -> int:
        return len(self.query)


def _select_msa_for_window(
    msa: Sequence[str],
    f_start: int,
    f_end: int,
    *,
    max_msa_sequences: int | None,
    msa_token_budget: int,
    seed: int | None,
) -> list[str]:
    sliced = [row[f_start:f_end] for row in msa]
    if max_msa_sequences is not None and len(sliced) <= max_msa_sequences:
        selected = sliced
    else:
        selected = greedy_select_msa(
            sliced,
            num_seqs=max_msa_sequences,
            num_tokens=None if max_msa_sequences is not None else msa_token_budget,
            mode="max",
            seed=seed,
        )
    selected.sort(key=lambda x: x.count(GAP_SYMBOL))
    return selected


def prepare_context(
    reference_sequence: str,
    msa: Sequence[str],
    tokenizer: "FM4BioTokenizer",
    *,
    max_context: int = 12_800,
    sliding_window: int = 768,
    sliding_step: int = 768,
    msa_token_budget: int = 12_800,
    max_msa_sequences: int | None = None,
    msa_selection_seed: int | None = 0,
) -> PreparedContext:
    """Build the tokenized inputs needed to score variants of ``reference_sequence``.

    Slices the MSA into one or more sliding windows that cover the full
    query; each window's MSA rows are passed through ``greedy_select_msa``
    and sorted by gap count before tokenization. The query (after insert
    removal) must be the first MSA row by convention — if it isn't, this
    transparently prepends it.
    """
    if sliding_window <= 0 or sliding_step <= 0:
        raise ValueError("sliding_window and sliding_step must be positive")
    if not msa:
        raise ValueError("MSA must contain at least one sequence (the query row)")

    query_match = remove_insertions(reference_sequence).upper()
    aligned_msa = [remove_insertions(row).upper() for row in msa]
    query_length = len(query_match)
    if any(len(row) != query_length for row in aligned_msa):
        raise ValueError(
            "MSA rows must all have the same aligned length as the reference"
        )
    if aligned_msa[0] != query_match:
        # ProteinGym AIDO assumes the query is the first MSA row. If the
        # user-supplied MSA does not yet include it, prepend it.
        aligned_msa = [query_match, *aligned_msa]

    ctx = PreparedContext(
        query=query_match,
        msa_rows=tuple(aligned_msa),
        max_context=max_context,
        sliding_window=sliding_window,
        sliding_step=sliding_step,
        msa_token_budget=msa_token_budget,
        max_msa_sequences=max_msa_sequences,
        msa_selection_seed=msa_selection_seed,
    )

    windows: list[dict[str, np.ndarray]] = []
    f_start = 0
    is_last = False
    while not is_last and f_start < query_length:
        if f_start + sliding_window > query_length and query_length > sliding_window:
            f_start = query_length - sliding_window
            is_last = True
        f_end = min(f_start + sliding_window, query_length)
        f_query = query_match[f_start:f_end]
        f_msa = _select_msa_for_window(
            aligned_msa[1:],
            f_start,
            f_end,
            max_msa_sequences=max_msa_sequences,
            msa_token_budget=msa_token_budget,
            seed=msa_selection_seed,
        )
        tokens, pos_enc = tokenize_query_and_msa(
            f_query, f_msa, tokenizer, max_context=max_context
        )
        windows.append(
            {
                "f_start": np.int64(f_start),
                "f_end": np.int64(f_end),
                "tokens": tokens,
                "pos_encoding": pos_enc,
            }
        )
        if f_end == query_length:
            break
        f_start += sliding_step
    ctx._windows = windows
    return ctx


# --------------------------------------------------------------------------
# Masked-LM forward + zero-shot scoring
# --------------------------------------------------------------------------


def _log_softmax_np(
    x: np.ndarray, axis: int = -1, temperature: float = 1.0
) -> np.ndarray:
    if temperature == 0:
        raise ValueError("temperature must be non-zero")
    scaled = x / float(temperature)
    shifted = scaled - np.max(scaled, axis=axis, keepdims=True)
    return shifted - np.log(np.sum(np.exp(shifted), axis=axis, keepdims=True))


def compute_position_logits(
    model: "FM4BioForMaskedLM",
    context: PreparedContext,
    positions: Sequence[int],
    tokenizer: "FM4BioTokenizer",
    *,
    device: str = "cpu",
) -> dict[int, np.ndarray]:
    """Run the masked-LM forward pass and return logits for each query position.

    The mask is applied only to the *query token* at the target position
    (``pos_encoding[1] == 0``) following the ProteinGym AIDO reference.
    Logits from multiple windows that cover the same query position are
    averaged. The returned mapping is in float32 NumPy form keyed by query
    position (0-based).
    """
    import torch  # noqa: PLC0415

    if not positions:
        return {}

    mask_token_id = tokenizer.convert_tokens_to_ids(MASK_SYMBOL)
    if mask_token_id is None or mask_token_id == tokenizer.convert_tokens_to_ids(
        GAP_SYMBOL
    ):
        raise RuntimeError(
            "AIDO FM4Bio tokenizer is missing the [MASK] token; got id="
            f"{mask_token_id!r}"
        )

    positions = sorted(set(int(p) for p in positions))
    invalid = [p for p in positions if p < 0 or p >= context.query_length]
    if invalid:
        raise ValueError(
            f"positions out of query range [0, {context.query_length}): {invalid}"
        )

    vocab_size = getattr(model.config, "vocab_size", None)
    if vocab_size is None:
        raise RuntimeError("model.config.vocab_size is not defined")

    logit_table = np.zeros((len(positions), vocab_size), dtype=np.float64)
    count_table = np.zeros(len(positions), dtype=np.int64)
    pos_index = {p: i for i, p in enumerate(positions)}

    for window in context._windows:
        f_start = int(window["f_start"])
        f_end = int(window["f_end"])
        window_positions = [p for p in positions if f_start <= p < f_end]
        if not window_positions:
            continue
        tokens_t = torch.as_tensor(window["tokens"], dtype=torch.long, device=device)
        pos_enc_t = torch.as_tensor(
            window["pos_encoding"], dtype=torch.long, device=device
        )

        with torch.inference_mode():
            for pos in window_positions:
                masked = tokens_t.clone()
                local_pos = pos - f_start
                # mask only the query token at this residue position
                # (sequence row 0 in pos_encoding)
                target = (pos_enc_t[0] == local_pos) & (pos_enc_t[1] == 0)
                if not bool(target.any()):
                    # query position was dropped because the query token was a
                    # gap; skip and let count_table stay at 0 for this position
                    continue
                masked[target] = mask_token_id
                outputs = model(
                    input_ids=masked.unsqueeze(0),
                    position_ids=pos_enc_t.unsqueeze(0),
                    return_dict=True,
                )
                logits = outputs.logits.squeeze(0)
                logits = (
                    logits[target].squeeze(0).to(torch.float32).cpu().numpy()
                )
                i = pos_index[pos]
                logit_table[i] += logits.astype(np.float64)
                count_table[i] += 1

    out: dict[int, np.ndarray] = {}
    for i, pos in enumerate(positions):
        if count_table[i] == 0:
            continue
        out[pos] = (logit_table[i] / count_table[i]).astype(np.float32)
    return out


def score_variant_sequences(
    reference_sequence: str,
    variant_sequences: Sequence[str],
    position_logits: Mapping[int, np.ndarray],
    tokenizer: "FM4BioTokenizer",
    *,
    mutant_temperature: float = 1.0,
    wildtype_temperature: float = 1.0,
) -> np.ndarray:
    """Aggregate per-position log-ratios into a scalar score per variant.

    Implements ``log P(mut | masked context) - log P(wt | masked context)``
    summed across positions that differ from the reference. Variants
    identical to the reference receive score ``0.0``.

    Both temperatures default to ``1.0`` for the 3B checkpoint; the 16B
    ProteinGym baseline uses ``temp_mt=1.0, temp_wt=1.5`` but those values
    are not transferred to the 3B integration by default — set them
    explicitly if you want to mirror that behavior.
    """
    if not variant_sequences:
        return np.empty(0, dtype=np.float64)
    ref_len = len(reference_sequence)
    for i, variant in enumerate(variant_sequences):
        if len(variant) != ref_len:
            raise ValueError(
                f"Variant #{i} has length {len(variant)} but reference has length {ref_len}"
            )

    vocab = tokenizer.get_vocab()

    def _token_id(symbol: str) -> int:
        if symbol not in vocab:
            raise ValueError(f"Symbol {symbol!r} is not in the FM4Bio tokenizer vocabulary")
        return vocab[symbol]

    log_probs_mt: dict[int, np.ndarray] = {}
    log_probs_wt: dict[int, np.ndarray] = {}
    for pos, logits in position_logits.items():
        log_probs_mt[pos] = _log_softmax_np(
            np.asarray(logits, dtype=np.float64),
            axis=-1,
            temperature=mutant_temperature,
        )
        if wildtype_temperature == mutant_temperature:
            log_probs_wt[pos] = log_probs_mt[pos]
        else:
            log_probs_wt[pos] = _log_softmax_np(
                np.asarray(logits, dtype=np.float64),
                axis=-1,
                temperature=wildtype_temperature,
            )

    scores = np.zeros(len(variant_sequences), dtype=np.float64)
    for i, variant in enumerate(variant_sequences):
        total = 0.0
        for pos, (wt, mt) in enumerate(zip(reference_sequence, variant)):
            if wt == mt:
                continue
            if pos not in log_probs_mt:
                raise ValueError(
                    f"Variant #{i} substitutes at position {pos} but no logits "
                    "were precomputed for this position (likely because the "
                    "reference residue was dropped as a gap)."
                )
            wt_id = _token_id(wt)
            mt_id = _token_id(mt)
            total += float(log_probs_mt[pos][mt_id] - log_probs_wt[pos][wt_id])
        scores[i] = total
    return scores


def positions_changed_between(
    reference: str, variants: Iterable[str]
) -> list[int]:
    """Return the union of 0-based positions at which any variant differs from the reference."""
    changed: set[int] = set()
    for variant in variants:
        if len(variant) != len(reference):
            raise ValueError("Variant length does not match reference length")
        for pos, (a, b) in enumerate(zip(reference, variant)):
            if a != b:
                changed.add(pos)
    return sorted(changed)


__all__ = [
    "DEFAULT_MODEL_NAME",
    "GAP_SYMBOL",
    "MASK_SYMBOL",
    "PreparedContext",
    "compute_position_logits",
    "greedy_select_msa",
    "load_model",
    "load_tokenizer",
    "positions_changed_between",
    "prepare_context",
    "remove_insertions",
    "score_variant_sequences",
    "tokenize_query_and_msa",
]
