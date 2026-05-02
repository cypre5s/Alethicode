"""LLM call abstraction — ReAct is off by default per project convention."""

from __future__ import annotations

import json
import os
from typing import Any


class LlmClient:
    """Thin wrapper around LLM provider. Supports replay mode for testing."""

    def __init__(
        self,
        *,
        provider: str = "openai",
        model: str = "gpt-4o",
        api_key: str = "",
        base_url: str = "",
        temperature: float = 0.3,
        replay_fixtures: dict[str, Any] | None = None,
    ) -> None:
        self._provider = provider
        self._model = model
        self._api_key = api_key
        self._base_url = base_url
        self._temperature = temperature
        self._replay = replay_fixtures
        self._llm = None

    def _get_llm(self):
        if self._llm is not None:
            return self._llm
        if self._provider == "openai":
            from langchain_openai import ChatOpenAI
            kwargs: dict = {"model": self._model, "temperature": self._temperature}
            if self._api_key:
                kwargs["api_key"] = self._api_key
            if self._base_url:
                kwargs["base_url"] = self._base_url
            self._llm = ChatOpenAI(**kwargs)
        else:
            raise RuntimeError(f"Unsupported LLM provider: {self._provider}")
        return self._llm

    def _build_langfuse_config(
        self,
        node_name: str,
        metadata: dict[str, Any] | None = None,
    ) -> dict[str, Any] | None:
        if not os.environ.get("LANGFUSE_PUBLIC_KEY") or not os.environ.get("LANGFUSE_SECRET_KEY"):
            return None
        try:
            from langfuse.langchain import CallbackHandler
        except Exception:
            return None
        langfuse_metadata = dict(metadata or {})
        if node_name:
            langfuse_metadata["langfuse_observation_name"] = node_name
            langfuse_metadata["langfuse_tags"] = [node_name]
        return {
            "callbacks": [CallbackHandler()],
            "metadata": langfuse_metadata,
        }

    async def generate(
        self,
        system_prompt: str,
        user_prompt: str,
        *,
        node_name: str = "",
        metadata: dict[str, Any] | None = None,
    ) -> str:
        return await self._generate(
            system_prompt,
            user_prompt,
            node_name=node_name,
            metadata=metadata,
        )

    async def _generate(
        self,
        system_prompt: str,
        user_prompt: str,
        *,
        node_name: str = "",
        metadata: dict[str, Any] | None = None,
        response_format: dict[str, Any] | None = None,
    ) -> str:
        if self._replay is not None:
            fixture = self._replay.get(node_name, {})
            if isinstance(fixture, str):
                return fixture
            return json.dumps(fixture, ensure_ascii=False)
        llm = self._get_llm()
        if response_format is not None:
            llm = llm.bind(response_format=response_format)
        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ]
        invoke_config = self._build_langfuse_config(node_name, metadata)
        response = await llm.ainvoke(messages, config=invoke_config) if invoke_config else await llm.ainvoke(messages)
        return response.content

    async def generate_json(
        self,
        system_prompt: str,
        user_prompt: str,
        *,
        node_name: str = "",
        metadata: dict[str, Any] | None = None,
    ) -> dict:
        raw = await self._generate(
            system_prompt,
            user_prompt,
            node_name=node_name,
            metadata=metadata,
            response_format={"type": "json_object"},
        )
        cleaned = raw.strip()
        if cleaned.startswith("```"):
            lines = cleaned.split("\n")
            lines = lines[1:] if lines[0].startswith("```") else lines
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            cleaned = "\n".join(lines)
        return json.loads(cleaned)
