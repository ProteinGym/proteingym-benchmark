"""
Wrapper class around GEMME (Global Epistasis Model for predicting Mutational Effects)

This wrapper reimplements the small amount of orchestration in GEMME's own gemme.py/
gemmeAnal.py directly in Python 3 (those scripts are Python 2 and not used here),
calling the two external tools GEMME itself calls out to:

JET2  computes per-position evolutionary conservation via an ensemble of resampled/realigned
neighbour-joining trees. GEMME feeds JET2 a synthetic, flat dummy PDB (every
residue at identical 3D coordinates) to satisfy JET2's structural input API -
GEMME does not use real structure at all, so building one is unnecessary

computePred.R (needs the seqinr and RColorBrewer R packages) reads JET2's conservation
output together with the full alignment and computes three related matrices
"""
import os
import re
import subprocess
import tempfile
from os import PathLike
from pathlib import Path
from typing import Any, Self, Sequence

import numpy as np
import pandas as pd

from evedesign.model import (
    BaseModel,
    Scorer,
    MutationScorer,
    ConditionalMutationScorer,
    assign_scores_to_instances,
)
from evedesign.system import System, SystemInstance, Entity
from evedesign.constants import VALID_AA_SORTED
from evedesign.types import StatusCallback
from evedesign.utils import status_done, status_start

# GEMME's own amino acid row order (lowercase); values are uppercased on parsing
_GEMME_ALPHABET = ["a", "c", "d", "e", "f", "g", "h", "i", "k", "l", "m", "n",
                   "p", "q", "r", "s", "t", "v", "w", "y"]
assert sorted(a.upper() for a in _GEMME_ALPHABET) == VALID_AA_SORTED

_AA3 = {
    "A": "ALA", "C": "CYS", "D": "ASP", "E": "GLU", "F": "PHE", "G": "GLY",
    "H": "HIS", "I": "ILE", "K": "LYS", "L": "LEU", "M": "MET", "N": "ASN",
    "P": "PRO", "Q": "GLN", "R": "ARG", "S": "SER", "T": "THR", "V": "VAL",
    "W": "TRP", "Y": "TYR",
}


class ExternalToolError(RuntimeError):
    """Raised when JET2 or the R prediction script fails or produces no output."""


def _run(cmd: Sequence[str], cwd: Path, log_file: Path, env: dict | None = None):
    """
    Run an external command, teeing stdout+stderr to log_file, raising
    FileNotFoundError if the binary is missing/not executable or ExternalToolError
    on a nonzero exit status (with the tail of the log included).
    """
    try:
        result = subprocess.run(
            [str(c) for c in cmd], cwd=str(cwd), env=env,
            capture_output=True, text=True, errors="replace",
        )
    except OSError as e:
        raise FileNotFoundError(f"Could not run {cmd[0]!r}: {e}") from e

    with open(log_file, "w") as f:
        f.write(result.stdout)
        f.write(result.stderr)

    if result.returncode != 0:
        tail = (result.stdout + result.stderr)[-4000:]
        raise ExternalToolError(
            f"{cmd[0]} exited with status {result.returncode}\n--- tail of output ---\n{tail}"
        )

    return result


class gemme(BaseModel, Scorer, MutationScorer, ConditionalMutationScorer):
    """
    Wrapper around GEMME's global epistasis model (the evolEpi output).
    """
    name: str = "gemme"
    citations: list[str] = [
        # GEMME
        "10.1093/molbev/msz179",
    ]

    requires_target: bool = True
    requires_fixed_length: bool = True
    # GEMME's alphabet has no gap symbol; deletions cannot be scored
    handles_deletions: bool = False
    handles_insertions: bool = False
    requires_gpu: bool = False
    supports_gpu: bool = False
    supports_gpu_parallel: bool = False
    # JET2's tree ensemble is single-threaded per call in the invocation this wrapper uses
    supports_cpu_parallel: bool = False

    required_entity_attributes: list[str] | None = ["sequences"]
    optional_entity_attributes: list[str] | None = None

    def __init__(
        self,
        n_iter: int = 10,
        max_sequences: int = 20000,
        gemme_path: str | PathLike = "/n/lw_groups/marks/shared_projects/ProteinGym2_models/GEMME",
        jet_path: str | PathLike = "/n/lw_groups/marks/shared_projects/ProteinGym2_models/GEMME/JET2",
        java_binary: str | PathLike = "java",
        rscript_binary: str | PathLike = "Rscript",
        muscle_binary: str | PathLike = "muscle",
        java_max_heap: str = "4096m",
    ):
        """
        Instantiate a GEMME model.

        Parameters
        ----------
        n_iter
            Number of JET2 (iJET) resampling iterations used to compute conservation.
            GEMME's own CLI defaults this to 1 (basic JET) but its documented/
            published usage runs iJET with 10 iterations and takes the max trace
            value across iterations - this wrapper defaults to 10 to match that
        max_sequences
            Maximum number of alignment sequences (including the query) handed to
            JET2 for conservation computation, truncated from the start of the
            alignment (matching GEMME's own -N/--NSeqs behaviour; not a smarter
            subsample). The full alignment is still used for the sequence
            count/epistatic computation
        gemme_path
            Path to the GEMME directory (containing computePred.R, pred.R,
            blosum62p.txt, default.conf)
        jet_path
            Path to the JET2 directory (containing jet/JET.class,
            jet/extLibs/vecmath.jar). default.conf's substMatrix entry must point at
            jet_path/matrix (already the case for the default.conf shipped here).
        java_binary
            Path to / name of the java binary used to run JET2
        rscript_binary
            Path to / name of the Rscript binary (needs seqinr and RColorBrewer
            installed) used to run computePred.R
        muscle_binary
            Path to / name of muscle **3.8.x specifically** (see module docstring)
            used by JET2 for its internal resampled realignments
        java_max_heap
            -Xmx value passed to the JET2 JVM invocation
        """
        if n_iter < 1:
            raise ValueError("n_iter must be >= 1")
        if max_sequences < 3:
            raise ValueError("max_sequences must be >= 3")

        self.n_iter = n_iter
        self.max_sequences = max_sequences
        self.gemme_path = Path(gemme_path)
        self.jet_path = Path(jet_path)
        self.java_binary = java_binary
        self.rscript_binary = rscript_binary
        self.muscle_binary = muscle_binary
        self.java_max_heap = java_max_heap

        self._system: System | None = None
        # positions x alphabet raw evolEpi scores (log-odds-like, self=0), np.nan for NA cells
        self._pssm: np.ndarray | None = None

    @property
    def system(self) -> System | None:
        return self._system

    @property
    def ready(self) -> bool:
        return self._system is not None and self._pssm is not None

    @classmethod
    def can_model(cls, system: System, data: Any = None) -> tuple[bool, str]:
        if data is not None:
            return False, "Model does not support a data parameter (must be None)"

        if len(system) != 1 or system[0].type != "protein":
            return False, "Can only handle a single-component protein system"

        target = system[0]
        if not target.defined_sequence():
            return False, "Entity must have a defined rep sequence"

        if target.sequences is None or len(target.sequences.seqs) == 0:
            return False, "Must provide an MSA (entity.sequences) for model inference"

        if not target.sequences.aligned:
            return False, "Provided sequences must be aligned"

        return True, ""

    @staticmethod
    def _match_state_seqs(target) -> tuple[str, list[str], list[str]]:
        """
        Convert the system MSA to match-state-only (insertions stripped) sequences
        """
        target_rep = "".join(target.rep)
        length = len(target_rep)

        raw_seqs = target.sequences.to_a3m().remove_inserts().seqs
        seqs = [s.seq for s in raw_seqs]

        bad = [i for i, m in enumerate(seqs) if len(m) != length]
        if bad:
            raise ValueError(
                f"MSA match-state length does not match target length ({length}) "
                f"for {len(bad)} sequence(s), e.g. sequence index {bad[0]}"
            )

        if seqs[0] != target_rep:
            raise ValueError(
                "First MSA sequence (match states) must equal the target/focus sequence. "
                "GEMME requires the target sequence as the first alignment record"
            )

        names = [
            "QUERY" if i == 0 else (re.sub(r"\s+", "_", str(s.id_)) if s.id_ else f"SEQ{i}")
            for i, s in enumerate(raw_seqs)
        ]

        return target_rep, names, seqs

    @staticmethod
    def _write_fasta(path: Path, names: Sequence[str], seqs: Sequence[str]):
        with open(path, "w") as f:
            for name, seq in zip(names, seqs):
                f.write(f">{name}\n{seq}\n")

    @staticmethod
    def _write_dummy_pdb(path: Path, target_rep: str):
        """
        Write a synthetic PDB with every CA at identical coordinates
        """
        with open(path, "w") as f:
            for i, letter in enumerate(target_rep, start=1):
                f.write(
                    f"ATOM{i:7d}  CA  {_AA3[letter]} A{(i - 1) % 9999 + 1:4d}"
                    f"      43.524  70.381  46.465  1.00   0.0\n"
                )

    def _write_conf(self, path: Path):
        """
        Write default.conf with substMatrix and muscle templated to this
        instance's jet_path/muscle_binary
        """
        text = (self.gemme_path / "default.conf").read_text()
        text = re.sub(
            r"^substMatrix\t.*$", f"substMatrix\t{self.jet_path / 'matrix'}",
            text, flags=re.MULTILINE,
        )
        text = re.sub(
            r"^muscle\t\t.*$", f"muscle\t\t{self.muscle_binary}",
            text, flags=re.MULTILINE,
        )
        path.write_text(text)

    def _run_jet(self, prot: str, tmp: Path, pdb_file: Path, jet_fasta: Path, length: int):
        """
        Run JET2 in '-r input -f <fasta>' mode (bypassing its own PSI-BLAST
        retrieval)
        """
        conf_file = tmp / "default.conf"
        self._write_conf(conf_file)

        jet_classpath = f"{self.jet_path}:{self.jet_path / 'jet' / 'extLibs' / 'vecmath.jar'}"
        cmd = [
            self.java_binary, f"-Xmx{self.java_max_heap}", "-cp", jet_classpath, "jet.JET",
            "-c", str(conf_file), "-i", str(pdb_file), "-o", str(tmp),
            "-p", "J", "-r", "input", "-f", str(jet_fasta), "-d", "chain", "-n", str(self.n_iter),
        ]
        _run(cmd, cwd=tmp, log_file=tmp / f"{prot}.jet.log")

        jet_res = tmp / prot / f"{prot}_jet.res"
        if not jet_res.exists():
            raise ExternalToolError(
                f"JET2 did not produce {jet_res.name}; check {prot}.jet.log for details"
            )

        with open(jet_res) as f:
            header = f.readline()
            trace_col_names = [c for c in header.split() if c.startswith("trace")]
            rows = f.readlines()
        n_rows = len(rows)
        if n_rows != length:
            raise ExternalToolError(f"JET2 returned {n_rows} positions, expected {length}")

        trace_col_idx = [i for i, c in enumerate(header.split()) if c in trace_col_names]
        all_zero = all(
            all(float(row.split()[i]) == 0.0 for i in trace_col_idx)
            for row in rows
        )
        if all_zero:
            raise ExternalToolError(
                f"JET2 produced an all-zero trace for every position in {jet_res.name} "
                f"(a real conservation signal is never uniformly zero) - check "
                f"{prot}.jet.log for a silently-swallowed internal JET2 error "
                f"(e.g. a NullPointerException from a filename-matching failure)"
            )

        jet_res.rename(tmp / f"{prot}_jet.res")

    def _run_pred(self, prot: str, tmp: Path, ali_file: Path):
        """
        Run computePred.R (default/whole-matrix mode, no mutation subset) and
        return the parsed evolEpi matrix as a (positions x alphabet) array.
        """
        cmd = [
            self.rscript_binary, "--save", str(self.gemme_path / "computePred.R"),
            prot, str(ali_file), "TRUE", "none",
        ]
        env = {**os.environ, "GEMME_PATH": str(self.gemme_path)}
        _run(cmd, cwd=tmp, log_file=tmp / f"{prot}.pred.log", env=env)

        pred_file = tmp / f"{prot}_normPred_evolEpi.txt"
        if not pred_file.exists():
            raise ExternalToolError(
                f"computePred.R did not produce {pred_file.name}; check {prot}.pred.log for details"
            )

        # R write.table format
        df = pd.read_csv(pred_file, sep=r"\s+")
        df.index = [str(ix).strip('"') for ix in df.index]
        df = df.reindex([a for a in _GEMME_ALPHABET])

        return df.to_numpy(dtype=float).T  # positions x alphabet

    def build(
        self,
        system: System,
        data: None = None,
        status_callback: StatusCallback | None = None,
    ) -> Self:
        self.can_model_or_raise(system, data)

        status_start(status_callback, "Fitting GEMME model")

        self._system = system
        target = system[0]

        self._pssm = None

        target_rep, names, seqs = self._match_state_seqs(target)
        prot = "QUERY"

        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)

            # full alignment, for computePred.R's own R-side computations
            ali_file = tmp / f"{prot}_ali.fasta"
            self._write_fasta(ali_file, names, seqs)

            # query-only record, written directly at the relative name computePred.R
            # expects to find in its cwd (tmp) - no separate temp copy needed
            self._write_fasta(tmp / f"{prot}.fasta", [prot], [target_rep])

            n_keep = min(self.max_sequences, len(seqs))
            jet_fasta = tmp / f"{prot}_A.fasta"
            self._write_fasta(jet_fasta, names[:n_keep], seqs[:n_keep])

            pdb_file = tmp / f"{prot}.pdb"
            self._write_dummy_pdb(pdb_file, target_rep)

            self._run_jet(prot, tmp, pdb_file, jet_fasta, len(target_rep))
            self._pssm = self._run_pred(prot, tmp, ali_file)

        alphabet_index = {aa.upper(): i for i, aa in enumerate(_GEMME_ALPHABET)}
        for pos, symbol in enumerate(target_rep):
            self._pssm[pos, alphabet_index[symbol]] = 0.0

        status_done(status_callback, "GEMME model finished fitting")

        return self

    def positions(
        self,
        instance: SystemInstance | None = None,
    ) -> list[tuple[int, int]]:
        """
        Return the modelled positions (entity 0). GEMME has no notion of an
        "unscored" position analogous to SIFT/EVcouplings, so every position in
        the query is included.
        """
        self.ready_or_raise()
        first_index = self.system[0].first_index
        return [(0, pos + first_index) for pos in range(self._pssm.shape[0])]

    def score(
        self,
        instances: Sequence[SystemInstance],
        status_callback: StatusCallback | None = None,
    ) -> list[SystemInstance]:
        """
        Score full sequences as the sum of per-position evolEpi values (already
        log-odds-like relative to the reference used to fit the model)
        """
        self.ready_or_raise()
        self._validate_instances(instances)

        if len(instances) == 0:
            return []

        status_start(status_callback, "Scoring sequences")

        alphabet_index = {aa.upper(): i for i, aa in enumerate(_GEMME_ALPHABET)}

        scores = []
        for instance in instances:
            rep = np.asarray(instance[0].rep)
            total = 0.0
            for pos, symbol in enumerate(rep):
                symbol = str(symbol)
                if symbol in alphabet_index:
                    val = self._pssm[pos, alphabet_index[symbol]]
                    if not np.isnan(val):
                        total += val
            scores.append(total)

        status_done(status_callback, "Scoring complete")

        return assign_scores_to_instances(instances, np.asarray(scores, dtype=float))

    def single_mutation_scan(
        self,
        instance: SystemInstance,
        entity: int | None = None,
        positions: Sequence[int] | None = None,
        status_callback: StatusCallback | None = None,
    ) -> pd.DataFrame:
        """
        Compute all single substitutions to an instance via the cached evolEpi
        matrix. Scores are relative to the instance (log P(mutant) - log P(ref),
        in GEMME's evolEpi units), so wt score 0
        """
        self.ready_or_raise()
        self._validate_instances([instance])

        if entity is not None and entity != 0:
            raise ValueError("GEMME only models entity 0")

        if positions is not None:
            if entity is None:
                raise ValueError(
                    "Parameter entity must be explicitly specified if using parameter positions"
                )
            self.valid_positions(positions, instance, entity, raise_invalid=True)

        status_start(status_callback, "Computing single mutation scan")

        first_index = self.system[0].first_index
        alphabet_index = {aa.upper(): i for i, aa in enumerate(_GEMME_ALPHABET)}
        rep = instance[0].rep

        pos_filter = set(positions) if positions is not None else None

        rows = []
        index_tuples = []
        for pos in range(self._pssm.shape[0]):
            evc_pos = pos + first_index
            if pos_filter is not None and evc_pos not in pos_filter:
                continue

            ref_symbol = str(rep[pos])
            ref_score = (
                self._pssm[pos, alphabet_index[ref_symbol]] if ref_symbol in alphabet_index else np.nan
            )

            rows.append(self._pssm[pos, :] - ref_score)
            index_tuples.append((0, evc_pos, ref_symbol))

        df = pd.DataFrame(
            rows,
            columns=[a.upper() for a in _GEMME_ALPHABET],
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
        Compute raw evolEpi scores for one position per instance
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

        rows = []
        index_tuples = []
        for inst_idx, (instance, entity, pos) in enumerate(zip(instances, entities, positions)):
            mi = int(pos) - first_index
            rows.append(self._pssm[mi, :])
            index_tuples.append((inst_idx, int(entity), int(pos)))

        df = pd.DataFrame(
            rows,
            columns=[a.upper() for a in _GEMME_ALPHABET],
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