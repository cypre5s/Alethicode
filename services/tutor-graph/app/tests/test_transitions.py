"""Tests for phase/event transition validation."""

import pytest

from app.graph.transitions import (
    AUXILIARY_EVENTS,
    PHASE_EVENTS,
    PHASE_TRANSITIONS,
    PLAN_EVIDENCE_TYPES,
    PLAN_EVENTS,
    PLAN_SIGNAL_TYPES,
    TransitionError,
    validate_transition,
)


def _event_data_for(event: str, *, evidence_type: str = "text", signal_type: str = "pause") -> dict:
    if event == "ERROR_FEEDBACK":
        return {"submission_id": "sub_123"}
    if event == "AC_REVIEW":
        return {"submission_id": "sub_123"}
    if event == "PLAN_START":
        return {
            "reason": "连续两次 WA",
            "trigger_source": "rules",
            "current_phase": "CODING",
        }
    if event == "PLAN_RESPONSE":
        payload = {
            "plan_id": "plan_1",
            "step_id": "step_1",
            "evidence_type": evidence_type,
        }
        if evidence_type in {"text", "reflection"}:
            payload["response_text"] = "我先把题意说完整，再继续推进。"
        elif evidence_type == "sample_prediction":
            payload["sample_prediction"] = "第一轮循环后 total 会变成 3"
        else:
            payload["code_snapshot_id"] = "snapshot_1"
        return payload
    if event == "PLAN_STEERING":
        payload = {
            "plan_id": "plan_1",
            "signal_type": signal_type,
        }
        if signal_type == "redirect":
            payload["redirect_instruction"] = "先回到样例手动模拟。"
        return payload
    return {}


class TestValidTransitions:
    def test_reading_to_reading(self):
        assert validate_transition("READING", "READING", language="Python3") == "READING"

    def test_reading_to_ideating(self):
        assert validate_transition("READING", "IDEATING", language="Python3") == "IDEATING"

    def test_reading_to_coding(self):
        assert validate_transition("READING", "CODING", language="Python3") == "CODING"

    def test_reading_to_error_feedback(self):
        result = validate_transition(
            "READING", "ERROR_FEEDBACK",
            language="Python3",
            event_data={"submission_id": "123"},
        )
        assert result == "ERROR_FEEDBACK"

    def test_reading_to_ac_review(self):
        result = validate_transition(
            "READING", "AC_REVIEW",
            language="Python3",
            event_data={"submission_id": "123"},
        )
        assert result == "AC_REVIEW"

    def test_ideating_to_coding(self):
        assert validate_transition("IDEATING", "CODING", language="Python3") == "CODING"

    def test_error_feedback_to_reading(self):
        assert validate_transition("ERROR_FEEDBACK", "READING", language="Python3") == "READING"

    def test_ac_review_to_transfer(self):
        assert validate_transition("AC_REVIEW", "TRANSFER", language="Python3") == "TRANSFER"

    def test_transfer_to_coding(self):
        assert validate_transition("TRANSFER", "CODING", language="Python3") == "CODING"

    @pytest.mark.parametrize(
        ("current_phase", "event"),
        [
            (phase, event)
            for phase, allowed in PHASE_TRANSITIONS.items()
            for event in sorted(PHASE_EVENTS)
            if event in allowed
        ],
    )
    def test_every_allowed_phase_transition_is_accepted(self, current_phase, event):
        assert validate_transition(
            current_phase,
            event,
            language="Python3",
            event_data=_event_data_for(event),
        ) == event


class TestAuxiliaryEvents:
    def test_chat_preserves_phase(self):
        assert validate_transition("IDEATING", "CHAT", language="Python3") == "IDEATING"

    def test_knowledge_review_preserves_phase(self):
        assert validate_transition("CODING", "KNOWLEDGE_REVIEW", language="Python3") == "CODING"

    def test_agent_feedback_preserves_phase(self):
        assert validate_transition("ERROR_FEEDBACK", "AGENT_FEEDBACK", language="Python3") == "ERROR_FEEDBACK"

    def test_visualize_preserves_phase(self):
        assert validate_transition("CODING", "VISUALIZE", language="Python3") == "CODING"

    def test_skeleton_preserves_phase(self):
        assert validate_transition("IDEATING", "SKELETON", language="Python3") == "IDEATING"

    def test_plan_recommend_preserves_phase(self):
        assert validate_transition("CODING", "PLAN_RECOMMEND", language="Python3") == "CODING"

    def test_plan_start_preserves_phase(self):
        result = validate_transition(
            "ERROR_FEEDBACK",
            "PLAN_START",
            language="Python3",
            event_data={
                "reason": "连续两次 WA",
                "trigger_source": "rules",
                "current_phase": "ERROR_FEEDBACK",
            },
        )
        assert result == "ERROR_FEEDBACK"

    def test_plan_response_preserves_phase(self):
        result = validate_transition(
            "IDEATING",
            "PLAN_RESPONSE",
            language="Python3",
            event_data={
                "plan_id": "plan_1",
                "step_id": "step_1",
                "evidence_type": "text",
                "response_text": "我准备先统计再判断",
            },
        )
        assert result == "IDEATING"

    def test_plan_steering_preserves_phase(self):
        result = validate_transition(
            "CODING",
            "PLAN_STEERING",
            language="Python3",
            event_data={
                "plan_id": "plan_1",
                "signal_type": "pause",
            },
        )
        assert result == "CODING"

    @pytest.mark.parametrize("current_phase", sorted(PHASE_TRANSITIONS))
    @pytest.mark.parametrize("event", sorted(AUXILIARY_EVENTS))
    def test_all_auxiliary_events_preserve_every_known_phase(self, current_phase, event):
        assert validate_transition(current_phase, event, language="Python3") == current_phase

    @pytest.mark.parametrize("current_phase", sorted(PHASE_TRANSITIONS))
    @pytest.mark.parametrize("event", sorted(PLAN_EVENTS))
    def test_all_plan_events_preserve_every_known_phase(self, current_phase, event):
        assert validate_transition(
            current_phase,
            event,
            language="Python3",
            event_data=_event_data_for(event),
        ) == current_phase

    @pytest.mark.parametrize("evidence_type", sorted(PLAN_EVIDENCE_TYPES))
    def test_plan_response_accepts_every_supported_evidence_type(self, evidence_type):
        assert validate_transition(
            "CODING",
            "PLAN_RESPONSE",
            language="Python3",
            event_data=_event_data_for("PLAN_RESPONSE", evidence_type=evidence_type),
        ) == "CODING"

    @pytest.mark.parametrize("signal_type", sorted(PLAN_SIGNAL_TYPES))
    def test_plan_steering_accepts_every_supported_signal_type(self, signal_type):
        assert validate_transition(
            "ERROR_FEEDBACK",
            "PLAN_STEERING",
            language="Python3",
            event_data=_event_data_for("PLAN_STEERING", signal_type=signal_type),
        ) == "ERROR_FEEDBACK"


class TestInvalidTransitions:
    def test_ideating_to_reading_blocked(self):
        with pytest.raises(TransitionError):
            validate_transition("IDEATING", "READING", language="Python3")

    def test_coding_to_reading_blocked(self):
        with pytest.raises(TransitionError):
            validate_transition("CODING", "READING", language="Python3")

    def test_ac_review_to_reading_blocked(self):
        with pytest.raises(TransitionError):
            validate_transition("AC_REVIEW", "READING", language="Python3")

    def test_unknown_event(self):
        with pytest.raises(TransitionError, match="unknown event"):
            validate_transition("READING", "UNKNOWN_EVENT", language="Python3")

    def test_missing_language(self):
        with pytest.raises(TransitionError, match="language is required"):
            validate_transition("READING", "READING", language="")

    def test_error_feedback_missing_submission_id(self):
        with pytest.raises(TransitionError, match="missing required field"):
            validate_transition("READING", "ERROR_FEEDBACK", language="Python3")

    def test_confirm_transfer_blocks_coding(self):
        with pytest.raises(TransitionError, match="confirm_transfer"):
            validate_transition(
                "TRANSFER", "CODING",
                language="Python3",
                pending_human_action="confirm_transfer",
            )

    def test_plan_start_missing_required_fields(self):
        with pytest.raises(TransitionError, match="missing required field"):
            validate_transition("READING", "PLAN_START", language="Python3", event_data={})

    def test_plan_response_requires_known_evidence_type(self):
        with pytest.raises(TransitionError, match="invalid evidence_type"):
            validate_transition(
                "READING",
                "PLAN_RESPONSE",
                language="Python3",
                event_data={
                    "plan_id": "plan_1",
                    "step_id": "step_1",
                    "evidence_type": "essay",
                },
            )

    def test_plan_steering_redirect_requires_instruction(self):
        with pytest.raises(TransitionError, match="redirect_instruction"):
            validate_transition(
                "READING",
                "PLAN_STEERING",
                language="Python3",
                event_data={
                    "plan_id": "plan_1",
                    "signal_type": "redirect",
                },
            )

    def test_case_insensitive(self):
        assert validate_transition("reading", "ideating", language="Python3") == "IDEATING"

    @pytest.mark.parametrize(
        ("current_phase", "event"),
        [
            (phase, event)
            for phase, allowed in PHASE_TRANSITIONS.items()
            for event in sorted(PHASE_EVENTS)
            if event not in allowed
        ],
    )
    def test_every_disallowed_phase_transition_is_rejected(self, current_phase, event):
        with pytest.raises(TransitionError, match="not allowed from current phase"):
            validate_transition(
                current_phase,
                event,
                language="Python3",
                event_data=_event_data_for(event),
            )

    def test_unknown_current_phase_blocks_auxiliary_event(self):
        with pytest.raises(TransitionError, match="unknown current phase"):
            validate_transition("BROKEN_PHASE", "CHAT", language="Python3")

    def test_unknown_current_phase_blocks_plan_event(self):
        with pytest.raises(TransitionError, match="unknown current phase"):
            validate_transition(
                "BROKEN_PHASE",
                "PLAN_START",
                language="Python3",
                event_data=_event_data_for("PLAN_START"),
            )
