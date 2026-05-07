"""提供后端 RagDiagnosticsService 使用的 KG / track 诊断端点。"""

from __future__ import annotations

import logging
from typing import Any

import asyncpg
from fastapi import APIRouter, Depends, Query
from neo4j import AsyncGraphDatabase

from ..auth import require_internal_token
from ..config import get_settings

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/v1/rag/diagnostics",
    tags=["diagnostics"],
    dependencies=[Depends(require_internal_token)],
)


@router.get("/track-stats")
async def get_track_stats(
    language_pack_id: int = Query(..., gt=0),
) -> dict[str, Any]:
    """统计指定语言包页对应的 KG 实体和关系数量。"""
    settings = get_settings()

    pg_conn = await asyncpg.connect(
        host=settings.postgres_host,
        port=settings.postgres_port,
        user=settings.postgres_user,
        password=settings.postgres_password,
        database=settings.postgres_database,
        timeout=5.0,
    )
    try:
        rows = await pg_conn.fetch(
            "SELECT id::text AS pid FROM language_pack_page WHERE language_pack_id = $1",
            language_pack_id,
        )
        track_ids = [f"courseware_page:{row['pid']}" for row in rows]
    finally:
        await pg_conn.close()

    if not track_ids:
        return {"entity_count": 0, "relation_count": 0, "track_count": 0}

    auth = (
        (settings.memgraph_username, settings.memgraph_password)
        if settings.memgraph_username
        else None
    )
    driver = AsyncGraphDatabase.driver(settings.memgraph_uri, auth=auth)
    try:
        async with driver.session(database=settings.memgraph_database) as session:
            entity_result = await session.run(
                """
                MATCH (n)
                WHERE any(track IN $tracks WHERE coalesce(n.source_id, "") CONTAINS track)
                RETURN count(n) AS cnt
                """,
                tracks=track_ids,
            )
            entity_record = await entity_result.single()
            entity_count = entity_record["cnt"] if entity_record else 0

            relation_result = await session.run(
                """
                MATCH ()-[r]->()
                WHERE any(track IN $tracks WHERE coalesce(r.source_id, "") CONTAINS track)
                RETURN count(r) AS cnt
                """,
                tracks=track_ids,
            )
            relation_record = await relation_result.single()
            relation_count = relation_record["cnt"] if relation_record else 0
    finally:
        await driver.close()

    return {
        "entity_count": entity_count,
        "relation_count": relation_count,
        "track_count": len(track_ids),
    }
