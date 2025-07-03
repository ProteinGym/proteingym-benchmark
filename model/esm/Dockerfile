FROM python:3.12-slim

RUN apt-get update && apt-get install -y git

WORKDIR /project

COPY ./src /project/src
COPY ./pyproject.toml /project/pyproject.toml
COPY ./README.md /project/README.md


# TODO: After pg2-dataset is public, below can be removed:
RUN --mount=type=secret,id=git_auth \
    git config --global credential.helper store && \
    cat /run/secrets/git_auth > ~/.git-credentials && \
    chmod 600 ~/.git-credentials

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

RUN uv sync

ENTRYPOINT ["uv", "run", "pg2-model"]