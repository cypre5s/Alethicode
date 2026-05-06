"""生成不改变阶段的 KC 短复习节点。"""

from __future__ import annotations

from app.clients.llm_client import LlmClient
from app.graph.state import TutorGraphState
from app.nodes.langfuse_metadata import build_langfuse_metadata
from app.nodes.output_normalization import normalize_courseware_refs
from app.nodes.prompts import assemble_learner_block

SYSTEM_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手。
请基于当前题目相关的薄弱知识点做简短知识回顾。
要求：
- 不进入 QA citation 协议
- 不回答与当前题无关的泛问题
- review_content 末尾必须引用相关课件章节（例："可回顾第四章·流程自动化 第38页"）
- review_content 最后以一个引导思考的问句结尾，帮助学生深入理解该知识点
- 输出 JSON 格式，字段: review_content (string), related_kcs (array of string KC names, 禁止输出纯数字 id), courseware_refs (array of object), practice_suggestions (array of string)
- related_kcs 里每个元素必须是**字符串形式的 KC 名称或编号**（例：\"range-边界\"、\"KC-880 列表切片\"），即使上游提供的是数字 id，也要转成带语义的字符串
- 若回顾内容适合可视化，可额外输出:
  - visualize_intent: kc_mastery_radar / flowchart / data_flow
  - visualize_prompt: 给可视化生成器的明确描述
- 输出 JSON 格式，字段: review_content (string), related_kcs (array of string KC names, 禁止输出纯数字 id), courseware_refs (array of object), practice_suggestions (array of string), visualize_intent(可选), visualize_prompt(可选)
"""


async def knowledge_review_node(
    state: TutorGraphState,
    *,
    llm_client: LlmClient,
) -> TutorGraphState:
    evidence = state.get("evidence_pack", {})
    learner = evidence.get("learner_state", {})
    courseware = evidence.get("courseware_hits", {})

    weak_kcs = learner.get("weak_kcs", [])
    mastery_by_kc = learner.get("mastery_by_kc", {})
    current_kcs = [
        str(name).strip()
        for name in (mastery_by_kc.keys() if isinstance(mastery_by_kc, dict) else [])
        if str(name).strip()
    ]
    candidate_kcs = weak_kcs if weak_kcs else current_kcs[:5]
    user_msg = (
        f"薄弱知识点: {weak_kcs}\n"
        f"当前题目知识点候选(related_kcs 仅可从此选择): {candidate_kcs}\n"
        f"课件参考: {courseware}"
    )

    learner_block = assemble_learner_block(learner)
    system_prompt = SYSTEM_PROMPT + ("\n\n" + learner_block if learner_block else "")
    metadata = build_langfuse_metadata(state, "knowledge_review")
    metadata["learner_block_injected"] = bool(learner_block)

    try:
        result = await llm_client.generate_json(
            system_prompt,
            user_msg,
            node_name="knowledge_review",
            metadata=metadata,
        )
    except Exception as e:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SYSTEM_ERROR",
            "last_error": f"Knowledge review generation failed: {e}",
        }

    result["courseware_refs"] = normalize_courseware_refs(
        result.get("courseware_refs"),
        fallback_hits=courseware,
    )

    # 接口边界规范化：LLM 可能把 `weak_kcs` 里的数字 id 直接抄进来，schema
    # 要求每项是 string。此处做类型规约（非兜底，是把 LLM 自由输出对齐到
    # JSON Schema 契约）；若转换失败（例：dict / 非标量）则直接丢弃该项，
    # 保证字段上的类型不变。
    raw_kcs = result.get("related_kcs")
    if isinstance(raw_kcs, list):
        normalized_kcs: list[str] = []
        for kc in raw_kcs:
            if kc is None or kc == "":
                continue
            if isinstance(kc, (str, int, float)):
                normalized_kcs.append(str(kc).strip())
        result["related_kcs"] = normalized_kcs

    raw_suggestions = result.get("practice_suggestions")
    if isinstance(raw_suggestions, list):
        result["practice_suggestions"] = [
            str(s).strip() for s in raw_suggestions
            if s is not None and s != "" and isinstance(s, (str, int, float))
        ]

    node_outputs = dict(state.get("node_outputs", {}))
    node_outputs["knowledge_review"] = result
    return {**state, "node_outputs": node_outputs}
