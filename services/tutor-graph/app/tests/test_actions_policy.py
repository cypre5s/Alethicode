"""Tests for decide_available_actions — covers action filtering under pending_human_action."""

from __future__ import annotations

from app.nodes.actions import decide_available_actions


def test_reading_default_actions():
    state = {"current_phase": "READING", "pending_human_action": ""}
    result = decide_available_actions(state)
    keys = [a["key"] for a in result["available_actions"]]
    assert "problem_guide" in keys
    assert "ideate" in keys
    assert "skeleton" in keys
    assert "visualize" in keys


def test_ac_review_exposes_transfer():
    state = {"current_phase": "AC_REVIEW", "pending_human_action": ""}
    result = decide_available_actions(state)
    keys = [a["key"] for a in result["available_actions"]]
    assert "transfer" in keys


def test_confirm_transfer_blocks_coding_action():
    """When a transfer interrupt is pending, we must not let the learner resume CODING."""
    state = {"current_phase": "TRANSFER", "pending_human_action": "confirm_transfer"}
    result = decide_available_actions(state)
    events = [a["event"] for a in result["available_actions"]]
    assert "CODING" not in events


def test_unknown_phase_falls_back_to_reading():
    state = {"current_phase": "UNKNOWN_PHASE", "pending_human_action": ""}
    result = decide_available_actions(state)
    assert len(result["available_actions"]) >= 1


def test_ideating_actions_route_skeleton_to_explicit_workflow_event():
    state = {"current_phase": "IDEATING", "pending_human_action": ""}
    result = decide_available_actions(state)
    skeleton_action = next(a for a in result["available_actions"] if a["key"] == "skeleton")
    assert skeleton_action["event"] == "SKELETON"
