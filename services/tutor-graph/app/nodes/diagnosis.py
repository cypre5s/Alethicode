"""错误反馈与诊断节点。"""

from __future__ import annotations

from app.clients.llm_client import LlmClient
from app.graph.state import TutorGraphState
from app.nodes.langfuse_metadata import build_langfuse_metadata
from app.nodes.prompts import assemble_learner_block

SYSTEM_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手。
学生提交了错误的代码。请分析错误原因。
要求：
- 不给完整可提交代码
- 给出根本原因和修复方向
- 必须先依据题目文本、输入说明、输出说明判断期望行为；若题面指定常量、精度或输出格式，以题面为准
- 不要建议与题面冲突的替代实现（例如题面明确要求 3.1415 时，不要建议改用 math.pi）
- 区分“程序现在在做什么”和“你当时为什么可能会这样写”
- 若有历史重复错误，给出针对性提醒
- 在“循环边界 / 递归 / 数据结构状态 / 引用语义”这类更适合看图的场景，可额外输出:
  - visualize_intent: for_loop_trace / recursion_stack / data_structure_state / memory_layout / data_flow / flowchart
  - visualize_prompt: 给可视化生成器的明确描述（例如“画 range(5) 的迭代过程，强调 i 实际取值范围”）
- 输出 JSON 格式，字段: root_cause, what_program_is_doing, expected_behavior, fix_direction, related_kcs, error_pattern, is_recurring, encouragement, teaching_goal, checkpoint_prompt, mentor_role, reflection_prompt, visualize_intent(可选), visualize_prompt(可选)"""


ANTI_CHEATING_GUARD = """
本次会话来自班级作业的 anti_cheating 模式，必须严格遵守：
- hint 等级降为 1：只给概念性纠错方向，不许出现可复制的代码片段或伪代码；
- 修复方向写成自然语言（例如：检查循环初始值是否覆盖第一个元素），不要直接写"应改为 i=0"这类赋值；
- 鼓励学生自己用纸笔或最小输入对照执行，确认错误来源。
"""


async def error_feedback_node(
    state: TutorGraphState,
    *,
    llm_client: LlmClient,
) -> TutorGraphState:
    evidence = state.get("evidence_pack", {})
    problem = evidence.get("workflow_context", {})
    diag = evidence.get("diagnosis_evidence", {})
    learner = evidence.get("learner_state", {})
    session_context = state.get("context", {}) or {}
    anti_cheating = (
        session_context.get("source") == "classroom_assignment"
        and bool(session_context.get("anti_cheating"))
    )

    user_msg = (
        f"{_format_problem_context(problem)}\n\n"
        f"提交结果: {diag.get('result', '未知')}\n"
        f"代码:\n{diag.get('code', '(无)')}\n"
        f"语言: {diag.get('language', state.get('language', ''))}\n"
        f"错误信息: {diag.get('err_info', '(无)')}"
    )

    learner_block = assemble_learner_block(learner)
    system_prompt = SYSTEM_PROMPT + ("\n\n" + learner_block if learner_block else "")
    if anti_cheating:
        system_prompt = system_prompt + ("\n\n" + ANTI_CHEATING_GUARD.strip())
    metadata = build_langfuse_metadata(state, "error_diagnosis")
    metadata["learner_block_injected"] = bool(learner_block)
    metadata["anti_cheating"] = anti_cheating

    try:
        result = await llm_client.generate_json(
            system_prompt,
            user_msg,
            node_name="error_diagnosis",
            metadata=metadata,
        )
    except Exception as e:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SYSTEM_ERROR",
            "last_error": f"LLM generation failed: {e}",
        }

    result.setdefault("teaching_goal", "先看懂代码实际行为，再定位思维偏差。")
    result.setdefault("checkpoint_prompt", "请先说出程序现在会发生什么，再说你原本希望它发生什么。")
    result.setdefault("mentor_role", "Yoshino")
    result.setdefault("reflection_prompt", "下次遇到同类错误时，你准备先检查哪一个变量或分支？")

    node_outputs = dict(state.get("node_outputs", {}))
    node_outputs["error_diagnosis"] = result
    return {**state, "node_outputs": node_outputs}


def _format_problem_context(context: dict) -> str:
    return "\n".join([
        f"题目标题: {_text(context.get('title')) or '(未知)'}",
        f"题目正文: {_text(context.get('statement')) or '(无)'}",
        f"输入说明: {_text(context.get('input_description')) or '(无)'}",
        f"输出说明: {_text(context.get('output_description')) or '(无)'}",
    ])


def _text(value: object) -> str:
    if value is None:
        return ""
    return str(value).strip()
