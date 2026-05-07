"""Fail-fast 读取 tutor-graph 必需配置。"""

from __future__ import annotations

import os


_CHECKPOINTER_MODES = {"postgres", "memory"}


def _require(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


CHECKPOINTER_MODE: str = os.environ.get("TUTOR_GRAPH_CHECKPOINTER", "postgres").strip().lower()
if CHECKPOINTER_MODE not in _CHECKPOINTER_MODES:
    raise RuntimeError(
        f"Invalid TUTOR_GRAPH_CHECKPOINTER={CHECKPOINTER_MODE!r}; "
        f"expected one of {sorted(_CHECKPOINTER_MODES)}"
    )

# memory 模式仅供单测使用，不要求 PostgreSQL URI。
DATABASE_URI: str = (
    _require("TUTOR_GRAPH_DATABASE_URI")
    if CHECKPOINTER_MODE == "postgres"
    else os.environ.get("TUTOR_GRAPH_DATABASE_URI", "")
)

JAVA_TOOL_BASE_URL: str = _require("TUTOR_GRAPH_JAVA_TOOL_BASE_URL")
INTERNAL_SERVICE_KEY: str = _require("TUTOR_GRAPH_INTERNAL_SERVICE_KEY")
INTERNAL_SERVICE_PREVIOUS_KEY: str = os.environ.get("TUTOR_GRAPH_INTERNAL_SERVICE_PREVIOUS_KEY", "")

LLM_PROVIDER: str = os.environ.get("TUTOR_GRAPH_LLM_PROVIDER", "openai")
LLM_MODEL: str = os.environ.get("TUTOR_GRAPH_LLM_MODEL", "gpt-4o")
LLM_API_KEY: str = os.environ.get("TUTOR_GRAPH_LLM_API_KEY", "")
LLM_BASE_URL: str = os.environ.get("TUTOR_GRAPH_LLM_BASE_URL", "")
LLM_TEMPERATURE: float = float(os.environ.get("TUTOR_GRAPH_LLM_TEMPERATURE", "0.3"))

REACT_ENABLED: bool = os.environ.get("TUTOR_GRAPH_REACT_ENABLED", "false").lower() == "true"
