# Nothing compiles against CUDA at build time - torch's own prebuilt cu130
# wheel supplies the runtime
FROM python:3.12-slim-bookworm

RUN apt-get update && apt-get install -y git curl ca-certificates build-essential && rm -rf /var/lib/apt/lists/*

# https://docs.astral.sh/uv/guides/integration/docker/#available-images
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /opt/program

# Copy and install dependencies
COPY README.md README.md
COPY LICENSE LICENSE
COPY pyproject.toml pyproject.toml
COPY uv.lock uv.lock
COPY .python-version .python-version
COPY src/ src/
COPY vendor/ vendor/
RUN uv sync --no-cache

RUN uv run --no-sync python -c "import torch, torch_scatter; from proteingym.models.esm_if1 import model; print('esm_if1.model import OK, torch', torch.__version__)"

RUN mkdir -p /opt/program/data && \
    curl -kL https://dl.fbaipublicfiles.com/fair-esm/models/esm_if1_gvp4_t16_142M_UR50.pt \
        -o /opt/program/data/esm_if1_gvp4_t16_142M_UR50.pt

ENTRYPOINT ["uv", "run", "esm-if1"]
