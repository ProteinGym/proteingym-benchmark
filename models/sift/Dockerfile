# Build on top of the upstream Ensembl SIFT image so the SIFT 6.2.1 install
# (including `info_on_seqs` and BLIMPS data) is already present at
# `/opt/sift`. The upstream image is linux/x86_64 only and must run under
# emulation on ARM hosts (e.g. Apple Silicon).
FROM --platform=linux/amd64 ensemblorg/sift:6.2.1

RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

# https://docs.astral.sh/uv/guides/integration/docker/#available-images
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

# uv manages its own Python toolchain, which avoids relying on the
# Ubuntu-bundled Python from the upstream image.
ENV UV_PYTHON_PREFERENCE=only-managed
ENV UV_PYTHON=3.12

# Expose the SIFT install paths so the wrapper can find them. The upstream
# image sets SIFT_DIR; we also surface SIFT_HOME (used by `_detect_sift_home`).
ENV SIFT_HOME=/opt/sift
ENV BLIMPS_DIR=/opt/sift/blimps
ENV PATH="/opt/sift/bin:${PATH}"

WORKDIR /opt/program

# Copy and install dependencies
COPY README.md README.md
COPY pyproject.toml pyproject.toml
COPY uv.lock uv.lock
COPY src/ src/
RUN uv sync --no-cache --frozen

ENTRYPOINT ["uv", "run", "sift"]
