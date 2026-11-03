FROM python:3.12-slim-bookworm

# install git, curl, certificates, and a C++ compiler
RUN apt-get update && apt-get install -y git curl ca-certificates build-essential && rm -rf /var/lib/apt/lists/*

# https://docs.astral.sh/uv/guides/integration/docker/#available-images
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /opt/program

# Copy and install dependencies
COPY README.md README.md
COPY pyproject.toml pyproject.toml
COPY uv.lock uv.lock
COPY src/ src/
RUN uv sync --no-cache

# native transformers checkpoint, revision pinned
RUN mkdir -p /opt/program/data && \
    uv run --no-sync hf download biohub/ESMC-300M --revision e4bac860f0c502cd80f3aeac3d0ce6524c6627cb \
        --local-dir /opt/program/data/esmc-300m && \
    rm -rf /opt/program/data/esmc-300m/.cache /root/.cache/huggingface

ENTRYPOINT ["uv", "run", "esmc"]
