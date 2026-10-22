FROM python:3.12-slim-bookworm

# bzip2 for the muscle tarball, a JVM for JET2 and R for GEMME's scripts
RUN apt-get update && apt-get install -y git curl ca-certificates bzip2 \
        default-jdk-headless r-base && rm -rf /var/lib/apt/lists/*

# seqinr/RColorBrewer for GEMME's R scripts
RUN R -e 'install.packages(c("seqinr", "RColorBrewer"), repos="https://cloud.r-project.org")'

# JET2 needs muscle 3.8's legacy CLI; Debian ships 5.x, which silently breaks it
RUN curl -fsSL https://conda.anaconda.org/bioconda/linux-64/muscle-3.8.1551-h9948957_9.tar.bz2 -o /tmp/muscle.tar.bz2 && \
    echo "07f12b158989bd98b4d7af97a2739ccd7bbef0728ddc5a036bd9784363ab0ba9  /tmp/muscle.tar.bz2" | sha256sum -c - && \
    tar xjf /tmp/muscle.tar.bz2 -C /usr/local bin/muscle && rm /tmp/muscle.tar.bz2

# https://docs.astral.sh/uv/guides/integration/docker/#available-images
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /opt/program

# Copy and install dependencies
COPY README.md README.md
COPY pyproject.toml pyproject.toml
COPY uv.lock uv.lock
COPY src/ src/
RUN uv sync --no-cache

# GEMME and JET2 from LCQB; the server's TLS chain is incomplete, so use -k and
# pin by sha256
RUN curl -fsSLk https://www.lcqb.upmc.fr/GEMME/package/GEMME.tgz -o /tmp/GEMME.tgz && \
    curl -fsSLk https://www.lcqb.upmc.fr/JET2/package/JET2.tgz -o /tmp/JET2.tgz && \
    echo "477938d6a3604ed3394f94ec2990043451db4ea3f94f69759020f7a6e8a39661  /tmp/GEMME.tgz" | sha256sum -c - && \
    echo "2b4abb459bd2a08893794b29950a27f44b877c0ea971af9e57f8aeed8f14973f  /tmp/JET2.tgz" | sha256sum -c - && \
    tar xzf /tmp/GEMME.tgz -C /opt/program && tar xzf /tmp/JET2.tgz -C /opt/program && \
    rm /tmp/GEMME.tgz /tmp/JET2.tgz

ENTRYPOINT ["uv", "run", "gemme"]
