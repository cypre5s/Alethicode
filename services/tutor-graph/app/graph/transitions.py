"""Phase / Event 状态迁移规则，纯逻辑无 I/O。"""

from __future__ import annotations

PHASE_TRANSITIONS: dict[str, frozenset[str]] = {
    "READING": frozenset({"READING", "IDEATING", "CODING", "ERROR_FEEDBACK", "AC_REVIEW"}),
    "IDEATING": frozenset({"IDEATING", "CODING", "ERROR_FEEDBACK", "AC_REVIEW"}),
    "CODING": frozenset({"CODING", "ERROR_FEEDBACK", "AC_REVIEW"}),
    "ERROR_FEEDBACK": frozenset({"ERROR_FEEDBACK", "READING", "IDEATING", "CODING", "AC_REVIEW"}),
    "AC_REVIEW": frozenset({"AC_REVIEW", "TRANSFER"}),
    "TRANSFER": frozenset({"TRANSFER", "CODING", "ERROR_FEEDBACK", "AC_REVIEW"}),
}

AUXILIARY_EVENTS: frozenset[str] = frozenset({"CHAT", "AGENT_FEEDBACK", "KNOWLEDGE_REVIEW", "VISUALIZE", "SKELETON", "PARSONS", "COMPACT"})
PLAN_EVENTS: frozenset[str] = frozenset({
    "PLAN_RECOMMEND", "PLAN_START", "PLAN_RESPONSE", "PLAN_STEERING",
})

PHASE_EVENTS: frozenset[str] = frozenset({
    "READING", "IDEATING", "CODING", "ERROR_FEEDBACK", "AC_REVIEW", "TRANSFER",
})

ALL_EVENTS: frozenset[str] = PHASE_EVENTS | AUXILIARY_EVENTS | PLAN_EVENTS

EVENT_REQUIRED_FIELDS: dict[str, list[str]] = {
    "ERROR_FEEDBACK": ["submission_id"],
    "AC_REVIEW": ["submission_id"],
    "PLAN_START": ["reason", "trigger_source", "current_phase"],
    "PLAN_RESPONSE": ["plan_id", "step_id", "evidence_type"],
    "PLAN_STEERING": ["plan_id", "signal_type"],
}

PLAN_EVIDENCE_TYPES: frozenset[str] = frozenset({
    "text", "sample_prediction", "code_change", "reflection",
})
PLAN_SIGNAL_TYPES: frozenset[str] = frozenset({
    "pause", "resume", "skip", "take_over", "redirect",
})


class TransitionError(Exception):
    """Raised when a phase/event transition is illegal."""

    def __init__(self, current_phase: str, event: str, reason: str = ""):
        self.current_phase = current_phase
        self.event = event
        self.reason = reason
        msg = f"Illegal transition: {current_phase} -> {event}"
        if reason:
            msg += f" ({reason})"
        super().__init__(msg)


def validate_transition(
    current_phase: str,
    event: str,
    *,
    pending_human_action: str = "",
    event_data: dict | None = None,
    language: str = "",
) -> str:
    """Return the new phase after a valid transition, or raise TransitionError."""
    event = event.upper()
    current_phase = current_phase.upper()

    if event not in ALL_EVENTS:
        raise TransitionError(current_phase, event, "unknown event")

    if not language:
        raise TransitionError(current_phase, event, "language is required")

    allowed = PHASE_TRANSITIONS.get(current_phase)
    if allowed is None:
        raise TransitionError(current_phase, event, "unknown current phase")

    if event in AUXILIARY_EVENTS or event in PLAN_EVENTS:
        data = event_data or {}
        required_fields = EVENT_REQUIRED_FIELDS.get(event, [])
        for field in required_fields:
            if not data.get(field):
                raise TransitionError(current_phase, event, f"missing required field: {field}")

        if event == "PLAN_RESPONSE":
            evidence_type = str(data.get("evidence_type", "")).strip().lower()
            if evidence_type not in PLAN_EVIDENCE_TYPES:
                raise TransitionError(current_phase, event, "invalid evidence_type")

        if event == "PLAN_STEERING":
            signal_type = str(data.get("signal_type", "")).strip().lower()
            if signal_type not in PLAN_SIGNAL_TYPES:
                raise TransitionError(current_phase, event, "invalid signal_type")
            if signal_type == "redirect" and not data.get("redirect_instruction"):
                raise TransitionError(current_phase, event, "missing required field: redirect_instruction")

        return current_phase

    if event not in allowed:
        raise TransitionError(current_phase, event, "not allowed from current phase")

    if pending_human_action == "confirm_transfer" and event == "CODING":
        raise TransitionError(current_phase, event, "blocked by pending confirm_transfer")

    required_fields = EVENT_REQUIRED_FIELDS.get(event, [])
    data = event_data or {}
    for field in required_fields:
        if not data.get(field):
            raise TransitionError(current_phase, event, f"missing required field: {field}")

    return event
