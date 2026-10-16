FROM python:3.12-slim-bookworm

# install git, curl, certificates, and a C++ compiler (git needed for the
# proteingym-base git dependency; g++ needed because structuredca builds a
# C++ extension (plmdca/lbfgs) at install time)
RUN apt-get update && apt-get install -y git curl ca-certificates g++ && rm -rf /var/lib/apt/lists/*

# https://docs.astral.sh/uv/guides/integration/docker/#available-images
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /opt/program

# Copy and install dependencies
COPY README.md README.md
COPY pyproject.toml pyproject.toml
COPY uv.lock uv.lock
COPY src/ src/
RUN uv sync --no-cache

ENTRYPOINT ["uv", "run", "structuredca"]
