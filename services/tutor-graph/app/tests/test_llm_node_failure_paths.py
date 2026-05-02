from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest
from langgraph.checkpoint.memory import MemorySaver

from app.graph.builder import build_tutor_graph


LLM_FAILURE_CASES = [
    pytest.param("READING", "READING", {}, "LLM generation failed", id="reading"),
    pytest.param("READING", "IDEATING", {}, "LLM generation failed", id="ideating"),
    pytest.param("CODING", "SKELETON", {}, "LLM generation failed", id="skeleton"),
    pytest.param(
        "IDEATING",
        "CODING",
        {"code": "print(1 + 2)", "request_execution_trace": True},
        "Execution trace generation failed",
        id="coding-execution-trace",
    ),
    pytest.param(
        "CODING",
        "ERROR_FEEDBACK",
        {"submission_id": 1001},
        "LLM generation failed",
        id="error-feedback",
    ),
    pytest.param(
        "CODING",
        "AC_REVIEW",
        {"submission_id": 1001},
        "LLM generation failed",
        id="ac-review",
    ),
    pytest.param("AC_REVIEW", "TRANSFER", {}, "Transfer draft generation failed", id="transfer"),
    pytest.param("CODING", "CHAT", {"message": "为什么这里会错？"}, "Chat generation failed", id="chat"),
    pytest.param(
        "READING",
        "KNOWLEDGE_REVIEW",
        {},
        "Knowledge review generation failed",
        id="knowledge-review",
    ),
]


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("current_phase", "client_event", "event_data", "last_error_prefix"),
    LLM_FAILURE_CASES,
)
async def test_llm_node_failure_paths_are_projected_as_system_error(
    current_phase: str,
    client_event: str,
    event_data: dict,
    last_error_prefix: str,
):
    java = _java_client()
    llm = MagicMock()
    llm.generate_json = AsyncMock(side_effect=RuntimeError("timeout from primary provider"))

    graph = build_tutor_graph(java_client=java, llm_client=llm, checkpointer=MemorySaver())
    config = {"configurable": {"thread_id": f"thread_llm_failure_{client_event.lower()}"}}
    input_state = _state(current_phase=current_phase, client_event=client_event, event_data=event_data)

    result = await graph.ainvoke(input_state, config)

    assert result["runtime_state"] == "FAILED"
    assert result["failure_bucket"] == "SYSTEM_ERROR"
    assert result["last_error"].startswith(last_error_prefix)
    assert "timeout from primary provider" in result["last_error"]
    assert result["node_outputs"]["preexisting"] == {"kept": True}
    assert result["available_actions"] == [{"type": "noop", "label": "keep me"}]

    java.post_workflow_event.assert_awaited_once()
    payload = java.post_workflow_event.await_args.args[0]
    assert payload["server_event"] == "TASK_FAILED"
    assert payload["runtime_state"] == "FAILED"
    assert payload["failure_bucket"] == "SYSTEM_ERROR"
    assert payload["client_event"] == client_event
    assert payload["error"] == result["last_error"]
    assert payload["node_outputs"]["preexisting"] == {"kept": True}


def _java_client() -> MagicMock:
    java = MagicMock()
    java.get_workflow_context = AsyncMock(return_value={
        "title": "两数求和",
        "statement": "输入两个整数，输出它们的和。",
        "input_description": "一行两个整数。",
        "output_description": "输出一个整数。",
        "samples": [{"input": "1 2", "output": "3"}],
        "kc_names": ["输入输出", "表达式"],
    })
    java.get_diagnosis_evidence = AsyncMock(return_value={
        "result": "Wrong Answer",
        "code": "a, b = map(int, input().split())\nprint(a - b)",
        "language": "Python3",
        "err_info": "答案错误",
    })
    java.get_learner_state = AsyncMock(return_value={
        "recent_errors": [{"kc": "表达式", "summary": "加减号混淆"}],
    })
    java.get_courseware_hits = AsyncMock(return_value={"hits": []})
    java.get_similar_errors = AsyncMock(return_value=[])
    java.get_last_cards = AsyncMock(return_value=[])
    java.resolve_references = AsyncMock(return_value=[])
    java.post_workflow_event = AsyncMock(return_value={"status": "recorded"})
    return java


def _state(*, current_phase: str, client_event: str, event_data: dict) -> dict:
    node_outputs = {"preexisting": {"kept": True}}
    if client_event == "TRANSFER":
        node_outputs["post_ac"] = {"next_practice_direction": "练习加法表达式与输入输出。"}
    return {
        "session_id": f"twf_llm_failure_{client_event.lower()}",
        "thread_id": f"thread_llm_failure_{client_event.lower()}",
        "run_id": f"run_llm_failure_{client_event.lower()}",
        "user_id": 1,
        "problem_id": 379,
        "language": "Python3",
        "client_event": client_event,
        "event_data": event_data,
        "current_phase": current_phase,
        "node_outputs": node_outputs,
        "behavior_metrics": {},
        "evidence_pack": {},
        "learner_state": {},
        "available_actions": [{"type": "noop", "label": "keep me"}],
        "runtime_state": "QUEUED",
        "pending_human_action": "",
        "side_effects": {},
        "execution_trace": [],
    }
