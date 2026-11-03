FROM python:3.12-slim-bookworm

# install git, curl, certificates
RUN apt-get update && apt-get install -y git curl ca-certificates && rm -rf /var/lib/apt/lists/*

# https://docs.astral.sh/uv/guides/integration/docker/#available-images
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /opt/program

# Copy and install dependencies
COPY README.md README.md
COPY pyproject.toml pyproject.toml
COPY uv.lock uv.lock
COPY src/ src/
RUN uv sync --no-cache

# all six released checkpoints (~20MB each), selected via checkpoint_path;
# pinned to the upstream commit the wrapper vendors
ARG POTTSMPNN_URL=https://github.com/KeatingLab/PottsMPNN/raw/1716f99605ac6c2b916a64f40abfe66ee2468fe3
RUN mkdir -p /opt/program/data/vanilla_model_weights /opt/program/data/soluble_model_weights && \
    for f in pottsmpnn_20 pottsmpnn_30 pottsmpnn_msa_20; do \
        curl -fL ${POTTSMPNN_URL}/vanilla_model_weights/$f.pt -o /opt/program/data/vanilla_model_weights/$f.pt; \
    done && \
    for f in sol_pottsmpnn_20 sol_pottsmpnn_30 sol_pottsmpnn_msa_20; do \
        curl -fL ${POTTSMPNN_URL}/soluble_model_weights/$f.pt -o /opt/program/data/soluble_model_weights/$f.pt; \
    done

ENV POTTSMPNN_DATA_DIR=/opt/program/data
# matplotlib (imported by the vendored run_utils) needs a writable config dir
ENV MPLCONFIGDIR=/tmp/mplconfig

ENTRYPOINT ["uv", "run", "pottsmpnn"]
