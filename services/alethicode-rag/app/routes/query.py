"""提供 Java 侧调用的四类 RAG 查询端点。"""

from __future__ import annotations

import logging
import re
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

# 课件 chunk 的 file_path 形如 "language_pack/{lpId}/p{pageNo}"，由
# scripts/ops/rag_backfill.py 写入 LightRAG。Java 后端 PageRetrievalServiceImpl
# 用拆出来的 (language_pack_id, page_no) 去 language_pack_page 反查 page_id。
_FILE_PATH_RE = re.compile(r"^language_pack/(\d+)/p(\d+)$")


def _parse_courseware_path(file_path: str | None) -> dict[str, int]:
    if not file_path:
        return {}
    match = _FILE_PATH_RE.match(file_path)
    if not match:
        return {}
    return {
        "language_pack_id": int(match.group(1)),
        "page_no": int(match.group(2)),
    }


def _coerce_data(raw: Any) -> QueryHits:
    """归一化 LightRAG 1.4.x ``aquery_data()`` 返回的结构化检索结果。

    LightRAG 1.4.x 把 ``aquery()`` 改成 LLM-content wrapper（永远返回 ``str``，
    即使 ``only_need_context=True`` 在新版也走 ``aquery_llm`` 分支），新增
    ``aquery_data()`` 返回 ``{status, data: {entities, relationships, chunks,
    references}, metadata}``。课件页检索只消费 ``data.chunks``（Java 侧通过
    metadata 反查 ``language_pack_page``），entities/relations 透传给 schema
    留作扩展。
    """

    if not isinstance(raw, dict):
        return QueryHits(raw_context=str(raw) if raw is not None else None)

    if raw.get("status") != "success":
        return QueryHits(raw_context=str(raw.get("message") or ""))

    data = raw.get("data") or {}
    chunks_raw = data.get("chunks") or []
    entities_raw = data.get("entities") or []
    relations_raw = data.get("relationships") or []
    references_raw = data.get("references") or []

    ref_path_by_id: dict[str, str] = {}
    for ref in references_raw:
        if isinstance(ref, dict):
            rid = ref.get("reference_id")
            fp = ref.get("file_path")
            if rid and fp:
                ref_path_by_id[str(rid)] = str(fp)

    chunks: list[RetrievedChunk] = []
    for c in chunks_raw:
        if not isinstance(c, dict):
            continue
        meta: dict[str, Any] = {k: v for k, v in c.items() if k != "content"}
        fp = c.get("file_path") or ref_path_by_id.get(str(c.get("reference_id") or ""), "")
        if fp and "file_path" not in meta:
            meta["file_path"] = fp
        meta.update(_parse_courseware_path(fp))
        chunks.append(RetrievedChunk(
            chunk_id=str(c.get("chunk_id") or ""),
            content=str(c.get("content") or ""),
            score=_optional_float(c.get("score") or c.get("similarity")),
            metadata=meta,
        ))

    entities = [
        RetrievedEntity(
            entity_id=str(e.get("entity_name") or e.get("id") or ""),
            entity_type=e.get("entity_type") or e.get("type"),
            description=e.get("description"),
        )
        for e in entities_raw if isinstance(e, dict)
    ]
    relations: list[RetrievedRelation] = []
    for r in relations_raw:
        if not isinstance(r, dict):
            continue
        kw = r.get("keywords")
        if isinstance(kw, str):
            keywords = [s.strip() for s in kw.split(",") if s.strip()]
        elif isinstance(kw, list):
            keywords = [str(s) for s in kw]
        else:
            keywords = []
        relations.append(RetrievedRelation(
            src_id=str(r.get("src_id") or r.get("source") or ""),
            tgt_id=str(r.get("tgt_id") or r.get("target") or ""),
            description=r.get("description"),
            keywords=keywords,
        ))
    return QueryHits(entities=entities, relations=relations, chunks=chunks)


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
    raw = await rag.aquery_data(query, param=param)
    return _coerce_data(raw)


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
