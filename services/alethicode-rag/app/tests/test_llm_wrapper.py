"""Pin the two non-obvious behaviours the LLM wrapper MUST preserve.

These are the exact bugs the 2026-04-28 local demo discovered:
  1. LightRAG sends `response_format=GPTKeywordExtractionFormat`. DeepSeek
     rejects Pydantic schemas with HTTP 400 — wrapper must rewrite to
     `{"type": "json_object"}` AND inject the field instructions in the
     system prompt so the model still emits the expected shape.
  2. Non-keyword calls must NOT have a Pydantic schema sneak through to
     DeepSeek; the wrapper drops the unsupported value silently.
"""

from __future__ import annotations

import pytest

from app.config import RagSettings
from app.rag import llm as llm_module
from app.rag.llm import _looks_like_keyword_extraction_schema, build_llm_callable


class _DummyKeywordSchema:
    __name__ = "GPTKeywordExtractionFormat"


class _UnrelatedSchema:
    __name__ = "OtherFormat"


def test_looks_like_keyword_extraction_schema_recognises_class() -> None:
    assert _looks_like_keyword_extraction_schema(_DummyKeywordSchema) is True
    assert _looks_like_keyword_extraction_schema(_DummyKeywordSchema()) is True


def test_looks_like_keyword_extraction_schema_ignores_dict_and_unrelated() -> None:
    assert _looks_like_keyword_extraction_schema({"type": "json_object"}) is False
    assert _looks_like_keyword_extraction_schema(_UnrelatedSchema) is False
    assert _looks_like_keyword_extraction_schema(None) is False


@pytest.mark.asyncio
async def test_keyword_extraction_path_rewrites_response_format(monkeypatch) -> None:
    captured: dict = {}

    async def _fake_complete(
        model, prompt, system_prompt=None, history_messages=None, api_key=None, base_url=None, **kwargs
    ):
        captured["model"] = model
        captured["prompt"] = prompt
        captured["system_prompt"] = system_prompt
        captured["history_messages"] = history_messages
        captured["kwargs"] = kwargs
        return '{"low_level_keywords": [], "high_level_keywords": []}'

    monkeypatch.setattr(llm_module, "openai_complete_if_cache", _fake_complete)

    settings = RagSettings(LLM_MODEL="deepseek-v4-flash", OPENAI_API_KEY="dummy")
    func = build_llm_callable(settings)

    out = await func(
        "extract keywords",
        system_prompt="you are a keyword extractor",
        keyword_extraction=True,
    )

    assert "low_level_keywords" in out
    assert captured["kwargs"]["response_format"] == {"type": "json_object"}
    assert "low_level_keywords" in captured["system_prompt"]
    assert "high_level_keywords" in captured["system_prompt"]


@pytest.mark.asyncio
async def test_non_keyword_call_drops_pydantic_schema(monkeypatch) -> None:
    captured: dict = {}

    async def _fake_complete(model, prompt, **kwargs):
        captured["kwargs"] = kwargs
        return "ok"

    monkeypatch.setattr(llm_module, "openai_complete_if_cache", _fake_complete)

    settings = RagSettings(LLM_MODEL="deepseek-v4-flash", OPENAI_API_KEY="dummy")
    func = build_llm_callable(settings)

    out = await func(
        "summarise",
        system_prompt="you are a summariser",
        response_format=_UnrelatedSchema,
        keyword_extraction=False,
    )

    assert out == "ok"
    # Non-keyword Pydantic schemas (deepseek can't handle them) are dropped silently.
    assert "response_format" not in captured["kwargs"]


@pytest.mark.asyncio
async def test_keyword_schema_triggers_rewrite_even_without_explicit_flag(monkeypatch) -> None:
    """If LightRAG passes a keyword schema but forgets keyword_extraction=True,
    we still detect by class name and rewrite — this is the safer default."""
    captured: dict = {}

    async def _fake_complete(model, prompt, system_prompt=None, **kwargs):
        captured["system_prompt"] = system_prompt
        captured["kwargs"] = kwargs
        return '{"low_level_keywords": [], "high_level_keywords": []}'

    monkeypatch.setattr(llm_module, "openai_complete_if_cache", _fake_complete)

    settings = RagSettings(LLM_MODEL="deepseek-v4-flash", OPENAI_API_KEY="dummy")
    func = build_llm_callable(settings)

    await func(
        "extract",
        system_prompt="extractor",
        response_format=_DummyKeywordSchema,
        keyword_extraction=False,
    )
    assert captured["kwargs"]["response_format"] == {"type": "json_object"}
    assert "low_level_keywords" in captured["system_prompt"]


@pytest.mark.asyncio
async def test_non_keyword_call_passes_through_dict_response_format(monkeypatch) -> None:
    captured: dict = {}

    async def _fake_complete(model, prompt, **kwargs):
        captured["kwargs"] = kwargs
        return "ok"

    monkeypatch.setattr(llm_module, "openai_complete_if_cache", _fake_complete)

    settings = RagSettings(LLM_MODEL="deepseek-v4-flash", OPENAI_API_KEY="dummy")
    func = build_llm_callable(settings)

    await func(
        "summarise",
        system_prompt=None,
        response_format={"type": "json_object"},
    )
    assert captured["kwargs"]["response_format"] == {"type": "json_object"}
