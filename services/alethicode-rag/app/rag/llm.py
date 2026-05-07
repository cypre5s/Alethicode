"""适配 LightRAG 调用 DeepSeek 的 LLM 包装层。

LightRAG 关键词抽取会传入 Pydantic schema 格式的 `response_format`，而
DeepSeek 当前部署会拒绝该格式。本包装层只在委托给 LightRAG 缓存、重试和
token 统计逻辑前，把关键词抽取 schema 改写为 `json_object` 并补充字段约束。
"""

from __future__ import annotations

import inspect
import logging
from typing import Any

from lightrag.llm.openai import openai_complete_if_cache

from ..config import RagSettings

logger = logging.getLogger(__name__)


_KEYWORD_FIELDS_INSTRUCTION = (
    "\n\nReturn ONLY a JSON object with exactly two top-level fields:\n"
    "  - \"low_level_keywords\": array of concrete entity / event keyword strings\n"
    "  - \"high_level_keywords\": array of higher-order concept / category keyword strings\n"
    "No extra fields, no prose, no markdown fences."
)


def _looks_like_keyword_extraction_schema(response_format: Any) -> bool:
    """识别 LightRAG 的关键词抽取 schema。"""
    if response_format is None:
        return False
    if isinstance(response_format, dict):
        return False
    name = getattr(response_format, "__name__", None) or getattr(
        type(response_format), "__name__", None
    )
    if not name:
        return False
    return "Keyword" in str(name)


def build_llm_callable(settings: RagSettings):
    """返回符合 LightRAG `llm_model_func` 契约的异步调用函数。"""

    async def llm_model_func(
        prompt: str,
        system_prompt: str | None = None,
        history_messages: list[dict[str, str]] | None = None,
        keyword_extraction: bool = False,  # LightRAG 在抽取路径传入该标记
        **kwargs: Any,
    ) -> str:
        history_messages = history_messages or []
        response_format = kwargs.pop("response_format", None)

        if keyword_extraction or _looks_like_keyword_extraction_schema(response_format):
            kwargs["response_format"] = {"type": "json_object"}
            patched_system = (system_prompt or "") + _KEYWORD_FIELDS_INSTRUCTION
        else:
            patched_system = system_prompt
            if isinstance(response_format, dict):
                kwargs["response_format"] = response_format
            elif response_format is not None:
                logger.debug(
                    "llm_wrapper: dropping unsupported response_format %r for non-keyword call",
                    response_format,
                )

        result = await openai_complete_if_cache(
            settings.llm_model,
            prompt,
            system_prompt=patched_system,
            history_messages=history_messages,
            api_key=settings.llm_api_key,
            base_url=settings.llm_base_url,
            **kwargs,
        )

        if inspect.isawaitable(result):  # pragma: no cover - 防御性兼容
            result = await result
        return str(result) if result is not None else ""

    return llm_model_func
