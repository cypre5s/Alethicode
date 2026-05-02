"""Zhipu embedding-3 (2048d) wrapper for LightRAG.

LightRAG ships `openai_embed` decorated with `@wrap_embedding_func_with_attrs(
embedding_dim=1536)`. If we hand the decorated function to LightRAG, it
will believe vectors are 1536d and the pgvector workspace tables will be
created with the wrong column dimension. The 2026-04-28 local demo
confirmed this with the exact error
`Embedding dimension mismatch detected: total elements (...) cannot be evenly
divided by expected dimension (1536)`.

The fix is to bind `openai_embed.func` (the raw inner coroutine) and wrap
it with our own `EmbeddingFunc(embedding_dim=2048, ...)` so LightRAG sees
the correct shape.
"""

from __future__ import annotations

from functools import partial
from typing import Any, Callable, Coroutine

from lightrag.llm.openai import openai_embed
from lightrag.utils import EmbeddingFunc

from ..config import RagSettings


def build_embedding_func(settings: RagSettings) -> EmbeddingFunc:
    """Return a LightRAG `EmbeddingFunc` configured for Zhipu embedding-3."""

    raw_inner: Callable[..., Coroutine[Any, Any, Any]] = openai_embed.func

    bound = partial(
        raw_inner,
        model=settings.embedding_model,
        api_key=settings.embedding_api_key,
        base_url=settings.embedding_base_url,
    )

    return EmbeddingFunc(
        embedding_dim=settings.embedding_dim,
        max_token_size=settings.embedding_max_token_size,
        func=bound,
        model_name=settings.embedding_model,
    )
