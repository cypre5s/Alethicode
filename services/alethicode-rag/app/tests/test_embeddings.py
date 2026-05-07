"""锁定 2048 维 embedding 契约。

LightRAG 自带 `openai_embed` 声明为 1536 维；本测试防止包装层误用它导致
pgvector 表维度错误。
"""

from __future__ import annotations

from app.config import RagSettings
from app.rag.embeddings import build_embedding_func


def test_embedding_func_reports_2048_dim() -> None:
    settings = RagSettings(
        EMBEDDING_API_KEY="dummy",
        EMBEDDING_MODEL="embedding-3",
        EMBEDDING_DIM=2048,
    )
    ef = build_embedding_func(settings)

    assert ef.embedding_dim == 2048
    assert ef.model_name == "embedding-3"
    assert ef.max_token_size > 0


def test_embedding_dim_can_be_overridden_for_other_providers() -> None:
    settings = RagSettings(
        EMBEDDING_API_KEY="dummy",
        EMBEDDING_MODEL="text-embedding-3-small",
        EMBEDDING_DIM=1536,
    )
    ef = build_embedding_func(settings)

    assert ef.embedding_dim == 1536
    assert ef.model_name == "text-embedding-3-small"
