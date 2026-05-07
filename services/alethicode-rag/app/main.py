"""alethicode-rag 的 FastAPI 入口。"""

from __future__ import annotations

import asyncio
import logging
import sys
from contextlib import asynccontextmanager

from fastapi import FastAPI
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from starlette.responses import Response

from .config import get_settings
from .rag.builder import shutdown_rag
from .rag.llm import build_llm_callable
from .routes.diagnostics import router as diagnostics_router
from .routes.health import router as health_router
from .routes.index import router as index_router
from .routes.query import router as query_router

logger = logging.getLogger(__name__)

LLM_SMOKE_OK: bool = False


@asynccontextmanager
async def lifespan(_app: FastAPI):
    global LLM_SMOKE_OK
    settings = get_settings()
    logging.basicConfig(level=getattr(logging, settings.log_level.upper(), logging.INFO))
    logger.info(
        "alethicode-rag starting: pg=%s memgraph=%s graph_storage=%s",
        f"{settings.postgres_host}:{settings.postgres_port}",
        settings.memgraph_uri,
        settings.graph_storage,
    )

    try:
        llm = build_llm_callable(settings)
        smoke_result = await asyncio.wait_for(
            llm("ping", system_prompt="reply 'ok' only"),
            timeout=15.0,
        )
        if not smoke_result or "ok" not in smoke_result.lower()[:64]:
            raise RuntimeError(
                f"LLM smoke probe returned unexpected response: {smoke_result!r}"
            )
        LLM_SMOKE_OK = True
        logger.info("LLM smoke probe ok: %r", smoke_result[:64])
    except Exception as exc:
        logger.error("LLM smoke probe FAILED, container exiting: %s", exc)
        sys.exit(1)

    try:
        yield
    finally:
        await shutdown_rag()
        logger.info("alethicode-rag stopped")


app = FastAPI(
    title="alethicode-rag",
    version="0.1.0",
    description="LightRAG microservice; KV/Vector/DocStatus on PostgreSQL, Graph on Memgraph.",
    lifespan=lifespan,
)


app.include_router(health_router)
app.include_router(index_router)
app.include_router(query_router)
app.include_router(diagnostics_router)


@app.get("/metrics", include_in_schema=False)
async def metrics() -> Response:
    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)
