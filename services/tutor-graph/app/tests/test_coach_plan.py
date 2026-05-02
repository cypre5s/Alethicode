"""Tests for metacognitive coach plan state transitions."""

from __future__ import annotations

import pytest

from app.nodes.coach_plan import (
    build_plan_payload,
    coach_plan_start_node,
    coach_recommendation_node,
    coach_replan_node,
    coach_step_complete_node,
    coach_step_evaluate_node,
    coach_step_remediate_node,
)


def _base_state() -> dict:
    return {
        "current_phase": "ERROR_FEEDBACK",
        "behavior_metrics": {
            "consecutiveErrors": 3,
            "dwellTime": 720,
            "deleteRatio": 0.4,
        },
        "learner_state": {"frustration_level": "high"},
        "plan_status": "idle",
        "active_plan": {},
        "plan_recommendation": {},
        "recommendation_reason": "",
        "trigger_features": {},
    }


def test_recommendation_is_rule_based_and_preserves_idle_plan():
    result = coach_recommendation_node(_base_state())
    assert result["plan_status"] == "recommended"
    assert result["plan_recommendation"]["status"] == "recommended"
    assert "连续错误次数偏高" in result["recommendation_reason"]


def test_plan_start_builds_five_step_checkpoint_plan():
    state = _base_state()
    state["event_data"] = {
        "reason": "连续两次 WA",
        "trigger_source": "rules",
        "current_phase": "ERROR_FEEDBACK",
    }
    result = coach_plan_start_node(state)
    assert result["plan_status"] == "active"
    assert result["current_step_index"] == 0
    assert len(result["active_plan"]["steps"]) == 5
    assert result["current_checkpoint"]["step_id"] == "task_representation"


def test_plan_response_first_failure_shrinks_current_task():
    started = coach_plan_start_node({
        **_base_state(),
        "event_data": {
            "reason": "卡住了",
            "trigger_source": "rules",
            "current_phase": "ERROR_FEEDBACK",
        },
    })
    evaluated = coach_step_evaluate_node({
        **started,
        "event_data": {
            "plan_id": started["plan_id"],
            "step_id": started["active_plan"]["steps"][0]["step_id"],
            "evidence_type": "text",
            "response_text": "太短",
        },
    })
    remediated = coach_step_remediate_node(evaluated)
    assert remediated["remediation_depth"] == 1
    assert "先只回答" in remediated["current_checkpoint"]["support_hint"]


def test_plan_response_second_failure_rolls_back_to_previous_understanding_step():
    started = coach_plan_start_node({
        **_base_state(),
        "event_data": {
            "reason": "卡住了",
            "trigger_source": "rules",
            "current_phase": "ERROR_FEEDBACK",
        },
    })
    completed_first = coach_step_complete_node({
        **started,
        "current_step_index": 0,
    })
    completed_first["remediation_depth"] = 1
    remediated = coach_step_remediate_node(completed_first)
    assert remediated["current_step_index"] == 0
    assert remediated["active_plan"]["steps"][0]["status"] == "active"


def test_plan_steering_redirect_updates_current_support_hint():
    started = coach_plan_start_node({
        **_base_state(),
        "event_data": {
            "reason": "卡住了",
            "trigger_source": "rules",
            "current_phase": "ERROR_FEEDBACK",
        },
    })
    redirected = coach_replan_node({
        **started,
        "event_data": {
            "plan_id": started["plan_id"],
            "signal_type": "redirect",
            "redirect_instruction": "先回到样例，逐行模拟第一轮循环。",
        },
    })
    assert redirected["current_checkpoint"]["support_hint"] == "先回到样例，逐行模拟第一轮循环。"


@pytest.mark.parametrize(
    ("signal_type", "expected_status", "expected_step_index"),
    [
        ("pause", "paused", 0),
        ("resume", "active", 0),
        ("take_over", "surrendered", 0),
        ("skip", "active", 1),
    ],
)
def test_plan_steering_covers_core_signal_transitions(signal_type, expected_status, expected_step_index):
    started = coach_plan_start_node({
        **_base_state(),
        "event_data": {
            "reason": "卡住了",
            "trigger_source": "rules",
            "current_phase": "ERROR_FEEDBACK",
        },
    })
    if signal_type == "resume":
        started = coach_replan_node({
            **started,
            "event_data": {
                "plan_id": started["plan_id"],
                "signal_type": "pause",
            },
        })

    result = coach_replan_node({
        **started,
        "event_data": {
            "plan_id": started["plan_id"],
            "signal_type": signal_type,
        },
    })

    assert result["plan_status"] == expected_status
    if signal_type == "take_over":
        assert result["current_checkpoint"] == {}
    else:
        assert result["current_step_index"] == expected_step_index


def test_plan_steering_skip_on_last_step_completes_plan():
    started = coach_plan_start_node({
        **_base_state(),
        "event_data": {
            "reason": "卡住了",
            "trigger_source": "rules",
            "current_phase": "ERROR_FEEDBACK",
        },
    })
    started["current_step_index"] = 4
    for index, step in enumerate(started["active_plan"]["steps"]):
        step["status"] = "completed" if index < 4 else "active"

    result = coach_replan_node({
        **started,
        "event_data": {
            "plan_id": started["plan_id"],
            "signal_type": "skip",
        },
    })

    assert result["plan_status"] == "completed"
    assert result["current_checkpoint"] == {}


def test_plan_steering_requires_matching_plan_id():
    started = coach_plan_start_node({
        **_base_state(),
        "event_data": {
            "reason": "卡住了",
            "trigger_source": "rules",
            "current_phase": "ERROR_FEEDBACK",
        },
    })

    result = coach_replan_node({
        **started,
        "event_data": {
            "plan_id": "plan_other",
            "signal_type": "pause",
        },
    })

    assert result["runtime_state"] == "FAILED"
    assert result["failure_bucket"] == "SCHEMA_VIOLATION"
    assert "plan_id does not match active plan" in result["last_error"]


def test_completing_last_step_marks_plan_completed():
    started = coach_plan_start_node({
        **_base_state(),
        "event_data": {
            "reason": "卡住了",
            "trigger_source": "rules",
            "current_phase": "ERROR_FEEDBACK",
        },
    })
    started["current_step_index"] = 4
    for index, step in enumerate(started["active_plan"]["steps"]):
        step["status"] = "completed" if index < 4 else "active"

    result = coach_step_complete_node(started)

    assert result["plan_status"] == "completed"
    assert result["current_checkpoint"] == {}


def test_build_plan_payload_exposes_current_step():
    started = coach_plan_start_node({
        **_base_state(),
        "event_data": {
            "reason": "卡住了",
            "trigger_source": "rules",
            "current_phase": "ERROR_FEEDBACK",
        },
    })
    payload = build_plan_payload(started)
    assert payload["status"] == "active"
    assert payload["current_step"]["step_id"] == "task_representation"
