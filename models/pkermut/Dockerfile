FROM python:3.12-slim-bookworm

# https://docs.astral.sh/uv/guides/integration/docker/#available-images
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /opt/program

# Install software
RUN mkdir -p software
COPY software/ software/
RUN python3 -m zipfile -e software/ProteinMPNN-main.zip software
ENV PROTEINMPNN_DIR=software/ProteinMPNN-main

# Copy and install dependencies
COPY README.md README.md
COPY pyproject.toml pyproject.toml
COPY src/ src/
RUN uv sync --no-cache

ENTRYPOINT ["uv", "run", "pkermut"]
