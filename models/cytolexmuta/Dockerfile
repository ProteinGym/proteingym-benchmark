FROM python:3.12-slim-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /opt/program
COPY README.md README.md
COPY pyproject.toml pyproject.toml
COPY src/ src/
RUN uv sync --no-cache

ENTRYPOINT ["uv", "run", "cytolexmuta"]
