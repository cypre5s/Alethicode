"""管理 alethicode-rag 的运行时配置。"""

from __future__ import annotations

from functools import lru_cache
from typing import Optional

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class RagSettings(BaseSettings):
    """从环境变量读取 RAG 服务配置。"""

    model_config = SettingsConfigDict(
        env_file=None,  # 只读取容器环境变量，避免自动加载本地 .env
        env_prefix="",
        case_sensitive=False,
        extra="ignore",
    )

    # 服务入口
    host: str = Field(default="0.0.0.0", alias="RAG_HOST")
    port: int = Field(default=8200, alias="RAG_PORT")
    internal_token: str = Field(default="dev-internal-key", alias="RAG_INTERNAL_TOKEN")
    internal_previous_token: str = Field(default="", alias="RAG_INTERNAL_PREVIOUS_TOKEN")
    working_dir: str = Field(default="/tmp/alethicode-rag-workdir", alias="RAG_WORKING_DIR")

    # PostgreSQL：与后端和 tutor_graph 共用
    postgres_host: str = Field(default="postgres", alias="POSTGRES_HOST")
    postgres_port: int = Field(default=5432, alias="POSTGRES_PORT")
    postgres_user: str = Field(default="onlinejudge", alias="POSTGRES_USER")
    postgres_password: str = Field(default="", alias="POSTGRES_PASSWORD")
    postgres_database: str = Field(default="alethicode", alias="POSTGRES_DATABASE")
    postgres_max_connections: int = Field(default=12, alias="POSTGRES_MAX_CONNECTIONS")

    # Memgraph 图存储
    memgraph_uri: str = Field(default="bolt://memgraph:7687", alias="MEMGRAPH_URI")
    memgraph_username: str = Field(default="", alias="MEMGRAPH_USERNAME")
    memgraph_password: str = Field(default="", alias="MEMGRAPH_PASSWORD")
    memgraph_database: str = Field(default="memgraph", alias="MEMGRAPH_DATABASE")
    memgraph_workspace: str = Field(default="alethicode", alias="MEMGRAPH_WORKSPACE")

    # 向量索引：2048 维 embedding 超过 pgvector 普通 HNSW 2000 维上限，必须使用 halfvec。
    vector_index_type: str = Field(default="HNSW_HALFVEC", alias="POSTGRES_VECTOR_INDEX_TYPE")
    hnsw_m: int = Field(default=16, alias="POSTGRES_HNSW_M")
    hnsw_ef: int = Field(default=200, alias="POSTGRES_HNSW_EF")

    # DeepSeek 兼容 LLM
    llm_api_key: str = Field(default="", alias="OPENAI_API_KEY")
    llm_base_url: str = Field(default="https://api.deepseek.com", alias="LLM_BASE_URL")
    llm_model: str = Field(default="deepseek-v4-flash", alias="LLM_MODEL")
    llm_max_tokens: int = Field(default=8192, alias="LLM_MAX_TOKENS")
    llm_timeout_seconds: int = Field(default=300, alias="LLM_API_TIMEOUT_SECONDS")

    # 智谱 embedding-3
    embedding_api_key: str = Field(default="", alias="EMBEDDING_API_KEY")
    embedding_base_url: str = Field(default="https://open.bigmodel.cn/api/paas/v4", alias="EMBEDDING_BASE_URL")
    embedding_model: str = Field(default="embedding-3", alias="EMBEDDING_MODEL")
    embedding_dim: int = Field(default=2048, alias="EMBEDDING_DIM")
    embedding_max_token_size: int = Field(default=8192, alias="EMBEDDING_MAX_TOKEN_SIZE")

    # LightRAG 存储后端：图走 Memgraph，KV / Vector / DocStatus 继续复用 PostgreSQL。
    kv_storage: str = Field(default="PGKVStorage", alias="LIGHTRAG_KV_STORAGE")
    vector_storage: str = Field(default="PGVectorStorage", alias="LIGHTRAG_VECTOR_STORAGE")
    doc_status_storage: str = Field(default="PGDocStatusStorage", alias="LIGHTRAG_DOC_STATUS_STORAGE")
    graph_storage: str = Field(default="MemgraphStorage", alias="LIGHTRAG_GRAPH_STORAGE")

    # 运维配置
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
    """返回可被测试清理缓存的配置单例。"""
    return RagSettings()
