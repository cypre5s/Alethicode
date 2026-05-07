"""锁定 LLM wrapper 必须保留的两个关键行为。

关键词抽取 schema 必须改写为 DeepSeek 可接受的 `json_object`；非关键词调用中的
Pydantic schema 必须被丢弃，避免上游 400。
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
    # 非关键词抽取的 Pydantic schema 会被丢弃，避免 DeepSeek 直接 400。
    assert "response_format" not in captured["kwargs"]


@pytest.mark.asyncio
async def test_keyword_schema_triggers_rewrite_even_without_explicit_flag(monkeypatch) -> None:
    """当 LightRAG 漏传 keyword_extraction=True 时仍按类名识别并改写。"""
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
