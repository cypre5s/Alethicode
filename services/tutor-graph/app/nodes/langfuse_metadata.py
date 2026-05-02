"""Helpers for attaching stable Langfuse metadata to tutor graph LLM calls."""

from __future__ import annotations

from typing import Any


def build_langfuse_metadata(state: dict[str, Any], node_name: str) -> dict[str, Any]:
    metadata: dict[str, Any] = {
        "service": "tutor_graph",
        "node_name": node_name,
    }
    for key in (
        "trace_id",
        "session_id",
        "thread_id",
        "run_id",
        "user_id",
        "problem_id",
        "language",
        "current_phase",
        "client_event",
    ):
        value = state.get(key)
        if value is not None and value != "":
            metadata[key] = value
    if state.get("current_phase"):
        metadata["phase"] = state["current_phase"]
    return metadata
