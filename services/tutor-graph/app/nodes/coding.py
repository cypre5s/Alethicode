"""Coding node — records code snapshot, optionally generates execution trace explanation."""

from __future__ import annotations

from app.clients.llm_client import LlmClient
from app.graph.state import TutorGraphState
from app.nodes.langfuse_metadata import build_langfuse_metadata
from app.nodes.prompts import assemble_learner_block

TRACE_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手。
请解释以下代码的执行过程。
输出 JSON 格式，字段: explanation, trace_steps(数组，每项含 line, description, variables), key_insight"""


async def coding_node(
    state: TutorGraphState,
    *,
    llm_client: LlmClient,
) -> TutorGraphState:
    event_data = state.get("event_data", {})
    node_outputs = dict(state.get("node_outputs", {}))

    if event_data.get("request_execution_trace"):
        code = event_data.get("code", "")
        language = state.get("language", "")
        user_msg = f"语言: {language}\n代码:\n{code}"

        learner = state.get("evidence_pack", {}).get("learner_state", {})
        learner_block = assemble_learner_block(learner)
        system_prompt = TRACE_PROMPT + ("\n\n" + learner_block if learner_block else "")
        metadata = build_langfuse_metadata(state, "execution_trace_explainer")
        metadata["learner_block_injected"] = bool(learner_block)

        try:
            result = await llm_client.generate_json(
                system_prompt,
                user_msg,
                node_name="execution_trace_explainer",
                metadata=metadata,
            )
            node_outputs["execution_trace_explainer"] = result
        except Exception as e:
            return {
                **state,
                "runtime_state": "FAILED",
                "failure_bucket": "SYSTEM_ERROR",
                "last_error": f"Execution trace generation failed: {e}",
            }

    return {**state, "node_outputs": node_outputs}
