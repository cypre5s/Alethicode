FROM python:3.12-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/ \
    PIP_TRUSTED_HOST=mirrors.aliyun.com \
    PIP_DEFAULT_TIMEOUT=180 \
    PIP_RETRIES=5 \
    PYTHONMALLOC=malloc \
    PYTHONHASHSEED=random \
    MALLOC_TRIM_THRESHOLD_=131072 \
    MALLOC_ARENA_MAX=2

WORKDIR /app

# Install build deps for asyncpg / pgvector / lightrag native bits
RUN sed -i \
        -e 's|http://deb.debian.org/debian-security|https://mirrors.aliyun.com/debian-security|g' \
        -e 's|http://deb.debian.org/debian|https://mirrors.aliyun.com/debian|g' \
        /etc/apt/sources.list /etc/apt/sources.list.d/debian.sources 2>/dev/null || true \
    && apt-get update \
    && apt-get install -y --no-install-recommends gcc curl \
    && rm -rf /var/lib/apt/lists/*

COPY services/alethicode-rag/pyproject.toml ./pyproject.toml
COPY services/alethicode-rag/app ./app

RUN pip install hatchling && \
    pip install --no-build-isolation .

EXPOSE 8200

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8200/health', timeout=3).read()"

# IMPORTANT: --workers 1. LightRAG holds connection pools and graph
# session state in process memory. Multi-worker would fan out caches and
# duplicate writes to lightrag_* tables. Scale horizontally with PG +
# Memgraph clustering, not multi-worker.
ENTRYPOINT ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8200", "--workers", "1"]
