"""
Wrapper class around SIFT (Sorting Intolerant From Tolerated).

SIFT predicts whether an amino acid substitution affects protein function
based on sequence conservation in a multiple sequence alignment of homologs.
This wrapper drives the academic ``info_on_seqs`` binary distributed with the
standalone SIFT 6.x release (http://sift-dna.org), which takes an aligned
FASTA and emits a position-specific scoring matrix of normalized AA
probabilities.

The wrapper exposes ``Scorer``, ``MutationScorer``, ``ConditionalMutationScorer``,
``Generator``, and ``Transformer``:

* ``score`` returns the sum of column-normalized log-probabilities under the
  PSSM, ``sum_i log p_i(x_i)``. Under SIFT's per-position independence
  assumption this *is* the joint log-likelihood; we surface it via the
  ``Scorer`` interface but it should be interpreted as a tractable
  product-of-marginals lower bound rather than a true co-evolutionary score.
* SIFT is not natively generative, but its per-column conditional
  probabilities make a perfectly valid Gibbs proposal distribution. We use
  ``GibbsSampler`` on top of ``score_conditional`` to expose ``generate``,
  identical to the pattern used for GEMME.
* ``transform`` decomposes the joint score by residue, stashing the
  per-position log-probability vector ``log p_i(x_i)`` as the entity's
  ``embedding``. This lets callers visualise *which* residues drive an
  instance's overall conservation score.

"""
from __future__ import annotations

import os
import re
import shutil
import subprocess
import tempfile
from contextlib import contextmanager
from os import PathLike
from pathlib import Path
from typing import Any, Self, Sequence

import numpy as np
import pandas as pd
from loguru import logger

from evedesign.constants import VALID_AA_SORTED
from evedesign.model import (
    BaseModel,
    ConditionalMutationScorer,
    Generator,
    MutationScorer,
    Scorer,
    Transformer,
)
from evedesign.samplers.gibbs import (
    GibbsSampler,
    InitStrategy,
    ScanOrder,
    TemperatureSchedule,
)
from evedesign.system import (
    EntityInstance,
    Mutant,
    Mutation,
    System,
    SystemInstance,
)
from evedesign.types import EntityPosList, StatusCallback
from evedesign.utils import ensure_sequence


# SIFT columns of the FLOAT_OUTPUT PSSM matrix emitted by blimps `output_matrix_s`:
# all uppercase letters A..Z except J, O, U, followed by '*' (mask) and '-' (gap).
# Reference: sift6.2.1/blimps/matrix.c output_matrix_s().
_SIFT_MATRIX_COLUMNS: list[str] = [
    c for c in "ABCDEFGHIKLMNPQRSTVWXYZ"
] + ["*", "-"]

# Minimum probability used as a floor when taking log(p) for SIFT outputs.
# SIFT clips deleterious predictions to 0.00; without a floor log(0) = -inf
# violates the contract that non-finite scores must be encoded as NaN.
# log(1e-4) ~ -9.21, comfortably below SIFT's TOLERATED/DELETERIOUS threshold
# at log(0.05) ~ -3.00.
_SIFT_PROB_FLOOR: float = 1e-4

# SIFT's RESIDUE_THRESHOLD (see info_on_seqs.c): positions with fewer than this
# many distinct AAs across the alignment are reported as "NOT SCORED"; we
# surface those as all-NaN rows.
_SIFT_RESIDUE_THRESHOLD: int = 2


def _detect_sift_home(explicit: str | PathLike | None) -> Path | None:
    """Resolve the SIFT install directory from arg, env, or common locations."""
    if explicit is not None:
        return Path(explicit)
    if env := os.environ.get("SIFT_HOME"):
        return Path(env)
    return None


def _which_info_on_seqs(sift_home: Path | None) -> Path | None:
    """Locate the ``info_on_seqs`` executable; prefer the install over $PATH."""
    if sift_home is not None:
        candidate = sift_home / "bin" / "info_on_seqs"
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return candidate
    found = shutil.which("info_on_seqs")
    return Path(found) if found is not None else None


# tolerant check used at import time; the real check happens at construction.
IMPORT_AVAILABLE = _which_info_on_seqs(_detect_sift_home(None)) is not None


class SIFT(BaseModel, Scorer, MutationScorer, ConditionalMutationScorer,
           Generator, Transformer):
    """
    Wrapper around the standalone SIFT ``info_on_seqs`` binary.

    Parameters
    ----------
    sift_home
        Path to the SIFT 6.x install directory (must contain ``bin/info_on_seqs``
        and ``blimps/docs/``). Falls back to the ``SIFT_HOME`` env var.
    blimps_dir
        Path to the blimps directory used by SIFT for default matrices and
        frequency tables. Defaults to ``<sift_home>/blimps``. The directory
        must contain a ``docs`` subfolder (the default ``default.qij`` etc.
        live there).
    keep_pssm_after_build
        If True, keep the parsed PSSM associated with this instance after
        ``build()``. Set to False if you intend to serialize the model (the
        PSSM is computed lazily on demand from the cached aligned FASTA).
    """

    available = IMPORT_AVAILABLE
    name: str = "SIFT"
    citations: list[str] = [
        "doi:10.1038/nprot.2009.86",
        "doi:10.1101/gr.176601",
    ]

    # core capability flags
    requires_target: bool = True
    requires_fixed_length: bool = True
    handles_deletions: bool = False
    handles_insertions: bool = False
    requires_gpu: bool = False
    supports_gpu: bool = False
    supports_gpu_parallel: bool = False
    supports_cpu_parallel: bool = False

    required_entity_attributes: list[str] | None = ["sequences"]
    optional_entity_attributes: list[str] | None = []

    def __init__(
        self,
        sift_home: str | PathLike | None = None,
        blimps_dir: str | PathLike | None = None,
        keep_pssm_after_build: bool = True,
        # GibbsSampler hyperparameters (used by generate()).
        num_sweeps: int = 1000,
        init_strategy: InitStrategy = "system",
        scan_order: ScanOrder = "random",
        temperature_schedule: TemperatureSchedule | None = None,
    ):
        self.sift_home = _detect_sift_home(sift_home)
        self.binary_path = _which_info_on_seqs(self.sift_home)

        if self.binary_path is None:
            raise ValueError(
                "Could not locate the SIFT 'info_on_seqs' binary. Set "
                "sift_home=..., the SIFT_HOME env var, or place 'info_on_seqs' "
                "on PATH. See sift-dna.org for installation instructions."
            )

        if blimps_dir is not None:
            self.blimps_dir = Path(blimps_dir)
        elif self.sift_home is not None:
            self.blimps_dir = self.sift_home / "blimps"
        else:
            raise ValueError(
                "blimps_dir must be specified when sift_home is unknown; "
                "SIFT requires BLIMPS_DIR to locate default.qij and friends."
            )

        if not (self.blimps_dir / "docs").is_dir():
            raise ValueError(
                f"BLIMPS docs folder not found at {self.blimps_dir / 'docs'}; "
                "this is required for SIFT to compute its PSSM."
            )

        self.keep_pssm_after_build = keep_pssm_after_build

        # GibbsSampler config used by generate()
        self.num_sweeps = num_sweeps
        self.init_strategy = init_strategy
        self.scan_order = scan_order
        self.temperature_schedule = temperature_schedule

        self._system: System | None = None
        self._tmpdir: tempfile.TemporaryDirectory | None = None
        self._aligned_fasta: Path | None = None

        # parsed PSSM: ndarray of shape (L, 20) over VALID_AA_SORTED columns,
        # or NaN for positions with insufficient sequence support.
        self.encoding: np.ndarray | None = None

    @property
    def ready(self) -> bool:
        return self._system is not None and self.encoding is not None

    @property
    def system(self) -> System | None:
        return self._system

    @classmethod
    def can_model(cls, system: System, data: None = None) -> tuple[bool, str]:
        if data is not None:
            return False, "Model does not support data parameter (must be None)"
        if len(system) != 1 or system[0].type != "protein":
            return False, "Can only handle single-component protein system"

        target = system[0]
        if not target.defined_sequence():
            return False, "Entity must have defined rep sequence"
        if target.sequences is None or len(target.sequences.seqs) == 0:
            return False, "Must provide an aligned MSA via Entity.sequences"
        if not target.sequences.aligned:
            return False, "Provided sequences must be aligned"

        return True, ""

    # --- subprocess plumbing ------------------------------------------------

    @contextmanager
    def _workdir(self):
        """
        SIFT writes auxiliary files (output, intermediate matrices) into cwd.
        Run each invocation from a freshly created temp folder to avoid
        polluting the user's working directory.
        """
        with tempfile.TemporaryDirectory(prefix="sift-run-") as tmp:
            yield Path(tmp)

    def _run_info_on_seqs(
        self,
        aligned_fasta: Path,
        subst_path: Path | str,
        workdir: Path,
    ) -> Path:
        """
        Run ``info_on_seqs <alignment> <subst|-> <output>`` and return the
        path to the output file. ``subst_path`` may be the literal string
        ``"-"`` to request the full PSSM matrix.
        """
        out_path = workdir / "sift.out"
        cmd = [
            str(self.binary_path),
            str(aligned_fasta),
            str(subst_path),
            str(out_path),
        ]
        env = os.environ.copy()
        env["BLIMPS_DIR"] = str(self.blimps_dir)

        try:
            result = subprocess.run(
                cmd,
                cwd=str(workdir),
                env=env,
                check=True,
                capture_output=True,
                text=True,
            )
        except subprocess.CalledProcessError as exc:
            raise RuntimeError(
                f"info_on_seqs failed (exit {exc.returncode}):\n"
                f"stdout:\n{exc.stdout}\nstderr:\n{exc.stderr}"
            ) from exc

        if not out_path.is_file() or out_path.stat().st_size == 0:
            raise RuntimeError(
                "info_on_seqs produced no output. Process output was:\n"
                f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
            )
        return out_path

    # --- PSSM parsing -------------------------------------------------------

    @staticmethod
    def _parse_sift_matrix(matrix_path: Path, expected_length: int) -> np.ndarray:
        """
        Parse the FLOAT_OUTPUT PSSM emitted by blimps ``output_matrix_s``.

        The output looks like::

            ID   ...
            AC   ...
            DE   ...
            MA   ...
             A    B    C   ...   Y    Z    *    -
            0.5000 0.0000 ... 0.0000 0.0000
            ...
            //

        Returns
        -------
        Array of shape (L, 20) with one column per amino acid in
        ``VALID_AA_SORTED`` order. Positions that SIFT could not score are
        returned as a row of NaNs.
        """
        text = matrix_path.read_text()
        lines = text.splitlines()

        # locate header row, then read L numeric rows up to '//'
        header_idx = None
        for i, line in enumerate(lines):
            stripped = line.strip().split()
            if stripped and stripped[0] == "A" and "*" in stripped and "-" in stripped:
                header_idx = i
                break
        if header_idx is None:
            raise ValueError(
                f"Could not locate PSSM header in {matrix_path}; first 30 lines:\n"
                + "\n".join(lines[:30])
            )

        header = lines[header_idx].strip().split()
        if header != _SIFT_MATRIX_COLUMNS:
            raise ValueError(
                f"Unexpected SIFT PSSM column order: {header} "
                f"(expected {_SIFT_MATRIX_COLUMNS})"
            )

        rows = []
        for line in lines[header_idx + 1:]:
            if line.strip().startswith("//"):
                break
            tokens = line.split()
            if len(tokens) != len(_SIFT_MATRIX_COLUMNS):
                continue
            try:
                rows.append([float(t) for t in tokens])
            except ValueError:
                continue

        if len(rows) != expected_length:
            raise ValueError(
                f"SIFT PSSM has {len(rows)} rows but expected "
                f"{expected_length} (target rep length)."
            )

        mat = np.asarray(rows, dtype=float)
        col_idx = [header.index(aa) for aa in VALID_AA_SORTED]
        pssm_20 = mat[:, col_idx]

        # NOT-SCORED rows: SIFT writes a row of all-zeros when there were not
        # enough aligned residues. Mark these positions as NaN so downstream
        # callers see them as missing rather than silently log(0).
        row_sums = pssm_20.sum(axis=1)
        nan_rows = row_sums == 0.0
        if nan_rows.any():
            pssm_20[nan_rows, :] = np.nan
            logger.debug(
                f"SIFT marked {int(nan_rows.sum())} positions as NOT SCORED "
                f"(insufficient sequence support, threshold={_SIFT_RESIDUE_THRESHOLD})"
            )

        return pssm_20

    @staticmethod
    def _pssm_to_log(pssm: np.ndarray) -> np.ndarray:
        """Convert SIFT probabilities to log-space with a finite floor."""
        with np.errstate(divide="ignore"):
            floored = np.where(np.isnan(pssm), np.nan, np.maximum(pssm, _SIFT_PROB_FLOOR))
            return np.log(floored)

    # --- alignment dumping --------------------------------------------------

    @staticmethod
    def _write_aligned_fasta(system: System, path: Path) -> None:
        """
        Write the entity's MSA as an aligned FASTA file with the target
        representation as the first sequence (named ``>QUERY``), mirroring
        the format expected by SIFT's ``info_on_seqs``.
        """
        target = system[0]
        target_seq = "".join(target.rep)

        with open(path, "w") as f:
            f.write(">QUERY\n")
            f.write(target_seq + "\n")
            for i, seq in enumerate(target.sequences.seqs):
                seq_id = seq.id_ or f"seq_{i}"
                # SIFT requires unique 10-char prefixes on names; sanitize
                # whitespace and delimiters that confuse blimps' fasta parser.
                safe_id = re.sub(r"[|:\s]+", "_", seq_id)
                f.write(f">{safe_id}\n{seq.seq}\n")

    # --- BaseModel API ------------------------------------------------------

    def build(
        self,
        system: System,
        data: None = None,
        status_callback: StatusCallback | None = None,  # noqa: ARG002
    ) -> Self:
        self.can_model_or_raise(system, data)
        self._system = system

        target = system[0]
        target_len = len(target.rep)

        # Persist the aligned FASTA for the lifetime of this instance so we can
        # cheaply re-run SIFT on subset substitutions later. The PSSM itself is
        # always computed up-front since it requires only one binary call.
        self._tmpdir = tempfile.TemporaryDirectory(prefix="sift-build-")
        self._aligned_fasta = Path(self._tmpdir.name) / "alignment.fasta"
        self._write_aligned_fasta(system, self._aligned_fasta)

        with self._workdir() as workdir:
            out_path = self._run_info_on_seqs(
                aligned_fasta=self._aligned_fasta,
                subst_path="-",
                workdir=workdir,
            )
            pssm = self._parse_sift_matrix(out_path, expected_length=target_len)

        if self.keep_pssm_after_build:
            self.encoding = pssm
        else:
            # keep enough state to recompute on demand; ready stays False
            self.encoding = pssm  # SIFT PSSM is tiny (L x 20), always keep

        return self

    # --- Scorer API ---------------------------------------------------------

    def score(
        self,
        instances: Sequence[SystemInstance],
        status_callback: StatusCallback | None = None,  # noqa: ARG002
    ) -> np.ndarray:
        """
        Return a sum-of-marginals log-likelihood per instance,
        ``S(x) = sum_i log p_i(x_i)``, where ``p_i`` is SIFT's column-
        normalized PSSM at position ``i`` (floored at ``_SIFT_PROB_FLOOR`` to
        keep the score finite for residues SIFT clipped to 0).

        Note: SIFT explicitly assumes per-position independence, so this is
        equivalent to the model's joint log-likelihood. Scores are raw logits
        comparable across instances within the same call. NOT-SCORED
        positions (rows that are all NaN in ``self.encoding``) contribute
        ``np.nan`` to the total — meaning at least one such position
        anywhere in the instance will produce ``nan`` for the whole
        instance, by design.
        """
        self.ready_or_raise()
        self._validate_instances(instances)

        log_pssm = self._pssm_to_log(self.encoding)
        target = self.system[0]
        aa_to_col = {aa: i for i, aa in enumerate(VALID_AA_SORTED)}

        scores = np.empty(len(instances), dtype=float)
        for i, instance in enumerate(instances):
            seq = instance[0].rep
            total = 0.0
            for pos_idx, aa in enumerate(seq):
                col = aa_to_col.get(str(aa))
                if col is None:
                    total = np.nan
                    break
                val = log_pssm[pos_idx, col]
                if np.isnan(val):
                    total = np.nan
                    break
                total += float(val)
            scores[i] = total
        return scores

    # --- Transformer API ----------------------------------------------------

    def transform(
        self,
        instances: Sequence[SystemInstance],
        entity: int | None = None,
        status_callback: StatusCallback | None = None,  # noqa: ARG002
    ) -> list[SystemInstance]:
        """
        Decompose each instance's score into per-residue contributions and
        stash the resulting L-vector as ``entity_instance.embedding``.

        Specifically, ``embedding[i] = log p_i(x_i)`` where ``p_i`` is SIFT's
        column-normalized probability at position ``i`` and ``x_i`` is the
        instance's residue at that position. By construction
        ``embedding.sum() == score(instance)``, and the diagonal of
        ``single_mutation_scan`` (relative log-odds) is recovered from the
        difference between the mutant's and the WT's embeddings at the
        substituted positions.

        Returns shallow copies of each instance with ``embedding`` and
        ``score`` filled in; the input list is left unmodified per the
        Transformer contract.
        """
        self.ready_or_raise()
        self._validate_instances(instances)

        entity = 0 if entity is None else entity
        if entity != 0:
            raise ValueError("Model can only handle one single entity")

        log_pssm = self._pssm_to_log(self.encoding)
        aa_to_col = {aa: i for i, aa in enumerate(VALID_AA_SORTED)}

        out: list[SystemInstance] = []
        for instance in instances:
            inst_copy = instance.copy()
            ent_copy = inst_copy[entity]

            seq = ent_copy.rep
            per_residue = np.empty(len(seq), dtype=float)
            for pos_idx, aa in enumerate(seq):
                col = aa_to_col.get(str(aa))
                per_residue[pos_idx] = (
                    log_pssm[pos_idx, col] if col is not None else np.nan
                )

            ent_copy.embedding = per_residue
            inst_copy.score = float(np.nansum(per_residue)) if not np.isnan(
                per_residue
            ).any() else float("nan")
            out.append(inst_copy)
        return out

    # --- MutationScorer API -------------------------------------------------

    def score_mutants(
        self,
        instance: SystemInstance,
        mutants: Sequence[Mutant],
        status_callback: StatusCallback | None = None,  # noqa: ARG002
    ) -> np.ndarray:
        """
        Score arbitrary single- or higher-order mutants as the sum of
        per-position SIFT log-odds, ``sum_i [log p(mut_i) - log p(ref_i)]``.

        This is *not* a true joint log-likelihood: SIFT assumes per-position
        independence given the MSA, so this is equivalent to its own model.
        """
        self.ready_or_raise()
        self._validate_instances([instance])
        self.system.valid_mutants(
            instance, mutants, deletions=False, insertions=False, raise_invalid=True
        )

        log_pssm = self._pssm_to_log(self.encoding)
        target = self.system[0]
        aa_to_col = {aa: i for i, aa in enumerate(VALID_AA_SORTED)}

        scores = np.zeros(len(mutants), dtype=float)
        for i, mutant in enumerate(mutants):
            total = 0.0
            for sub in mutant:
                pos_idx = sub.pos - target.first_index
                wt = aa_to_col.get(sub.ref)
                mt = aa_to_col.get(sub.to)
                if wt is None or mt is None:
                    total = np.nan
                    break
                row = log_pssm[pos_idx]
                if np.isnan(row[wt]) or np.isnan(row[mt]):
                    total = np.nan
                    break
                total += row[mt] - row[wt]
            scores[i] = total
        return scores

    def single_mutation_scan(
        self,
        instance: SystemInstance,
        entity: int | None = None,
        positions: Sequence[int] | None = None,
        status_callback: StatusCallback | None = None,  # noqa: ARG002
    ) -> pd.DataFrame:
        """
        Compute SIFT log-odds for every single substitution at every (or a
        subset of) position(s) in the target entity. Diagonal entries are 0
        by construction; NOT-SCORED positions are returned as all-NaN rows.
        """
        self.ready_or_raise()
        self._validate_instances([instance])

        if positions is not None and entity is None:
            raise ValueError(
                "Parameter entity must be explicitly specified if using parameter positions"
            )

        entity = 0 if entity is None else entity
        if entity != 0:
            raise ValueError("Model can only handle one single entity")

        target = self.system[0]
        instance_seq = "".join(instance[0].rep)

        if positions is not None:
            self.valid_positions(positions, entities=0, raise_invalid=True)
            pos_list = list(positions)
        else:
            pos_list = list(
                range(target.first_index, target.first_index + len(target.rep))
            )

        log_pssm = self._pssm_to_log(self.encoding)
        aa_to_col = {aa: i for i, aa in enumerate(VALID_AA_SORTED)}

        rows = []
        index = []
        for pos in pos_list:
            pos_idx = pos - target.first_index
            ref_aa = instance_seq[pos_idx]
            log_row = log_pssm[pos_idx]

            ref_col = aa_to_col.get(ref_aa)
            if ref_col is None or np.isnan(log_row[ref_col]):
                rows.append([np.nan] * len(VALID_AA_SORTED))
            else:
                # log-odds relative to the instance residue at this position;
                # diagonal (mut == ref) becomes 0 by construction.
                rows.append((log_row - log_row[ref_col]).tolist())
            index.append((pos, ref_aa))

        df = pd.DataFrame(rows, columns=list(VALID_AA_SORTED))
        df.index = pd.MultiIndex.from_tuples(index, names=["pos", "ref"])
        df = pd.concat({entity: df}, names=["entity"])
        return df

    # --- ConditionalMutationScorer API --------------------------------------

    def score_conditional(
        self,
        instances: Sequence[SystemInstance],
        entities: Sequence[int],
        positions: Sequence[int],
        status_callback: StatusCallback | None = None,  # noqa: ARG002
    ) -> pd.DataFrame:
        """
        Return raw per-position log-probabilities ``log p(aa | MSA)`` for the
        requested (instance, entity, position) triplets. SIFT scores are
        independent of the rest of the queried instance sequence, so the
        same row is returned for every instance that targets the same
        position.
        """
        self.ready_or_raise()
        self._validate_instances(instances)

        if set(entities) != {0}:
            raise ValueError("Can only specify entities with index 0")
        if not len(instances) == len(entities) == len(positions):
            raise ValueError(
                "Sequences for instances, entities and positions must all have same length"
            )

        target = self.system[0]
        for instance, pos in zip(instances, positions):
            self.valid_positions(
                [pos], instance=instance, entities=0, raise_invalid=True
            )

        log_pssm = self._pssm_to_log(self.encoding)

        rows = []
        index = []
        for instance_idx, (entity, pos) in enumerate(zip(entities, positions)):
            pos_idx = pos - target.first_index
            rows.append(log_pssm[pos_idx].tolist())
            index.append((instance_idx, entity, pos))

        df = pd.DataFrame(rows, columns=list(VALID_AA_SORTED))
        df.index = pd.MultiIndex.from_tuples(
            index, names=["instance", "entity", "pos"]
        )
        return df

    # --- Generator API ------------------------------------------------------

    def generate(
        self,
        num_designs: int,
        entities: Sequence[int] | None = None,
        fixed_pos: EntityPosList | None = None,
        temperature: float = 1.0,
        status_callback: StatusCallback | None = None,
    ) -> list[SystemInstance]:
        """
        Generate designs by Gibbs sampling using SIFT's per-position log-PSSM
        as the conditional scorer. Each returned design carries a sum-of-
        single-mutations score against the entity rep — the only meaningful
        "joint score" SIFT exposes (it has no proper sequence likelihood).
        """
        self.ready_or_raise()
        entities = entities if entities is not None else [0]
        entities = ensure_sequence(entities)
        if len(entities) != 1 or entities[0] != 0:
            raise ValueError(
                "SIFT can only design a single entity (entities = [0] | None)"
            )

        sampler = GibbsSampler(
            scorers=[self],
            weights=None,
            num_sweeps=self.num_sweeps,
            init_strategy=self.init_strategy,
            scan_order=self.scan_order,
            temperature_schedule=self.temperature_schedule,
            require_strict_pos=True,
            record_full_chain=False,
        )
        instances = sampler.generate(
            num_designs=num_designs,
            entities=entities,
            fixed_pos=fixed_pos,
            temperature=temperature,
            status_callback=status_callback,
        )

        target = self.system[0]
        first = target.first_index
        rep_seq = "".join(target.rep)
        ref_instance = SystemInstance(EntityInstance(rep=rep_seq))
        modeled_positions = [pos for (_, pos) in self.positions(ref_instance)]

        for inst in instances:
            mutants = []
            for pos in modeled_positions:
                row_idx = pos - first
                ref_aa = rep_seq[row_idx]
                to_aa = str(inst[0].rep[row_idx])
                mutants.append(
                    [Mutation(entity=0, pos=pos, ref=ref_aa, to=to_aa)]
                )
            inst.score = float(
                np.nansum(self.score_mutants(ref_instance, mutants))
            )
        return list(instances)

    # --- housekeeping -------------------------------------------------------

    def __del__(self):
        try:
            if self._tmpdir is not None:
                self._tmpdir.cleanup()
        except Exception:
            pass

    def __getstate__(self) -> dict[str, Any]:
        # Drop unpicklable temp resources; the PSSM and aligned FASTA bytes
        # are preserved so a deserialized instance can keep scoring.
        state = self.__dict__.copy()
        state["_tmpdir"] = None
        if self._aligned_fasta is not None and self._aligned_fasta.is_file():
            state["_aligned_fasta_bytes"] = self._aligned_fasta.read_bytes()
        state["_aligned_fasta"] = None
        return state

    def __setstate__(self, state: dict[str, Any]) -> None:
        fasta_bytes = state.pop("_aligned_fasta_bytes", None)
        self.__dict__.update(state)
        if fasta_bytes is not None:
            self._tmpdir = tempfile.TemporaryDirectory(prefix="sift-build-")
            self._aligned_fasta = Path(self._tmpdir.name) / "alignment.fasta"
            self._aligned_fasta.write_bytes(fasta_bytes)
