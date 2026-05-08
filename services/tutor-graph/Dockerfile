FROM python:3.12-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/ \
    PIP_TRUSTED_HOST=mirrors.aliyun.com \
    PIP_DEFAULT_TIMEOUT=120 \
    PIP_RETRIES=5 \
    PYTHONMALLOC=malloc \
    PYTHONHASHSEED=random \
    MALLOC_TRIM_THRESHOLD_=131072 \
    MALLOC_ARENA_MAX=2

WORKDIR /app

# Copy the project metadata first so the dependency layer caches on every image
# rebuild where only `app/` source changed. Dependencies live in pyproject.toml —
# never duplicate the list in this Dockerfile.
COPY services/tutor-graph/pyproject.toml ./pyproject.toml
COPY services/tutor-graph/app ./app
COPY contracts ./contracts

RUN pip install hatchling && \
    pip install --no-build-isolation .

EXPOSE 8100

HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8100/health', timeout=3).read()"

# IMPORTANT: tutor_graph keeps run events / active run registry / poller threads in
# process memory. Running more than one uvicorn worker would fan out state per worker
# and the Java gateway would randomly see empty event streams. Always --workers 1.
# Scale horizontally via LangGraph-Postgres checkpointer + sticky routing, not via
# multi-worker on a single instance.
ENTRYPOINT ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8100", "--workers", "1"]
