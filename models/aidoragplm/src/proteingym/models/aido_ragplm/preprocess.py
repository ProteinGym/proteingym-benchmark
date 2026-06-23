"""Bridge between proteingym-base ``Subsets`` and the AIDO.RAGPLM scoring API.

Translates a ``Subsets`` archive (a ``.splits.pgdata`` file) into the inputs
the evedesign ``AIDORAGPLM`` wrapper expects:

* a reference protein sequence (``WILD_TYPE`` row in ``dataset.sequences``),
* an aligned MSA (the first MSA in ``dataset.msas``, gap-padded if needed),
* a list of mutated full-length variant sequences from the chosen test fold.

The same conventions used by the existing zero-shot baseline (``models/esm``)
are followed for the k-fold split layout.
"""

from __future__ import annotations

import logging
import string
from typing import Any

import numpy as np
import polars as pl
from evedesign.sequence import Sequence as EvSequence
from evedesign.sequence import Sequences
from evedesign.system import Entity, EntityInstance, System, SystemInstance
from proteingym.base import Subsets
from proteingym.base.sequence import SequenceType

logger = logging.getLogger(__name__)


_LOWERCASE_TRANSLATION = str.maketrans("", "", string.ascii_lowercase + ".")


def load_x_and_y(
    subset: Subsets,
    split: str,
    test_fold: int,
    target: str,
) -> tuple[list[str], list[float], list[str], list[float]]:
    """Load train / test variant sequences and targets from a Subsets archive.

    Mirrors the convention used by ``models/esm`` and ``models/pls``: the
    requested ``split`` is indexed by ``test_fold`` to pull the test slice;
    all other slices in that split are concatenated for the train side. For a
    zero-shot model the ``train_*`` outputs are unused but are returned for
    interface symmetry.
    """
    test_dataset = subset[split].slices[test_fold]
    test_df = subset[split].dataset[test_dataset].to_df()

    train_dfs: list[pl.DataFrame] = []
    for i in range(len(subset[split].slices)):
        if i != test_fold:
            train_slice = subset[split].slices[i]
            train_dfs.append(subset[split].dataset[train_slice].to_df())
    train_df = pl.concat(train_dfs) if train_dfs else pl.DataFrame()

    train_X = train_df["sequence"].to_list() if "sequence" in train_df.columns else []
    train_Y = train_df[target].to_list() if target in train_df.columns else []
    test_X = test_df["sequence"].to_list()
    test_Y = test_df[target].to_list()

    return train_X, train_Y, test_X, test_Y


def extract_reference_sequence(subset: Subsets, split: str) -> str:
    """Pull the wild-type (reference) sequence out of the dataset."""
    dataset = subset[split].dataset
    for seq in dataset.sequences:
        if seq.type == SequenceType.WILD_TYPE:
            return str(seq.value)
    raise ValueError(
        "Dataset does not contain a WILD_TYPE sequence; "
        "AIDO.RAGPLM requires a reference sequence to score against."
    )


def extract_msa_rows(
    subset: Subsets,
    split: str,
    reference_sequence: str,
) -> list[str]:
    """Pull an MSA out of the dataset and gap-pad rows to the query length.

    Real-world a3m files (e.g. from hhblits) often emit partial hits that do
    not span the full query length. We right-pad those with ``-`` to match
    ``len(reference_sequence)`` so the AIDO tokenizer can align them. Rows that
    are longer than the query (after stripping lowercase insertion columns)
    are dropped with a warning — we cannot safely realign them.
    """
    dataset = subset[split].dataset
    if not dataset.msas:
        raise ValueError(
            "Dataset has no MSA — AIDO.RAGPLM requires an aligned MSA."
        )

    msa = dataset.msas[0]
    if msa.reference_sequence_name is not None:
        for candidate in dataset.msas:
            if candidate.reference_sequence_name == msa.reference_sequence_name:
                msa = candidate
                break

    expected_match_len = len(reference_sequence)
    rows: list[str] = []
    dropped = 0
    for raw_seq in msa.value:
        match_state = str(raw_seq).translate(_LOWERCASE_TRANSLATION)
        n = len(match_state)
        if n == expected_match_len:
            rows.append(match_state)
        elif n < expected_match_len:
            rows.append(match_state + ("-" * (expected_match_len - n)))
        else:
            dropped += 1

    if dropped:
        logger.warning(
            "Dropped %d MSA rows longer than the reference (%d residues)",
            dropped,
            expected_match_len,
        )
    if not rows:
        raise ValueError(
            "After length-filtering, no usable MSA rows remained."
        )
    return rows


def build_evedesign_system(
    reference_sequence: str,
    msa_rows: list[str],
    entity_id: str = "protein",
) -> System:
    """Wrap a reference + MSA into a single-entity evedesign ``System``."""
    sequences = Sequences(
        seqs=[EvSequence(seq=row, id=f"row_{i}", type="protein")
              for i, row in enumerate(msa_rows)],
        aligned=True,
        type="protein",
        format="a3m",
    )
    entity = Entity(
        type="protein",
        rep=np.array(list(reference_sequence), dtype="U1"),
        id=entity_id,
        first_index=1,
        sequences=sequences,
    )
    return System([entity])


def build_instances(test_X: list[str]) -> list[SystemInstance]:
    """Wrap a list of full-length variant sequence strings as ``SystemInstance``s."""
    return [
        SystemInstance(EntityInstance(rep=np.array(list(seq), dtype="U1")))
        for seq in test_X
    ]
