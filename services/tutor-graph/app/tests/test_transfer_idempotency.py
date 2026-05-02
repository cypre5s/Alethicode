"""Tests for transfer materialization idempotency — covers Bug 2's idempotency logic."""

from __future__ import annotations

import hashlib
import json

import pytest
from unittest.mock import AsyncMock, MagicMock

from app.nodes.transfer import materialize_transfer_problem_node


@pytest.mark.asyncio
async def test_materialize_idempotency_key_format():
    """The idempotency key must follow `{session}:{run}:transfer:materialize:v1`."""
    java = MagicMock()
    java.create_transfer_problem = AsyncMock(return_value={
        "problem_id": 99, "problem_display_id": "T1-99", "temporary_problem": True,
    })

    state = {
        "session_id": "twf_abc123",
        "run_id": "run_xyz",
        "user_id": 1,
        "problem_id": 1,
        "language": "Python3",
        "node_outputs": {
            "transfer_draft": {"title": "T", "description": "D"},
        },
    }

    await materialize_transfer_problem_node(state, java_client=java)
    called_payload = java.create_transfer_problem.call_args[0][0]
    assert called_payload["idempotency_key"] == "twf_abc123:run_xyz:transfer:materialize:v1"


@pytest.mark.asyncio
async def test_materialize_request_hash_deterministic():
    """Same draft → same hash (so same key + same hash means cache hit)."""
    java = MagicMock()
    java.create_transfer_problem = AsyncMock(return_value={
        "problem_id": 1, "problem_display_id": "T1-1", "temporary_problem": True,
    })

    draft = {"title": "X", "description": "Y", "samples": [{"input": "1", "output": "1"}]}
    expected_hash = hashlib.sha256(
        json.dumps(draft, sort_keys=True, ensure_ascii=False).encode()
    ).hexdigest()

    state = {
        "session_id": "s", "run_id": "r",
        "user_id": 1, "problem_id": 1, "language": "Python3",
        "node_outputs": {"transfer_draft": draft},
    }
    await materialize_transfer_problem_node(state, java_client=java)
    payload = java.create_transfer_problem.call_args[0][0]
    assert payload["request_hash"] == expected_hash


@pytest.mark.asyncio
async def test_materialize_missing_draft_fails_fast():
    java = MagicMock()
    state = {
        "session_id": "s", "run_id": "r",
        "user_id": 1, "problem_id": 1, "language": "Python3",
        "node_outputs": {},
    }
    result = await materialize_transfer_problem_node(state, java_client=java)
    assert result["runtime_state"] == "FAILED"
    assert result["failure_bucket"] == "INSUFFICIENT_EVIDENCE"
    java.create_transfer_problem.assert_not_called()


@pytest.mark.asyncio
async def test_materialize_tool_error_bubbles_as_system_error():
    java = MagicMock()
    java.create_transfer_problem = AsyncMock(side_effect=RuntimeError("upstream 500"))

    state = {
        "session_id": "s", "run_id": "r",
        "user_id": 1, "problem_id": 1, "language": "Python3",
        "node_outputs": {"transfer_draft": {"title": "T", "description": "D"}},
    }
    result = await materialize_transfer_problem_node(state, java_client=java)
    assert result["runtime_state"] == "FAILED"
    assert result["failure_bucket"] == "SYSTEM_ERROR"
