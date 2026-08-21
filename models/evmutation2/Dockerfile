FROM python:3.12-slim-bookworm

# install git, curl, certificates, and a C compiler (needed by triton to
# JIT-compile its CUDA wrapper kernels at runtime)
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

# fetch the model weights from Hugging Face instead of bundling them into
# the build context
RUN mkdir -p /opt/program/data && \
    curl -kL https://huggingface.co/thomashopf/evmutation2/resolve/main/msa-only-small.ckpt \
        -o /opt/program/data/msa-only-small.ckpt

ENTRYPOINT ["uv", "run", "evmutation2"]
