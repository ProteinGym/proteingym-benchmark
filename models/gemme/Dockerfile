FROM python:3.12-slim-bookworm

# install git, curl, certificates (needed by uv/the proteingym-base git
# dependency), a JVM (JET2), and R (for computePred.R/pred.R). muscle is
# not installed via apt - see below
RUN apt-get update && apt-get install -y \
        git curl ca-certificates \
        default-jdk-headless \
        r-base \
    && rm -rf /var/lib/apt/lists/*

# fetch computePred.R, pred.R, default.conf, blosum62p.txt, alphabets/, and
# LICENSE.txt from GEMME
RUN curl -kLsSf http://www.lcqb.upmc.fr/GEMME/package/GEMME.tgz | tar xz -C /tmp \
    && mkdir -p /opt/program \
    && cp -r /tmp/GEMME/computePred.R /tmp/GEMME/pred.R /tmp/GEMME/default.conf \
        /tmp/GEMME/blosum62p.txt /tmp/GEMME/alphabets /tmp/GEMME/LICENSE.txt \
        /opt/program/ \
    && rm -rf /tmp/GEMME

# seqinr/RColorBrewer from R package manager
RUN R -e 'install.packages(c("seqinr", "RColorBrewer"), repos="https://cloud.r-project.org")'

# install uv
RUN curl -LsSf https://astral.sh/uv/install.sh | sh \
    && mv /root/.local/bin/uv /root/.local/bin/uvx /usr/local/bin/

COPY src/ pyproject.toml uv.lock README.md JET2/ /opt/program/

# Debian bookworm's packaged `muscle` apt package is actually 5.2 - rewritten 
# CLI doesn't support the legacy `-in ... -clw -out ...` flags JET2 
# Muscle.java hardcodes - JET2 silently produces degenerate output, 
# computePred.R runs to completion on it anyway, and the
# wrapper's NaN-skipping in score() masks the result into uniformly 0.0
# scores for every variant, with no exception raised. This local version
# is built from the latest source (5.2.1) and works correctly with JET2.
COPY bin/muscle /usr/local/bin/muscle
RUN chmod +x /usr/local/bin/muscle

# sync dependencies
WORKDIR /opt/program
RUN uv sync --no-cache

ENV PATH="/opt/program/.venv/bin:${PATH}"

ENTRYPOINT ["uv", "run", "--no-sync", "gemme"]
