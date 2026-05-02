"""Ideating node — thought analysis only."""

from __future__ import annotations

from app.clients.llm_client import LlmClient
from app.graph.state import TutorGraphState
from app.nodes.langfuse_metadata import build_langfuse_metadata
from app.nodes.prompts import assemble_learner_block

SYSTEM_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手。
学生正在思考解题思路。
要求：
- 只拆解步骤，不给完整答案
- 若思路明显错误，指出第一个关键偏差
- 若学生没有输入思路，给 2-3 个引导问题
- 必须追问“为什么这样想”，帮助学生把思路说完整
- 若当前思路更适合先看图（例如流程拆解、函数调用链），可额外输出:
  - visualize_intent: flowchart / data_flow / recursion_stack
  - visualize_prompt: 给可视化生成器的明确描述
- 输出 JSON 格式，字段: analysis, steps, guiding_questions, misconception_alert(可选), teaching_goal, checkpoint_prompt, mentor_role, reflection_prompt, visualize_intent(可选), visualize_prompt(可选)"""

async def ideating_node(
    state: TutorGraphState,
    *,
    llm_client: LlmClient,
) -> TutorGraphState:
    evidence = state.get("evidence_pack", {})
    context = evidence.get("workflow_context", {})
    learner = evidence.get("learner_state", {})
    event_data = state.get("event_data", {})
    language = state.get("language", "")

    thought_text = event_data.get("thought_text", "")
    learner_block = assemble_learner_block(learner)

    kc_section = evidence.get("kc", {})
    kc_error_profile = kc_section.get("kc_error_profile", [])
    kc_warning = ""
    if kc_error_profile:
        kc_lines = [f"  - {kp.get('kc_name', '?')}（{kp.get('error_count', 0)} 次错过）" for kp in kc_error_profile[:5]]
        kc_warning = "\n该学生在本题相关知识点上的历史错点：\n" + "\n".join(kc_lines)

    prompt = SYSTEM_PROMPT + ("\n\n" + learner_block if learner_block else "") + kc_warning
    user_msg = (
        f"题目: {context.get('statement', '(未知)')}\n"
        f"语言: {language}\n"
        f"学生思路: {thought_text}"
    )

    metadata = build_langfuse_metadata(state, "ideate")
    metadata["learner_block_injected"] = bool(learner_block)

    try:
        result = await llm_client.generate_json(
            prompt,
            user_msg,
            node_name="ideate",
            metadata=metadata,
        )
    except Exception as e:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SYSTEM_ERROR",
            "last_error": f"LLM generation failed: {e}",
        }

    result.setdefault("teaching_goal", "把思路外化出来，避免一边猜一边写。")
    result.setdefault("checkpoint_prompt", "请先解释你为什么选择这个处理顺序，再决定是否写代码。")
    result.setdefault("mentor_role", "Nene")
    result.setdefault("reflection_prompt", "如果这条思路失败，你最先会检查哪一步的假设？")

    node_outputs = dict(state.get("node_outputs", {}))
    node_outputs["ideate"] = result
    return {**state, "node_outputs": node_outputs}
