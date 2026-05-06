"""compact 节点测试：对话历史压缩的正常、边界、失败场景。"""

from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest

from app.nodes.compact import COMPACT_K, compact_node


@pytest.mark.asyncio
async def test_compact_compresses_history_longer_than_k():
    """History > K: older messages replaced by a single system summary + recent K turns."""
    history = [
        {"role": "user", "content": f"msg-{i}"}
        for i in range(10)
    ]

    llm = MagicMock()
    llm.generate = AsyncMock(return_value="这是压缩后的摘要内容")

    state = {
        "client_event": "COMPACT",
        "current_phase": "CODING",
        "event_data": {},
        "node_outputs": {"chat": {"history": history, "content": "last"}},
        "evidence_pack": {},
    }

    result = await compact_node(state, llm_client=llm)

    new_history = result["node_outputs"]["chat"]["history"]
    assert new_history[0]["role"] == "system"
    assert "摘要" in new_history[0]["content"]
    assert len(new_history) == COMPACT_K + 1
    assert new_history[-1] == history[-1]
    llm.generate.assert_awaited_once()


@pytest.mark.asyncio
async def test_compact_noop_when_history_within_k():
    """History <= K: returned unchanged, LLM never called."""
    history = [
        {"role": "user", "content": f"msg-{i}"}
        for i in range(COMPACT_K)
    ]

    llm = MagicMock()
    llm.generate = AsyncMock()

    state = {
        "client_event": "COMPACT",
        "current_phase": "READING",
        "event_data": {},
        "node_outputs": {"chat": {"history": history}},
        "evidence_pack": {},
    }

    result = await compact_node(state, llm_client=llm)

    assert result["node_outputs"]["chat"]["history"] == history
    llm.generate.assert_not_awaited()


@pytest.mark.asyncio
async def test_compact_llm_failure_sets_failed_state():
    """LLM call failure -> runtime_state=FAILED."""
    history = [
        {"role": "user", "content": f"msg-{i}"}
        for i in range(10)
    ]

    llm = MagicMock()
    llm.generate = AsyncMock(side_effect=RuntimeError("LLM down"))

    state = {
        "client_event": "COMPACT",
        "current_phase": "CODING",
        "event_data": {},
        "node_outputs": {"chat": {"history": history}},
        "evidence_pack": {},
    }

    result = await compact_node(state, llm_client=llm)

    assert result["runtime_state"] == "FAILED"
    assert result["failure_bucket"] == "SYSTEM_ERROR"
    assert "Compact generation failed" in result["last_error"]
