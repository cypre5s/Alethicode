"""Transfer nodes — draft generation + LangGraph interrupt + idempotent materialization."""

from __future__ import annotations

import hashlib
import json

from langgraph.types import interrupt

from app.clients.java_tools_client import JavaToolsClient
from app.clients.llm_client import LlmClient
from app.graph.state import TutorGraphState
from app.nodes.langfuse_metadata import build_langfuse_metadata
from app.nodes.prompts import assemble_learner_block

DRAFT_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手。
请根据 AC 复盘方向生成一道迁移练习题草稿。
输出 JSON 格式，字段: title, description, input_description, output_description, samples(数组), test_cases(数组), hint, reference_solution_language, reference_solution_code, target_kcs(数组)"""


async def transfer_draft_node(
    state: TutorGraphState,
    *,
    llm_client: LlmClient,
) -> TutorGraphState:
    evidence = state.get("evidence_pack", {})
    node_outputs = dict(state.get("node_outputs", {}))
    post_ac = node_outputs.get("post_ac", {})
    direction = post_ac.get("next_practice_direction", "")

    if not direction:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "INSUFFICIENT_EVIDENCE",
            "last_error": "post_ac.next_practice_direction is required for TRANSFER",
        }

    learner = evidence.get("learner_state", {})
    user_msg = (
        f"练习方向: {direction}\n"
        f"语言: {state.get('language', 'Python3')}"
    )

    learner_block = assemble_learner_block(learner)
    system_prompt = DRAFT_PROMPT + ("\n\n" + learner_block if learner_block else "")
    metadata = build_langfuse_metadata(state, "transfer_draft")
    metadata["learner_block_injected"] = bool(learner_block)

    try:
        draft = await llm_client.generate_json(
            system_prompt,
            user_msg,
            node_name="transfer_draft",
            metadata=metadata,
        )
    except Exception as e:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SYSTEM_ERROR",
            "last_error": f"Transfer draft generation failed: {e}",
        }

    node_outputs["transfer_draft"] = draft

    human_response = interrupt({
        "type": "confirm_transfer",
        "draft": draft,
        "message": "是否创建这道迁移练习？",
    })

    action = human_response.get("action", "reject") if isinstance(human_response, dict) else "reject"

    if action == "reject":
        return {
            **state,
            "node_outputs": node_outputs,
            "pending_human_action": "",
            "runtime_state": "COMPLETED",
        }

    if action == "modify":
        return {
            **state,
            "node_outputs": node_outputs,
            "pending_human_action": "",
            "client_event": "TRANSFER",
            "event_data": {**state.get("event_data", {}), "modify_instruction": human_response.get("data", {})},
        }

    node_outputs["_transfer_confirmed"] = True
    return {
        **state,
        "node_outputs": node_outputs,
        "pending_human_action": "",
    }


async def materialize_transfer_problem_node(
    state: TutorGraphState,
    *,
    java_client: JavaToolsClient,
) -> TutorGraphState:
    node_outputs = dict(state.get("node_outputs", {}))
    draft = node_outputs.get("transfer_draft", {})
    if not draft:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "INSUFFICIENT_EVIDENCE",
            "last_error": "transfer_draft is missing",
        }

    session_id = state.get("session_id", "")
    run_id = state.get("run_id", "")
    idempotency_key = f"{session_id}:{run_id}:transfer:materialize:v1"
    request_hash = hashlib.sha256(
        json.dumps(draft, sort_keys=True, ensure_ascii=False).encode()
    ).hexdigest()

    payload = {
        "session_id": session_id,
        "run_id": run_id,
        "idempotency_key": idempotency_key,
        "user_id": state.get("user_id", 0),
        "source_problem_id": state.get("problem_id", 0),
        "language": state.get("language", ""),
        "request_hash": request_hash,
        "draft": draft,
    }

    try:
        result = await java_client.create_transfer_problem(payload)
    except Exception as e:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SYSTEM_ERROR",
            "last_error": f"Transfer materialization failed: {e}",
        }

    transfer_output = {**draft}
    transfer_output["problem_id"] = result.get("problem_id")
    transfer_output["problem_display_id"] = result.get("problem_display_id")
    transfer_output["temporary_problem"] = result.get("temporary_problem", True)

    node_outputs["transfer"] = transfer_output
    return {
        **state,
        "node_outputs": node_outputs,
        "pending_human_action": "",
    }
