"""将学生触发的 PARSONS 事件路由到 Java 能力。"""

from __future__ import annotations

from app.clients.java_tools_client import JavaToolsClient
from app.graph.state import TutorGraphState


async def parsons_node(
    state: TutorGraphState,
    *,
    java_client: JavaToolsClient,
) -> TutorGraphState:
    event_data = state.get("event_data", {}) or {}

    request_payload = {
        "user_id": state.get("user_id"),
        "problem_id": state.get("problem_id"),
        "session_id": state.get("session_id"),
        "source_card_id": _normalize(event_data.get("source_card_id")),
        "previous_session_id": _normalize(event_data.get("previous_session_id")),
        "fsrs_origin": _normalize(event_data.get("fsrs_origin")),
    }
    override_level = event_data.get("override_fading_level")
    if isinstance(override_level, (int, float)):
        request_payload["override_fading_level"] = int(override_level)

    try:
        response = await java_client.dispatch_parsons(request_payload)
    except Exception as e:  # noqa: BLE001 - failfast 风格，包成 SYSTEM_ERROR
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SYSTEM_ERROR",
            "last_error": f"Parsons dispatch failed: {e}",
        }

    card_payload = _extract_card_payload(response)
    if not card_payload:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SCHEMA_VIOLATION",
            "last_error": "Parsons dispatch returned empty card payload",
        }

    node_outputs = dict(state.get("node_outputs", {}))
    node_outputs["parsons"] = card_payload
    return {**state, "node_outputs": node_outputs}


def _extract_card_payload(response: dict | None) -> dict:
    if not isinstance(response, dict):
        return {}
    payload_obj = response.get("card_payload")
    if isinstance(payload_obj, dict) and payload_obj:
        return payload_obj
    required = ("parsons_session_id", "fading_level", "blocks")
    if all(k in response for k in required):
        return {
            "parsons_session_id": response.get("parsons_session_id"),
            "fading_level": response.get("fading_level"),
            "blocks": response.get("blocks", []),
            "distractors": response.get("distractors", []),
            "mastery_snapshot": response.get("mastery_snapshot", {}),
            "instructions": response.get("instructions", ""),
            **(
                {"language": response["language"]}
                if "language" in response else {}
            ),
            **(
                {"fsrs_origin": response["fsrs_origin"]}
                if response.get("fsrs_origin") else {}
            ),
            **(
                {"previous_session_id": response["previous_session_id"]}
                if response.get("previous_session_id") else {}
            ),
        }
    return {}


def _normalize(value: object) -> str:
    if value is None:
        return ""
    return str(value).strip()
