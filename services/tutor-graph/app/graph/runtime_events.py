"""Standard server_event enum and emitter helpers."""

from __future__ import annotations

import enum
from datetime import datetime, timezone


class ServerEvent(str, enum.Enum):
    TASK_QUEUED = "TASK_QUEUED"
    TASK_STARTED = "TASK_STARTED"
    TASK_PROGRESS = "TASK_PROGRESS"
    TOOL_CALL_STARTED = "TOOL_CALL_STARTED"
    TOOL_CALL_COMPLETED = "TOOL_CALL_COMPLETED"
    CARD_GENERATED = "CARD_GENERATED"
    APPROVAL_REQUESTED = "APPROVAL_REQUESTED"
    APPROVAL_RESOLVED = "APPROVAL_RESOLVED"
    TASK_INTERRUPTED = "TASK_INTERRUPTED"
    TASK_RESTORING = "TASK_RESTORING"
    TASK_COMPLETED = "TASK_COMPLETED"
    TASK_FAILED = "TASK_FAILED"
    TASK_EXPIRED = "TASK_EXPIRED"


def build_runtime_event(
    *,
    server_event: ServerEvent,
    session_id: str = "",
    run_id: str = "",
    thread_id: str = "",
    checkpoint_id: str = "",
    trace_id: str = "",
    runtime_state: str = "",
    client_event: str = "",
    approval_state: str | None = None,
    failure_bucket: str | None = None,
    data: dict | None = None,
) -> dict:
    return {
        "type": "runtime_event",
        "session_id": session_id,
        "run_id": run_id,
        "thread_id": thread_id,
        "checkpoint_id": checkpoint_id,
        "trace_id": trace_id,
        "runtime_state": runtime_state,
        "client_event": client_event,
        "server_event": server_event.value,
        "approval_state": approval_state,
        "failure_bucket": failure_bucket,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "data": data or {},
    }
