"""Chat node — contextual tutoring chat without changing phase."""

from __future__ import annotations

from app.clients.llm_client import LlmClient
from app.graph.state import TutorGraphState
from app.nodes.langfuse_metadata import build_langfuse_metadata
from app.nodes.prompts import assemble_learner_block

SYSTEM_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手。
在当前题目和当前学习阶段进行导学对话。
要求：
- 不给完整代码
- 不退化为普通闲聊
- 引用当前题、当前阶段、最近输出
- 如果用户消息中含有 @card:<id> 或 @last_xxx，必须明确引用对应卡片的内容
- 不要凭空编造没看到的卡片内容
- 输出 JSON 格式，字段: content, history(数组，每项含 role 和 content), referenced_card_ids(数组，仅包含你真实引用过的 card_id)"""


def _format_card_refs(label: str, cards: list[dict]) -> str:
    if not cards:
        return ""
    lines = [f"【{label}】"]
    for card in cards:
        if not isinstance(card, dict):
            continue
        card_id = card.get("card_id") or ""
        card_type = card.get("card_type") or ""
        short_text = card.get("short_text") or ""
        if not card_id:
            continue
        if len(short_text) > 200:
            short_text = short_text[:200] + "…"
        lines.append(f"- [{card_id}|{card_type}] {short_text}")
    return "\n".join(lines) if len(lines) > 1 else ""


async def chat_node(
    state: TutorGraphState,
    *,
    llm_client: LlmClient,
) -> TutorGraphState:
    event_data = state.get("event_data", {})
    message = event_data.get("message", "")
    phase = state.get("current_phase", "READING")
    user_mode = state.get("user_mode", "") or "chat"
    node_outputs = dict(state.get("node_outputs", {}))

    existing_chat = node_outputs.get("chat", {})
    history = list(existing_chat.get("history", []))
    if message:
        history.append({"role": "user", "content": message})

    references = state.get("references", []) or []
    last_cards = state.get("last_cards", []) or []
    references_block = _format_card_refs("用户显式引用的卡片", references)
    last_cards_block = _format_card_refs("最近卡片摘要（仅供上下文）", last_cards[:5])

    user_msg_parts = [
        f"当前阶段: {phase}",
        f"用户当前 Mode: {user_mode}",
        f"用户消息: {message}",
        f"对话历史: {history[-6:]}",
    ]
    if references_block:
        user_msg_parts.append(references_block)
    if last_cards_block:
        user_msg_parts.append(last_cards_block)
    user_msg = "\n".join(user_msg_parts)

    learner = state.get("evidence_pack", {}).get("learner_state", {})
    learner_block = assemble_learner_block(learner)
    system_prompt = SYSTEM_PROMPT + ("\n\n" + learner_block if learner_block else "")
    metadata = build_langfuse_metadata(state, "chat")
    metadata["learner_block_injected"] = bool(learner_block)
    metadata["references_count"] = len(references)
    metadata["last_cards_count"] = len(last_cards)

    try:
        result = await llm_client.generate_json(
            system_prompt,
            user_msg,
            node_name="chat",
            metadata=metadata,
        )
    except Exception as e:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SYSTEM_ERROR",
            "last_error": f"Chat generation failed: {e}",
        }

    if "history" not in result:
        result["history"] = history
    if message:
        result["history"] = history + [{"role": "assistant", "content": result.get("content", "")}]

    # Validate referenced_card_ids: only ids that actually appear in references / last_cards may
    # be persisted, so the LLM cannot fabricate ids that the frontend would render as broken
    # anchors.
    allowed_ids: set[str] = set()
    for card in references:
        if isinstance(card, dict) and card.get("card_id"):
            allowed_ids.add(str(card["card_id"]))
    for card in last_cards:
        if isinstance(card, dict) and card.get("card_id"):
            allowed_ids.add(str(card["card_id"]))

    raw_ids = result.get("referenced_card_ids")
    cleaned_ids: list[str] = []
    if isinstance(raw_ids, list):
        for item in raw_ids:
            if item is None:
                continue
            sid = str(item).strip()
            if sid and sid in allowed_ids and sid not in cleaned_ids:
                cleaned_ids.append(sid)
    result["referenced_card_ids"] = cleaned_ids

    node_outputs["chat"] = result
    return {**state, "node_outputs": node_outputs}
