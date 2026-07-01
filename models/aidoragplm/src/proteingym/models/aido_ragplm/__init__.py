"""AIDO.RAGPLM ProteinGym2 model submission.

This package wraps the zero-shot mutation scoring algorithm of the
``genbio-ai/AIDO.Protein-RAG-3B`` masked protein language model
(`bioRxiv 10.1101/2024.12.02.626519
<https://www.biorxiv.org/content/10.1101/2024.12.02.626519v1>`_, "AIDO.RAGPLM")
for use with the proteingym-benchmark harness.

Three layers live here:

* ``_vendored.fm4bio`` — verbatim model / tokenizer code from
  ``genbio-ai/ModelGenerator @ c562a20b`` (plus one MPS-correctness patch to
  ``RotaryEmbedding``).
* ``_inference`` — model-agnostic AIDO inference functions (tokenization,
  masked forward pass, zero-shot log-ratio scoring).
* ``model`` — the ``evedesign.model.BaseModel`` / ``Scorer`` wrapper
  (``AIDORAGPLM``) plus the proteingym-benchmark ``load`` / ``infer``
  adapter. The typer CLI in :mod:`.__main__` calls into ``model``.
"""

__version__ = "0.1.0"
