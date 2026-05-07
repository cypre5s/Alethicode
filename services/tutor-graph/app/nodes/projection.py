"""将投影持久化到 Java，投影失败即 run 失败。"""

from __future__ import annotations

import logging

from app.clients.java_tools_client import JavaToolsClient
from app.graph.state import TutorGraphState
from app.nodes.output_sanitization import strip_projection_visualize_helpers
from app.nodes.coach_plan import build_plan_payload

logger = logging.getLogger("tutor_graph.nodes.projection")


async def persist_projection(
    state: TutorGraphState,
    *,
    java_client: JavaToolsClient,
) -> TutorGraphState:
    node_outputs = strip_projection_visualize_helpers(state.get("node_outputs", {}))
    runtime_state = state.get("runtime_state", "COMPLETED")
    failure_bucket = state.get("failure_bucket")
    last_error = state.get("last_error", "") or ""

    try:
        node_outputs = await _dispatch_visualize_cards(state, node_outputs, java_client=java_client)
    except _VisualizeDispatchFailed as e:
        runtime_state = "FAILED"
        failure_bucket = "SCHEMA_VIOLATION"
        last_error = str(e)

    payload = {
        "session_id": state.get("session_id", ""),
        "run_id": state.get("run_id", ""),
        "thread_id": state.get("thread_id", ""),
        "event_type": state.get("client_event", ""),
        "runtime_state": runtime_state,
        "server_event": "TASK_FAILED" if runtime_state == "FAILED" else "TASK_COMPLETED",
        "client_event": state.get("client_event", ""),
        "failure_bucket": failure_bucket,
        "trace_id": state.get("trace_id", ""),
        "phase": state.get("current_phase", ""),
        "node_outputs": node_outputs,
        "available_actions": state.get("available_actions", []),
        "pending_human_action": state.get("pending_human_action", ""),
        "behavior_metrics": state.get("behavior_metrics", {}),
        "plan": build_plan_payload(state),
        "recommendation_reason": state.get("recommendation_reason", ""),
        "error": last_error,
    }

    try:
        await java_client.post_workflow_event(payload)
    except Exception as e:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SYSTEM_ERROR",
            "last_error": f"Projection write failed: {e}",
        }

    return {
        **state,
        "node_outputs": node_outputs,
        "runtime_state": runtime_state,
        "failure_bucket": failure_bucket,
        "last_error": last_error,
    }


class _VisualizeDispatchFailed(Exception):
    """节点声明的可视化意图无法生成合法卡片时抛出。"""

    def __init__(self, node_name: str, reason: str):
        super().__init__(f"visualize dispatch failed for node {node_name}: {reason}")
        self.node_name = node_name
        self.reason = reason


async def _dispatch_visualize_cards(
    state: TutorGraphState,
    node_outputs: dict,
    *,
    java_client: JavaToolsClient,
) -> dict:
    if not node_outputs:
        return node_outputs
    # VISUALIZE 事件已直接物化 node_outputs["visualize"]。
    if "visualize" in node_outputs:
        return node_outputs

    for node_name, output in list(node_outputs.items()):
        if not isinstance(output, dict):
            continue
        intent = _normalize(output.get("visualize_intent"))
        prompt = _normalize(output.get("visualize_prompt"))
        if not intent and not prompt:
            continue
        # 只声明部分 intent / prompt 表示 prompt 配置错误，直接 fail-fast。
        if not intent or not prompt:
            raise _VisualizeDispatchFailed(
                node_name,
                f"missing {'intent' if not intent else 'prompt'} for declared visualize_intent",
            )

        request_payload = {
            "intent": intent,
            "prompt": prompt,
            "context_hints": {
                "node_name": node_name,
                "phase": state.get("current_phase", ""),
                "current_kcs": output.get("related_kcs", []),
                "client_event": state.get("client_event", ""),
            },
            "user_id": state.get("user_id"),
            "problem_id": state.get("problem_id"),
            "session_id": state.get("session_id"),
            "source_role": _normalize(output.get("mentor_role")) or "AI",
        }

        try:
            response = await java_client.dispatch_visualize(request_payload)
        except Exception as e:
            raise _VisualizeDispatchFailed(node_name, f"transport error: {e}") from e

        card_payload = _extract_card_payload(response)
        if not card_payload:
            raise _VisualizeDispatchFailed(node_name, "empty card payload")
        node_outputs["visualize"] = card_payload

    return node_outputs


def _extract_card_payload(response: dict | None) -> dict:
    if not isinstance(response, dict):
        return {}
    card_payload = response.get("card_payload")
    if isinstance(card_payload, dict):
        return card_payload
    required = ("intent", "format", "payload")
    if all(key in response for key in required):
        return {
            "intent": response.get("intent"),
            "format": response.get("format"),
            "payload": response.get("payload"),
            "alt_text": response.get("alt_text") or "",
            "source_role": response.get("source_role") or "AI",
        }
    return {}


def _normalize(value: object) -> str:
    return "" if value is None else str(value).strip()
