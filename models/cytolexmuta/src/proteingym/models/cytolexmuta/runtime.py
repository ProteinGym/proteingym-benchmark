from __future__ import annotations

import math
import os
import subprocess
import sys
import tempfile
from collections.abc import Iterable, Sequence
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np

AA20 = frozenset("ACDEFGHIKLMNPQRSTVWY")


@dataclass(frozen=True, order=True)
class Substitution:
    wt: str
    position: int
    mutant: str

    def __str__(self) -> str:
        return f"{self.wt}{self.position}{self.mutant}"


@dataclass(frozen=True)
class RuntimeConfig:
    esmc_model_id: str = "biohub/ESMC-600M"
    esmc_revision: str = "a7e82012c83126b9eedb055fea9fa84b6c02f094"
    prosst_model_id: str = "AI4Protein/ProSST-2048"
    prosst_revision: str = "e94ffee7846d7f55c1bf5efa8ec7372a336ac4b8"
    prosst_repo: str = "/opt/ProSST"
    gemme_path: str = "/opt/GEMME"
    jet_path: str = "/opt/JET2"
    device: str = "auto"
    position_batch_size: int = 8
    sequence_batch_size: int = 8
    max_residues: int = 1022
    gemme_nseqs: int = 20000


def substitutions_from_sequence(
    reference: str,
    sequence: str,
    first_index: int = 1,
) -> tuple[Substitution, ...]:
    if len(reference) != len(sequence):
        raise ValueError("cytolexmuta supports fixed-length substitutions only")
    if set(reference) - AA20 or set(sequence) - AA20:
        raise ValueError("sequences must contain only the 20 canonical amino acids")
    return tuple(
        Substitution(wt, first_index + offset, mutant)
        for offset, (wt, mutant) in enumerate(zip(reference, sequence, strict=True))
        if wt != mutant
    )


def mutation_key(mutations: Sequence[Substitution]) -> str:
    return ":".join(str(mutation) for mutation in mutations) if mutations else "WT"


def robust_z(values: np.ndarray) -> np.ndarray:
    values = np.asarray(values, dtype=np.float64)
    if values.ndim != 1 or not np.isfinite(values).all():
        raise ValueError("expert scores must be a finite one-dimensional array")
    median = float(np.median(values))
    mad = float(np.median(np.abs(values - median)))
    if mad <= 1e-12:
        return np.zeros_like(values, dtype=np.float64)
    return (values - median) / (1.4826 * mad)


def fuse_scores(
    esmc: np.ndarray,
    prosst: np.ndarray,
    gemme: np.ndarray,
    esm_if1: np.ndarray,
) -> np.ndarray:
    arrays = [
        np.asarray(values, dtype=np.float64)
        for values in (esmc, prosst, gemme, esm_if1)
    ]
    if not arrays or any(values.shape != arrays[0].shape for values in arrays):
        raise ValueError("all expert score arrays must have the same shape")
    if any(values.ndim != 1 or not np.isfinite(values).all() for values in arrays):
        raise ValueError("all expert score arrays must be finite and one-dimensional")
    sequence_anchor = 0.5 * arrays[0] + 0.5 * arrays[1]
    result = (
        0.5 * robust_z(sequence_anchor)
        + 0.25 * robust_z(arrays[2])
        + 0.25 * robust_z(arrays[3])
    )
    if not np.isfinite(result).all():
        raise ValueError("non-finite cytolexmuta score")
    return result


def _resolve_device(requested: str):
    import torch

    if requested == "auto":
        return torch.device("cuda:0" if torch.cuda.is_available() else "cpu")
    device = torch.device(requested)
    if device.type == "cuda" and not torch.cuda.is_available():
        raise RuntimeError("CUDA was requested but is unavailable")
    return device


def _clear_device(device: Any) -> None:
    import torch

    if device.type == "cuda":
        torch.cuda.empty_cache()


def _window_bounds(
    sequence_length: int, position: int, max_residues: int
) -> tuple[int, int]:
    if sequence_length <= max_residues:
        return 1, sequence_length
    half_window = max_residues // 2
    start = max(1, position - half_window)
    end = min(sequence_length, start + max_residues - 1)
    if end == sequence_length:
        start = sequence_length - max_residues + 1
    return start, end


def _variant_totals(
    variants: Sequence[Sequence[Substitution]],
    site_scores: dict[Substitution, float],
) -> np.ndarray:
    values = []
    for mutations in variants:
        try:
            value = sum(float(site_scores[mutation]) for mutation in mutations)
        except KeyError as error:
            raise ValueError(f"missing expert score for {error.args[0]}") from error
        values.append(value)
    result = np.asarray(values, dtype=np.float64)
    if not np.isfinite(result).all():
        raise ValueError("non-finite aggregated expert score")
    return result


def score_esmc(
    reference: str,
    variants: Sequence[Sequence[Substitution]],
    config: RuntimeConfig,
) -> np.ndarray:
    unique = sorted({mutation for mutations in variants for mutation in mutations})
    if not unique:
        return np.zeros(len(variants), dtype=np.float64)

    import torch
    from torch.nn import functional
    from transformers import AutoModelForMaskedLM, AutoTokenizer

    device = _resolve_device(config.device)
    dtype = torch.bfloat16 if device.type == "cuda" else torch.float32
    tokenizer = AutoTokenizer.from_pretrained(
        config.esmc_model_id,
        revision=config.esmc_revision,
    )
    model = (
        AutoModelForMaskedLM.from_pretrained(
            config.esmc_model_id,
            revision=config.esmc_revision,
            torch_dtype=dtype,
        )
        .to(device)
        .eval()
    )
    by_position: dict[int, list[Substitution]] = {}
    for mutation in unique:
        by_position.setdefault(mutation.position, []).append(mutation)
    scores: dict[Substitution, float] = {}
    by_window: dict[tuple[int, int], list[int]] = {}
    for position in by_position:
        bounds = _window_bounds(len(reference), position, config.max_residues)
        by_window.setdefault(bounds, []).append(position)
    aa_ids = {aa: int(tokenizer.convert_tokens_to_ids(aa)) for aa in AA20}
    if any(value == tokenizer.unk_token_id for value in aa_ids.values()):
        raise ValueError(
            "ESM-C tokenizer does not expose the canonical amino-acid vocabulary"
        )
    for (start, end), positions in by_window.items():
        window = reference[start - 1 : end]
        encoded = tokenizer(window, return_tensors="pt")
        input_ids = encoded["input_ids"][0]
        attention = encoded["attention_mask"][0]
        if input_ids.numel() != len(window) + 2:
            raise ValueError("ESM-C tokenizer/reference alignment mismatch")
        for offset in range(0, len(positions), config.position_batch_size):
            batch_positions = positions[offset : offset + config.position_batch_size]
            batch_ids = input_ids.unsqueeze(0).repeat(len(batch_positions), 1)
            batch_attention = attention.unsqueeze(0).repeat(len(batch_positions), 1)
            local_positions = []
            for row, position in enumerate(batch_positions):
                local_position = position - start + 1
                local_positions.append(local_position)
                batch_ids[row, local_position] = tokenizer.mask_token_id
            with torch.inference_mode():
                logits = model(
                    input_ids=batch_ids.to(device),
                    attention_mask=batch_attention.to(device),
                ).logits.float()
            for row, position in enumerate(batch_positions):
                log_probs = functional.log_softmax(
                    logits[row, local_positions[row]], dim=-1
                )
                for mutation in by_position[position]:
                    scores[mutation] = float(
                        (
                            log_probs[aa_ids[mutation.mutant]]
                            - log_probs[aa_ids[mutation.wt]]
                        ).item()
                    )
    del model
    _clear_device(device)
    return _variant_totals(variants, scores)


def _relative_variants(
    variants: Sequence[Sequence[Substitution]],
    first_index: int,
) -> tuple[tuple[Substitution, ...], ...]:
    return tuple(
        tuple(
            Substitution(
                mutation.wt,
                mutation.position - first_index + 1,
                mutation.mutant,
            )
            for mutation in mutations
        )
        for mutations in variants
    )


def _window_offsets(length: int, max_residues: int) -> tuple[int, ...]:
    if length <= max_residues:
        return (0,)
    offsets = [0]
    while offsets[-1] + max_residues < length:
        next_offset = min(offsets[-1] + max_residues, length - max_residues)
        if next_offset <= offsets[-1]:
            raise AssertionError("ProSST window construction did not advance")
        offsets.append(next_offset)
    return tuple(offsets)


def _assign_window(
    position: int, windows: Sequence[tuple[int, int]]
) -> tuple[int, int]:
    candidates = [(start, end) for start, end in windows if start <= position <= end]
    if not candidates:
        raise ValueError(f"position {position} has no ProSST scoring window")
    return max(
        candidates,
        key=lambda item: (min(position - item[0], item[1] - position), -item[0]),
    )


def _quantize_structure(
    pdb_path: Path,
    reference: str,
    prosst_repo: Path,
    device: Any,
) -> list[int]:
    import joblib
    import torch
    from torch.nn import functional
    from torch_geometric.data import Batch, Data

    from torch_scatter import scatter_mean

    if not prosst_repo.is_dir():
        raise FileNotFoundError(f"missing pinned ProSST checkout: {prosst_repo}")
    if str(prosst_repo) not in sys.path:
        sys.path.insert(0, str(prosst_repo))
    from prosst.structure.build_graph import generate_graph
    from prosst.structure.build_subgraph import generate_pos_subgraph
    from prosst.structure.encoder.gvp import AutoGraphEncoder

    graph = generate_graph(str(pdb_path), max_distance=10)
    graph_sequence = (
        graph.aa_seq if isinstance(graph.aa_seq, str) else "".join(graph.aa_seq)
    )
    if graph_sequence != reference:
        raise ValueError("PDB/WT sequence mismatch during ProSST quantization")
    model = AutoGraphEncoder(
        node_in_dim=(20, 3),
        node_h_dim=(256, 32),
        edge_in_dim=(32, 1),
        edge_h_dim=(64, 2),
        num_layers=6,
    )
    static = prosst_repo / "prosst" / "structure" / "static"
    model.load_state_dict(
        torch.load(static / "AE.pt", map_location="cpu", weights_only=True)
    )
    model = model.to(device).eval()
    cluster = joblib.load(static / "2048.joblib")
    labels: list[int] = []
    with torch.inference_mode():
        for start in range(0, len(reference), 128):
            subgraphs = []
            for anchor in range(start, min(start + 128, len(reference))):
                raw = generate_pos_subgraph(graph, None, 10, anchor, False, True)[
                    anchor
                ]
                subgraphs.append(
                    Data(
                        node_s=raw.node_s.to(torch.float32),
                        node_v=raw.node_v.to(torch.float32),
                        edge_index=raw.edge_index.to(torch.int64),
                        edge_s=raw.edge_s.to(torch.float32),
                        edge_v=raw.edge_v.to(torch.float32),
                    )
                )
            batch = Batch.from_data_list(subgraphs).to(device)
            batch.node_s = torch.zeros_like(batch.node_s)
            embeddings = model.get_embedding(
                (batch.node_s, batch.node_v),
                batch.edge_index,
                (batch.edge_s, batch.edge_v),
            )
            pooled = scatter_mean(embeddings, batch.batch, dim=0)
            normalized = functional.normalize(pooled, p=2, dim=1).float().cpu().numpy()
            prediction = cluster.predict(
                normalized.astype(cluster.cluster_centers_.dtype, copy=False)
            )
            labels.extend(int(value) for value in prediction.tolist())
    del model, graph
    _clear_device(device)
    if len(labels) != len(reference) or any(
        value < 0 or value >= 2048 for value in labels
    ):
        raise ValueError("invalid ProSST structure-token sequence")
    return labels


def score_prosst(
    reference: str,
    variants: Sequence[Sequence[Substitution]],
    pdb_path: Path,
    config: RuntimeConfig,
) -> np.ndarray:
    unique = sorted({mutation for mutations in variants for mutation in mutations})
    if not unique:
        return np.zeros(len(variants), dtype=np.float64)

    import torch
    from torch.nn import functional
    from transformers import AutoModelForMaskedLM, AutoTokenizer

    device = _resolve_device(config.device)
    structure_tokens = _quantize_structure(
        pdb_path, reference, Path(config.prosst_repo), device
    )
    tokenizer = AutoTokenizer.from_pretrained(
        config.prosst_model_id,
        revision=config.prosst_revision,
        trust_remote_code=True,
    )
    model = (
        AutoModelForMaskedLM.from_pretrained(
            config.prosst_model_id,
            revision=config.prosst_revision,
            trust_remote_code=True,
        )
        .to(device)
        .eval()
    )
    windows = tuple(
        (offset + 1, min(len(reference), offset + config.max_residues))
        for offset in _window_offsets(len(reference), config.max_residues)
    )
    by_window: dict[tuple[int, int], list[Substitution]] = {}
    for mutation in unique:
        by_window.setdefault(_assign_window(mutation.position, windows), []).append(
            mutation
        )
    vocab = tokenizer.get_vocab()
    scores: dict[Substitution, float] = {}
    for (start, end), mutations in by_window.items():
        sequence = reference[start - 1 : end]
        tokenized = tokenizer([sequence], return_tensors="pt")
        input_ids = tokenized["input_ids"].to(device)
        attention_mask = tokenized["attention_mask"].to(device)
        if input_ids.shape[1] != len(sequence) + 2:
            raise ValueError("ProSST tokenizer/reference alignment mismatch")
        shifted = torch.tensor(
            [[1, *[value + 3 for value in structure_tokens[start - 1 : end]], 2]],
            dtype=torch.long,
            device=device,
        )
        with torch.inference_mode():
            logits = functional.log_softmax(
                model(
                    input_ids=input_ids,
                    attention_mask=attention_mask,
                    ss_input_ids=shifted,
                )
                .logits[0, 1:-1]
                .float(),
                dim=-1,
            ).cpu()
        for mutation in mutations:
            local = mutation.position - start
            scores[mutation] = float(
                (
                    logits[local, vocab[mutation.mutant]]
                    - logits[local, vocab[mutation.wt]]
                ).item()
            )
    del model
    _clear_device(device)
    return _variant_totals(variants, scores)


def _batched(values: Sequence[str], size: int) -> Iterable[list[str]]:
    for start in range(0, len(values), size):
        yield list(values[start : start + size])


def score_esm_if1(
    sequences: Sequence[str],
    reference: str,
    pdb_path: Path,
    chain: str,
    config: RuntimeConfig,
) -> np.ndarray:
    import biotite.structure
    import esm
    import torch
    from esm.inverse_folding.util import CoordBatchConverter, load_coords
    from torch.nn import functional

    if not hasattr(biotite.structure, "filter_backbone"):
        biotite.structure.filter_backbone = biotite.structure.filter_peptide_backbone
    coordinates, pdb_sequence = load_coords(str(pdb_path), chain)
    if pdb_sequence != reference:
        raise ValueError("PDB/WT sequence mismatch during ESM-IF1 scoring")
    model, alphabet = esm.pretrained.esm_if1_gvp4_t16_142M_UR50()
    device = _resolve_device(config.device)
    model = model.to(device).eval()
    converter = CoordBatchConverter(alphabet)
    scores: list[float] = []
    for sequence_batch in _batched(sequences, config.sequence_batch_size):
        batch = [(coordinates, None, sequence) for sequence in sequence_batch]
        coords, confidence, _, tokens, padding_mask = converter(
            batch, device=str(device)
        )
        previous = tokens[:, :-1]
        target = tokens[:, 1:]
        with torch.inference_mode():
            logits, _ = model.forward(coords, padding_mask, confidence, previous)
            losses = functional.cross_entropy(logits.float(), target, reduction="none")
        nonpadding = target.ne(alphabet.padding_idx)
        counts = nonpadding.sum(dim=1)
        if torch.any(counts == 0):
            raise ValueError("empty ESM-IF1 target sequence")
        scores.extend(
            (-(losses * nonpadding).sum(dim=1) / counts).detach().cpu().tolist()
        )
    del model
    _clear_device(device)
    result = np.asarray(scores, dtype=np.float64)
    if result.shape != (len(sequences),) or not np.isfinite(result).all():
        raise ValueError("invalid ESM-IF1 score array")
    return result


def _write_gemme_inputs(
    work: Path,
    msa_sequences: Sequence[str],
    variants: Sequence[Sequence[Substitution]],
    first_index: int,
) -> tuple[Path, Path, list[int]]:
    msa_path = work / "query_MSA_upper.a2m"
    length = len(msa_sequences[0])
    with msa_path.open("w", encoding="utf-8", newline="") as handle:
        for index, sequence in enumerate(msa_sequences):
            header = f">seq{index}/{first_index}-{first_index + length - 1}"
            handle.write(f"{header}\n{sequence.upper()}\n")
    mutation_path = work / "mutants.txt"
    active_indices = [index for index, mutations in enumerate(variants) if mutations]
    with mutation_path.open("w", encoding="utf-8", newline="") as handle:
        for index in active_indices:
            tokens = []
            for mutation in variants[index]:
                local_position = mutation.position - first_index + 1
                if not 1 <= local_position <= length:
                    raise ValueError(
                        f"mutation outside MSA coordinate range: {mutation}"
                    )
                tokens.append(f"{mutation.wt}{local_position}{mutation.mutant}")
            handle.write(",".join(tokens) + "\n")
    return msa_path, mutation_path, active_indices


def score_gemme(
    msa_sequences: Sequence[str],
    variants: Sequence[Sequence[Substitution]],
    first_index: int,
    config: RuntimeConfig,
) -> np.ndarray:
    gemme_script = Path(config.gemme_path) / "gemme.py"
    jet_class = Path(config.jet_path) / "jet" / "JET.class"
    if not gemme_script.is_file() or not jet_class.is_file():
        raise FileNotFoundError("the pinned GEMME/JET2 author runtime is unavailable")
    result = np.zeros(len(variants), dtype=np.float64)
    if all(not mutations for mutations in variants):
        return result
    with tempfile.TemporaryDirectory(prefix="cytolexmuta_gemme_") as temporary:
        work = Path(temporary)
        msa_path, mutation_path, active_indices = _write_gemme_inputs(
            work, msa_sequences, variants, first_index
        )
        environment = os.environ.copy()
        environment["GEMME_PATH"] = config.gemme_path
        environment["JET_PATH"] = config.jet_path
        command = [
            "python2.7",
            str(gemme_script),
            msa_path.name,
            "-r",
            "input",
            "-f",
            msa_path.name,
            "-m",
            mutation_path.name,
            "-N",
            str(config.gemme_nseqs),
        ]
        completed = subprocess.run(
            command,
            cwd=work,
            env=environment,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        if completed.returncode != 0:
            raise RuntimeError(
                f"GEMME failed ({completed.returncode}): {completed.stdout[-4000:]}"
            )
        candidates = list(work.glob("*_evolCombi.txt"))
        if len(candidates) != 1:
            raise ValueError("expected exactly one GEMME evolCombi output")
        values: list[float] = []
        for line in candidates[0].read_text(encoding="utf-8").splitlines():
            fields = line.strip().split()
            if not fields:
                continue
            try:
                value = float(fields[-1])
            except ValueError:
                continue
            if not math.isfinite(value):
                raise ValueError("non-finite GEMME score")
            values.append(value)
        if len(values) != len(active_indices):
            raise ValueError(
                f"GEMME row mismatch: {len(values)} != {len(active_indices)}"
            )
        result[np.asarray(active_indices, dtype=int)] = np.asarray(
            values, dtype=np.float64
        )
    return result


def score_live(
    *,
    reference: str,
    sequences: Sequence[str],
    msa_sequences: Sequence[str],
    pdb_path: Path,
    chain: str,
    first_index: int,
    config: RuntimeConfig,
) -> np.ndarray:
    variants = tuple(
        substitutions_from_sequence(reference, sequence, first_index)
        for sequence in sequences
    )
    relative_variants = _relative_variants(variants, first_index)
    if not msa_sequences or msa_sequences[0] != reference:
        raise ValueError("the first MSA sequence must be the WT/reference sequence")
    if any(len(sequence) != len(reference) for sequence in msa_sequences):
        raise ValueError("MSA match-state length does not match the WT sequence")
    with ThreadPoolExecutor(max_workers=1) as pool:
        gemme_future = pool.submit(
            score_gemme, msa_sequences, variants, first_index, config
        )
        esmc = score_esmc(reference, relative_variants, config)
        prosst = score_prosst(reference, relative_variants, pdb_path, config)
        esm_if1 = score_esm_if1(sequences, reference, pdb_path, chain, config)
        gemme = gemme_future.result()
    return fuse_scores(esmc, prosst, gemme, esm_if1)
