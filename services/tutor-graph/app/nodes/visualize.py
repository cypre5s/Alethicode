"""将学生触发的 visualize 事件路由到 Java 能力。"""

from __future__ import annotations

from app.clients.java_tools_client import JavaToolsClient
from app.graph.state import TutorGraphState


DEFAULT_INTENT_BY_PHASE: dict[str, str] = {
    "ERROR_FEEDBACK": "for_loop_trace",
    "AC_REVIEW": "complexity_compare",
    "READING": "flowchart",
    "IDEATING": "flowchart",
    "CODING": "flowchart",
    "TRANSFER": "data_flow",
}


async def visualize_node(
    state: TutorGraphState,
    *,
    java_client: JavaToolsClient,
) -> TutorGraphState:
    event_data = state.get("event_data", {}) or {}
    intent = _normalize(event_data.get("intent"))
    prompt = _normalize(event_data.get("prompt"))
    if not intent:
        intent = DEFAULT_INTENT_BY_PHASE.get(state.get("current_phase", "READING"), "flowchart")
    if not prompt:
        prompt = _build_fallback_prompt(state, event_data)

    context_hints = event_data.get("context_hints")
    if not isinstance(context_hints, dict):
        context_hints = {
            "phase": state.get("current_phase", ""),
            "client_event": state.get("client_event", ""),
            "language": state.get("language", ""),
        }
        code = _normalize(event_data.get("code"))
        if code:
            context_hints["code_preview"] = code[:600]

    request_payload = {
        "intent": intent,
        "prompt": prompt,
        "context_hints": context_hints,
        "user_id": state.get("user_id"),
        "problem_id": state.get("problem_id"),
        "session_id": state.get("session_id"),
        "source_role": _normalize(event_data.get("source_role")) or "Student",
    }

    try:
        response = await java_client.dispatch_visualize(request_payload)
    except Exception as e:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SYSTEM_ERROR",
            "last_error": f"Visualize dispatch failed: {e}",
        }

    card_payload = _extract_card_payload(response)
    if not card_payload:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SCHEMA_VIOLATION",
            "last_error": "Visualize dispatch returned empty card payload",
        }

    node_outputs = dict(state.get("node_outputs", {}))
    node_outputs["visualize"] = card_payload
    return {**state, "node_outputs": node_outputs}


def _extract_card_payload(response: dict | None) -> dict:
    if not isinstance(response, dict):
        return {}
    payload_obj = response.get("card_payload")
    if isinstance(payload_obj, dict):
        return payload_obj
    required = ("intent", "format", "payload")
    if all(k in response for k in required):
        return {
            "intent": response.get("intent"),
            "format": response.get("format"),
            "payload": response.get("payload"),
            "alt_text": response.get("alt_text") or "",
            "source_role": response.get("source_role") or "AI",
        }
    return {}


def _build_fallback_prompt(state: TutorGraphState, event_data: dict) -> str:
    code = _normalize(event_data.get("code"))
    phase = _normalize(state.get("current_phase")) or "READING"
    if code:
        return (
            "请根据以下代码画出一张帮助初学者理解当前逻辑的教学图，突出关键分支与变量变化：\n"
            + code[:1000]
        )
    return f"请为当前 {phase} 阶段生成一张帮助初学者理解解题流程的教学示意图。"


def _normalize(value: object) -> str:
    return "" if value is None else str(value).strip()
