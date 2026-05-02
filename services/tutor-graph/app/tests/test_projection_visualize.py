from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest
from langgraph.checkpoint.memory import MemorySaver

from app.graph.builder import build_tutor_graph


@pytest.mark.asyncio
async def test_projection_dispatches_visualize_when_intent_present():
    java = MagicMock()
    java.get_workflow_context = AsyncMock(return_value={
        "statement": "输出两个整数之和",
        "samples": [{"input": "1 2", "output": "3"}],
    })
    java.get_learner_state = AsyncMock(return_value={})
    java.get_courseware_hits = AsyncMock(return_value={"hits": []})
    java.dispatch_visualize = AsyncMock(return_value={
        "card_id": "C-V-001",
        "card_payload": {
            "intent": "flowchart",
            "format": "mermaid",
            "payload": "flowchart TD\nstart-->end",
            "alt_text": "解题流程图",
            "source_role": "Nene",
        },
    })
    java.post_workflow_event = AsyncMock(return_value={"status": "recorded"})

    llm = MagicMock()
    llm.generate_json = AsyncMock(return_value={
        "problem_restatement": "先读取两个整数，再输出它们的和。",
        "input_output_focus": "输入两个整数，输出一个整数。",
        "key_observation": "只需要做一次加法。",
        "starter_questions": ["输入是几个数字？", "输出是什么类型？"],
        "related_kcs": [],
        "courseware_refs": [],
        "visualize_intent": "flowchart",
        "visualize_prompt": "画出读取输入并求和输出的流程",
    })

    graph = build_tutor_graph(java_client=java, llm_client=llm, checkpointer=MemorySaver())
    config = {"configurable": {"thread_id": "thread_projection_visualize"}}

    result = await graph.ainvoke({
        "session_id": "twf_projection_visualize",
        "thread_id": "thread_projection_visualize",
        "run_id": "run_projection_visualize",
        "user_id": 1,
        "problem_id": 379,
        "language": "Python3",
        "client_event": "READING",
        "event_data": {},
        "current_phase": "READING",
        "node_outputs": {},
        "behavior_metrics": {},
        "evidence_pack": {},
        "learner_state": {},
        "available_actions": [],
        "runtime_state": "QUEUED",
        "pending_human_action": "",
        "side_effects": {},
        "execution_trace": [],
    }, config)

    assert result["runtime_state"] == "COMPLETED"
    java.dispatch_visualize.assert_awaited_once()
    java.post_workflow_event.assert_awaited_once()

    projection_payload = java.post_workflow_event.await_args.args[0]
    assert projection_payload["node_outputs"]["visualize"]["format"] == "mermaid"
    assert "visualize_card_id" not in projection_payload["node_outputs"]["problem_guide"]
    assert "visualize" not in projection_payload["node_outputs"]["problem_guide"]
    assert "visualize_failed" not in projection_payload["node_outputs"]["problem_guide"]


@pytest.mark.asyncio
async def test_projection_fails_run_when_visualize_dispatch_returns_empty_payload():
    java = MagicMock()
    java.get_workflow_context = AsyncMock(return_value={
        "statement": "输出两个整数之和",
        "samples": [{"input": "1 2", "output": "3"}],
    })
    java.get_learner_state = AsyncMock(return_value={})
    java.get_courseware_hits = AsyncMock(return_value={"hits": []})
    # Empty payload simulates schema violation from VisualizeCapabilityService.
    java.dispatch_visualize = AsyncMock(return_value={})
    java.post_workflow_event = AsyncMock(return_value={"status": "recorded"})

    llm = MagicMock()
    llm.generate_json = AsyncMock(return_value={
        "problem_restatement": "先读取两个整数，再输出它们的和。",
        "input_output_focus": "输入两个整数，输出一个整数。",
        "key_observation": "只需要做一次加法。",
        "starter_questions": ["输入是几个数字？", "输出是什么类型？"],
        "related_kcs": [],
        "courseware_refs": [],
        "visualize_intent": "flowchart",
        "visualize_prompt": "画出读取输入并求和输出的流程",
    })

    graph = build_tutor_graph(java_client=java, llm_client=llm, checkpointer=MemorySaver())
    config = {"configurable": {"thread_id": "thread_projection_visualize_fail"}}

    result = await graph.ainvoke({
        "session_id": "twf_projection_visualize_fail",
        "thread_id": "thread_projection_visualize_fail",
        "run_id": "run_projection_visualize_fail",
        "user_id": 1,
        "problem_id": 379,
        "language": "Python3",
        "client_event": "READING",
        "event_data": {},
        "current_phase": "READING",
        "node_outputs": {},
        "behavior_metrics": {},
        "evidence_pack": {},
        "learner_state": {},
        "available_actions": [],
        "runtime_state": "QUEUED",
        "pending_human_action": "",
        "side_effects": {},
        "execution_trace": [],
    }, config)

    assert result["runtime_state"] == "FAILED"
    assert result["failure_bucket"] == "SCHEMA_VIOLATION"
    assert "visualize dispatch failed" in result["last_error"]
    java.post_workflow_event.assert_awaited_once()
    failed_payload = java.post_workflow_event.await_args.args[0]
    assert failed_payload["runtime_state"] == "FAILED"
    assert failed_payload["failure_bucket"] == "SCHEMA_VIOLATION"


@pytest.mark.asyncio
async def test_projection_fails_run_when_node_declares_intent_without_prompt():
    java = MagicMock()
    java.get_workflow_context = AsyncMock(return_value={
        "statement": "输出两个整数之和",
        "samples": [{"input": "1 2", "output": "3"}],
    })
    java.get_learner_state = AsyncMock(return_value={})
    java.get_courseware_hits = AsyncMock(return_value={"hits": []})
    java.dispatch_visualize = AsyncMock()
    java.post_workflow_event = AsyncMock(return_value={"status": "recorded"})

    llm = MagicMock()
    llm.generate_json = AsyncMock(return_value={
        "problem_restatement": "略",
        "input_output_focus": "略",
        "key_observation": "略",
        "starter_questions": ["A?", "B?"],
        "related_kcs": [],
        "courseware_refs": [],
        "visualize_intent": "flowchart",
        # Missing visualize_prompt → fail fast in projection.
    })

    graph = build_tutor_graph(java_client=java, llm_client=llm, checkpointer=MemorySaver())
    config = {"configurable": {"thread_id": "thread_projection_visualize_partial"}}

    result = await graph.ainvoke({
        "session_id": "twf_projection_visualize_partial",
        "thread_id": "thread_projection_visualize_partial",
        "run_id": "run_projection_visualize_partial",
        "user_id": 1,
        "problem_id": 379,
        "language": "Python3",
        "client_event": "READING",
        "event_data": {},
        "current_phase": "READING",
        "node_outputs": {},
        "behavior_metrics": {},
        "evidence_pack": {},
        "learner_state": {},
        "available_actions": [],
        "runtime_state": "QUEUED",
        "pending_human_action": "",
        "side_effects": {},
        "execution_trace": [],
    }, config)

    assert result["runtime_state"] == "FAILED"
    assert result["failure_bucket"] == "SCHEMA_VIOLATION"
    java.dispatch_visualize.assert_not_awaited()
    java.post_workflow_event.assert_awaited_once()
