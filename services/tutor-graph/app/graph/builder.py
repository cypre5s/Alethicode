"""组装完整的 tutor StateGraph 并注册所有节点和路由。"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from langgraph.graph import END, StateGraph

from app.clients.java_tools_client import JavaToolsClient
from app.clients.llm_client import LlmClient
from app.graph.runtime_events import ServerEvent
from app.graph.state import TutorGraphState
from app.graph.transitions import (
    AUXILIARY_EVENTS,
    PLAN_EVENTS,
    TransitionError,
    validate_transition,
)
from app.nodes.actions import decide_available_actions
from app.nodes.coach_plan import (
    coach_plan_start_node,
    coach_recommendation_node,
    coach_replan_node,
    coach_step_complete_node,
    coach_step_evaluate_node,
    coach_step_prompt_node,
    coach_step_remediate_node,
)
from app.nodes.schema_validation import validate_card_schema


def build_tutor_graph(
    *,
    java_client: JavaToolsClient,
    llm_client: LlmClient,
    checkpointer: Any = None,
):
    """构建并编译 tutor workflow StateGraph。"""
    from app.nodes.ingest import ingest_event
    from app.nodes.evidence import assemble_evidence_pack
    from app.nodes.reading import problem_guide_node
    from app.nodes.ideating import ideating_node
    from app.nodes.skeleton import skeleton_node
    from app.nodes.coding import coding_node
    from app.nodes.diagnosis import error_feedback_node
    from app.nodes.ac_review import ac_review_node
    from app.nodes.transfer import transfer_draft_node, materialize_transfer_problem_node
    from app.nodes.chat import chat_node
    from app.nodes.compact import compact_node
    from app.nodes.knowledge_review import knowledge_review_node
    from app.nodes.visualize import visualize_node
    from app.nodes.parsons import parsons_node
    from app.nodes.projection import persist_projection

    def _validate_transition(state: TutorGraphState) -> TutorGraphState:
        try:
            new_phase = validate_transition(
                state.get("current_phase", "READING"),
                state["client_event"],
                pending_human_action=state.get("pending_human_action", ""),
                event_data=state.get("event_data"),
                language=state.get("language", ""),
            )
            return {**state, "current_phase": new_phase}
        except TransitionError as e:
            return {
                **state,
                "runtime_state": "FAILED",
                "failure_bucket": "SCHEMA_VIOLATION",
                "last_error": str(e),
            }

    async def _evidence(state: TutorGraphState) -> TutorGraphState:
        return await assemble_evidence_pack(state, java_client=java_client)

    async def _reading(state: TutorGraphState) -> TutorGraphState:
        return await problem_guide_node(state, llm_client=llm_client)

    async def _ideating(state: TutorGraphState) -> TutorGraphState:
        return await ideating_node(state, llm_client=llm_client)

    async def _skeleton(state: TutorGraphState) -> TutorGraphState:
        return await skeleton_node(state, llm_client=llm_client)

    async def _coding(state: TutorGraphState) -> TutorGraphState:
        return await coding_node(state, llm_client=llm_client)

    async def _diagnosis(state: TutorGraphState) -> TutorGraphState:
        return await error_feedback_node(state, llm_client=llm_client)

    async def _ac_review(state: TutorGraphState) -> TutorGraphState:
        return await ac_review_node(state, llm_client=llm_client)

    async def _transfer_draft(state: TutorGraphState) -> TutorGraphState:
        return await transfer_draft_node(state, llm_client=llm_client)

    async def _transfer_materialize(state: TutorGraphState) -> TutorGraphState:
        return await materialize_transfer_problem_node(state, java_client=java_client)

    async def _chat(state: TutorGraphState) -> TutorGraphState:
        return await chat_node(state, llm_client=llm_client)

    async def _compact(state: TutorGraphState) -> TutorGraphState:
        return await compact_node(state, llm_client=llm_client)

    async def _knowledge_review(state: TutorGraphState) -> TutorGraphState:
        return await knowledge_review_node(state, llm_client=llm_client)

    async def _visualize(state: TutorGraphState) -> TutorGraphState:
        return await visualize_node(state, java_client=java_client)

    async def _parsons(state: TutorGraphState) -> TutorGraphState:
        return await parsons_node(state, java_client=java_client)

    def _coach_recommendation(state: TutorGraphState) -> TutorGraphState:
        return coach_recommendation_node(state)

    def _coach_plan_start(state: TutorGraphState) -> TutorGraphState:
        return coach_plan_start_node(state)

    def _coach_step_prompt(state: TutorGraphState) -> TutorGraphState:
        return coach_step_prompt_node(state)

    def _coach_step_evaluate(state: TutorGraphState) -> TutorGraphState:
        return coach_step_evaluate_node(state)

    def _coach_step_remediate(state: TutorGraphState) -> TutorGraphState:
        return coach_step_remediate_node(state)

    def _coach_replan(state: TutorGraphState) -> TutorGraphState:
        return coach_replan_node(state)

    def _coach_step_complete(state: TutorGraphState) -> TutorGraphState:
        return coach_step_complete_node(state)

    def _validate_card(state: TutorGraphState) -> TutorGraphState:
        return validate_card_schema(state)

    def _actions(state: TutorGraphState) -> TutorGraphState:
        return decide_available_actions(state)

    async def _projection(state: TutorGraphState) -> TutorGraphState:
        return await persist_projection(state, java_client=java_client)

    def _emit_completed(state: TutorGraphState) -> TutorGraphState:
        return {
            **state,
            "runtime_state": "COMPLETED",
            "updated_at": datetime.now(timezone.utc).isoformat(),
        }

    def _emit_failed(state: TutorGraphState) -> TutorGraphState:
        return {
            **state,
            "runtime_state": "FAILED",
            "updated_at": datetime.now(timezone.utc).isoformat(),
        }

    def _route_after_validation(state: TutorGraphState) -> str:
        if state.get("runtime_state") == "FAILED":
            return "emit_failed"
        return "assemble_evidence"

    def _route_after_evidence(state: TutorGraphState) -> str:
        if state.get("runtime_state") == "FAILED":
            return "emit_failed"
        event = state.get("client_event", "").upper()
        route_map = {
            "READING": "reading",
            "IDEATING": "ideating",
            "SKELETON": "skeleton",
            "CODING": "coding",
            "ERROR_FEEDBACK": "diagnosis",
            "AC_REVIEW": "ac_review",
            "TRANSFER": "transfer_draft",
            "CHAT": "chat",
            "AGENT_FEEDBACK": "chat",
            "KNOWLEDGE_REVIEW": "knowledge_review",
            "VISUALIZE": "visualize",
            "PARSONS": "parsons",
            "COMPACT": "compact",
            "PLAN_RECOMMEND": "coach_recommendation",
            "PLAN_START": "coach_plan_start",
            "PLAN_RESPONSE": "coach_step_evaluate",
            "PLAN_STEERING": "coach_replan",
        }
        return route_map.get(event, "emit_failed")

    def _route_after_transfer_draft(state: TutorGraphState) -> str:
        if state.get("runtime_state") == "FAILED":
            return "emit_failed"
        if state.get("runtime_state") == "COMPLETED":
            return "validate_card"
        if state.get("node_outputs", {}).get("_transfer_confirmed"):
            return "transfer_materialize"
        return "validate_card"

    def _route_after_node(state: TutorGraphState) -> str:
        if state.get("runtime_state") == "FAILED":
            return "emit_failed"
        return "validate_card"

    def _route_after_plan_evaluate(state: TutorGraphState) -> str:
        if state.get("runtime_state") == "FAILED":
            return "emit_failed"
        assessment = state.get("evidence_assessment", {})
        if assessment.get("passed"):
            return "coach_step_complete"
        return "coach_step_remediate"

    def _route_after_decide_actions(state: TutorGraphState) -> str:
        if state.get("runtime_state") == "FAILED":
            return "emit_failed"
        if state.get("client_event", "").upper() in PLAN_EVENTS:
            return "emit_completed"
        return "coach_recommendation"

    def _route_after_card_validation(state: TutorGraphState) -> str:
        if state.get("runtime_state") == "FAILED":
            return "emit_failed"
        return "decide_actions"

    graph = StateGraph(TutorGraphState)

    graph.add_node("ingest", ingest_event)
    graph.add_node("validate_transition", _validate_transition)
    graph.add_node("assemble_evidence", _evidence)
    graph.add_node("reading", _reading)
    graph.add_node("ideating", _ideating)
    graph.add_node("skeleton", _skeleton)
    graph.add_node("coding", _coding)
    graph.add_node("diagnosis", _diagnosis)
    graph.add_node("ac_review", _ac_review)
    graph.add_node("transfer_draft", _transfer_draft)
    graph.add_node("transfer_materialize", _transfer_materialize)
    graph.add_node("chat", _chat)
    graph.add_node("compact", _compact)
    graph.add_node("knowledge_review", _knowledge_review)
    graph.add_node("visualize", _visualize)
    graph.add_node("parsons", _parsons)
    graph.add_node("coach_recommendation", _coach_recommendation)
    graph.add_node("coach_plan_start", _coach_plan_start)
    graph.add_node("coach_step_prompt", _coach_step_prompt)
    graph.add_node("coach_step_evaluate", _coach_step_evaluate)
    graph.add_node("coach_step_remediate", _coach_step_remediate)
    graph.add_node("coach_replan", _coach_replan)
    graph.add_node("coach_step_complete", _coach_step_complete)
    graph.add_node("validate_card", _validate_card)
    graph.add_node("decide_actions", _actions)
    graph.add_node("persist_projection", _projection)
    graph.add_node("emit_completed", _emit_completed)
    graph.add_node("emit_failed", _emit_failed)

    graph.set_entry_point("ingest")
    graph.add_edge("ingest", "validate_transition")
    graph.add_conditional_edges("validate_transition", _route_after_validation)
    graph.add_conditional_edges("assemble_evidence", _route_after_evidence)

    for node_name in (
        "reading",
        "ideating",
        "skeleton",
        "coding",
        "diagnosis",
        "ac_review",
        "chat",
        "compact",
        "knowledge_review",
        "visualize",
        "parsons",
        "transfer_materialize",
    ):
        graph.add_conditional_edges(node_name, _route_after_node)

    graph.add_conditional_edges("transfer_draft", _route_after_transfer_draft)
    graph.add_edge("coach_plan_start", "coach_step_prompt")
    graph.add_conditional_edges("coach_step_prompt", _route_after_node)
    graph.add_conditional_edges("coach_step_evaluate", _route_after_plan_evaluate)
    graph.add_edge("coach_step_remediate", "coach_step_prompt")
    graph.add_edge("coach_replan", "coach_step_prompt")
    graph.add_edge("coach_step_complete", "coach_step_prompt")
    graph.add_conditional_edges("validate_card", _route_after_card_validation)
    graph.add_conditional_edges("decide_actions", _route_after_decide_actions)
    graph.add_edge("coach_recommendation", "emit_completed")
    graph.add_edge("emit_completed", "persist_projection")
    graph.add_edge("emit_failed", "persist_projection")
    graph.add_edge("persist_projection", END)

    return graph.compile(checkpointer=checkpointer)
