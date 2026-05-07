"""LangGraph 运行时状态定义，所有节点的唯一数据源。"""

from __future__ import annotations

from typing import Literal, TypedDict


Phase = Literal[
    "READING",
    "IDEATING",
    "CODING",
    "ERROR_FEEDBACK",
    "AC_REVIEW",
    "TRANSFER",
]

ClientEvent = Literal[
    "READING",
    "IDEATING",
    "SKELETON",
    "CODING",
    "ERROR_FEEDBACK",
    "AC_REVIEW",
    "TRANSFER",
    "CHAT",
    "AGENT_FEEDBACK",
    "KNOWLEDGE_REVIEW",
    "VISUALIZE",
    "COMPACT",
    "PLAN_RECOMMEND",
    "PLAN_START",
    "PLAN_RESPONSE",
    "PLAN_STEERING",
]


class TutorGraphState(TypedDict, total=False):
    session_id: str
    thread_id: str
    run_id: str
    user_id: int
    problem_id: int
    language: str

    current_phase: Phase
    client_event: ClientEvent

    event_data: dict
    behavior_metrics: dict
    node_outputs: dict
    evidence_pack: dict
    learner_state: dict
    # 跨卡片上下文供对话节点使用。
    user_mode: str
    references: list[dict]
    last_cards: list[dict]
    available_actions: list[dict]
    active_plan: dict
    plan_id: str | None
    plan_status: str
    current_step_index: int | None
    current_checkpoint: dict
    last_student_evidence: dict
    evidence_assessment: dict
    remediation_depth: int
    plan_recommendation: dict
    recommendation_reason: str
    recommended_by: str
    trigger_features: dict

    # 创建 thread 时附加的跨 run 上下文。
    context: dict

    pending_human_action: str
    interrupt_id: str | None
    runtime_state: str
    failure_bucket: str | None
    last_error: str | None
    trace_id: str | None

    side_effects: dict
    execution_trace: list[dict]
    created_at: str
    updated_at: str
