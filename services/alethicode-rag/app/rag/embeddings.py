"""为 LightRAG 封装 2048 维智谱 embedding-3。"""

from __future__ import annotations

from functools import partial
from typing import Any, Callable, Coroutine

from lightrag.llm.openai import openai_embed
from lightrag.utils import EmbeddingFunc

from ..config import RagSettings


def build_embedding_func(settings: RagSettings) -> EmbeddingFunc:
    """返回配置为智谱 embedding-3 的 LightRAG `EmbeddingFunc`。"""

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
