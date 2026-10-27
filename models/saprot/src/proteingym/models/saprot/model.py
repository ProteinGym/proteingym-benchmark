"""
Wrapper class around SaProt (https://github.com/westlake-repl/SaProt)
"""
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Any, Self, Sequence

import numpy as np
import pandas as pd

from evedesign.constants import VALID_AA_SORTED
from evedesign.model import (
    BaseModel,
    Scorer,
    MutationScorer,
    ConditionalMutationScorer,
    assign_scores_to_instances,
)
from evedesign.structure import Structure
from evedesign.system import Entity, System, SystemInstance
from evedesign.types import StatusCallback
from evedesign.utils import ensure_sequence, status_done, status_start

try:
    import biotite.structure as struc
    import biotite.structure.io.pdb as biotite_pdb
    from biotite.sequence import ProteinSequence

    import torch
    from transformers import EsmForMaskedLM, EsmTokenizer

    IMPORT_AVAILABLE = True
except ImportError:
    IMPORT_AVAILABLE = False

# upstream utils/constants.py: residue halves and 3Di halves of SaProt tokens,
# "#" being the mask symbol in both
FOLDSEEK_SEQ_VOCAB = "ACDEFGHIKLMNPQRSTVWY#"
FOLDSEEK_STRUC_VOCAB = "pynwrqhgdlvtmfsaeikc#"
MASK = "#"


def foldseek_3di(
    chain: "Structure",
    foldseek_path: str,
) -> tuple[np.ndarray, str]:
    """
    Run foldseek structureto3didescriptor on one chain.

    Foldseek's residue sequence is checked against the chain so the 3Di
    string can be mapped back to residue numbers.

    Parameters
    ----------
    chain
        Single-chain structure (hetero atoms are dropped)
    foldseek_path
        Path to the foldseek binary

    Returns
    -------
    tuple[np.ndarray, str]
        Residue numbers of the encoded residues and their 3Di states as a
        lowercase string of the same length
    """
    atom_array = chain.atom_array
    atom_array = atom_array[~atom_array.hetero]
    if len(atom_array) == 0:
        raise ValueError("Structure has no non-hetero atoms")

    with tempfile.TemporaryDirectory(prefix="saprot_foldseek_") as tmp:
        pdb_path = Path(tmp) / "target.pdb"
        out_path = Path(tmp) / "target.tsv"
        pdb_file = biotite_pdb.PDBFile()
        pdb_file.set_structure(atom_array)
        pdb_file.write(str(pdb_path))

        # same invocation as upstream utils/foldseek_util.get_struc_seq
        proc = subprocess.run(
            [
                foldseek_path, "structureto3didescriptor",
                "-v", "0", "--threads", "1", "--chain-name-mode", "1",
                str(pdb_path), str(out_path),
            ],
            capture_output=True,
            text=True,
        )
        if proc.returncode != 0 or not out_path.exists():
            raise RuntimeError(
                f"foldseek structureto3didescriptor failed "
                f"(exit {proc.returncode}): {proc.stderr.strip()}"
            )
        lines = out_path.read_text().splitlines()

    if len(lines) != 1:
        raise ValueError(f"Expected foldseek output for one chain, got {len(lines)}")
    _, fs_seq, struc_seq = lines[0].split("\t")[:3]

    # foldseek encodes residues that have a CA atom
    ca = atom_array[atom_array.atom_name == "CA"]
    res_ids = np.asarray(ca.res_id, dtype=int)
    res_seq = "".join(
        ProteinSequence.convert_letter_3to1(name)
        if name in ProteinSequence._dict_3to1 else "X"
        for name in ca.res_name
    )

    if len(fs_seq) != len(struc_seq) or len(fs_seq) != len(res_ids):
        raise ValueError(
            f"foldseek encoded {len(fs_seq)} residues ({len(struc_seq)} 3Di states) "
            f"but the structure has {len(res_ids)} CA residues"
        )
    mismatch = [
        i for i, (a, b) in enumerate(zip(fs_seq, res_seq))
        if a != b and "X" not in (a, b)
    ]
    if mismatch:
        raise ValueError(
            f"foldseek residue sequence disagrees with the structure at "
            f"{len(mismatch)} positions (first: res_id {res_ids[mismatch[0]]})"
        )

    return res_ids, struc_seq.lower()


class SaProt(BaseModel, Scorer, MutationScorer, ConditionalMutationScorer):
    """
    Wrapper around SaProt zero-shot substitution scoring (masked marginals
    over the structure-aware vocabulary).
    """
    available = IMPORT_AVAILABLE
    name: str = "SaProt"
    citations: list[str] = [
        # SaProt: Protein Language Modeling with Structure-aware Vocabulary
        # (ICLR 2024)
        "10.1101/2023.10.01.560349",
    ]

    requires_target: bool = True
    # one 3Di token per target residue
    requires_fixed_length: bool = True
    # vocabulary has no gap token
    handles_deletions: bool = False
    handles_insertions: bool = False
    requires_gpu: bool = False
    supports_gpu: bool = True
    supports_gpu_parallel: bool = False
    supports_cpu_parallel: bool = False

    required_entity_attributes: list[str] | None = ["structures"]
    optional_entity_attributes: list[str] | None = None

    def __init__(
        self,
        model_name: str = "westlake-repl/SaProt_650M_AF2",
        foldseek_path: str | None = None,
        structure_name: str | None = None,
        allow_missing_structure: bool = True,
        batch_size: int = 8,
        device: str | None = None,
    ):
        """
        Instantiate a SaProt model.

        Parameters
        ----------
        model_name
            HuggingFace hub identifier or local directory of a SaProt
            checkpoint (EsmForMaskedLM + EsmTokenizer).
        foldseek_path
            Path to the foldseek binary. If None, foldseek is looked up on
            PATH.
        structure_name
            Key into the entity's structures mapping, if it has more than
            one structure. If None, the entity must have exactly one.
        allow_missing_structure
            If True, target positions the structure does not cover get the
            "#" (masked) 3Di token. If False, raise instead.
        batch_size
            Number of sequences per forward pass.
        device
            Torch device. Defaults to cuda if available, else cpu.
        """
        if not self.available:
            raise ImportError(
                "torch/transformers/biotite dependencies could not be imported. "
                "Install them to use this model."
            )

        if foldseek_path is None:
            foldseek_path = shutil.which("foldseek")
            if foldseek_path is None:
                raise FileNotFoundError(
                    "foldseek not found on PATH; set foldseek_path explicitly"
                )

        self.model_name = model_name
        self.foldseek_path = foldseek_path
        self.structure_name = structure_name
        self.allow_missing_structure = allow_missing_structure
        self.batch_size = batch_size
        self.device = device if device is not None else (
            "cuda" if torch.cuda.is_available() else "cpu"
        )

        self._system: System | None = None
        # (length,) 3Di letters fed to the model ("#" where uncovered)
        self.structure_tokens: str | None = None
        # target position index -> (20,) log P(aa) over VALID_AA_SORTED with
        # only that position masked in the target sequence
        self._site_cache: dict[int, np.ndarray] = {}

        # not pickled, reloaded on demand (see __getstate__)
        self._model = None
        self._tokenizer = None
        self._group_index = None

    def __getstate__(self):
        state = self.__dict__.copy()
        state["_model"] = None
        state["_tokenizer"] = None
        state["_group_index"] = None
        return state

    @property
    def system(self) -> System | None:
        return self._system

    @property
    def ready(self) -> bool:
        return self._system is not None and self.structure_tokens is not None

    @classmethod
    def can_model(cls, system: System, data: Any = None) -> tuple[bool, str]:
        if data is not None:
            return False, "Model does not support a data parameter (must be None)"

        if len(system) != 1 or system[0].type != "protein":
            return False, "Can only handle a single-component protein system"

        target = system[0]
        if not target.defined_sequence():
            return False, "Entity must have a defined rep sequence"

        if not target.structures:
            return False, "Must provide a structure (entity.structures) for model inference"

        return True, ""

    def _load_model(self):
        if self._model is not None:
            return

        tokenizer = EsmTokenizer.from_pretrained(self.model_name)
        model = EsmForMaskedLM.from_pretrained(self.model_name)

        # The MLM decoder must be tied to (or loaded with the same values as)
        # the word embeddings, otherwise it was randomly initialized
        decoder = model.get_output_embeddings().weight
        embeddings = model.get_input_embeddings().weight
        if decoder.data_ptr() != embeddings.data_ptr() and not torch.equal(decoder, embeddings):
            raise RuntimeError(
                "SaProt MLM decoder does not match the word embeddings (randomly "
                "initialized decoder) - check the installed transformers version (<5)"
            )

        vocab = tokenizer.get_vocab()
        # token ids of every "<aa><3Di>" token, per amino acid; upstream sums
        # the contiguous slice vocab[aa + "p"] : +21, which this reproduces
        group_index = np.array([
            [vocab[aa + s] for s in FOLDSEEK_STRUC_VOCAB] for aa in VALID_AA_SORTED
        ])
        starts = group_index[:, 0]
        if not np.array_equal(group_index, starts[:, None] + np.arange(len(FOLDSEEK_STRUC_VOCAB))):
            raise RuntimeError("SaProt vocabulary is not laid out as upstream assumes")

        self._tokenizer = tokenizer
        self._model = model.to(self.device).eval()
        self._group_index = torch.as_tensor(group_index, device=self.device)

    def _select_target_chain(self, target: Entity) -> Structure:
        """
        Pick the structure (see structure_name) and return its first chain.

        Parameters
        ----------
        target
            Target entity holding the structures mapping

        Returns
        -------
        Structure
            First chain of the selected structure, taken as the target
        """
        structures = target.structures
        if self.structure_name is not None:
            if self.structure_name not in structures:
                raise ValueError(
                    f"structure_name={self.structure_name!r} not found in entity "
                    f"structures, available keys: {list(structures.keys())}"
                )
            structure = structures[self.structure_name]
        else:
            if len(structures) != 1:
                raise ValueError(
                    "Entity has more than one structure; set structure_name to "
                    f"select one of: {list(structures.keys())}"
                )
            structure = next(iter(structures.values()))

        chain = list(ensure_sequence(structure))[0]
        if len(chain.chains()) != 1:
            raise ValueError(f"Expected a single-chain structure, got chains={chain.chains()}")
        return chain

    def _structure_tokens(self, target: Entity, rep: str) -> str:
        """
        Compute 3Di tokens for the target and align them to rep by residue number.

        Parameters
        ----------
        target
            Target entity holding the structures mapping
        rep
            Target sequence

        Returns
        -------
        str
            One 3Di letter per target position ("#" where uncovered)
        """
        length = len(rep)
        first_index = target.first_index
        chain = self._select_target_chain(target)

        # raises on residue identity mismatch with rep / out-of-range res_ids
        chain.represents(
            positions=list(range(first_index, first_index + length)),
            sequence=list(rep),
            allow_missing=True,
            raise_invalid=True,
        )

        res_ids, struc_seq = foldseek_3di(chain, self.foldseek_path)

        tokens = [MASK] * length
        for res_id, s in zip(res_ids, struc_seq):
            pos = int(res_id) - first_index
            if 0 <= pos < length:
                tokens[pos] = s if s in FOLDSEEK_STRUC_VOCAB else MASK

        n_missing = tokens.count(MASK)
        if n_missing and not self.allow_missing_structure:
            raise ValueError(
                f"Structure does not cover {n_missing}/{length} target positions; "
                "set allow_missing_structure=True to mask them"
            )

        return "".join(tokens)

    def build(
        self,
        system: System,
        data: None = None,
        status_callback: StatusCallback | None = None,
    ) -> Self:
        self.can_model_or_raise(system, data)
        status_start(status_callback, "Computing 3Di structure tokens and loading SaProt")

        self._system = system
        self._site_cache = {}
        target = system[0]
        rep = "".join(target.rep)

        self.structure_tokens = self._structure_tokens(target, rep)
        self._load_model()

        status_done(status_callback, "SaProt model built")

        return self

    def _masked_group_logp(
        self,
        seqs: Sequence[str],
        masked: Sequence[Sequence[int]],
    ) -> list[np.ndarray]:
        """
        Run SaProt with the amino-acid half of the given positions masked.

        Parameters
        ----------
        seqs
            Input sequences
        masked
            Position indices to mask, per sequence

        Returns
        -------
        list[np.ndarray]
            Per sequence, (len(masked[i]), 20) log P(aa) over VALID_AA_SORTED
            at each masked position
        """
        self._load_model()
        vocab = self._tokenizer.get_vocab()
        cls_id = self._tokenizer.cls_token_id
        eos_id = self._tokenizer.eos_token_id
        struc_seq = self.structure_tokens

        # One preallocated output array: many small per-sequence arrays
        # fragment the heap and grow memory without bound on large archives
        offsets = np.cumsum([0] + [len(m) for m in masked])
        out = np.empty((offsets[-1], len(VALID_AA_SORTED)), dtype=np.float32)
        k = 0
        for start in range(0, len(seqs), self.batch_size):
            batch_seqs = seqs[start:start + self.batch_size]
            batch_masked = masked[start:start + self.batch_size]

            input_ids = []
            for seq, mask_pos in zip(batch_seqs, batch_masked):
                aas = [aa if aa in FOLDSEEK_SEQ_VOCAB else MASK for aa in seq.upper()]
                for i in mask_pos:
                    aas[i] = MASK
                input_ids.append(
                    [cls_id] + [vocab[a + s] for a, s in zip(aas, struc_seq)] + [eos_id]
                )

            with torch.no_grad():
                logits = self._model(
                    input_ids=torch.tensor(input_ids, device=self.device)
                ).logits.float()
                for b, mask_pos in enumerate(batch_masked):
                    # +1 for the leading <cls> token
                    rows = torch.log_softmax(logits[b, [i + 1 for i in mask_pos]], dim=-1)
                    # log of the summed probability over each aa's 21 tokens
                    grouped = torch.logsumexp(rows[:, self._group_index], dim=-1)
                    out[offsets[k]:offsets[k + 1]] = grouped.cpu().numpy()
                    k += 1

        return [out[offsets[i]:offsets[i + 1]] for i in range(len(masked))]

    def _site_logp(self, positions: Sequence[int]) -> dict[int, np.ndarray]:
        """
        Single-position masked log P(aa) on the target sequence, cached.

        Parameters
        ----------
        positions
            Target position indices (0-based)

        Returns
        -------
        dict[int, np.ndarray]
            (20,) log P(aa) over VALID_AA_SORTED per position
        """
        missing = sorted(set(positions) - set(self._site_cache))
        if missing:
            rep = "".join(self.system[0].rep)
            out = self._masked_group_logp([rep] * len(missing), [[i] for i in missing])
            for i, row in zip(missing, out):
                self._site_cache[i] = row[0]
        return {i: self._site_cache[i] for i in positions}

    def score(
        self,
        instances: Sequence[SystemInstance],
        status_callback: StatusCallback | None = None,
    ) -> list[SystemInstance]:
        """
        Score sequences as the summed log P(mutant) - log P(wild type) over
        positions differing from the target, masked jointly.

        Parameters
        ----------
        instances
            Instances to score
        status_callback
            Optional callback for progress updates

        Returns
        -------
        list[SystemInstance]
            Instances with scores assigned
        """
        self.ready_or_raise()
        self._validate_instances(instances)

        if len(instances) == 0:
            return []

        status_start(status_callback, "Scoring sequences")

        aa_idx = {aa: i for i, aa in enumerate(VALID_AA_SORTED)}
        target_rep = np.asarray([str(s).upper() for s in self.system[0].rep])
        first_index = self.system[0].first_index

        diffs = []
        for instance in instances:
            rep = np.asarray([str(s).upper() for s in instance[0].rep])
            pos = np.flatnonzero(rep != target_rep)
            for p in pos:
                if rep[p] not in aa_idx or target_rep[p] not in aa_idx:
                    raise ValueError(
                        f"Symbol {rep[p]!r} or {target_rep[p]!r} at position "
                        f"{p + first_index} is not a standard amino acid"
                    )
            diffs.append((rep, pos))

        site = self._site_logp(sorted({int(d[1][0]) for d in diffs if len(d[1]) == 1}))

        # Masked positions lose their residue, so variants sharing a position
        # set share one forward pass
        multi_sets = sorted({tuple(pos.tolist()) for _, pos in diffs if len(pos) > 1})
        multi_logp = dict(zip(multi_sets, self._masked_group_logp(
            ["".join(target_rep)] * len(multi_sets),
            [list(s) for s in multi_sets],
        ))) if multi_sets else {}

        scores = np.zeros(len(instances), dtype=float)
        for k, (rep, pos) in enumerate(diffs):
            if len(pos) == 0:
                continue
            if len(pos) == 1:
                rows = site[int(pos[0])][None, :]
            else:
                rows = multi_logp[tuple(pos.tolist())]
            scores[k] = float(sum(
                row[aa_idx[rep[p]]] - row[aa_idx[target_rep[p]]]
                for row, p in zip(rows, pos)
            ))

        status_done(status_callback, "Scoring complete")

        return assign_scores_to_instances(instances, scores)

    def single_mutation_scan(
        self,
        instance: SystemInstance,
        entity: int | None = None,
        positions: Sequence[int] | None = None,
        status_callback: StatusCallback | None = None,
    ) -> pd.DataFrame:
        """
        Compute all single substitutions to an instance, masking each position
        in the instance's own sequence and scoring relative to its residue.

        Parameters
        ----------
        instance
            Instance to scan
        entity
            Entity to scan (only 0 is modelled)
        positions
            Positions to scan; all if None
        status_callback
            Optional callback for progress updates

        Returns
        -------
        pd.DataFrame
            Scores indexed by (entity, pos, ref) with one column per symbol
        """
        self.ready_or_raise()
        self._validate_instances([instance])

        if entity is not None and entity != 0:
            raise ValueError("SaProt only models entity 0")

        if positions is not None:
            if entity is None:
                raise ValueError(
                    "Parameter entity must be explicitly specified if using parameter positions"
                )
            self.valid_positions(positions, instance, entity, raise_invalid=True)

        status_start(status_callback, "Computing single mutation scan")

        first_index = self.system[0].first_index
        rep = [str(s) for s in instance[0].rep]
        length = len(rep)
        pos_filter = set(positions) if positions is not None else None
        indices = [
            i for i in range(length)
            if pos_filter is None or i + first_index in pos_filter
        ]

        if "".join(rep).upper() == "".join(self.system[0].rep).upper():
            logp = self._site_logp(indices)
        else:
            seq = "".join(rep)
            logp = dict(zip(indices, (r[0] for r in self._masked_group_logp(
                [seq] * len(indices), [[i] for i in indices]
            ))))

        aa_idx = {aa: i for i, aa in enumerate(VALID_AA_SORTED)}
        rows = []
        index_tuples = []
        for i in indices:
            ref_symbol = rep[i]
            ref_i = aa_idx.get(ref_symbol.upper())
            ref_score = logp[i][ref_i] if ref_i is not None else np.nan
            rows.append(logp[i] - ref_score)
            index_tuples.append((0, i + first_index, ref_symbol))

        df = pd.DataFrame(
            rows,
            columns=VALID_AA_SORTED,
            index=pd.MultiIndex.from_tuples(index_tuples, names=["entity", "pos", "ref"]),
        )

        merged_alphabet = Entity.merge_alphabet_symbols([
            self.system[0].alphabet(
                include_gap=self.handles_deletions,
                include_inserts=self.handles_insertions,
            )
        ])
        df = df.reindex(merged_alphabet, axis=1)

        status_done(status_callback, "Single mutation scan complete")

        return df

    def score_conditional(
        self,
        instances: Sequence[SystemInstance],
        entities: Sequence[int],
        positions: Sequence[int],
        status_callback: StatusCallback | None = None,
    ) -> pd.DataFrame:
        """
        Return raw log P(aa) at one position per instance, with that position
        masked in the instance's own sequence.

        Parameters
        ----------
        instances
            Instances to score
        entities
            Entity index per instance
        positions
            Position per instance
        status_callback
            Optional callback for progress updates

        Returns
        -------
        pd.DataFrame
            Log-probabilities indexed by (instance, entity, pos)
        """
        self.ready_or_raise()

        if not len(instances) == len(entities) == len(positions):
            raise ValueError(
                "Sequences for instances, entities and positions must all have same length"
            )

        self._validate_instances(instances)

        for instance, entity, pos in zip(instances, entities, positions):
            self.valid_positions(
                positions=[pos], instance=instance, entities=[entity], raise_invalid=True
            )

        status_start(status_callback, "Computing conditional scores")

        first_index = self.system[0].first_index
        logp = self._masked_group_logp(
            ["".join(str(s) for s in instance[0].rep) for instance in instances],
            [[int(pos) - first_index] for pos in positions],
        )

        rows = [row[0] for row in logp]
        index_tuples = [
            (inst_idx, int(entity), int(pos))
            for inst_idx, (entity, pos) in enumerate(zip(entities, positions))
        ]

        df = pd.DataFrame(
            rows,
            columns=VALID_AA_SORTED,
            index=pd.MultiIndex.from_tuples(index_tuples, names=["instance", "entity", "pos"]),
        )

        merged_alphabet = Entity.merge_alphabet_symbols([
            self.system[entity_idx].alphabet(
                include_gap=self.handles_deletions,
                include_inserts=self.handles_insertions,
            ) for entity_idx in set(entities)
        ])
        df = df.reindex(merged_alphabet, axis=1)

        status_done(status_callback, "Conditional scoring complete")

        return df
