"""Compact node — compress older conversation history into a summary."""

from __future__ import annotations

from app.clients.llm_client import LlmClient
from app.graph.state import TutorGraphState
from app.nodes.langfuse_metadata import build_langfuse_metadata

COMPACT_K = 6

COMPACT_SYSTEM_PROMPT = (
    "请把以下对话历史浓缩为一段简洁摘要（不超过 300 字），"
    "保留关键概念、代码片段和学生理解水平描述。"
    "只输出摘要文本，不加任何 JSON 包裹或额外标注。"
)


async def compact_node(
    state: TutorGraphState,
    *,
    llm_client: LlmClient,
) -> TutorGraphState:
    node_outputs = dict(state.get("node_outputs", {}))
    existing_chat = node_outputs.get("chat", {})
    history = list(existing_chat.get("history", []))

    if len(history) <= COMPACT_K:
        return {**state, "node_outputs": node_outputs}

    old_messages = history[:-COMPACT_K]
    recent_messages = history[-COMPACT_K:]

    old_text_parts: list[str] = []
    for msg in old_messages:
        if isinstance(msg, dict):
            role = msg.get("role", "unknown")
            content = msg.get("content", "")
            old_text_parts.append(f"[{role}] {content}")
    old_text = "\n".join(old_text_parts)

    metadata = build_langfuse_metadata(state, "compact")

    try:
        summary = await llm_client.generate(
            COMPACT_SYSTEM_PROMPT,
            old_text,
            node_name="compact",
            metadata=metadata,
        )
    except Exception as e:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SYSTEM_ERROR",
            "last_error": f"Compact generation failed: {e}",
        }

    compacted_history = [
        {"role": "system", "content": summary.strip()},
        *recent_messages,
    ]

    updated_chat = dict(existing_chat)
    updated_chat["history"] = compacted_history
    node_outputs["chat"] = updated_chat

    return {**state, "node_outputs": node_outputs}
