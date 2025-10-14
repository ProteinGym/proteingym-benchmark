FROM python:3.12-slim-bookworm

# https://docs.astral.sh/uv/guides/integration/docker/#available-images
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /opt/program

# TODO: After proteingym-base and proteingym-benchmark are public repositories, below COPY can be removed
COPY dist/proteingym*.whl dist/

# Copy and install dependencies
COPY README.md README.md
COPY pyproject.toml pyproject.toml
COPY src/ src/
RUN uv sync --no-cache

ENTRYPOINT ["uv", "run", "huggingface-regressor"]
