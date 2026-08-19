"""
Wrapper class around RSALOR (https://github.com/3BioCompBio/RSALOR)
"""
import tempfile
from os import PathLike
from pathlib import Path
from typing import Any, Literal, Self, Sequence

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

from rsalor import MSA as RSALOR_MSA
from rsalor.sequence import AminoAcid as RSALOR_AminoAcid


_RSALOR_AA_ORDER = [aa.one for aa in RSALOR_AminoAcid.get_all()]
assert _RSALOR_AA_ORDER == VALID_AA_SORTED


class rsalor(BaseModel, Scorer, MutationScorer, ConditionalMutationScorer):
    """
    Wrapper around RSALOR's RSA*LOR per-position substitution score.
    """
    name: str = "rsalor"
    citations: list[str] = [
        # Residue conservation and solvent accessibility are (almost) all you need
        # for predicting mutational effects in proteins
        "10.1093/bioinformatics/btaf322",
        # Exploring evolution to uncover insights into protein mutational stability
        "10.1093/molbev/msae267",
    ]

    requires_target: bool = True
    requires_fixed_length: bool = True
    handles_deletions: bool = False
    handles_insertions: bool = False
    requires_gpu: bool = False
    supports_gpu: bool = False
    supports_gpu_parallel: bool = False
    supports_cpu_parallel: bool = True

    required_entity_attributes: list[str] | None = ["sequences", "structures"]
    optional_entity_attributes: list[str] | None = None

    def __init__(
        self,
        theta_regularization: float = 0.01,
        n_regularization: float = 0.0,
        count_target_sequence: bool = True,
        remove_redundant_sequences: bool = True,
        seqid_weights: float | None = 0.80,
        min_seqid: float | None = 0.35,
        num_threads: int = 4,
        rsa_solver: Literal["biopython", "DSSP", "MuSiC"] = "biopython",
        metric: Literal["LOR", "LR"] = "LOR",
        use_rsa_factor: bool = True,
        structure_name: str | None = None,
        verbose: bool = False,
    ):
        """
        Instantiate an RSALOR model.

        Parameters
        ----------
        theta_regularization
            Regularization term for LOR/LR at the amino-acid frequencies level
        n_regularization
            Regularization term for LOR/LR at the amino-acid counts level.
            unused by default
        count_target_sequence
            Whether to count the target (first) sequence itself in the
            frequency computation
        remove_redundant_sequences
            Whether to pre-process the MSA to remove duplicate
            sequences before weighting
        seqid_weights
            Sequence-identity threshold for clustering sequences into weight
            groups (EVE/EVcouplings-style reweighting
        min_seqid
            Discard MSA sequences whose identity to the target sequence is
            below this threshold before weighting
        num_threads
            Number of CPU threads used by RSALOR's C++ sequence-weighting
            backend
        rsa_solver
            RSA solver to run on the entity's structure. Defaults to
            "biopython" "DSSP"/"MuSiC" are supported
            by upstream but require the corresponding external executable
        metric
            "LOR" (log odd ratio) or "LR" (log ratio) - which upstream metric
            to build the per-position score matrix from
        use_rsa_factor
            If True (default, and the method this wrapper is named/submitted
            for), multiply the per-position metric by the RSA-derived weight
            (1 - min(RSA, 100) / 100) before taking differences - "RSA*LOR"
        structure_name
            Key into the entity's `structures` mapping to select which
            structure to use, if more than one is present
        verbose
            Forwarded to rsalor.MSA(verbose=...)
        """
        self.theta_regularization = theta_regularization
        self.n_regularization = n_regularization
        self.count_target_sequence = count_target_sequence
        self.remove_redundant_sequences = remove_redundant_sequences
        self.seqid_weights = seqid_weights
        self.min_seqid = min_seqid
        self.num_threads = num_threads
        self.rsa_solver = rsa_solver
        self.metric = metric
        self.use_rsa_factor = use_rsa_factor
        self.structure_name = structure_name
        self.verbose = verbose

        self._system: System | None = None
        # (length x 20) WT-relative per-position score matrix, dE(pos, aa) =
        # sign-flipped RSA_factor * (metric(aa) - metric(wt)) (see build()) -
        # np.nan for positions with no assigned RSA value matching upstream's 
        # "RSA*LOR = None" convention.
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

        if not target.structures:
            return False, "Must provide a structure (entity.structures) to compute RSA"

        return True, ""

    @staticmethod
    def _match_state_seqs(target) -> tuple[str, list[str]]:
        """
        Convert the system MSA to match-state-only (insertions stripped,
        uppercased) sequences, with the query/WT sequence first - identical
        approach to the GEMME/MSA Pairformer wrappers' _match_state_seqs.
        """
        target_rep = "".join(target.rep)
        length = len(target_rep)

        raw_seqs = target.sequences.to_a3m().remove_inserts().seqs
        seqs = [s.seq.upper() for s in raw_seqs]

        bad = [i for i, s in enumerate(seqs) if len(s) != length]
        if bad:
            raise ValueError(
                f"MSA match-state length does not match target length ({length}) "
                f"for {len(bad)} sequence(s), e.g. sequence index {bad[0]}"
            )

        if seqs[0] != target_rep:
            raise ValueError(
                "First MSA sequence (match states) must equal the target/focus "
                "sequence, matching RSALOR's own convention of treating the "
                "first alignment record as the target sequence"
            )

        return target_rep, seqs

    def _select_structure(self, target):
        """
        Select which structure to use from the entity's structures mapping (see
        structure_name in __init__'s docstring)
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

        if isinstance(structure, list):
            if len(structure) != 1:
                raise ValueError(
                    "RSALOR only supports a single-chain monomer structure, got "
                    f"a list of {len(structure)} structures"
                )
            structure = structure[0]

        chains = structure.chains()
        if len(chains) != 1:
            raise ValueError(
                f"RSALOR only supports a single-chain monomer structure, got chains={chains}"
            )

        return structure, chains[0]

    def build(
        self,
        system: System,
        data: None = None,
        status_callback: StatusCallback | None = None,
    ) -> Self:
        self.can_model_or_raise(system, data)

        status_start(status_callback, "Fitting RSALOR model")

        self._system = system
        target = system[0]

        self._pssm = None

        target_rep, seqs = self._match_state_seqs(target)
        structure, chain = self._select_structure(target)

        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)

            # Match-state MSA, single-line FASTA records (target/WT first),
            # already uppercase - the ".fasta" extension makes RSALOR's own
            # FastaStream treat this as an already column-aligned alignment
            # (no lowercase-insert stripping applied, unlike ".a3m").
            fasta_path = tmp / "msa.fasta"
            with open(fasta_path, "w") as f:
                for i, seq in enumerate(seqs):
                    f.write(f">seq{i}\n{seq}\n")

            # RSALOR's PDB parser reads plain ATOM-line columns directly
            # (chain, resid, 3-letter AA, B-factor-as-pLDDT) - biotite's PDB
            # writer produces a standard-column ATOM record layout
            pdb_path = tmp / "structure.pdb"
            structure.to_file(str(pdb_path), format="pdb")

            msa = RSALOR_MSA(
                str(fasta_path),
                str(pdb_path),
                chain,
                theta_regularization=self.theta_regularization,
                n_regularization=self.n_regularization,
                count_target_sequence=self.count_target_sequence,
                remove_redundant_sequences=self.remove_redundant_sequences,
                seqid_weights=self.seqid_weights,
                min_seqid=self.min_seqid,
                num_threads=self.num_threads,
                rsa_solver=self.rsa_solver,
                verbose=self.verbose,
                disable_warnings=not self.verbose,
            )

        # Guard against RSALOR's own target-sequence trimming (it silently
        # removes gap/non-standard-AA columns from *its* notion of the target
        # sequence, see MSA._read_sequences) - ProteinGym WT sequences are
        # expected to be clean
        if msa.length != len(target_rep):
            raise ValueError(
                f"RSALOR trimmed the target sequence ({len(target_rep)} -> "
                f"{msa.length} positions); this wrapper assumes the WT "
                "sequence contains only standard amino acids with no gaps."
            )

        metric_matrix = msa.LOR if self.metric == "LOR" else msa.LR

        one_2_id = RSALOR_AminoAcid.ONE_2_ID
        length = msa.length
        pssm = np.full((length, len(VALID_AA_SORTED)), np.nan, dtype=float)
        for pos in range(length):
            wt_char = target_rep[pos]
            wt_id = one_2_id[wt_char]
            rsa_factor = msa.rsa_factor_array[pos]
            if self.use_rsa_factor and rsa_factor is None:
                continue  # no assigned RSA at this position -> leave row as NaN
            factor = rsa_factor if self.use_rsa_factor else 1.0
            # Sign-flipped relative to upstream's own eval_mutations()/
            # get_scores() convention (see module docstring): here, higher =
            # more fit/tolerated (mt more represented than wt)
            row = factor * (metric_matrix[pos, :] - metric_matrix[pos, wt_id])
            pssm[pos, :] = row[0:len(VALID_AA_SORTED)]

        self._pssm = pssm

        status_done(status_callback, "RSALOR model finished fitting")

        return self

    def positions(
        self,
        instance: SystemInstance | None = None,
    ) -> list[tuple[int, int]]:
        """
        Return the modelled positions (entity 0). RSALOR scores every position
        of the (assumed untrimmed) target sequence.
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
        Score full sequences as the sum of per-position dE values (already
        WT-relative, see build()) over all positions for the instance's actual
        residues. Positions with no assigned RSA value (NaN in the cached
        matrix) do not contribute + instances identical to the WT score
        exactly 0.
        """
        self.ready_or_raise()
        self._validate_instances(instances)

        if len(instances) == 0:
            return []

        status_start(status_callback, "Scoring sequences")

        alphabet_index = {aa: i for i, aa in enumerate(VALID_AA_SORTED)}

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
        Compute all single substitutions to an instance via the cached
        per-position matrix. Scores are relative to the instance (dE(mutant) -
        dE(instance's residue))
        """
        self.ready_or_raise()
        self._validate_instances([instance])

        if entity is not None and entity != 0:
            raise ValueError("RSALOR only models entity 0")

        if positions is not None:
            if entity is None:
                raise ValueError(
                    "Parameter entity must be explicitly specified if using parameter positions"
                )
            self.valid_positions(positions, instance, entity, raise_invalid=True)

        status_start(status_callback, "Computing single mutation scan")

        first_index = self.system[0].first_index
        alphabet_index = {aa: i for i, aa in enumerate(VALID_AA_SORTED)}
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
        Compute raw (WT-relative-by-construction) dE scores for one position
        per instance.

        Note: RSALOR's per-position matrix is a fixed function of the
        alignment + structure (see class docstring), not of any particular
        instance's full sequence, so the returned row for a given position is
        identical regardless of the rest of the instance's sequence
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
