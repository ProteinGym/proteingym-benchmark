FROM python:3.12-slim-bookworm

# Fix GPG signature issues
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    git \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# https://docs.astral.sh/uv/guides/integration/docker/#available-images
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /opt/program

# Copy pg2-benchmark source
COPY ./README.md ./pg2-benchmark/README.md
COPY ./pyproject.toml ./pg2-benchmark/pyproject.toml
COPY ./src ./pg2-benchmark/src

# Copy pg2-model-pls dependencies
COPY ./models/pls/README.md ./README.md
COPY ./models/pls/pyproject.toml ./pyproject.toml

# TODO: After pg2-dataset is public, below can be removed:
# Currently, it is required to git clone pg2-dataset, which is a private repo.

ARG GIT_CACHE_BUST=1

RUN --mount=type=secret,id=git_auth \
    echo "Git credentials version: $GIT_CACHE_BUST" && \
    git config --global credential.helper store && \
    cat /run/secrets/git_auth > ~/.git-credentials && \
    chmod 600 ~/.git-credentials

RUN uv sync --no-cache

COPY ./models/pls/src ./src

ENTRYPOINT ["uv", "run", "pg2-model"]
