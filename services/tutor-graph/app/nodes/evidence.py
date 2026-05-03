"""Assemble evidence pack by calling Java internal tool APIs."""

from __future__ import annotations

from app.clients.java_tools_client import JavaToolsClient
from app.graph.state import TutorGraphState

EVENT_EVIDENCE_REQUIREMENTS: dict[str, list[str]] = {
    "READING": ["workflow_context", "courseware_hits", "learner_state"],
    "IDEATING": ["workflow_context", "learner_state"],
    "SKELETON": ["workflow_context", "learner_state"],
    "CODING": ["workflow_context", "learner_state"],
    "ERROR_FEEDBACK": ["workflow_context", "diagnosis_evidence", "learner_state", "similar_errors"],
    "AC_REVIEW": ["diagnosis_evidence", "learner_state", "courseware_hits"],
    "TRANSFER": ["workflow_context", "learner_state"],
    "CHAT": ["learner_state", "last_cards", "references"],
    "AGENT_FEEDBACK": [],
    "KNOWLEDGE_REVIEW": ["workflow_context", "learner_state", "courseware_hits"],
    "PLAN_RECOMMEND": ["learner_state"],
    "PLAN_START": [],
    "PLAN_RESPONSE": [],
    "PLAN_STEERING": [],
}


async def assemble_evidence_pack(
    state: TutorGraphState,
    *,
    java_client: JavaToolsClient,
) -> TutorGraphState:
    event = state.get("client_event", "").upper()
    requirements = EVENT_EVIDENCE_REQUIREMENTS.get(event, [])
    evidence: dict = {}
    event_data = state.get("event_data", {})

    user_id = state.get("user_id", 0)
    problem_id = state.get("problem_id", 0)
    session_id = state.get("session_id", "")
    language = state.get("language", "")
    submission_id = event_data.get("submission_id", "")

    if "diagnosis_evidence" in requirements and not submission_id:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "INSUFFICIENT_EVIDENCE",
            "last_error": f"submission_id required for {event}",
        }

    try:
        if "workflow_context" in requirements:
            evidence["workflow_context"] = await java_client.get_workflow_context(
                problem_id, user_id=user_id, session_id=session_id, language=language,
            )

        if "diagnosis_evidence" in requirements:
            evidence["diagnosis_evidence"] = await java_client.get_diagnosis_evidence(
                submission_id, user_id=user_id, problem_id=problem_id, session_id=session_id,
            )

        if "learner_state" in requirements:
            workflow_ctx = evidence.get("workflow_context") or {}
            diag = evidence.get("diagnosis_evidence") or {}
            context_signals = {
                "current_kcs": _kc_names_from_context(workflow_ctx),
                "current_error_context": diag.get("err_info", "") or diag.get("result", ""),
                "current_problem_statement": (workflow_ctx.get("statement") or "")[:1000],
            }
            evidence["learner_state"] = await java_client.get_learner_state(
                user_id, problem_id=problem_id, session_id=session_id, language=language,
                context_signals=context_signals,
            )

        if "courseware_hits" in requirements:
            evidence["courseware_hits"] = await java_client.get_courseware_hits(
                problem_id, user_id=user_id, session_id=session_id,
            )

        if "similar_errors" in requirements:
            evidence["similar_errors"] = await java_client.get_similar_errors(
                user_id, problem_id=problem_id, session_id=session_id, language=language,
            )

        if "last_cards" in requirements:
            evidence["last_cards"] = await java_client.get_last_cards(session_id, limit=5)

        if "references" in requirements:
            raw_refs = (event_data or {}).get("references", []) or []
            normalized: list[str] = []
            if isinstance(raw_refs, list):
                for item in raw_refs:
                    if item is None:
                        continue
                    text = str(item).strip()
                    if text:
                        normalized.append(text)
            # Pass the user's current message as the RAG query so backend can resolve any
            # @courseware:<lpId> tokens into top-k page chunks. Backwards compat: legacy
            # call sites that don't include @courseware tokens get empty `coursewares`.
            current_query = ""
            if isinstance(event_data, dict):
                msg = event_data.get("message")
                if isinstance(msg, str):
                    current_query = msg.strip()
            if normalized:
                resolved = await java_client.resolve_references(
                    session_id, normalized, current_query=current_query or None
                )
                # Preserve old field shape (list of cards) for downstream nodes that haven't
                # adopted the new dict shape yet, but also expose coursewares for prompt nodes.
                if isinstance(resolved, dict):
                    evidence["references"] = resolved.get("cards", [])
                    evidence["coursewares"] = resolved.get("coursewares", [])
                else:
                    # Defensive fallback: old call site that returned a flat list.
                    evidence["references"] = resolved if isinstance(resolved, list) else []
                    evidence["coursewares"] = []
            else:
                evidence["references"] = []
                evidence["coursewares"] = []

    except Exception as e:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "TOOL_EXECUTION_FAILED",
            "last_error": f"Evidence assembly failed: {e}",
        }

    user_mode = ""
    if isinstance(event_data, dict):
        mode_raw = event_data.get("mode")
        if isinstance(mode_raw, str):
            user_mode = mode_raw.strip()

    return {
        **state,
        "evidence_pack": evidence,
        "learner_state": evidence.get("learner_state", {}),
        "user_mode": user_mode,
        "last_cards": evidence.get("last_cards", []),
        "references": evidence.get("references", []),
    }


def _kc_names_from_context(workflow_ctx: dict) -> list[str]:
    """Extract human-readable KC names from workflow_context for the semantic memory query."""
    names: list[str] = []
    candidates = workflow_ctx.get("kc_names") or workflow_ctx.get("kcs") or []
    if isinstance(candidates, list):
        for item in candidates:
            if isinstance(item, str) and item.strip():
                names.append(item.strip())
            elif isinstance(item, dict) and item.get("name"):
                names.append(str(item["name"]).strip())
    return names
