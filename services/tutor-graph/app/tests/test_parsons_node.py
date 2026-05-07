"""测试 parsons_node 的路由、载荷提取和失败处理。"""

from __future__ import annotations

import pytest

from app.nodes.parsons import parsons_node


class _FakeJavaClient:
    def __init__(self, *, response=None, raise_on_dispatch=None):
        self._response = response or {}
        self._raise_on_dispatch = raise_on_dispatch
        self.last_payload = None

    async def dispatch_parsons(self, payload: dict) -> dict:
        self.last_payload = payload
        if self._raise_on_dispatch:
            raise self._raise_on_dispatch
        return self._response


@pytest.fixture
def base_state():
    return {
        "user_id": 7,
        "problem_id": 101,
        "session_id": "twf-1",
        "event_data": {
            "source_card_id": "card-x",
            "fsrs_origin": "pkg-9",
        },
    }


async def test_successful_dispatch_writes_parsons_output(base_state):
    client = _FakeJavaClient(response={
        "card_payload": {
            "parsons_session_id": "ps-abc",
            "fading_level": 2,
            "blocks": [{"id": "B0", "code": "a=1", "indent": 0}],
            "distractors": [],
            "mastery_snapshot": {"routing": {}},
            "instructions": "排序",
        }
    })
    result = await parsons_node(base_state, java_client=client)

    assert "parsons" in result["node_outputs"]
    assert result["node_outputs"]["parsons"]["parsons_session_id"] == "ps-abc"
    assert result["node_outputs"]["parsons"]["fading_level"] == 2
    assert client.last_payload["user_id"] == 7
    assert client.last_payload["fsrs_origin"] == "pkg-9"


async def test_dispatch_failure_sets_failed_state(base_state):
    client = _FakeJavaClient(raise_on_dispatch=RuntimeError("server down"))
    result = await parsons_node(base_state, java_client=client)

    assert result["runtime_state"] == "FAILED"
    assert result["failure_bucket"] == "SYSTEM_ERROR"
    assert "server down" in result["last_error"]


async def test_empty_response_sets_schema_violation(base_state):
    client = _FakeJavaClient(response={"card_payload": {}})
    result = await parsons_node(base_state, java_client=client)

    assert result["runtime_state"] == "FAILED"
    assert result["failure_bucket"] == "SCHEMA_VIOLATION"


async def test_override_fading_level_forwarded(base_state):
    base_state["event_data"]["override_fading_level"] = 1
    client = _FakeJavaClient(response={
        "card_payload": {
            "parsons_session_id": "ps-x",
            "fading_level": 1,
            "blocks": [{"id": "B0"}, {"id": "B1"}],
            "distractors": [],
            "mastery_snapshot": {},
            "instructions": "",
        }
    })
    result = await parsons_node(base_state, java_client=client)

    assert client.last_payload["override_fading_level"] == 1
    assert result["node_outputs"]["parsons"]["fading_level"] == 1


async def test_flat_response_without_card_payload_wrapper(base_state):
    client = _FakeJavaClient(response={
        "parsons_session_id": "ps-flat",
        "fading_level": 0,
        "blocks": [{"id": "B0"}, {"id": "B1"}],
        "distractors": [],
        "mastery_snapshot": {},
        "instructions": "flat",
    })
    result = await parsons_node(base_state, java_client=client)

    assert "parsons" in result["node_outputs"]
    assert result["node_outputs"]["parsons"]["parsons_session_id"] == "ps-flat"
