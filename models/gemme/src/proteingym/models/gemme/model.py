"""
Wrapper class around GEMME (Global Epistatic Model for predicting Mutational
Effects; Laine, Karami, Carbone, Molecular Biology and Evolution 2019,
doi:10.1093/molbev/msz179).

GEMME predicts the mutational landscape of a protein from a single multiple
sequence alignment by combining per-position evolutionary conservation
(estimated with JET2) and a global epistatic term that summarises how far
each homologous sequence is from the query in conservation-weighted Hamming
distance. The output is a 20xL matrix of log-odds-like scores that is
normalised so the wild-type residue at each position has score 0.

The reference implementation is a small Python 2.7 / R / Java stack
distributed as source from the LCQB lab
(https://www.lcqb.upmc.fr/GEMME/download.html). This wrapper drives the
local source tree directly via ``subprocess`` -- no Docker plumbing in the
wrapper itself -- pointed at by ``GEMME_PATH`` (the GEMME source root, which
ships ``gemme.py``) and ``JET_PATH`` (the JET2 source root used by GEMME
internally for conservation analysis). Both env vars match the upstream
convention.

Interfaces implemented:

* `MutationScorer`            — single-site scan from the cached matrix and
                                higher-order mutants via a second GEMME run
                                (using ``-m mutations.txt``).
* `ConditionalMutationScorer` — per-position log-odds slice of the matrix.
* `Generator`                 — Gibbs sampling via the standard
                                ``GibbsSampler`` composition.
* `Transformer`               — stashes per-residue normalised log-odds (vs
                                the entity rep) as ``entity_instance.embedding``.

We deliberately do **not** implement `Scorer`: GEMME outputs are
per-substitution deltas, not joint sequence likelihoods. Summing the
per-position contributions would give nothing more than the sum of
single-mutant scores, which conveys no extra information.
"""
from __future__ import annotations

import os
import re
import shutil
import subprocess
import tempfile
from contextlib import contextmanager
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
    System,
    SystemInstance,
)
from evedesign.types import EntityPosList, StatusCallback
from evedesign.utils import ensure_sequence


# GEMME emits scores in the lowercase alphabet
#   a c d e f g h i k l m n p q r s t v w y
# which is the same set as evedesign's `VALID_AA_SORTED` in upper case.
_GEMME_AA_LOWER: list[str] = [aa.lower() for aa in VALID_AA_SORTED]

# Floor used when a non-NaN GEMME score happens to be -inf (in practice we
# have not observed this, but the parser is defensive). Matches SIFT's floor
# convention to keep the contract test ("no -inf cells") satisfied.
_GEMME_SCORE_FLOOR: float = -20.0


# Default install paths used by the upstream Docker image and the canonical
# tarball layout from the LCQB download page. Either env var or constructor
# argument can override these.
_DEFAULT_GEMME_PATH = "/opt/GEMME"
_DEFAULT_JET_PATH = "/opt/JET2"


def _detect_gemme_install(
    explicit_gemme: str | os.PathLike | None,
    explicit_jet: str | os.PathLike | None,
) -> tuple[Path | None, Path | None]:
    """
    Resolve GEMME and JET2 source roots from explicit args, env vars, or
    the canonical defaults. Returns (None, None) for components missing
    on this machine.
    """
    gemme = explicit_gemme or os.environ.get("GEMME_PATH") or _DEFAULT_GEMME_PATH
    jet = explicit_jet or os.environ.get("JET_PATH") or _DEFAULT_JET_PATH
    gemme_path = Path(gemme).resolve() if gemme else None
    jet_path = Path(jet).resolve() if jet else None
    if gemme_path is not None and not (gemme_path / "gemme.py").is_file():
        gemme_path = None
    if jet_path is not None and not jet_path.is_dir():
        jet_path = None
    return gemme_path, jet_path


# Importable iff a usable GEMME tree, a JET2 tree, and a `python2.7` binary
# are all reachable. Note: we still allow construction when this is False --
# the wrapper raises a precise error at that point -- so unit tests that
# only inspect class metadata can still import the module.
def _import_check() -> bool:
    gemme_path, jet_path = _detect_gemme_install(None, None)
    return (
        gemme_path is not None
        and jet_path is not None
        and shutil.which("python2.7") is not None
    )


IMPORT_AVAILABLE = _import_check()


class GEMME(
    BaseModel,
    MutationScorer,
    ConditionalMutationScorer,
    Generator,
    Transformer,
):
    """
    Wrapper around the GEMME 2019 reference implementation, run from a local
    source checkout (https://www.lcqb.upmc.fr/GEMME/download.html).

    Parameters
    ----------
    gemme_path
        Path to the GEMME source root that contains ``gemme.py``. Defaults
        to the ``GEMME_PATH`` env var, falling back to ``/opt/GEMME`` (the
        canonical install location used by the upstream Docker image).
    jet_path
        Path to the JET2 source root used internally by GEMME for
        conservation analysis. Defaults to ``JET_PATH``, falling back to
        ``/opt/JET2``.
    python_command
        Python interpreter to invoke. GEMME's reference implementation is
        Python 2.7 only, so the default is ``"python2.7"``.
    n_iter
        Number of JET iterations used to compute conservation levels. The
        paper recommends 1 (default) for production; bump to 7-10 to
        mitigate JET's Gibbs-sampling stochasticity at higher cost.
    n_seqs
        Maximum number of MSA sequences passed to JET. Larger MSAs are
        subsampled. Default of 20000 matches the upstream webserver.
    model_variant
        Which GEMME output table to load: ``"combi"`` (default; combined
        independent + epistatic, recommended for most tasks), ``"epi"``
        (epistatic only) or ``"ind"`` (independent / per-position only).
    keep_workdir
        If True, the temp directory where GEMME wrote its outputs is kept
        around for inspection. Defaults to False (cleaned up on ``__del__``).
    """

    available = IMPORT_AVAILABLE
    name: str = "GEMME"
    citations: list[str] = [
        "doi:10.1093/molbev/msz179",
    ]

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

    _VARIANT_TO_FILE = {
        "combi": "normPred_evolCombi.txt",
        "epi": "normPred_evolEpi.txt",
        "ind": "normPred_evolInd.txt",
    }

    def __init__(
        self,
        gemme_path: str | os.PathLike | None = None,
        jet_path: str | os.PathLike | None = None,
        python_command: str = "python2.7",
        n_iter: int = 1,
        n_seqs: int = 20000,
        model_variant: str = "combi",
        keep_workdir: bool = False,
        # GibbsSampler hyperparameters used by generate()
        num_sweeps: int = 1000,
        init_strategy: InitStrategy = "system",
        scan_order: ScanOrder = "random",
        temperature_schedule: TemperatureSchedule | None = None,
    ):
        if model_variant not in self._VARIANT_TO_FILE:
            raise ValueError(
                f"Unknown model_variant {model_variant!r}. "
                f"Expected one of {sorted(self._VARIANT_TO_FILE)}."
            )

        resolved_gemme, resolved_jet = _detect_gemme_install(gemme_path, jet_path)
        if resolved_gemme is None:
            raise ValueError(
                "Could not locate a GEMME source tree containing gemme.py. "
                "Set the GEMME_PATH env var (or pass gemme_path=...) to the "
                "directory you got from "
                "https://www.lcqb.upmc.fr/GEMME/download.html, or run inside "
                "the upstream Docker image elodielaine/gemme:gemme via "
                "scripts/gemme-in-docker.sh."
            )
        if resolved_jet is None:
            raise ValueError(
                "Could not locate a JET2 source tree. Set the JET_PATH env "
                "var (or pass jet_path=...) to the directory bundled with "
                "GEMME, or run inside the upstream Docker image."
            )
        if shutil.which(python_command) is None:
            raise ValueError(
                f"GEMME requires {python_command!r} on $PATH (the reference "
                "implementation is Python 2.7 only). Either install it "
                "natively or run inside the upstream Docker image via "
                "scripts/gemme-in-docker.sh."
            )

        self.gemme_path = resolved_gemme
        self.jet_path = resolved_jet
        self.python_command = python_command
        self.n_iter = int(n_iter)
        self.n_seqs = int(n_seqs)
        self.model_variant = model_variant
        self.keep_workdir = keep_workdir

        self.num_sweeps = num_sweeps
        self.init_strategy = init_strategy
        self.scan_order = scan_order
        self.temperature_schedule = temperature_schedule

        self._system: System | None = None
        self._tmpdir: tempfile.TemporaryDirectory | None = None
        self._workdir_path: Path | None = None
        self._query_name: str | None = None

        # (L, 20) GEMME score matrix in VALID_AA_SORTED column order, in
        # log-odds-like space relative to the WT residue at each position.
        # Diagonal entries are 0 by construction; NaN for positions/AAs
        # GEMME could not score.
        self.encoding: np.ndarray | None = None
        # (L,) per-position JET conservation (the "trace" row of
        # `*_conservation.txt`). Not part of any interface but exposed for
        # downstream inspection.
        self.conservation: np.ndarray | None = None

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

    # ------------------------------------------------------------- I/O helpers

    @staticmethod
    def _write_aligned_fasta(system: System, path: Path) -> str:
        """
        Write the entity's MSA as an aligned FASTA file with the target rep
        as the first sequence (this is the format ``gemme.py`` expects).

        Returns the sanitised query name extracted from the first header
        (GEMME uses everything up to the first non-alphanumeric character).
        """
        target = system[0]
        target_seq = "".join(target.rep)
        # GEMME's `extractQuerySeq` does `re.split(r"[^A-Z0-9a-z]", header[1:])[0]`
        # to determine `prot`. Pick a header that survives that rule unchanged.
        query_name = "QUERY"

        with open(path, "w") as f:
            f.write(f">{query_name}\n")
            f.write(target_seq + "\n")
            for i, seq in enumerate(target.sequences.seqs):
                seq_id = seq.id_ or f"seq_{i}"
                safe_id = re.sub(r"[|:\s]+", "_", seq_id)
                f.write(f">{safe_id}\n{seq.seq}\n")
        return query_name

    def _run_gemme(
        self,
        workdir: Path,
        alignment_filename: str,
        query_name: str,
        mutations_filename: str | None = None,
    ) -> None:
        """
        Run a single ``python2.7 gemme.py ...`` invocation against the local
        GEMME source tree. ``GEMME_PATH`` and ``JET_PATH`` are exported into
        the child environment because GEMME's R/Java callouts read them.
        """
        cmd = [
            self.python_command,
            str(self.gemme_path / "gemme.py"),
            alignment_filename,
            "-r", "input",
            "-f", alignment_filename,
            "-n", str(self.n_iter),
            "-N", str(self.n_seqs),
        ]
        if mutations_filename is not None:
            cmd += ["-m", mutations_filename]

        env = os.environ.copy()
        env["GEMME_PATH"] = f"{self.gemme_path}/"
        env["JET_PATH"] = f"{self.jet_path}/"

        logger.debug("GEMME cmd: {} (cwd={})", " ".join(cmd), workdir)
        try:
            subprocess.run(
                cmd,
                check=True,
                capture_output=True,
                text=True,
                cwd=str(workdir),
                env=env,
            )
        except subprocess.CalledProcessError as exc:
            raise RuntimeError(
                f"GEMME invocation failed (exit {exc.returncode}):\n"
                f"stdout (tail):\n{exc.stdout[-2000:]}\n"
                f"stderr (tail):\n{exc.stderr[-2000:]}"
            ) from exc

        out_file = workdir / f"{query_name}_{self._VARIANT_TO_FILE[self.model_variant]}"
        if not out_file.is_file():
            raise RuntimeError(
                f"GEMME finished but expected output file {out_file} is "
                f"missing. Workdir contents: {sorted(p.name for p in workdir.iterdir())}"
            )

    # --------------------------------------------------------------- parsing

    @staticmethod
    def _parse_gemme_matrix(path: Path) -> tuple[np.ndarray, list[str]]:
        """
        Parse a `<prot>_normPred_evolXxxx.txt` matrix written by R's
        ``write.table``.

        Returns
        -------
        scan : np.ndarray, shape (L, 20)
            Score matrix in `VALID_AA_SORTED` column order, with NaN for any
            position/AA GEMME could not score.
        aa_index : list[str]
            The lowercase amino acid order GEMME used (for diagnostics).
        """
        # The R default is space-delimited with quoted character strings.
        # pandas handles quoting transparently when `sep=r"\s+"`.
        df = pd.read_csv(path, sep=r"\s+", index_col=0, na_values=["NA"])
        # df.index : ['a','c','d',...,'y'] - 20 amino acids in lowercase
        # df.columns : ['V1', 'V2', ..., 'V<L>'] - position labels
        if len(df) != 20:
            raise ValueError(
                f"Expected 20 amino acid rows in {path}, got {len(df)}: "
                f"{list(df.index)}"
            )
        aa_index = [str(x).lower() for x in df.index]
        if sorted(aa_index) != sorted(_GEMME_AA_LOWER):
            raise ValueError(
                f"Unexpected GEMME alphabet rows in {path}: {aa_index} "
                f"(expected permutation of {_GEMME_AA_LOWER})"
            )
        df = df.reindex(index=_GEMME_AA_LOWER)
        scan = df.to_numpy(dtype=float).T  # transpose: (L, 20) in VALID_AA_SORTED order
        # Defensively floor any -inf values; GEMME usually emits NaN instead.
        scan = np.where(np.isneginf(scan), _GEMME_SCORE_FLOOR, scan)
        return scan, aa_index

    @staticmethod
    def _parse_gemme_mutfile(path: Path, mutant_keys: Sequence[str]) -> np.ndarray:
        """
        Parse the ``-m mutations.txt`` form of GEMME's output. R's
        ``write.table`` on a named vector emits one header line (``"x"``)
        followed by ``"<mutant_key>" <value>`` per line.
        """
        score_by_name: dict[str, float] = {}
        with open(path) as f:
            lines = [ln.strip() for ln in f if ln.strip()]
        # Drop the header row (R writes the column name "x" on the first line).
        if lines and lines[0].strip('"') in ("x", "V1"):
            lines = lines[1:]
        for ln in lines:
            parts = ln.split(None, 1)
            if len(parts) != 2:
                continue
            key = parts[0].strip('"')
            try:
                score_by_name[key] = float(parts[1])
            except ValueError:
                score_by_name[key] = float("nan")
        out = np.full(len(mutant_keys), np.nan)
        for i, key in enumerate(mutant_keys):
            out[i] = float(score_by_name.get(key, np.nan))
        return out

    @staticmethod
    def _parse_gemme_conservation(path: Path) -> np.ndarray | None:
        """Parse the JET ``trace`` row of ``<prot>_conservation.txt``."""
        if not path.is_file():
            return None
        df = pd.read_csv(path, sep=r"\s+", index_col=0, na_values=["NA"])
        if "trace" not in df.index:
            return None
        return df.loc["trace"].to_numpy(dtype=float)

    # --------------------------------------------------------------- BaseModel

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

        # Persist the working directory for the lifetime of this instance so
        # we can re-invoke GEMME cheaply for higher-order mutant scoring.
        self._tmpdir = tempfile.TemporaryDirectory(prefix="gemme-")
        self._workdir_path = Path(self._tmpdir.name)
        alignment_path = self._workdir_path / "alignment.fasta"
        self._query_name = self._write_aligned_fasta(system, alignment_path)

        logger.info(
            "Running GEMME (variant={}, n_iter={}, n_seqs={}, gemme_path={}) ...",
            self.model_variant, self.n_iter, self.n_seqs, self.gemme_path,
        )
        self._run_gemme(
            workdir=self._workdir_path,
            alignment_filename=alignment_path.name,
            query_name=self._query_name,
        )

        score_file = self._workdir_path / (
            f"{self._query_name}_{self._VARIANT_TO_FILE[self.model_variant]}"
        )
        scan, _ = self._parse_gemme_matrix(score_file)
        if scan.shape[0] != target_len:
            raise ValueError(
                f"GEMME score matrix has {scan.shape[0]} positions but the "
                f"target sequence has length {target_len}. The MSA query "
                f"sequence may not match the entity rep."
            )

        # GEMME emits NA at the WT residue of each position by convention.
        # Mathematically the WT cell is the reference (ΔS = 0); restore that
        # explicitly so downstream arithmetic does not propagate NaN.
        rep_chars = np.asarray(target.rep, dtype="U1")
        aa_to_col = {aa: i for i, aa in enumerate(VALID_AA_SORTED)}
        for i, ch in enumerate(rep_chars):
            col = aa_to_col.get(str(ch))
            if col is not None and np.isnan(scan[i, col]):
                scan[i, col] = 0.0
        self.encoding = scan

        self.conservation = self._parse_gemme_conservation(
            self._workdir_path / f"{self._query_name}_conservation.txt"
        )

        logger.debug(
            "GEMME build done: L={}, modeled positions={}, NaN cells={}",
            scan.shape[0],
            int((~np.isnan(scan).all(axis=1)).sum()),
            int(np.isnan(scan).sum()),
        )
        return self

    # ------------------------------------------------------------- positions

    def positions(
        self,
        instance: SystemInstance | None,  # noqa: ARG002
    ) -> list[tuple[int, int]]:
        """
        Return positions that GEMME scored (rows with at least one non-NaN
        entry). Fully unscored rows are excluded so that downstream samplers
        do not try to design at positions GEMME has no information about.
        """
        if self.encoding is None:
            raise ValueError("Must call build() first")
        target = self._system[0]
        first = target.first_index
        good = ~np.isnan(self.encoding).all(axis=1)
        return [(0, first + i) for i, ok in enumerate(good) if ok]

    # ----------------------------------------------------- MutationScorer API

    def score_mutants(
        self,
        instance: SystemInstance,
        mutants: Sequence[Mutant],
        status_callback: StatusCallback | None = None,  # noqa: ARG002
    ) -> np.ndarray:
        """
        Score each (single or higher-order) mutant. Single-site mutants are
        served from the cached matrix; higher-order mutants trigger a second
        GEMME invocation with the ``-m mutations.txt`` flag, which uses
        GEMME's own multi-substitution scoring path.
        """
        self.ready_or_raise()
        self._validate_instances([instance])
        self.system.valid_mutants(
            instance, mutants, deletions=False, insertions=False, raise_invalid=True
        )

        target = self.system[0]
        first = target.first_index
        aa_to_col = {aa: i for i, aa in enumerate(VALID_AA_SORTED)}
        scores = np.full(len(mutants), np.nan)

        # Index single mutants directly from the cache. The instance may
        # differ from the system rep, so we apply ΔH relative to the *instance*
        # by adding (M_inst[pos, alt] - M_inst[pos, ref]) where M_inst is the
        # cached scan re-anchored to the instance. The cache is anchored to
        # the *system rep*, so:
        #   score(rep -> mut) = M[pos, mut]
        #   score(rep -> inst[pos]) = M[pos, inst_aa]
        #   score(inst -> mut) = M[pos, mut] - M[pos, inst_aa]
        inst_chars = np.asarray(instance[0].rep, dtype="U1")
        higher_order_indices: list[int] = []
        for i, mutant in enumerate(mutants):
            if len(mutant) == 0:
                scores[i] = 0.0
                continue
            if len(mutant) > 1:
                higher_order_indices.append(i)
                continue
            sub = mutant[0]
            # Explicit self-mutation short-circuit (regardless of GEMME's
            # ability to score that position): contract requires self == 0.
            if sub.to == sub.ref:
                scores[i] = 0.0
                continue
            row_idx = sub.pos - first
            if not (0 <= row_idx < self.encoding.shape[0]):
                scores[i] = np.nan
                continue
            row = self.encoding[row_idx]
            inst_aa = str(inst_chars[row_idx])
            mt_col = aa_to_col.get(sub.to)
            inst_col = aa_to_col.get(inst_aa)
            if mt_col is None or inst_col is None:
                scores[i] = np.nan
                continue
            mt_val = row[mt_col]
            inst_val = row[inst_col]
            if np.isnan(mt_val) or np.isnan(inst_val):
                scores[i] = np.nan
                continue
            scores[i] = float(mt_val - inst_val)

        if higher_order_indices:
            scores_ho = self._score_higher_order(
                instance, [mutants[i] for i in higher_order_indices]
            )
            for k, i in enumerate(higher_order_indices):
                scores[i] = scores_ho[k]

        return scores

    def _score_higher_order(
        self,
        instance: SystemInstance,
        mutants: Sequence[Mutant],
    ) -> np.ndarray:
        """
        Score higher-order mutants by invoking GEMME a second time with a
        ``-m mutations.txt`` file. The instance is *assumed* to equal the
        system rep here (GEMME's multi-mutation file is interpreted relative
        to the MSA's query sequence, which is what we wrote at build()).
        Substitutions of the instance away from rep are folded in by adding
        them to each mutant — but in practice score_mutants's most useful
        call pattern is to score mutations relative to WT, so we keep this
        simple and warn loudly when instance != rep.
        """
        target = self.system[0]
        first = target.first_index
        rep_chars = np.asarray(target.rep, dtype="U1")
        inst_chars = np.asarray(instance[0].rep, dtype="U1")
        if not np.array_equal(rep_chars, inst_chars):
            logger.warning(
                "score_mutants: higher-order mutants are scored relative to "
                "the entity rep, but the supplied instance differs from rep. "
                "The returned scores are ΔH relative to rep, not the instance. "
                "For instance-relative scores call build() again with the "
                "instance promoted to entity.rep."
            )

        mut_keys: list[str] = []
        for mutant in mutants:
            parts = []
            for sub in mutant:
                row_idx = sub.pos - first
                ref_aa = str(rep_chars[row_idx])
                parts.append(f"{ref_aa}{row_idx + 1}{sub.to}")
            mut_keys.append(",".join(parts))

        # GEMME's R script (computePred.R::runIndependentModel) crashes with
        # "subscript out of bounds" when the mutations file contains *only*
        # multi-site entries — the per-position pooling step indexes into an
        # empty list. We avoid this by extracting one single-substitution
        # component from each multi-site mutant and prepending them as
        # padding rows; they are dropped after parsing.
        padding: list[str] = []
        seen_pad = set()
        for key in mut_keys:
            if "," in key:
                first_sub = key.split(",", 1)[0]
                if first_sub not in seen_pad:
                    padding.append(first_sub)
                    seen_pad.add(first_sub)
        padded_keys = padding + mut_keys

        with self._mutfile_workdir() as work:
            mut_path = work / "mutations.txt"
            mut_path.write_text("\n".join(padded_keys) + "\n")
            # We can't keep build's workdir because GEMME would overwrite the
            # cached single-mutation matrix. Make a fresh sandbox.
            alignment_path = work / "alignment.fasta"
            self._write_aligned_fasta(self.system, alignment_path)
            self._run_gemme(
                workdir=work,
                alignment_filename=alignment_path.name,
                query_name=self._query_name,
                mutations_filename=mut_path.name,
            )
            out_file = work / (
                f"{self._query_name}_{self._VARIANT_TO_FILE[self.model_variant]}"
            )
            all_scores = self._parse_gemme_mutfile(out_file, padded_keys)
            # Drop padding rows; they were only present to keep GEMME happy.
            return all_scores[len(padding):]

    @contextmanager
    def _mutfile_workdir(self):
        with tempfile.TemporaryDirectory(prefix="gemme-mut-") as tmp:
            yield Path(tmp)

    def single_mutation_scan(
        self,
        instance: SystemInstance,
        entity: int | None = None,
        positions: Sequence[int] | None = None,
        status_callback: StatusCallback | None = None,  # noqa: ARG002
    ) -> pd.DataFrame:
        """
        Per-position single-mutation effect matrix relative to the supplied
        instance. The diagonal (mut == ref) is exactly 0; positions GEMME
        could not score are returned as all-NaN rows.
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
        first = target.first_index
        rep_len = len(target.rep)
        instance_seq = "".join(instance[0].rep)

        if positions is not None:
            self.valid_positions(positions, entities=0, raise_invalid=True)
            pos_list = list(positions)
        else:
            pos_list = list(range(first, first + rep_len))

        aa_to_col = {aa: i for i, aa in enumerate(VALID_AA_SORTED)}
        rows = []
        index = []
        for pos in pos_list:
            row_idx = pos - first
            ref_aa = instance_seq[row_idx]
            row = self.encoding[row_idx]
            ref_col = aa_to_col.get(ref_aa)
            if ref_col is None or np.isnan(row[ref_col]):
                rows.append([np.nan] * len(VALID_AA_SORTED))
            else:
                # log-odds relative to the instance residue at this position;
                # diagonal (mut == ref) becomes 0 by construction.
                rows.append((row - row[ref_col]).tolist())
            index.append((pos, ref_aa))

        df = pd.DataFrame(rows, columns=list(VALID_AA_SORTED))
        df.index = pd.MultiIndex.from_tuples(index, names=["pos", "ref"])
        df = pd.concat({entity: df}, names=["entity"])
        return df

    # --------------------------------- ConditionalMutationScorer API

    def score_conditional(
        self,
        instances: Sequence[SystemInstance],
        entities: Sequence[int],
        positions: Sequence[int],
        status_callback: StatusCallback | None = None,  # noqa: ARG002
    ) -> pd.DataFrame:
        """
        Raw per-position GEMME log-odds for the requested (instance, entity,
        position) triplets. GEMME assumes per-position conditional
        independence given the MSA, so the same row is returned for every
        instance that targets the same position.
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
        first = target.first_index
        for inst, pos in zip(instances, positions):
            self.valid_positions(
                [pos], instance=inst, entities=0, raise_invalid=True
            )

        rows = []
        index = []
        for instance_idx, (entity, pos) in enumerate(zip(entities, positions)):
            row_idx = pos - first
            rows.append(self.encoding[row_idx].tolist())
            index.append((instance_idx, entity, pos))

        df = pd.DataFrame(rows, columns=list(VALID_AA_SORTED))
        df.index = pd.MultiIndex.from_tuples(
            index, names=["instance", "entity", "pos"]
        )
        return df

    # ----------------------------------------------------- Transformer API

    def transform(
        self,
        instances: Sequence[SystemInstance],
        entity: int | None = None,
        status_callback: StatusCallback | None = None,  # noqa: ARG002
    ) -> list[SystemInstance]:
        """
        Decompose each instance into per-residue GEMME log-odds (relative
        to the entity rep) and stash the result as
        ``entity_instance.embedding``.

        ``embedding[i]`` is the normalised GEMME score for substituting
        the entity rep's residue at position i with the *instance*'s
        residue at the same position. By GEMME's normalisation, this is 0
        when the instance matches the rep at that position and otherwise
        equals the predicted ΔS for that single substitution. The vector
        sums to the same "sum-of-singles ΔS" that ``generate()`` reports as
        ``inst.score`` for designed sequences.

        Returns shallow copies of each instance with ``embedding`` and
        ``score`` filled in; the input list is left unmodified per the
        Transformer contract.
        """
        self.ready_or_raise()
        self._validate_instances(instances)

        entity = 0 if entity is None else entity
        if entity != 0:
            raise ValueError("Model can only handle one single entity")

        target = self.system[0]
        rep_chars = np.asarray(target.rep, dtype="U1")
        aa_to_col = {aa: i for i, aa in enumerate(VALID_AA_SORTED)}
        L = len(rep_chars)

        out: list[SystemInstance] = []
        for instance in instances:
            inst_chars = np.asarray(instance[entity].rep, dtype="U1")
            per_residue = np.full(L, np.nan)
            for i in range(L):
                ref_aa = str(rep_chars[i])
                inst_aa = str(inst_chars[i])
                if inst_aa == ref_aa:
                    per_residue[i] = 0.0
                    continue
                col = aa_to_col.get(inst_aa)
                if col is None:
                    continue  # leaves NaN
                val = self.encoding[i, col]
                # NaN cells (positions GEMME couldn't score) propagate
                # through to the embedding by design.
                per_residue[i] = float(val)

            inst_copy = instance.copy()
            inst_copy[entity].embedding = per_residue
            inst_copy.score = float(np.nansum(per_residue))
            out.append(inst_copy)
        return out

    # ------------------------------------------------------- Generator API

    def generate(
        self,
        num_designs: int,
        entities: Sequence[int] | None = None,
        fixed_pos: EntityPosList | None = None,
        temperature: float = 1.0,
        status_callback: StatusCallback | None = None,
    ) -> list[SystemInstance]:
        """
        Generate designs by Gibbs sampling, using GEMME's per-position
        log-odds as the conditional scorer. Designs are returned with a
        per-instance score that is the sum of single-site ΔH against the
        WT entity rep (the only meaningful "joint score" GEMME exposes).
        """
        self.ready_or_raise()
        entities = entities if entities is not None else [0]
        entities = ensure_sequence(entities)
        if len(entities) != 1 or entities[0] != 0:
            raise ValueError(
                "Can only design single entity (entities = [0] | None)"
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

        # Score each generated design as sum-of-single-mutations against WT.
        target = self.system[0]
        first = target.first_index
        ref_instance = SystemInstance(EntityInstance(rep="".join(target.rep)))
        modeled_positions = [pos for (_, pos) in self.positions(ref_instance)]
        for inst in instances:
            mutants = [
                [self._sub_for_position(pos, inst, first)]
                for pos in modeled_positions
            ]
            inst.score = float(
                np.nansum(self.score_mutants(ref_instance, mutants))
            )
        return list(instances)

    def _sub_for_position(self, pos: int, instance: SystemInstance, first: int):
        """Build a single substitution that pins the instance's residue at pos."""
        from evedesign.system import Mutation
        rep = self.system[0].rep
        row_idx = pos - first
        return Mutation(
            entity=0,
            pos=pos,
            ref=str(rep[row_idx]),
            to=str(instance[0].rep[row_idx]),
        )

    # ----------------------------------------------------- housekeeping

    def __del__(self):
        try:
            if (not self.keep_workdir) and (self._tmpdir is not None):
                self._tmpdir.cleanup()
        except Exception:
            pass

    def __getstate__(self) -> dict[str, Any]:
        state = self.__dict__.copy()
        state["_tmpdir"] = None
        state["_workdir_path"] = None
        return state

    def __setstate__(self, state: dict[str, Any]) -> None:
        self.__dict__.update(state)
        # the workdir is gone after unpickling; any further `_score_higher_order`
        # call will create a fresh one.
        self._tmpdir = None
        self._workdir_path = None
