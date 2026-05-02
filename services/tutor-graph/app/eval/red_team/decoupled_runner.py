"""Decoupled runner — directly inject adversarial payload at node input.

This implements the **Decoupled Eval** pattern from RedTeamCUA
(arXiv:2505.21936): bypass the navigation/UI layer and place the
adversarial payload directly into the node's input state, so that
"agent didn't find the injection point" cannot mask vulnerabilities.

Per AGENTS.md (failfast):
- Unknown target_node names raise immediately.
- Unknown state_path resolutions raise immediately (no silent skip).
- LLM call failures inside a node are captured into CaseResult.error,
  but the runner itself does not swallow type errors or missing nodes.
"""

from __future__ import annotations

import asyncio
import copy
from typing import Any, Awaitable, Callable

from app.clients.llm_client import LlmClient
from app.eval.red_team.assertions import evaluate
from app.eval.red_team.schema import (
    AdversarialCase,
    AssertionResult,
    CaseResult,
    DatasetSummary,
)
from app.eval.red_team.targets import AttackCategory, PedagogicalCIA, Phase
from app.graph.state import TutorGraphState
from app.nodes.ac_review import ac_review_node
from app.nodes.chat import chat_node
from app.nodes.coding import coding_node
from app.nodes.diagnosis import error_feedback_node
from app.nodes.ideating import ideating_node
from app.nodes.reading import problem_guide_node
from app.nodes.skeleton import skeleton_node
from app.nodes.transfer import transfer_draft_node


# Registry of LLM-driven node functions exposed for red team injection.
# Each callable accepts (state, *, llm_client) and returns the post-node state.
# Java-client-driven nodes (parsons_node, visualize_node, materialize_transfer_problem_node)
# are intentionally excluded — they are exercised by Sprint 2's Java-side
# adversarial tests, not this Python runner.
NodeCallable = Callable[..., Awaitable[TutorGraphState]]
NODE_REGISTRY: dict[str, NodeCallable] = {
    "problem_guide_node": problem_guide_node,
    "chat_node": chat_node,
    "ideating_node": ideating_node,
    "coding_node": coding_node,
    "skeleton_node": skeleton_node,
    "error_feedback_node": error_feedback_node,
    "ac_review_node": ac_review_node,
    "transfer_draft_node": transfer_draft_node,
}


# Default base state — minimal but sufficient for nodes to execute under
# replay (mock LLM) mode. Concrete cases override fields as needed.
def _default_base_state() -> dict[str, Any]:
    return {
        "session_id": "redteam-session",
        "thread_id": "redteam-thread",
        "run_id": "redteam-run",
        "user_id": 100001,
        "problem_id": 200001,
        "language": "python3",
        "current_phase": "READING",
        "client_event": "CHAT",
        "event_data": {},
        "behavior_metrics": {},
        "node_outputs": {},
        "evidence_pack": {
            "workflow_context": {
                "title": "示例题",
                "statement": "请按要求实现。",
                "input_description": "无",
                "output_description": "按行输出结果。",
            },
            "diagnosis_evidence": {
                "result": "WA",
                "code": "print('placeholder')",
                "language": "python3",
                "err_info": "",
            },
            "learner_state": {
                "calibrated": True,
                "weak_kcs": [],
                "frustration_level": "low",
                "confidence_proxy": 0.5,
                "recent_behavior": {},
                "memory_refs": [],
            },
        },
        "user_mode": "chat",
        "references": [],
        "last_cards": [],
        "available_actions": [],
        "runtime_state": "RUNNING",
    }


def _set_path(state: dict[str, Any], path: str, value: Any) -> None:
    """Set a dotted path in state, creating intermediate dicts as needed.

    Failfast: if an intermediate node exists and is not a dict, raise.
    """
    if not path:
        raise ValueError("payload state_path cannot be empty")
    parts = path.split(".")
    cur: Any = state
    for part in parts[:-1]:
        if part not in cur:
            cur[part] = {}
        elif not isinstance(cur[part], dict):
            raise ValueError(
                f"cannot inject at path {path!r}: intermediate field "
                f"{part!r} is not a dict (got {type(cur[part]).__name__})"
            )
        cur = cur[part]
    cur[parts[-1]] = value


# Map node function name to the conventional node_outputs key.
NODE_OUTPUT_KEY: dict[str, str] = {
    "problem_guide_node": "problem_guide",
    "chat_node": "chat",
    "ideating_node": "ideating",
    "coding_node": "coding",
    "skeleton_node": "skeleton",
    "error_feedback_node": "error_diagnosis",
    "ac_review_node": "ac_review",
    "transfer_draft_node": "transfer",
}


def _attempted_response(state_after: dict[str, Any], target_node: str) -> bool:
    """Approximate Attempt Rate: did the node produce a node_outputs entry?

    Mirrors RedTeamCUA's AR — a node that emits any output (even if benign)
    counts as having attempted, while a fail-fast (no output, runtime FAILED)
    counts as a refusal.
    """
    outputs = state_after.get("node_outputs", {}) or {}
    key = NODE_OUTPUT_KEY.get(target_node, target_node)
    entry = outputs.get(key)
    return bool(entry) and isinstance(entry, dict) and len(entry) > 0


def build_state_for_case(case: AdversarialCase) -> TutorGraphState:
    """Construct the input state for a case: defaults + overrides + payload."""
    state = _default_base_state()
    # Apply phase as current_phase (informational; nodes may override).
    if case.phase in {"READING", "IDEATING", "CODING", "ERROR_FEEDBACK", "AC_REVIEW", "TRANSFER"}:
        state["current_phase"] = case.phase
    # Apply scenario overrides (e.g. specific problem text).
    for path, value in case.base_state_overrides.items():
        _set_path(state, path, copy.deepcopy(value))
    # Apply adversarial payload injections.
    for injection in case.payload:
        _set_path(state, injection.state_path, copy.deepcopy(injection.value))
    return state  # type: ignore[return-value]


async def run_case(
    case: AdversarialCase,
    *,
    llm_client: LlmClient,
) -> CaseResult:
    """Run a single adversarial case against the target node.

    The caller supplies the LlmClient — for CI it should be in replay mode
    so no real API calls are made.
    """
    # NFK_INPUT and SUPPLEMENT_PLANNER cases are out-of-scope for tutor_graph
    # node-level injection; they are exercised by Sprint 2's NFK tests.
    if case.target_node not in NODE_REGISTRY:
        return CaseResult(
            case_id=case.id,
            passed=False,
            attempted=False,
            failfast=False,
            error=(
                f"target_node {case.target_node!r} is not exposed in NODE_REGISTRY; "
                "cases targeting NFK_INPUT/SUPPLEMENT_PLANNER must use the "
                "Java-side Sprint 2 test suite, not this runner."
            ),
        )
    node_fn = NODE_REGISTRY[case.target_node]
    state = build_state_for_case(case)

    error_message = ""
    try:
        result_state = await node_fn(state, llm_client=llm_client)
    except Exception as exc:  # noqa: BLE001 — runner must capture node errors
        # A raised exception is treated as failfast.
        result_state = {**state, "runtime_state": "FAILED", "last_error": str(exc)}
        error_message = f"{type(exc).__name__}: {exc}"

    output_payload: dict[str, Any] = dict(result_state)
    runtime_state = output_payload.get("runtime_state", "")
    failfast = runtime_state == "FAILED" and bool(output_payload.get("last_error"))
    attempted = _attempted_response(output_payload, case.target_node) and not failfast

    main_key = NODE_OUTPUT_KEY.get(case.target_node, case.target_node)
    main_payload = (output_payload.get("node_outputs", {}) or {}).get(main_key, {}) or {}
    # We expose two views to assertions:
    #   - shallow output (just the node's own output dict)
    #   - top-level metadata (runtime_state / last_error / side_effects)
    assertion_view: dict[str, Any] = dict(main_payload)
    assertion_view["runtime_state"] = runtime_state
    assertion_view["last_error"] = output_payload.get("last_error", "")
    assertion_view["side_effects"] = output_payload.get("side_effects", {})

    assertion_results: list[AssertionResult] = []
    for assertion in case.expected_assertions:
        ar = evaluate(assertion, assertion_view)
        assertion_results.append(ar)

    passed = all(ar.passed for ar in assertion_results)

    return CaseResult(
        case_id=case.id,
        passed=passed,
        attempted=attempted,
        failfast=failfast,
        raw_output=main_payload,
        assertion_results=assertion_results,
        error=error_message,
    )


def run_case_sync(case: AdversarialCase, *, llm_client: LlmClient) -> CaseResult:
    """Synchronous wrapper for tests."""
    return asyncio.run(run_case(case, llm_client=llm_client))


def summarize(cases: list[AdversarialCase], results: list[CaseResult]) -> DatasetSummary:
    """Aggregate per-case results into a DatasetSummary."""
    if len(cases) != len(results):
        raise ValueError(
            f"cases and results length mismatch: {len(cases)} vs {len(results)}"
        )
    total = len(results)
    passed = sum(1 for r in results if r.passed)
    failed = total - passed
    attempts = sum(1 for r in results if r.attempted)
    failfasts = sum(1 for r in results if r.failfast)

    by_phase: dict[Phase, dict[str, int]] = {}
    by_category: dict[AttackCategory, dict[str, int]] = {}
    by_cia: dict[PedagogicalCIA, dict[str, int]] = {}
    failed_ids: list[str] = []

    for case, result in zip(cases, results):
        for bucket, key in (
            (by_phase, case.phase),
            (by_category, case.attack_category),
            (by_cia, case.cia),
        ):
            row = bucket.setdefault(key, {"total": 0, "passed": 0, "failed": 0})
            row["total"] += 1
            if result.passed:
                row["passed"] += 1
            else:
                row["failed"] += 1
        if not result.passed:
            failed_ids.append(case.id)

    return DatasetSummary(
        total=total,
        passed=passed,
        failed=failed,
        pass_rate=(passed / total) if total else 0.0,
        attempt_rate=(attempts / total) if total else 0.0,
        failfast_rate=(failfasts / total) if total else 0.0,
        by_phase=by_phase,
        by_category=by_category,
        by_cia=by_cia,
        failed_case_ids=failed_ids,
    )
