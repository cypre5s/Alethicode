"""Ingest node — standardize event, init runtime state, generate trace_id."""

from __future__ import annotations

import uuid
from datetime import datetime, timezone

from app.graph.state import TutorGraphState


def ingest_event(state: TutorGraphState) -> TutorGraphState:
    event = state.get("client_event", "")
    if not event:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SCHEMA_VIOLATION",
            "last_error": "client_event is required",
        }

    language = state.get("language", "")
    if not language:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SCHEMA_VIOLATION",
            "last_error": "language is required",
        }

    now = datetime.now(timezone.utc).isoformat()
    trace_id = state.get("trace_id") or f"trace_{uuid.uuid4().hex[:16]}"
    event_data = state.get("event_data", {})

    node_outputs = dict(state.get("node_outputs", {}))
    node_outputs["last_event"] = {
        "event": event.upper(),
        "event_data": event_data,
        "ts": now,
    }

    return {
        **state,
        "client_event": event.upper(),
        "runtime_state": "RUNNING",
        "trace_id": trace_id,
        "node_outputs": node_outputs,
        "updated_at": now,
    }
