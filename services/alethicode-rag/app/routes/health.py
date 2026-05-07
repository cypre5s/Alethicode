"""提供存活与就绪探针。"""

from __future__ import annotations

import logging

import asyncpg
from fastapi import APIRouter
from neo4j import AsyncGraphDatabase

from ..config import get_settings
from ..rag.builder import _RAG  # type: ignore[attr-defined]
from ..schemas import HealthStatus

logger = logging.getLogger(__name__)

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthStatus)
async def health() -> HealthStatus:
    settings = get_settings()

    pg_status = "ok"
    try:
        conn = await asyncpg.connect(
            host=settings.postgres_host,
            port=settings.postgres_port,
            user=settings.postgres_user,
            password=settings.postgres_password,
            database=settings.postgres_database,
            timeout=3.0,
        )
        try:
            await conn.execute("SELECT 1")
        finally:
            await conn.close()
    except Exception as exc:
        logger.warning("health: postgres ping failed: %s", exc)
        pg_status = f"down: {exc.__class__.__name__}"

    mg_status = "ok"
    try:
        auth = (
            (settings.memgraph_username, settings.memgraph_password)
            if settings.memgraph_username
            else None
        )
        driver = AsyncGraphDatabase.driver(settings.memgraph_uri, auth=auth)
        try:
            async with driver.session(database=settings.memgraph_database) as session:
                await session.run("RETURN 1")
        finally:
            await driver.close()
    except Exception as exc:
        logger.warning("health: memgraph ping failed: %s", exc)
        mg_status = f"down: {exc.__class__.__name__}"

    from ..main import LLM_SMOKE_OK

    overall_ok = pg_status == "ok" and mg_status == "ok" and LLM_SMOKE_OK
    return HealthStatus(
        status="ok" if overall_ok else "degraded",
        postgres=pg_status,
        memgraph=mg_status,
        rag_initialized=_RAG is not None,
        llm_smoke_ok=LLM_SMOKE_OK,
    )
