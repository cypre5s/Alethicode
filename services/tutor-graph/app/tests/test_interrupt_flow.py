"""测试迁移练习节点的 LangGraph interrupt / resume 流程。"""

from __future__ import annotations

import sys

import pytest
from unittest.mock import AsyncMock, MagicMock
from langgraph.checkpoint.memory import MemorySaver

from app.graph.builder import build_tutor_graph

pytestmark = pytest.mark.skipif(
    sys.version_info < (3, 11),
    reason="LangGraph async interrupt() requires Python 3.11+ for ContextVar propagation",
)


@pytest.fixture
def mock_clients():
    """构造最小 Java 与 LLM mock 客户端。"""
    java = MagicMock()
    java.get_workflow_context = AsyncMock(return_value={
        "statement": "累加 1..n", "samples": [], "languages": ["Python3"],
    })
    java.get_diagnosis_evidence = AsyncMock(return_value={
        "code": "for i in range(n): total += i", "language": "Python3",
        "result": 1, "info": {},
    })
    java.get_learner_state = AsyncMock(return_value={
        "user_id": 1, "problem_id": 1, "submission_count": 3, "ac_count": 1,
    })
    java.get_courseware_hits = AsyncMock(return_value={"hits": []})
    java.get_similar_errors = AsyncMock(return_value={"similar_errors": []})
    java.create_transfer_problem = AsyncMock(return_value={
        "problem_id": 9999, "problem_display_id": "T1-1", "temporary_problem": True,
    })
    java.post_workflow_event = AsyncMock(return_value={"status": "recorded"})

    llm = MagicMock()

    async def _gen_json(system, user, *, node_name="", metadata=None):
        fixtures = {
            "post_ac": {
                "success_summary": "你成功了",
                "next_practice_direction": "尝试嵌套循环",
                "courseware_refs": [],
            },
            "transfer_draft": {
                "title": "九九乘法表",
                "description": "输出 9 行乘法表",
                "input_description": "无",
                "output_description": "9 行",
                "samples": [],
                "test_cases": [],
                "hint": "嵌套循环",
                "reference_solution_language": "Python3",
                "reference_solution_code": "pass",
                "target_kcs": ["嵌套循环"],
            },
        }
        return fixtures.get(node_name, {})

    llm.generate_json = _gen_json
    return java, llm


@pytest.mark.asyncio
async def test_transfer_interrupts_before_materialize(mock_clients):
    """transfer_draft 调用 Java 物化接口前必须先 interrupt。"""
    java, llm = mock_clients
    checkpointer = MemorySaver()
    graph = build_tutor_graph(java_client=java, llm_client=llm, checkpointer=checkpointer)

    config = {"configurable": {"thread_id": "t_test_1"}}
    initial = {
        "session_id": "s1", "thread_id": "t_test_1", "run_id": "r1",
        "user_id": 1, "problem_id": 1, "language": "Python3",
        "client_event": "TRANSFER", "event_data": {},
        "current_phase": "AC_REVIEW",
        "node_outputs": {"post_ac": {
            "success_summary": "ok", "next_practice_direction": "nested loops",
            "courseware_refs": [],
        }},
        "runtime_state": "QUEUED", "pending_human_action": "",
        "behavior_metrics": {}, "evidence_pack": {}, "learner_state": {},
        "available_actions": [], "side_effects": {}, "execution_trace": [],
    }

    await graph.ainvoke(initial, config)

    state = await graph.aget_state(config)
    assert state is not None
    assert state.next, "graph should have pending next nodes (interrupted)"

    # interrupt 前不得物化迁移练习。
    java.create_transfer_problem.assert_not_called()


@pytest.mark.asyncio
async def test_transfer_resume_confirm_materializes(mock_clients):
    """确认恢复后必须调用 materialize_transfer_problem_node。"""
    from langgraph.types import Command

    java, llm = mock_clients
    checkpointer = MemorySaver()
    graph = build_tutor_graph(java_client=java, llm_client=llm, checkpointer=checkpointer)

    config = {"configurable": {"thread_id": "t_test_2"}}
    initial = {
        "session_id": "s1", "thread_id": "t_test_2", "run_id": "r1",
        "user_id": 1, "problem_id": 1, "language": "Python3",
        "client_event": "TRANSFER", "event_data": {},
        "current_phase": "AC_REVIEW",
        "node_outputs": {"post_ac": {
            "success_summary": "ok", "next_practice_direction": "nested loops",
            "courseware_refs": [],
        }},
        "runtime_state": "QUEUED", "pending_human_action": "",
        "behavior_metrics": {}, "evidence_pack": {}, "learner_state": {},
        "available_actions": [], "side_effects": {}, "execution_trace": [],
    }

    await graph.ainvoke(initial, config)
    await graph.ainvoke(Command(resume={"action": "confirm", "data": {}}), config)

    java.create_transfer_problem.assert_called_once()
    call_payload = java.create_transfer_problem.call_args[0][0]
    assert call_payload["idempotency_key"] == "s1:r1:transfer:materialize:v1"
    assert call_payload["user_id"] == 1
    assert call_payload["source_problem_id"] == 1


@pytest.mark.asyncio
async def test_transfer_resume_reject_does_not_materialize(mock_clients):
    """拒绝恢复后不得物化迁移练习。"""
    from langgraph.types import Command

    java, llm = mock_clients
    checkpointer = MemorySaver()
    graph = build_tutor_graph(java_client=java, llm_client=llm, checkpointer=checkpointer)

    config = {"configurable": {"thread_id": "t_test_3"}}
    initial = {
        "session_id": "s1", "thread_id": "t_test_3", "run_id": "r1",
        "user_id": 1, "problem_id": 1, "language": "Python3",
        "client_event": "TRANSFER", "event_data": {},
        "current_phase": "AC_REVIEW",
        "node_outputs": {"post_ac": {
            "success_summary": "ok", "next_practice_direction": "nested loops",
            "courseware_refs": [],
        }},
        "runtime_state": "QUEUED", "pending_human_action": "",
        "behavior_metrics": {}, "evidence_pack": {}, "learner_state": {},
        "available_actions": [], "side_effects": {}, "execution_trace": [],
    }

    await graph.ainvoke(initial, config)
    await graph.ainvoke(Command(resume={"action": "reject", "data": {}}), config)

    java.create_transfer_problem.assert_not_called()
