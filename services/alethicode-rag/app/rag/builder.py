"""LightRAG singleton factory.

A single LightRAG instance is created lazily on first use and reused for
the lifetime of the FastAPI process. LightRAG owns its own connection
pools (PG via asyncpg + Memgraph via neo4j-bolt) and pipeline-status
locks; instantiating more than one in the same process duplicates state
and causes write conflicts.
"""

from __future__ import annotations

import asyncio
import logging
import os
import pathlib
from typing import Optional

from lightrag import LightRAG, QueryParam
from lightrag.kg.shared_storage import initialize_pipeline_status

from ..config import RagSettings, get_settings
from .embeddings import build_embedding_func
from .llm import build_llm_callable

logger = logging.getLogger(__name__)


_RAG: Optional[LightRAG] = None
_RAG_LOCK = asyncio.Lock()


def _apply_storage_env(settings: RagSettings) -> None:
    """Push storage config into the env LightRAG inspects on init."""
    os.environ.setdefault("POSTGRES_HOST", settings.postgres_host)
    os.environ.setdefault("POSTGRES_PORT", str(settings.postgres_port))
    os.environ.setdefault("POSTGRES_USER", settings.postgres_user)
    os.environ.setdefault("POSTGRES_PASSWORD", settings.postgres_password)
    os.environ.setdefault("POSTGRES_DATABASE", settings.postgres_database)
    os.environ.setdefault(
        "POSTGRES_MAX_CONNECTIONS", str(settings.postgres_max_connections)
    )
    os.environ.setdefault("POSTGRES_VECTOR_INDEX_TYPE", settings.vector_index_type)
    os.environ.setdefault("POSTGRES_HNSW_M", str(settings.hnsw_m))
    os.environ.setdefault("POSTGRES_HNSW_EF", str(settings.hnsw_ef))

    os.environ.setdefault("MEMGRAPH_URI", settings.memgraph_uri)
    if settings.memgraph_username:
        os.environ.setdefault("MEMGRAPH_USERNAME", settings.memgraph_username)
    if settings.memgraph_password:
        os.environ.setdefault("MEMGRAPH_PASSWORD", settings.memgraph_password)
    os.environ.setdefault("MEMGRAPH_DATABASE", settings.memgraph_database)
    os.environ.setdefault("MEMGRAPH_WORKSPACE", settings.memgraph_workspace)


async def build_rag(settings: RagSettings | None = None) -> LightRAG:
    settings = settings or get_settings()

    pathlib.Path(settings.working_dir).mkdir(parents=True, exist_ok=True)
    _apply_storage_env(settings)

    rag = LightRAG(
        working_dir=settings.working_dir,
        workspace=settings.memgraph_workspace,
        llm_model_func=build_llm_callable(settings),
        llm_model_name=settings.llm_model,
        embedding_func=build_embedding_func(settings),
        kv_storage=settings.kv_storage,
        vector_storage=settings.vector_storage,
        doc_status_storage=settings.doc_status_storage,
        graph_storage=settings.graph_storage,
        default_llm_timeout=settings.llm_timeout_seconds,
        # Backfill 加速：LightRAG 默认 llm_model_max_async=8,
        # max_parallel_insert=2，KG 抽取调 LLM 串行严重，整批 561 doc 要跑 4-5h。
        # Deepseek-v4-flash TPM/RPM 上限较宽（"flash" 系列高吞吐型号），把 LLM
        # 并发提到 16，文档并行处理提到 6，预期 ~3× 加速；embedding 并发同步提
        # 到 24，避免成为 KG 抽取流水线瓶颈。
        llm_model_max_async=int(os.environ.get("LIGHTRAG_LLM_MAX_ASYNC", "16")),
        embedding_func_max_async=int(os.environ.get("LIGHTRAG_EMBEDDING_MAX_ASYNC", "24")),
        max_parallel_insert=int(os.environ.get("LIGHTRAG_MAX_PARALLEL_INSERT", "6")),
    )

    await rag.initialize_storages()
    await initialize_pipeline_status()
    return rag


async def get_rag() -> LightRAG:
    global _RAG
    if _RAG is not None:
        return _RAG
    async with _RAG_LOCK:
        if _RAG is None:
            _RAG = await build_rag()
    return _RAG


async def shutdown_rag() -> None:
    global _RAG
    if _RAG is None:
        return
    try:
        await _RAG.finalize_storages()
    except Exception as exc:  # pragma: no cover - defensive shutdown
        logger.warning("rag finalize failed: %s", exc)
    _RAG = None


def default_query_param() -> QueryParam:
    """The single QueryParam shape every internal endpoint must use.

    `only_need_context=True` means LightRAG returns the structured
    entities + relations + chunks blob and skips the final LLM "generate
    answer" call. The Java tutor / agent layer assembles the actual
    prompt. This drops query LLM calls from 2 → 1 and shaves off the
    second-scale tail latency.
    """
    return QueryParam(mode="mix", only_need_context=True, enable_rerank=False)
