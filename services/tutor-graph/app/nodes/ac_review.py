"""生成 AC 后复盘与下一步练习建议。"""

from __future__ import annotations

from app.clients.llm_client import LlmClient
from app.graph.state import TutorGraphState
from app.nodes.langfuse_metadata import build_langfuse_metadata
from app.nodes.output_normalization import normalize_courseware_refs
from app.nodes.prompts import assemble_learner_block

SYSTEM_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手。
学生已 AC 本题。请做 AC 复盘。
要求：
- 先总结学生做对了什么
- 再给一个最小优化方向
- 必须输出 next_practice_direction（供迁移题使用）
- 必须加入“以后如何更早发现同类问题”的反思句
- 如果输出 courseware_refs，数组里的每一项都必须是对象，优先复用提供给你的课件命中对象，禁止只输出 PPT 文件名字符串
- 若适合做复杂度或流程对比，可额外输出:
  - visualize_intent: complexity_compare / flowchart / data_flow
  - visualize_prompt: 给可视化生成器的明确描述
- 输出 JSON 格式，字段: success_summary, key_action, code_quality_notes, knowledge_points, next_practice_direction, courseware_refs, teaching_goal, checkpoint_prompt, mentor_role, reflection_prompt, visualize_intent(可选), visualize_prompt(可选)"""


async def ac_review_node(
    state: TutorGraphState,
    *,
    llm_client: LlmClient,
) -> TutorGraphState:
    evidence = state.get("evidence_pack", {})
    diag = evidence.get("diagnosis_evidence", {})
    learner = evidence.get("learner_state", {})
    courseware = evidence.get("courseware_hits", {})

    user_msg = (
        f"代码:\n{diag.get('code', '(无)')}\n"
        f"语言: {state.get('language', '')}\n"
        f"课件参考: {courseware}"
    )

    learner_block = assemble_learner_block(learner)
    system_prompt = SYSTEM_PROMPT + ("\n\n" + learner_block if learner_block else "")
    metadata = build_langfuse_metadata(state, "post_ac")
    metadata["learner_block_injected"] = bool(learner_block)

    try:
        result = await llm_client.generate_json(
            system_prompt,
            user_msg,
            node_name="post_ac",
            metadata=metadata,
        )
    except Exception as e:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SYSTEM_ERROR",
            "last_error": f"LLM generation failed: {e}",
        }

    result["courseware_refs"] = normalize_courseware_refs(
        result.get("courseware_refs"),
        fallback_hits=courseware,
    )
    result.setdefault("teaching_goal", "把这次做对的原因总结成下次也能复用的策略。")
    result.setdefault("checkpoint_prompt", "请用一句话说出这次为什么改对，再说下次如何更早发现。")
    result.setdefault("mentor_role", "Kanna")
    result.setdefault("reflection_prompt", "下次再遇到类似题目时，你会先检查什么，避免重复同类错误？")

    for array_field in ("code_quality_notes", "knowledge_points"):
        val = result.get(array_field)
        if isinstance(val, str):
            result[array_field] = [val] if val.strip() else []
        elif not isinstance(val, list):
            result[array_field] = []
        else:
            result[array_field] = [
                str(item).strip() for item in val
                if item is not None and str(item).strip()
            ]

    node_outputs = dict(state.get("node_outputs", {}))
    node_outputs["post_ac"] = result
    return {**state, "node_outputs": node_outputs}
