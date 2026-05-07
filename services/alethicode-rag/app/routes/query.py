"""提供 Java 侧调用的四类 RAG 查询端点。"""

from __future__ import annotations

import logging
from typing import Any

from fastapi import APIRouter, Depends

from ..auth import require_internal_token
from ..rag.builder import default_query_param, get_rag
from ..schemas import (
    CoursewareQueryRequest,
    MemoryQueryRequest,
    QueryHits,
    RetrievedChunk,
    RetrievedEntity,
    RetrievedRelation,
    SimilarErrorQueryRequest,
    TransferQueryRequest,
)

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/v1/rag/query",
    tags=["query"],
    dependencies=[Depends(require_internal_token)],
)


def _coerce_hits(raw: Any) -> QueryHits:
    """归一化 LightRAG 不同版本返回的查询结果。"""

    if raw is None:
        return QueryHits(raw_context=None)

    if isinstance(raw, str):
        return QueryHits(raw_context=raw)

    if not isinstance(raw, dict):
        return QueryHits(raw_context=str(raw))

    chunks_raw = raw.get("chunks") or raw.get("text_units") or []
    entities_raw = raw.get("entities") or []
    relations_raw = raw.get("relations") or raw.get("relationships") or []

    chunks = [
        RetrievedChunk(
            chunk_id=str(c.get("chunk_id") or c.get("id") or c.get("chunk_order_index", "")),
            content=str(c.get("content") or c.get("text") or ""),
            score=_optional_float(c.get("score") or c.get("similarity")),
            metadata={k: v for k, v in c.items() if k not in {"content", "text"}},
        )
        for c in chunks_raw
        if isinstance(c, dict)
    ]
    entities = [
        RetrievedEntity(
            entity_id=str(e.get("entity_name") or e.get("id") or ""),
            entity_type=e.get("entity_type") or e.get("type"),
            description=e.get("description"),
        )
        for e in entities_raw
        if isinstance(e, dict)
    ]
    relations = [
        RetrievedRelation(
            src_id=str(r.get("src_id") or r.get("source") or ""),
            tgt_id=str(r.get("tgt_id") or r.get("target") or ""),
            description=r.get("description"),
            keywords=list(r.get("keywords") or []),
        )
        for r in relations_raw
        if isinstance(r, dict)
    ]
    return QueryHits(
        entities=entities,
        relations=relations,
        chunks=chunks,
        raw_context=raw.get("context") if isinstance(raw.get("context"), str) else None,
    )


def _optional_float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


async def _run_query(query: str, top_k: int) -> QueryHits:
    rag = await get_rag()
    param = default_query_param()
    param.top_k = top_k
    raw = await rag.aquery(query, param=param)
    return _coerce_hits(raw)


@router.post("/courseware", response_model=QueryHits)
async def query_courseware(payload: CoursewareQueryRequest) -> QueryHits:
    return await _run_query(payload.query, payload.top_k)


@router.post("/similar-error", response_model=QueryHits)
async def query_similar_error(payload: SimilarErrorQueryRequest) -> QueryHits:
    return await _run_query(payload.query, payload.top_k)


@router.post("/memory", response_model=QueryHits)
async def query_memory(payload: MemoryQueryRequest) -> QueryHits:
    return await _run_query(payload.query, payload.top_k)


@router.post("/transfer", response_model=QueryHits)
async def query_transfer(payload: TransferQueryRequest) -> QueryHits:
    return await _run_query(payload.query, payload.top_k)
