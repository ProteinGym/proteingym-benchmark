FROM python:3.12-slim-bookworm

# Fix GPG signature issues
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/program

# https://docs.astral.sh/uv/guides/integration/docker/#available-images
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

COPY ./README.md /opt/program/README.md
COPY ./pyproject.toml /opt/program/pyproject.toml

# TODO: After pg2-dataset is public, below can be removed:
# Currently, it is required to git clone pg2-dataset, which is a private repo.

ARG GIT_CACHE_BUST=1

RUN --mount=type=secret,id=git_auth \
    echo "Git credentials version: $GIT_CACHE_BUST" && \
    git config --global credential.helper store && \
    cat /run/secrets/git_auth > ~/.git-credentials && \
    chmod 600 ~/.git-credentials

RUN uv sync --no-cache

COPY ./src /opt/program/src

ENTRYPOINT ["uv", "run", "pg2-model"]
