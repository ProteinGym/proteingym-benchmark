FROM python:3.12-slim-bookworm

# install git, curl, certificates, and a C compiler (triton's runtime C-wrapper JIT)
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

# SaProt_35M_AF2 checkpoint (HF westlake-repl/SaProt_35M_AF2, commit-pinned)
# and the static foldseek release used for 3Di structure tokens
RUN mkdir -p /opt/program/data/SaProt_35M_AF2 /opt/program/bin && \
    for f in config.json special_tokens_map.json tokenizer_config.json vocab.txt pytorch_model.bin; do \
        curl -fL --retry 3 -o /opt/program/data/SaProt_35M_AF2/$f \
            https://huggingface.co/westlake-repl/SaProt_35M_AF2/resolve/316cd4017d29f4657b959365f24b57f1ee278912/$f || exit 1; \
    done && \
    curl -fL https://github.com/steineggerlab/foldseek/releases/download/10-941cd33/foldseek-linux-avx2.tar.gz \
        | tar -xz -C /opt/program/bin --strip-components=2 foldseek/bin/foldseek && \
    chmod 755 /opt/program/bin/foldseek && \
    /opt/program/bin/foldseek version

ENV HF_HUB_OFFLINE=1

ENTRYPOINT ["uv", "run", "saprot"]
