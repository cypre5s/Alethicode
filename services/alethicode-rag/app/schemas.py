"""Public request/response shapes for alethicode-rag HTTP endpoints.

These mirror the data contract the Java `RagServiceClient` will speak.
Defining them here means the FastAPI layer enforces the schema and the
contract tests on the Java side can pin against the JSON form rendered
by `model_dump_json()` — no hand-rolled JSON in either side.
"""

from __future__ import annotations

from enum import Enum
from typing import Any, Optional

from pydantic import BaseModel, Field


class EntityType(str, Enum):
    COURSEWARE_PAGE = "courseware-page"
    NOTEBOOK = "notebook"
    MEMORY = "memory"


class IndexRequest(BaseModel):
    entity_id: str = Field(..., description="Business id, e.g. page id / notebook id / memory_key")
    content: str = Field(..., min_length=1, description="Text to embed and KG-extract")
    metadata: dict[str, Any] = Field(
        default_factory=dict,
        description="Arbitrary business metadata; returned verbatim in query hits",
    )


class IndexAccepted(BaseModel):
    indexing_task_id: str
    entity_type: str
    entity_id: str


class CoursewareQueryRequest(BaseModel):
    language_pack_id: Optional[int] = None
    query: str = Field(..., min_length=1)
    kc_ids: list[str] = Field(default_factory=list)
    top_k: int = Field(default=8, ge=1, le=50)


class SimilarErrorQueryRequest(BaseModel):
    user_id: int
    current_problem_id: Optional[int] = None
    error_taxonomy: Optional[str] = None
    query: str = Field(..., min_length=1)
    top_k: int = Field(default=5, ge=1, le=50)


class MemoryQueryRequest(BaseModel):
    user_id: int
    current_kcs: list[str] = Field(default_factory=list)
    error_context: Optional[str] = None
    query: str = Field(..., min_length=1)
    top_k: int = Field(default=5, ge=1, le=50)


class TransferQueryRequest(BaseModel):
    current_problem_id: int
    kc_ids: list[str] = Field(default_factory=list)
    query: str = Field(..., min_length=1)
    top_k: int = Field(default=5, ge=1, le=50)


class RetrievedChunk(BaseModel):
    chunk_id: str
    content: str
    score: Optional[float] = None
    metadata: dict[str, Any] = Field(default_factory=dict)


class RetrievedEntity(BaseModel):
    entity_id: str
    entity_type: Optional[str] = None
    description: Optional[str] = None


class RetrievedRelation(BaseModel):
    src_id: str
    tgt_id: str
    description: Optional[str] = None
    keywords: list[str] = Field(default_factory=list)


class QueryHits(BaseModel):
    """Structured RAG hits shared by all four query endpoints."""

    entities: list[RetrievedEntity] = Field(default_factory=list)
    relations: list[RetrievedRelation] = Field(default_factory=list)
    chunks: list[RetrievedChunk] = Field(default_factory=list)
    raw_context: Optional[str] = Field(
        default=None,
        description="Raw context string LightRAG built when only_need_context=True",
    )


class HealthStatus(BaseModel):
    status: str
    postgres: str
    memgraph: str
    rag_initialized: bool
    llm_smoke_ok: bool