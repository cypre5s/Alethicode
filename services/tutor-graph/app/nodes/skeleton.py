"""通过显式工作流事件生成脚手架代码。"""

from __future__ import annotations

import json
from typing import Any

from app.clients.llm_client import LlmClient
from app.graph.state import TutorGraphState
from app.nodes.langfuse_metadata import build_langfuse_metadata
from app.nodes.prompts import assemble_learner_block

OFF_TOPIC_MARKERS = (
    "sklearn",
    "load_iris",
    "kneighbors",
    "kneighborsclassifier",
    "pandas",
    "numpy",
    "iris",
    "鸢尾花",
    "机器学习",
    "分类器",
    "训练集",
    "测试集",
    "模型准确率",
)

SYSTEM_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手。
学生请求生成代码骨架。
要求：
- 只生成可练习的代码骨架，不给完整答案
- 至少保留两个 TODO 注释
- 输入输出结构要完整，核心逻辑必须留空
- 注释默认使用简体中文，标识符使用清晰英文
- 必须严格围绕【当前题目】生成，禁止借用历史示例、课件样例或其他题目的数据集
- 禁止引入题面未要求的第三方库、机器学习模型、示例数据集或额外业务背景
- Python 骨架风格约束（除非题面或课件明确要求，否则强制遵守）：
  - 禁止使用 if __name__ == "__main__": 包裹主逻辑，直接在顶层写输入输出代码
  - 禁止使用 try-except 包裹输入读取，直接写 input() / int(input()) 等
  - 保持代码尽可能扁平、直白，适合零基础学生阅读
- 输出 JSON 格式，字段: description, skeleton, teaching_goal, checkpoint_prompt, mentor_role, reflection_prompt"""


async def skeleton_node(
    state: TutorGraphState,
    *,
    llm_client: LlmClient,
) -> TutorGraphState:
    evidence = state.get("evidence_pack", {})
    context = evidence.get("workflow_context", {})
    learner = evidence.get("learner_state", {})
    language = state.get("language", "")

    if not _text(context.get("statement")):
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "INSUFFICIENT_EVIDENCE",
            "last_error": "workflow_context.statement required for SKELETON",
        }

    learner_block = assemble_learner_block(learner)

    kc_section = evidence.get("kc", {})
    kc_error_profile = kc_section.get("kc_error_profile", [])
    kc_warning = ""
    if kc_error_profile:
        kc_lines = [f"  - {kp.get('kc_name', '?')}（{kp.get('error_count', 0)} 次错过）" for kp in kc_error_profile[:5]]
        kc_warning = "\n重点关注以下知识点：\n" + "\n".join(kc_lines)

    prompt = SYSTEM_PROMPT + ("\n\n" + learner_block if learner_block else "") + kc_warning
    user_msg = _build_user_message(context, language)

    metadata = build_langfuse_metadata(state, "skeleton")
    metadata["learner_block_injected"] = bool(learner_block)

    try:
        result = await llm_client.generate_json(
            prompt,
            user_msg,
            node_name="skeleton",
            metadata=metadata,
        )
    except Exception as e:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SYSTEM_ERROR",
            "last_error": f"LLM generation failed: {e}",
        }

    result.setdefault("description", "先把输入输出搭好，再逐步补全 TODO。")
    result.setdefault("teaching_goal", "先搭骨架，再把注意力集中到关键逻辑。")
    result.setdefault("checkpoint_prompt", "你准备先补哪一个 TODO？为什么？")
    result.setdefault("mentor_role", "Nene")
    result.setdefault("reflection_prompt", "如果现在直接写完整逻辑，你最容易漏掉哪一步？")

    grounding_error = _validate_skeleton_grounding(result, context)
    if grounding_error:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SCHEMA_VIOLATION",
            "last_error": grounding_error,
        }

    node_outputs = dict(state.get("node_outputs", {}))
    node_outputs["skeleton_code"] = result
    return {**state, "node_outputs": node_outputs}


def _build_user_message(context: dict[str, Any], language: str) -> str:
    samples = context.get("samples")
    if isinstance(samples, str):
        samples_text = samples
    else:
        samples_text = repr(samples or [])
    return (
        f"题目标题: {_text(context.get('title')) or '(未知)'}\n"
        f"题目描述: {_text(context.get('statement')) or '(未知)'}\n"
        f"输入说明: {_text(context.get('input_description')) or '(无)'}\n"
        f"输出说明: {_text(context.get('output_description')) or '(无)'}\n"
        f"样例: {samples_text}\n"
        f"语言: {language}\n"
        "请只为以上当前题目生成练习骨架。"
    )


def _validate_skeleton_grounding(result: dict[str, Any], context: dict[str, Any]) -> str:
    problem_text = _problem_text(context).lower()
    output_text = json.dumps(result, ensure_ascii=False).lower()
    for marker in OFF_TOPIC_MARKERS:
        normalized_marker = marker.lower()
        if normalized_marker in output_text and normalized_marker not in problem_text:
            return f"Skeleton output is not grounded in current problem: unexpected marker '{marker}'"
    return ""


def _problem_text(context: dict[str, Any]) -> str:
    return "\n".join([
        _text(context.get("title")),
        _text(context.get("statement")),
        _text(context.get("input_description")),
        _text(context.get("output_description")),
        _text(context.get("hint")),
    ])


def _text(value: Any) -> str:
    return "" if value is None else str(value).strip()
