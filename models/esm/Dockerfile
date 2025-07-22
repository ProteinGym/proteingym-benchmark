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

COPY ./README.md ./README.md
COPY ./pyproject.toml ./pyproject.toml

# TODO: After pg2-dataset is public, below can be removed:
# Currently, it is required to git clone pg2-dataset, which is a private repo.
RUN --mount=type=secret,id=git_auth \
    git config --global credential.helper store && \
    cat /run/secrets/git_auth > ~/.git-credentials && \
    chmod 600 ~/.git-credentials

RUN uv sync --no-cache

COPY ./src /opt/program/src

ENTRYPOINT ["uv", "run", "pg2-model"]
