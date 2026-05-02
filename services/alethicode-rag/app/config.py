"""Runtime settings for alethicode-rag.

All settings are loaded from environment variables. Defaults match the
project's `deploy/.env.example` and `backend/.env` conventions; they are
production-safe except `INTERNAL_TOKEN`, which must be set in real
deployments.
"""

from __future__ import annotations

from functools import lru_cache
from typing import Optional

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class RagSettings(BaseSettings):
    """Settings injected from env. See `deploy/.env.example` for canonical names."""

    model_config = SettingsConfigDict(
        env_file=None,  # Container env only; do not auto-read a .env file
        env_prefix="",
        case_sensitive=False,
        extra="ignore",
    )

    # === Server ===
    host: str = Field(default="0.0.0.0", alias="RAG_HOST")
    port: int = Field(default=8200, alias="RAG_PORT")
    internal_token: str = Field(default="dev-internal-key", alias="RAG_INTERNAL_TOKEN")
    internal_previous_token: str = Field(default="", alias="RAG_INTERNAL_PREVIOUS_TOKEN")
    working_dir: str = Field(default="/tmp/alethicode-rag-workdir", alias="RAG_WORKING_DIR")

    # === PostgreSQL (shared with backend & tutor_graph) ===
    postgres_host: str = Field(default="postgres", alias="POSTGRES_HOST")
    postgres_port: int = Field(default=5432, alias="POSTGRES_PORT")
    postgres_user: str = Field(default="onlinejudge", alias="POSTGRES_USER")
    postgres_password: str = Field(default="", alias="POSTGRES_PASSWORD")
    postgres_database: str = Field(default="alethicode", alias="POSTGRES_DATABASE")
    postgres_max_connections: int = Field(default=12, alias="POSTGRES_MAX_CONNECTIONS")

    # === Memgraph (graph storage) ===
    memgraph_uri: str = Field(default="bolt://memgraph:7687", alias="MEMGRAPH_URI")
    memgraph_username: str = Field(default="", alias="MEMGRAPH_USERNAME")
    memgraph_password: str = Field(default="", alias="MEMGRAPH_PASSWORD")
    memgraph_database: str = Field(default="memgraph", alias="MEMGRAPH_DATABASE")
    memgraph_workspace: str = Field(default="alethicode", alias="MEMGRAPH_WORKSPACE")

    # === Vector index tuning (pgvector HNSW_HALFVEC) ===
    # 智谱 embedding-3 = 2048 dim; pgvector HNSW caps at 2000 dim per
    # `pgvector` docs (https://github.com/pgvector/pgvector#hnsw):
    #   "max dimensions for HNSW: 2000 for vector / 4000 for halfvec".
    # Smoke-test on 2026-04-28 confirmed plain HNSW fails with
    #   "column cannot have more than 2000 dimensions for hnsw index".
    # HNSW_HALFVEC stores the same embedding as half-precision floats
    # (~50% storage saving, retrieval quality difference is below noise
    # floor for 2048-dim cosine retrieval) and is the standard fix.
    # pgvector >= 0.7.0 is required; the deploy/docker-compose.yml uses
    # pgvector/pgvector:pg16 which ships 0.8.x — already satisfied.
    vector_index_type: str = Field(default="HNSW_HALFVEC", alias="POSTGRES_VECTOR_INDEX_TYPE")
    hnsw_m: int = Field(default=16, alias="POSTGRES_HNSW_M")
    hnsw_ef: int = Field(default=200, alias="POSTGRES_HNSW_EF")

    # === LLM (DeepSeek-compatible) ===
    llm_api_key: str = Field(default="", alias="OPENAI_API_KEY")
    llm_base_url: str = Field(default="https://api.deepseek.com", alias="LLM_BASE_URL")
    llm_model: str = Field(default="deepseek-v4-flash", alias="LLM_MODEL")
    llm_max_tokens: int = Field(default=8192, alias="LLM_MAX_TOKENS")
    llm_timeout_seconds: int = Field(default=300, alias="LLM_API_TIMEOUT_SECONDS")

    # === Embedding (Zhipu / 智谱 embedding-3) ===
    embedding_api_key: str = Field(default="", alias="EMBEDDING_API_KEY")
    embedding_base_url: str = Field(default="https://open.bigmodel.cn/api/paas/v4", alias="EMBEDDING_BASE_URL")
    embedding_model: str = Field(default="embedding-3", alias="EMBEDDING_MODEL")
    embedding_dim: int = Field(default=2048, alias="EMBEDDING_DIM")
    embedding_max_token_size: int = Field(default=8192, alias="EMBEDDING_MAX_TOKEN_SIZE")

    # === LightRAG storage backend selection ===
    # Memgraph for graph (Q1 decision: avoid PG+AGE 12h migration / 3-5min queries).
    # KV / Vector / DocStatus stay on PG to leverage the existing pgvector image.
    kv_storage: str = Field(default="PGKVStorage", alias="LIGHTRAG_KV_STORAGE")
    vector_storage: str = Field(default="PGVectorStorage", alias="LIGHTRAG_VECTOR_STORAGE")
    doc_status_storage: str = Field(default="PGDocStatusStorage", alias="LIGHTRAG_DOC_STATUS_STORAGE")
    graph_storage: str = Field(default="MemgraphStorage", alias="LIGHTRAG_GRAPH_STORAGE")

    # === Operational ===
    log_level: str = Field(default="INFO", alias="RAG_LOG_LEVEL")
    enable_request_logging: bool = Field(default=True, alias="RAG_ENABLE_REQUEST_LOGGING")

    @property
    def postgres_dsn(self) -> str:
        return (
            f"postgresql://{self.postgres_user}:{self.postgres_password}"
            f"@{self.postgres_host}:{self.postgres_port}/{self.postgres_database}"
        )


@lru_cache(maxsize=1)
def get_settings() -> RagSettings:
    """Cached singleton; tests can override via `get_settings.cache_clear()`."""
    return RagSettings()
