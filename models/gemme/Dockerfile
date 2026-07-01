# Build on top of the upstream GEMME image so the Python 2.7 / R / Java
# stack, plus the GEMME and JET2 source trees, are already installed at the
# canonical paths (`/opt/GEMME`, `/opt/JET2`).
FROM elodielaine/gemme:gemme

RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

# https://docs.astral.sh/uv/guides/integration/docker/#available-images
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

# uv manages its own Python toolchain, which avoids relying on the
# Ubuntu-bundled Python from the upstream image (only Python 2.7 is
# guaranteed there).
ENV UV_PYTHON_PREFERENCE=only-managed
ENV UV_PYTHON=3.12

# Expose the GEMME/JET2 install paths so the wrapper can find them.
ENV GEMME_PATH=/opt/GEMME
ENV JET_PATH=/opt/JET2

WORKDIR /opt/program

# Copy and install dependencies
COPY README.md README.md
COPY pyproject.toml pyproject.toml
COPY uv.lock uv.lock
COPY src/ src/
RUN uv sync --no-cache --frozen

ENTRYPOINT ["uv", "run", "gemme"]
