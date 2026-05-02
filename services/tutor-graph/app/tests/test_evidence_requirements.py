"""Tests for evidence pack assembly — covers Bug fix #15 (ERROR_FEEDBACK needs similar_errors)."""

from __future__ import annotations

import pytest
from unittest.mock import AsyncMock, MagicMock

from app.nodes.evidence import assemble_evidence_pack, EVENT_EVIDENCE_REQUIREMENTS


def test_error_feedback_requires_similar_errors():
    """ERROR_FEEDBACK must fetch similar_errors per contract §7.4."""
    assert "similar_errors" in EVENT_EVIDENCE_REQUIREMENTS["ERROR_FEEDBACK"]


def test_error_feedback_requires_workflow_context_for_problem_statement():
    """ERROR_FEEDBACK must read the problem text so diagnosis follows题面约束."""
    assert "workflow_context" in EVENT_EVIDENCE_REQUIREMENTS["ERROR_FEEDBACK"]


def test_agent_feedback_has_no_required_evidence():
    """AGENT_FEEDBACK is an auxiliary event and should not require evidence fetches."""
    assert EVENT_EVIDENCE_REQUIREMENTS["AGENT_FEEDBACK"] == []


def test_chat_now_requires_learner_state_for_personalization():
    """P1 Persistent Memory: CHAT must now carry learner_state so the chat node injects style/profile."""
    assert "learner_state" in EVENT_EVIDENCE_REQUIREMENTS["CHAT"]


def test_chat_now_requires_last_cards_and_references_for_unified_chat():
    """P3 Unified Chat: CHAT evidence must include last_cards + references for cross-card context."""
    assert "last_cards" in EVENT_EVIDENCE_REQUIREMENTS["CHAT"]
    assert "references" in EVENT_EVIDENCE_REQUIREMENTS["CHAT"]


@pytest.mark.asyncio
async def test_chat_evidence_calls_get_last_cards_and_resolves_references():
    java = MagicMock()
    java.get_learner_state = AsyncMock(return_value={})
    java.get_last_cards = AsyncMock(return_value=[
        {"card_id": "C-E-001", "card_type": "error_diagnosis", "short_text": "范围错"}
    ])
    java.resolve_references = AsyncMock(return_value=[
        {"card_id": "C-V-001", "card_type": "visualize", "short_text": "图"}
    ])

    state = {
        "client_event": "CHAT",
        "event_data": {"message": "@card:C-V-001 这图哪里错了", "references": ["@card:C-V-001"], "mode": "chat"},
        "user_id": 1,
        "problem_id": 1,
        "session_id": "twf_chat",
        "language": "Python3",
    }
    result = await assemble_evidence_pack(state, java_client=java)

    assert result["evidence_pack"]["last_cards"] == [
        {"card_id": "C-E-001", "card_type": "error_diagnosis", "short_text": "范围错"}
    ]
    assert result["evidence_pack"]["references"] == [
        {"card_id": "C-V-001", "card_type": "visualize", "short_text": "图"}
    ]
    assert result["user_mode"] == "chat"
    assert result["last_cards"] == result["evidence_pack"]["last_cards"]
    assert result["references"] == result["evidence_pack"]["references"]
    java.get_last_cards.assert_awaited_once_with("twf_chat", limit=5)
    java.resolve_references.assert_awaited_once_with("twf_chat", ["@card:C-V-001"])


@pytest.mark.asyncio
async def test_chat_evidence_skips_resolve_when_no_references_provided():
    java = MagicMock()
    java.get_learner_state = AsyncMock(return_value={})
    java.get_last_cards = AsyncMock(return_value=[])
    java.resolve_references = AsyncMock()

    state = {
        "client_event": "CHAT",
        "event_data": {"message": "你好"},
        "user_id": 1,
        "problem_id": 1,
        "session_id": "twf_chat",
        "language": "Python3",
    }
    result = await assemble_evidence_pack(state, java_client=java)

    assert result["evidence_pack"]["references"] == []
    java.resolve_references.assert_not_awaited()


def test_knowledge_review_requires_workflow_context_for_kc_anchoring():
    """Knowledge review must fetch workflow_context so current_kcs are available."""
    assert "workflow_context" in EVENT_EVIDENCE_REQUIREMENTS["KNOWLEDGE_REVIEW"]


def test_skeleton_requires_problem_context_and_learner_state():
    """SKELETON must fetch the current problem before generating scaffold code."""
    assert "workflow_context" in EVENT_EVIDENCE_REQUIREMENTS["SKELETON"]
    assert "learner_state" in EVENT_EVIDENCE_REQUIREMENTS["SKELETON"]


@pytest.mark.asyncio
async def test_skeleton_evidence_calls_workflow_context_before_learner_state():
    java = MagicMock()
    java.get_workflow_context = AsyncMock(return_value={
        "title": "圆面积计算",
        "statement": "读入半径 radius，输出圆面积。",
        "kc_names": ["输入输出", "浮点数运算"],
    })
    java.get_learner_state = AsyncMock(return_value={"profile": {"pace": "steady"}})

    state = {
        "client_event": "SKELETON",
        "event_data": {},
        "user_id": 1,
        "problem_id": 88,
        "session_id": "twf_skeleton",
        "language": "Python3",
    }
    result = await assemble_evidence_pack(state, java_client=java)

    assert result["evidence_pack"]["workflow_context"]["statement"] == "读入半径 radius，输出圆面积。"
    assert result["learner_state"] == {"profile": {"pace": "steady"}}
    java.get_workflow_context.assert_awaited_once_with(
        88, user_id=1, session_id="twf_skeleton", language="Python3",
    )
    java.get_learner_state.assert_awaited_once()
    learner_kwargs = java.get_learner_state.await_args.kwargs
    assert learner_kwargs["context_signals"]["current_kcs"] == ["输入输出", "浮点数运算"]
    assert learner_kwargs["context_signals"]["current_problem_statement"] == "读入半径 radius，输出圆面积。"


def test_reading_now_requires_learner_state_for_personalization():
    """P1: READING must include learner_state so the problem_guide tone matches the learner."""
    assert "learner_state" in EVENT_EVIDENCE_REQUIREMENTS["READING"]


def test_coding_now_requires_learner_state_for_personalization():
    """P1: CODING must include learner_state so execution trace explanation respects the learner style."""
    assert "learner_state" in EVENT_EVIDENCE_REQUIREMENTS["CODING"]


@pytest.mark.asyncio
async def test_missing_submission_id_fails_fast():
    """ERROR_FEEDBACK without submission_id → INSUFFICIENT_EVIDENCE bucket."""
    java = MagicMock()
    java.get_learner_state = AsyncMock(return_value={})

    state = {
        "client_event": "ERROR_FEEDBACK",
        "event_data": {},
        "user_id": 1,
        "problem_id": 1,
        "session_id": "s1",
        "language": "Python3",
    }
    result = await assemble_evidence_pack(state, java_client=java)
    assert result["runtime_state"] == "FAILED"
    assert result["failure_bucket"] == "INSUFFICIENT_EVIDENCE"


@pytest.mark.asyncio
async def test_error_feedback_evidence_includes_problem_context_for_diagnosis():
    """Diagnosis evidence should include题干 before learner-state projection."""
    java = MagicMock()
    java.get_workflow_context = AsyncMock(return_value={
        "title": "圆面积计算",
        "statement": "题目明确要求使用 3.1415 计算圆面积。",
        "input_description": "输入半径 radius。",
        "output_description": "输出保留四位小数的面积。",
        "kc_names": ["浮点数运算"],
    })
    java.get_diagnosis_evidence = AsyncMock(return_value={
        "result": "Wrong Answer",
        "code": "pi=3.1415\narea=pi*r*r",
        "err_info": "答案错误",
    })
    java.get_learner_state = AsyncMock(return_value={"profile": {"pace": "steady"}})
    java.get_similar_errors = AsyncMock(return_value={"similar_errors": []})

    state = {
        "client_event": "ERROR_FEEDBACK",
        "event_data": {"submission_id": "sub_1"},
        "user_id": 1,
        "problem_id": 88,
        "session_id": "twf_error",
        "language": "Python3",
    }
    result = await assemble_evidence_pack(state, java_client=java)

    assert result["evidence_pack"]["workflow_context"]["statement"] == "题目明确要求使用 3.1415 计算圆面积。"
    java.get_workflow_context.assert_awaited_once_with(
        88, user_id=1, session_id="twf_error", language="Python3",
    )
    java.get_diagnosis_evidence.assert_awaited_once_with(
        "sub_1", user_id=1, problem_id=88, session_id="twf_error",
    )
    learner_kwargs = java.get_learner_state.await_args.kwargs
    assert learner_kwargs["context_signals"]["current_kcs"] == ["浮点数运算"]
    assert learner_kwargs["context_signals"]["current_problem_statement"] == "题目明确要求使用 3.1415 计算圆面积。"


@pytest.mark.asyncio
async def test_tool_call_exception_bubbles_as_tool_execution_failed():
    """If the Java tool API raises, we should set TOOL_EXECUTION_FAILED bucket."""
    java = MagicMock()
    java.get_workflow_context = AsyncMock(side_effect=RuntimeError("network down"))

    state = {
        "client_event": "READING",
        "event_data": {},
        "user_id": 1,
        "problem_id": 1,
        "session_id": "s1",
        "language": "Python3",
    }
    result = await assemble_evidence_pack(state, java_client=java)
    assert result["runtime_state"] == "FAILED"
    assert result["failure_bucket"] == "TOOL_EXECUTION_FAILED"
