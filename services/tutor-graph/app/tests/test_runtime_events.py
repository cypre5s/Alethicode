"""Tests for runtime event envelope and enum."""

from __future__ import annotations

from app.graph.runtime_events import ServerEvent, build_runtime_event


def test_build_runtime_event_has_required_fields():
    evt = build_runtime_event(
        server_event=ServerEvent.TASK_STARTED,
        session_id="s1", run_id="r1", thread_id="t1",
        client_event="READING",
    )
    required = {"type", "session_id", "run_id", "thread_id", "server_event", "timestamp", "data"}
    assert required.issubset(evt.keys())
    assert evt["type"] == "runtime_event"
    assert evt["server_event"] == "TASK_STARTED"


def test_all_server_events_present():
    """Ensure the contract's 13 standard events are all declared."""
    expected = {
        "TASK_QUEUED", "TASK_STARTED", "TASK_PROGRESS",
        "TOOL_CALL_STARTED", "TOOL_CALL_COMPLETED", "CARD_GENERATED",
        "APPROVAL_REQUESTED", "APPROVAL_RESOLVED",
        "TASK_INTERRUPTED", "TASK_RESTORING",
        "TASK_COMPLETED", "TASK_FAILED", "TASK_EXPIRED",
    }
    actual = {e.value for e in ServerEvent}
    assert expected == actual


def test_build_runtime_event_null_data_coerced_to_empty_dict():
    evt = build_runtime_event(server_event=ServerEvent.TASK_COMPLETED, data=None)
    assert evt["data"] == {}
