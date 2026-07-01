---
# Model identifier used for referencing this model in the benchmark system
name: aidoragplm

tags: ["zero-shot"]

# Multi-target support flag
multi_y: false

hyper_parameters:
    # Hugging Face checkpoint repository for AIDO.RAGPLM (3B-parameter MSA-only MLM)
    model_name: "genbio-ai/AIDO.Protein-RAG-3B"
    # Pinned revision so the benchmark is reproducible; bump deliberately
    model_revision: "dd545e41"
    # Inference device. "cuda" inside the harness containers; "mps" on Apple Silicon dev hosts
    device: "cuda"
    # Tensor dtype. bf16 fits the 2.83B model in ~5.7 GB and matches the published serving config
    dtype: "bfloat16"
    # Match the ProteinGym AIDO baseline's MSA token budget
    max_context: 12800
    # Sliding-window length over the query (queries shorter than this run in one pass)
    sliding_window: 768
    # Stride equal to the window — non-overlapping, mirrors the upstream `get_logits_table_sliding`
    sliding_step: 768
    # Greedy-select MSA rows until this many non-gap tokens have been packed in
    msa_token_budget: 12800
    # Optional hard cap on number of MSA rows (null = use the token budget only)
    max_msa_sequences: null
    # Random seed for the greedy MSA selection (matches upstream default)
    msa_selection_seed: 0
    # Temperature for the mutant log-softmax. 1.0 = no scaling
    # The 16B ProteinGym baseline uses {mt=1.0, wt=1.5}; that was introduced
    # *together with* the 16B fine-tuning and is not transferred to the 3B model
    mutant_temperature: 1.0
    wildtype_temperature: 1.0
    # Keep model weights resident between scoring calls to avoid repeated 5.7 GB loads
    keep_model_after_pred: true
---

# Model Card for AIDO.RAGPLM

[**AIDO.RAGPLM**](https://www.biorxiv.org/content/10.1101/2024.12.02.626519v1) is a 3-billion-parameter retrieval-augmented masked protein language model from GenBio AI. It conditions a protein masked-LM forward pass on a packed multiple-sequence alignment retrieved for the query and is the language-modelling backbone underlying AIDO.RAGFold (structure prediction).

This submission uses the public release [`genbio-ai/AIDO.Protein-RAG-3B`](https://huggingface.co/genbio-ai/AIDO.Protein-RAG-3B). It is **not** the same checkpoint as [`genbio-ai/AIDO.Protein-RAG-16B-proteingym-dms-zeroshot`](https://huggingface.co/genbio-ai/AIDO.Protein-RAG-16B-proteingym-dms-zeroshot), which is a 16B fine-tune that additionally consumes PDB-derived structural embeddings; the 3B checkpoint targeted here ships with `str_embedding_in: null` and is MSA-only.

## Architecture

| | |
| --- | --- |
| Architecture | `FM4BioForMaskedLM` (Transformer, 36 layers, 2-D RoPE) |
| Parameters | 2,834,611,584 (~2.83 B) |
| Checkpoint size | 11.34 GB (fp32 on disk; bf16 in memory ≈ 5.67 GB) |
| Input modality | Query protein sequence + aligned MSA |
| Structural input | **None** (`str_embedding_in: null`) |
| Tokenizer | `FM4BioTokenizer`, 44-entry protein vocabulary |

## Scoring algorithm

Zero-shot mutation scoring follows the reference [`proteingym/baselines/AIDO/utils/misc.py`](https://github.com/OATML-Markslab/ProteinGym/blob/144fe22b07dfaeec2b366f2346203a9838a55b4c/proteingym/baselines/AIDO/utils/misc.py) at commit `144fe22b`, adapted for the 3B MSA-only path:

1. Greedy-select diverse MSA rows up to `msa_token_budget` non-gap tokens.
2. For long proteins, slide a `sliding_window`-residue window across the query.
3. For each variant position, mask **only the query token** at that position (not the full MSA column) and run a single forward pass through the masked-LM.
4. Score each variant as
   ```
   sum_pos  log P(mut[pos] | masked context, mutant_temperature)
          - log P(wt[pos]  | masked context, wildtype_temperature)
   ```
   Reference-identical variants therefore score exactly `0.0`.

## Vendored model code

The HF repository for `AIDO.Protein-RAG-3B` ships only `config.json` and weights — no modeling code, no tokenizer files. We vendor the four files required for inference from [`genbio-ai/ModelGenerator @ c562a20b`](https://github.com/genbio-ai/ModelGenerator/tree/c562a20b2bb45e10655ac1de1ff48f7c8cc015e3/modelgenerator/huggingface_models/fm4bio) (Apache-2.0):

* `modeling_fm4bio.py` (with one inline `# PATCH (AIDO_RAGPLM):` to `RotaryEmbedding.inv_freq` so the model works on non-CUDA devices — upstream stored it as a plain attribute on the current CUDA device, causing a device-mismatch on MPS/CPU)
* `configuration_fm4bio.py`
* `tokenization_fm4bio.py`
* `vocab_protein.txt`

This avoids pulling in ModelGenerator's heavy training dependencies (Lightning, OpenFold, AnnData, DeepSpeed, …).

## Hardware

* **Verified on Apple Silicon MPS** (16 GB unified memory, M-series, bf16): integration test loads the checkpoint in ~40 s and the full HXK4 zero-shot pipeline produces **Spearman ρ = +0.584 (p = 1.2e-19)** against experimental DMS_scores on a 200-mutant slice, inside the published AIDO performance band.
* **CUDA recommended for production** (harness Docker container): ~8 GB VRAM at bf16. CPU inference works but is slow (~3 s/masked-position per residue at 4 K-token context).

## Inputs / outputs

* Reads a `.splits.pgdata` Subsets archive (any ProteinGym DMS dataset with an aligned MSA in `dataset.msas` and a `WILD_TYPE` reference in `dataset.sequences`).
* Writes `predictions.json` (polars JSON) with columns:
  * `mutation_col` — full-length mutated sequence (string)
  * `test` — experimental target value (`DMS_score`)
  * `pred` — AIDO.RAGPLM zero-shot score (float)

## Citations

* Li, Cheng, Song, Xing (2024). *Retrieval Augmented Protein Language Models for Protein Structure Prediction*. [bioRxiv 10.1101/2024.12.02.626519](https://www.biorxiv.org/content/10.1101/2024.12.02.626519v1).

## Licensing

* **Submission code** — Apache-2.0.
* **Vendored modeling code** — Apache-2.0 (headers preserved in each file).
* **Model checkpoint (`genbio-ai/AIDO.Protein-RAG-3B`)** — [GenBio AI Community License Agreement](https://huggingface.co/genbio-ai/AIDO.Protein-RAG-3B/blob/main/LICENSE) (custom, non-commercial; attribution required). Weights are **not** redistributed; the harness downloads them from Hugging Face on first run.
