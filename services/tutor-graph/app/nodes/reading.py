"""Problem guide node — generates the reading/导读 card."""

from __future__ import annotations

from app.clients.llm_client import LlmClient
from app.graph.state import TutorGraphState
from app.nodes.langfuse_metadata import build_langfuse_metadata
from app.nodes.output_normalization import normalize_courseware_refs
from app.nodes.prompts import assemble_learner_block

SYSTEM_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手。
用户正在审读一道编程题。请用中文生成题目导读。
要求：
- 用通俗语言重述题意
- 指出输入输出的关键约束
- 给出 1 个核心观察
- 给出 2-3 个引导性问题帮助学生思考，其中至少 1 个必须是自我检查问题
- 不给完整代码
- 如果输出 courseware_refs，数组里的每一项都必须是对象，优先复用提供给你的课件命中对象，禁止只输出 PPT 文件名字符串
- 若题目更适合先看流程图（例如分支较多、状态转换复杂），可额外输出:
  - visualize_intent: flowchart / data_flow
  - visualize_prompt: 给可视化生成器的明确描述
- 输出 JSON 格式，字段: problem_restatement, input_output_focus, key_observation, starter_questions, related_kcs, courseware_refs, teaching_goal, checkpoint_prompt, mentor_role, reflection_prompt, visualize_intent(可选), visualize_prompt(可选)"""


ANTI_CHEATING_GUARD = """
本次会话来自班级作业的 anti_cheating 模式，必须严格遵守：
- hint 等级降为 1：仅给概念性提示，不允许给出代码片段（包括关键变量赋值、函数签名）；
- 不要给出可直接复制的伪代码；
- 让学生自行尝试输出最小输入对应的预期结果，不要替学生执行算法。
"""


async def problem_guide_node(
    state: TutorGraphState,
    *,
    llm_client: LlmClient,
) -> TutorGraphState:
    evidence = state.get("evidence_pack", {})
    context = evidence.get("workflow_context", {})
    courseware = evidence.get("courseware_hits", {})
    learner = evidence.get("learner_state", {})
    language = state.get("language", "")
    session_context = state.get("context", {}) or {}
    anti_cheating = (
        session_context.get("source") == "classroom_assignment"
        and bool(session_context.get("anti_cheating"))
    )

    user_prompt = (
        f"题目信息:\n{_format_context(context)}\n"
        f"语言: {language}\n"
        f"课件参考: {_format_courseware(courseware)}"
    )

    learner_block = assemble_learner_block(learner)
    system_prompt = SYSTEM_PROMPT + ("\n\n" + learner_block if learner_block else "")
    if anti_cheating:
        system_prompt = system_prompt + ("\n\n" + ANTI_CHEATING_GUARD.strip())
    metadata = build_langfuse_metadata(state, "problem_guide")
    metadata["learner_block_injected"] = bool(learner_block)
    metadata["anti_cheating"] = anti_cheating

    try:
        result = await llm_client.generate_json(
            system_prompt,
            user_prompt,
            node_name="problem_guide",
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
    if "related_kcs" not in result:
        result["related_kcs"] = []
    result.setdefault("teaching_goal", "先把题目真正读懂，再开始写代码。")
    result.setdefault("checkpoint_prompt", "请先用自己的话重述题意，并说出一个你最担心漏掉的约束。")
    result.setdefault("mentor_role", "Nene")
    result.setdefault("reflection_prompt", "如果你现在就开始写代码，最可能漏掉哪一个条件？")

    node_outputs = dict(state.get("node_outputs", {}))
    node_outputs["problem_guide"] = result

    return {**state, "node_outputs": node_outputs}


def _format_context(ctx: dict) -> str:
    if not ctx:
        return "(无)"
    parts = []
    if ctx.get("statement"):
        parts.append(f"描述: {ctx['statement']}")
    if ctx.get("samples"):
        parts.append(f"样例: {ctx['samples']}")
    return "\n".join(parts) if parts else str(ctx)


def _format_courseware(cw: dict) -> str:
    if not cw:
        return "(无)"
    hits = cw.get("hits", [])
    if not hits:
        return "(无)"
    return "\n".join(str(h) for h in hits[:3])
