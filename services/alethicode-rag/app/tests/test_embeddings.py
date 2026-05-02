"""Pin the embedding-dim-2048 contract.

If the wrapper accidentally hands LightRAG the decorated `openai_embed`
(which is wrap_embedding_func_with_attrs(embedding_dim=1536)), pgvector
tables will be created with the wrong column dimension and every insert
will fail. This test guards against that regression.
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
