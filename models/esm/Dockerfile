FROM python:3.12

RUN apt-get update && apt-get install -y --no-install-recommends \ 
    git && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/program

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

COPY ./README.md /opt/program/README.md
COPY ./pyproject.toml /opt/program/pyproject.toml

# TODO: After pg2-dataset is public, below can be removed:
RUN --mount=type=secret,id=git_auth \
    git config --global credential.helper store && \
    cat /run/secrets/git_auth > ~/.git-credentials && \
    chmod 600 ~/.git-credentials

# https://docs.astral.sh/uv/guides/integration/docker/#available-images
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

RUN uv sync --no-cache

COPY ./src /opt/program/src

ENTRYPOINT ["uv", "run", "pg2-model"]
