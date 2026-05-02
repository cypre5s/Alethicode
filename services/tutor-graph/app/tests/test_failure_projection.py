from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest
from langgraph.checkpoint.memory import MemorySaver

from app.graph.builder import build_tutor_graph


@pytest.mark.asyncio
async def test_failed_run_should_still_persist_projection():
    java = MagicMock()
    java.get_workflow_context = AsyncMock(return_value={
        "statement": "输出两个整数之和",
        "samples": [{"input": "1 2", "output": "3"}],
    })
    java.get_learner_state = AsyncMock(return_value={})
    java.get_courseware_hits = AsyncMock(return_value={"hits": []})
    java.post_workflow_event = AsyncMock(return_value={"status": "recorded"})

    llm = MagicMock()
    llm.generate_json = AsyncMock(side_effect=RuntimeError("Connection error."))

    graph = build_tutor_graph(java_client=java, llm_client=llm, checkpointer=MemorySaver())
    config = {"configurable": {"thread_id": "thread_failure_projection"}}

    result = await graph.ainvoke({
        "session_id": "twf_failure_projection",
        "thread_id": "thread_failure_projection",
        "run_id": "run_failure_projection",
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
    java.post_workflow_event.assert_awaited_once()

    payload = java.post_workflow_event.await_args.args[0]
    assert payload["runtime_state"] == "FAILED"
    assert payload["server_event"] == "TASK_FAILED"
    assert payload["failure_bucket"] == "SYSTEM_ERROR"
    assert "LLM generation failed" in payload["error"]


@pytest.mark.asyncio
async def test_successful_run_should_persist_completed_runtime_state():
    java = MagicMock()
    java.get_workflow_context = AsyncMock(return_value={
        "statement": "输出两个整数之和",
        "samples": [{"input": "1 2", "output": "3"}],
    })
    java.get_learner_state = AsyncMock(return_value={})
    java.get_courseware_hits = AsyncMock(return_value={"hits": []})
    java.post_workflow_event = AsyncMock(return_value={"status": "recorded"})

    llm = MagicMock()
    llm.generate_json = AsyncMock(return_value={
        "problem_restatement": "先读取两个整数，再输出它们的和。",
        "input_output_focus": "输入两个整数，输出一个整数。",
        "key_observation": "只需要做一次加法。",
        "starter_questions": ["输入是几个数字？", "输出是什么类型？"],
        "related_kcs": [],
        "courseware_refs": [],
    })

    graph = build_tutor_graph(java_client=java, llm_client=llm, checkpointer=MemorySaver())
    config = {"configurable": {"thread_id": "thread_success_projection"}}

    result = await graph.ainvoke({
        "session_id": "twf_success_projection",
        "thread_id": "thread_success_projection",
        "run_id": "run_success_projection",
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
    java.post_workflow_event.assert_awaited_once()

    payload = java.post_workflow_event.await_args.args[0]
    assert payload["runtime_state"] == "COMPLETED"
    assert payload["server_event"] == "TASK_COMPLETED"
    assert payload["node_outputs"]["problem_guide"]["problem_restatement"] == "先读取两个整数，再输出它们的和。"
