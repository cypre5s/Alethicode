"""DeepSeek LLM wrapper for LightRAG.

Why a wrapper layer is needed (validated in 2026-04-28 local demo):
- LightRAG's keyword extraction stage passes `response_format=GPTKeywordExtractionFormat`
  (a Pydantic schema). DeepSeek's `chat.completions.parse` returns
  HTTP 400 `This response_format type is unavailable now` for the configured
  `deepseek-v4-flash` deployment. We must intercept the schema and rewrite
  it to `{"type": "json_object"}`, then append explicit field instructions
  to the system prompt so the model still emits the expected
  `{"low_level_keywords": [...], "high_level_keywords": [...]}` shape.

We wrap `openai_complete_if_cache` from LightRAG, which already implements
the cache + retry + token accounting layer. Our wrapper only mutates the
kwargs before delegating.
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
    """Detect LightRAG's GPTKeywordExtractionFormat Pydantic schema.

    LightRAG passes the actual class; some integrations pass a string class
    name. We accept both. We deliberately do NOT import the symbol from
    LightRAG to keep this wrapper version-resilient.
    """
    if response_format is None:
        return False
    if isinstance(response_format, dict):
        # Already-OpenAI-shaped formats are passed through untouched.
        return False
    name = getattr(response_format, "__name__", None) or getattr(
        type(response_format), "__name__", None
    )
    if not name:
        return False
    return "Keyword" in str(name)


def build_llm_callable(settings: RagSettings):
    """Return an async callable matching LightRAG's `llm_model_func` contract."""

    async def llm_model_func(
        prompt: str,
        system_prompt: str | None = None,
        history_messages: list[dict[str, str]] | None = None,
        keyword_extraction: bool = False,  # LightRAG passes this on extract path
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
            # Drop unsupported Pydantic schemas silently rather than 400.
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

        # Normalize potential streaming wrappers / awaitables to a string.
        if inspect.isawaitable(result):  # pragma: no cover - defensive
            result = await result
        return str(result) if result is not None else ""

    return llm_model_func
