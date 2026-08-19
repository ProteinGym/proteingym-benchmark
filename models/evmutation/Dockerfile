FROM python:3.12-slim-bookworm

# install git, curl, certificates, and a C compiler needed to build the
# plmc binary
RUN apt-get update && apt-get install -y git curl ca-certificates build-essential && rm -rf /var/lib/apt/lists/*

# build plmc with OpenMP support, so the wrapper's "cpu" hyperparameter can
# parallelize fitting across cores
RUN git clone --depth 1 https://github.com/debbiemarkslab/plmc.git /opt/plmc \
    && cd /opt/plmc && make all-openmp32 \
    && ln -s /opt/plmc/bin/plmc /usr/local/bin/plmc

# https://docs.astral.sh/uv/guides/integration/docker/#available-images
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /opt/program

# Copy and install dependencies
COPY README.md README.md
COPY pyproject.toml pyproject.toml
COPY uv.lock uv.lock
COPY src/ src/
RUN uv sync --no-cache

ENTRYPOINT ["uv", "run", "evmutation"]
