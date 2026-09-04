FROM elodielaine/gemme@sha256:a35099f7dee9411842e3e04318f4a9b435541bce16aff1c9ef5baa507baf5e7d

RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=ghcr.io/astral-sh/uv:0.8.22 /uv /uvx /bin/

ENV UV_PYTHON_INSTALL_DIR=/opt/uv-python \
    UV_PROJECT_ENVIRONMENT=/opt/program/.venv

RUN uv python install 3.12 \
    && git clone https://github.com/ai4protein/ProSST.git /opt/ProSST \
    && cd /opt/ProSST \
    && git checkout 0bcfae91356d70e0da56fdd9bade4a3e7944769c

WORKDIR /opt/program
COPY README.md pyproject.toml uv.lock ./
COPY src/ src/
RUN uv sync --frozen --no-cache

ENTRYPOINT ["uv", "run", "--frozen", "cytolexmuta"]
