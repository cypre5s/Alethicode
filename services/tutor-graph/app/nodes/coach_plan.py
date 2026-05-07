"""导师图的元认知辅导计划循环。"""

from __future__ import annotations

import uuid
from copy import deepcopy

from app.graph.state import TutorGraphState

PLAN_STATUSES = frozenset({
    "idle",
    "recommended",
    "active",
    "paused",
    "completed",
    "surrendered",
})

RECOMMENDATION_PHASES = frozenset({"IDEATING", "CODING", "ERROR_FEEDBACK"})
EVIDENCE_TYPES = frozenset({"text", "sample_prediction", "code_change", "reflection"})
STEERING_SIGNAL_TYPES = frozenset({"pause", "resume", "skip", "take_over", "redirect"})


def build_plan_payload(state: TutorGraphState) -> dict:
    active_plan = deepcopy(state.get("active_plan") or {})
    if not active_plan:
        recommendation = deepcopy(state.get("plan_recommendation") or {})
        if not recommendation:
            return {}
        return {
            "plan_id": recommendation.get("plan_id", state.get("plan_id", "")),
            "status": recommendation.get("status", "recommended"),
            "current_step_index": None,
            "current_step": None,
            "steps": [],
            "coordination_reasoning": recommendation.get("coordination_reasoning", ""),
            "recommendation_reason": state.get("recommendation_reason", ""),
            "trigger_features": deepcopy(state.get("trigger_features", {})),
        }

    current_step_index = state.get("current_step_index")
    steps = deepcopy(active_plan.get("steps", []))
    current_step = _get_step_by_index(steps, current_step_index)
    return {
        "plan_id": active_plan.get("plan_id", state.get("plan_id", "")),
        "status": state.get("plan_status") or active_plan.get("status", "active"),
        "current_step_index": current_step_index,
        "current_step": current_step,
        "steps": steps,
        "coordination_reasoning": active_plan.get("coordination_reasoning", ""),
        "recommendation_reason": state.get("recommendation_reason", ""),
        "trigger_features": deepcopy(state.get("trigger_features", {})),
    }


def coach_recommendation_node(state: TutorGraphState) -> TutorGraphState:
    if state.get("active_plan"):
        return state
    if state.get("plan_status") in {"active", "paused"}:
        return state

    current_phase = str(state.get("current_phase", "READING")).upper()
    if current_phase not in RECOMMENDATION_PHASES:
        return _clear_recommendation(state)

    trigger_features, recommendation_reason = _evaluate_recommendation(
        state.get("behavior_metrics", {}),
        state.get("learner_state", {}),
        current_phase=current_phase,
    )
    if not recommendation_reason:
        return _clear_recommendation(state)

    plan_id = state.get("plan_id") or f"plan_{uuid.uuid4().hex[:12]}"
    recommendation = {
        "plan_id": plan_id,
        "status": "recommended",
        "coordination_reasoning": "建议先进入 5 步单题陪练，再继续当前题目。",
        "trigger_source": "rules",
    }
    return {
        **state,
        "plan_id": plan_id,
        "plan_status": "recommended",
        "plan_recommendation": recommendation,
        "recommendation_reason": recommendation_reason,
        "recommended_by": "rules",
        "trigger_features": trigger_features,
        "current_checkpoint": {},
    }


def coach_plan_start_node(state: TutorGraphState) -> TutorGraphState:
    event_data = state.get("event_data", {})
    plan_id = str(event_data.get("plan_id") or state.get("plan_id") or f"plan_{uuid.uuid4().hex[:12]}")
    current_phase = str(state.get("current_phase", "READING")).upper()
    reason = str(event_data.get("reason", "")).strip()
    trigger_source = str(event_data.get("trigger_source", "")).strip() or state.get("recommended_by", "rules")
    recommendation_reason = state.get("recommendation_reason") or reason
    steps = _build_default_steps()

    for index, step in enumerate(steps):
        step["status"] = "active" if index == 0 else "pending"

    active_plan = {
        "plan_id": plan_id,
        "status": "active",
        "steps": steps,
        "coordination_reasoning": _build_coordination_reasoning(current_phase, reason or recommendation_reason),
        "trigger_source": trigger_source,
    }
    current_checkpoint = _build_checkpoint(steps[0])
    return {
        **state,
        "active_plan": active_plan,
        "plan_id": plan_id,
        "plan_status": "active",
        "current_step_index": 0,
        "current_checkpoint": current_checkpoint,
        "last_student_evidence": {},
        "evidence_assessment": {},
        "remediation_depth": 0,
        "plan_recommendation": {},
        "recommendation_reason": recommendation_reason,
        "recommended_by": trigger_source,
    }


def coach_step_prompt_node(state: TutorGraphState) -> TutorGraphState:
    active_plan = deepcopy(state.get("active_plan") or {})
    if not active_plan:
        return state

    status = state.get("plan_status") or active_plan.get("status", "active")
    active_plan["status"] = status
    if status in {"paused", "completed", "surrendered"}:
        return {**state, "active_plan": active_plan}

    steps = deepcopy(active_plan.get("steps", []))
    current_step = _get_step_by_index(steps, state.get("current_step_index"))
    if current_step is None:
        return {
            **state,
            "runtime_state": "FAILED",
            "failure_bucket": "SCHEMA_VIOLATION",
            "last_error": "active plan is missing current step",
        }

    active_plan["steps"] = steps
    return {
        **state,
        "active_plan": active_plan,
        "plan_status": "active",
        "current_checkpoint": _build_checkpoint(current_step),
    }


def coach_step_evaluate_node(state: TutorGraphState) -> TutorGraphState:
    active_plan = deepcopy(state.get("active_plan") or {})
    event_data = state.get("event_data", {})
    if not active_plan:
        return _fail(state, "SCHEMA_VIOLATION", "PLAN_RESPONSE requires an active plan")
    if str(event_data.get("plan_id", "")).strip() != str(active_plan.get("plan_id", "")).strip():
        return _fail(state, "SCHEMA_VIOLATION", "plan_id does not match active plan")

    steps = deepcopy(active_plan.get("steps", []))
    current_step = _get_step_by_index(steps, state.get("current_step_index"))
    if current_step is None:
        return _fail(state, "SCHEMA_VIOLATION", "active plan is missing current step")
    if str(event_data.get("step_id", "")).strip() != str(current_step.get("step_id", "")).strip():
        return _fail(state, "SCHEMA_VIOLATION", "step_id does not match current plan step")

    evidence_type = str(event_data.get("evidence_type", "")).strip().lower()
    if evidence_type not in EVIDENCE_TYPES:
        return _fail(state, "SCHEMA_VIOLATION", f"invalid evidence_type: {evidence_type}")

    assessment = _evaluate_step_evidence(current_step, event_data, evidence_type)
    return {
        **state,
        "active_plan": active_plan,
        "last_student_evidence": {
            "step_id": current_step.get("step_id", ""),
            "evidence_type": evidence_type,
            "response_text": str(event_data.get("response_text", "")).strip(),
            "sample_prediction": str(event_data.get("sample_prediction", "")).strip(),
            "code_snapshot_id": str(event_data.get("code_snapshot_id", "")).strip(),
        },
        "evidence_assessment": assessment,
    }


def coach_step_remediate_node(state: TutorGraphState) -> TutorGraphState:
    active_plan = deepcopy(state.get("active_plan") or {})
    steps = deepcopy(active_plan.get("steps", []))
    current_index = state.get("current_step_index")
    current_step = _get_step_by_index(steps, current_index)
    if current_step is None:
        return _fail(state, "SCHEMA_VIOLATION", "cannot remediate without a current step")

    remediation_depth = int(state.get("remediation_depth") or 0) + 1
    current_step["status"] = "active"
    if remediation_depth == 1:
        current_step["support_hint"] = _shrink_support_hint(current_step)
        active_plan["steps"] = steps
        active_plan["status"] = "active"
        return {
            **state,
            "active_plan": active_plan,
            "plan_status": "active",
            "remediation_depth": remediation_depth,
            "current_checkpoint": _build_checkpoint(current_step),
        }

    rollback_index = max(int(current_index or 0) - 1, 0)
    for index, step in enumerate(steps):
        if index < rollback_index:
            step["status"] = "completed"
        elif index == rollback_index:
            step["status"] = "active"
        else:
            step["status"] = "pending"
    rollback_step = steps[rollback_index]
    rollback_step["support_hint"] = _shrink_support_hint(rollback_step)
    active_plan["steps"] = steps
    active_plan["status"] = "active"
    return {
        **state,
        "active_plan": active_plan,
        "plan_status": "active",
        "current_step_index": rollback_index,
        "remediation_depth": 0,
        "current_checkpoint": _build_checkpoint(rollback_step),
    }


def coach_replan_node(state: TutorGraphState) -> TutorGraphState:
    active_plan = deepcopy(state.get("active_plan") or {})
    event_data = state.get("event_data", {})
    signal_type = str(event_data.get("signal_type", "")).strip().lower()
    if signal_type not in STEERING_SIGNAL_TYPES:
        return _fail(state, "SCHEMA_VIOLATION", f"invalid signal_type: {signal_type}")
    if not active_plan:
        return _fail(state, "SCHEMA_VIOLATION", "PLAN_STEERING requires an active plan")
    if str(event_data.get("plan_id", "")).strip() != str(active_plan.get("plan_id", "")).strip():
        return _fail(state, "SCHEMA_VIOLATION", "plan_id does not match active plan")

    steps = deepcopy(active_plan.get("steps", []))
    current_index = int(state.get("current_step_index") or 0)

    if signal_type == "pause":
        active_plan["status"] = "paused"
        return {**state, "active_plan": active_plan, "plan_status": "paused"}

    if signal_type == "resume":
        active_plan["status"] = "active"
        return {**state, "active_plan": active_plan, "plan_status": "active"}

    if signal_type == "take_over":
        active_plan["status"] = "surrendered"
        return {
            **state,
            "active_plan": active_plan,
            "plan_status": "surrendered",
            "current_checkpoint": {},
        }

    if signal_type == "skip":
        current_step = _get_step_by_index(steps, current_index)
        if current_step is not None:
            current_step["status"] = "skipped"
        next_index = current_index + 1
        if next_index >= len(steps):
            active_plan["status"] = "completed"
            return {
                **state,
                "active_plan": active_plan,
                "plan_status": "completed",
                "current_step_index": len(steps) - 1 if steps else None,
                "current_checkpoint": {},
                "remediation_depth": 0,
            }
        steps[next_index]["status"] = "active"
        active_plan["steps"] = steps
        active_plan["status"] = "active"
        return {
            **state,
            "active_plan": active_plan,
            "plan_status": "active",
            "current_step_index": next_index,
            "current_checkpoint": _build_checkpoint(steps[next_index]),
            "remediation_depth": 0,
        }

    current_step = _get_step_by_index(steps, current_index)
    if current_step is None:
        return _fail(state, "SCHEMA_VIOLATION", "cannot redirect without a current step")
    current_step["support_hint"] = str(event_data.get("redirect_instruction", "")).strip()
    active_plan["steps"] = steps
    active_plan["status"] = "active"
    return {
        **state,
        "active_plan": active_plan,
        "plan_status": "active",
        "current_checkpoint": _build_checkpoint(current_step),
    }


def coach_step_complete_node(state: TutorGraphState) -> TutorGraphState:
    active_plan = deepcopy(state.get("active_plan") or {})
    steps = deepcopy(active_plan.get("steps", []))
    current_index = state.get("current_step_index")
    current_step = _get_step_by_index(steps, current_index)
    if current_step is None:
        return _fail(state, "SCHEMA_VIOLATION", "cannot complete without a current step")

    current_step["status"] = "completed"
    next_index = int(current_index or 0) + 1
    if next_index >= len(steps):
        active_plan["steps"] = steps
        active_plan["status"] = "completed"
        return {
            **state,
            "active_plan": active_plan,
            "plan_status": "completed",
            "current_checkpoint": {},
            "remediation_depth": 0,
        }

    steps[next_index]["status"] = "active"
    active_plan["steps"] = steps
    active_plan["status"] = "active"
    return {
        **state,
        "active_plan": active_plan,
        "plan_status": "active",
        "current_step_index": next_index,
        "current_checkpoint": _build_checkpoint(steps[next_index]),
        "remediation_depth": 0,
    }


def _build_default_steps() -> list[dict]:
    return [
        {
            "step_id": "task_representation",
            "title": "任务表征",
            "learning_goal": "用自己的话说清题目要你做什么、输入输出是什么。",
            "student_task": "先用 2-3 句话重述题意，再指出一个你最担心漏掉的约束。",
            "mentor_role": "Nene",
            "evidence_type": "text",
            "pass_rule": "能准确重述任务，并说出至少一个关键约束。",
            "support_hint": "先回答：输入是什么？输出是什么？样例说明了什么？",
            "status": "pending",
        },
        {
            "step_id": "idea_externalization",
            "title": "思路外化",
            "learning_goal": "把思路从脑中说出来，而不是直接闷头写代码。",
            "student_task": "说出你准备维护哪些变量、按什么顺序处理数据、为什么这样做。",
            "mentor_role": "Nene",
            "evidence_type": "text",
            "pass_rule": "思路里包含处理顺序和关键变量，并说明为什么这样想。",
            "support_hint": "可以先只回答第一步打算做什么，再补充为什么。",
            "status": "pending",
        },
        {
            "step_id": "minimum_implementation",
            "title": "最小实现",
            "learning_goal": "只完成当前最小可验证的一步，不追求一次写完。",
            "student_task": "先写出最小代码骨架或核心循环，再停下来准备自检。",
            "mentor_role": "Yoshino",
            "evidence_type": "code_change",
            "pass_rule": "提交了最小实现证据，能看出你已经开始把思路落到代码。",
            "support_hint": "不用一次写全，先把输入处理或核心循环写出来。",
            "status": "pending",
        },
        {
            "step_id": "self_check",
            "title": "自检定位",
            "learning_goal": "先预测程序会怎么跑，再定位最可能出错的位置。",
            "student_task": "任选一个样例，先预测关键变量变化或输出，再指出最可能出错的一行。",
            "mentor_role": "Yoshino",
            "evidence_type": "sample_prediction",
            "pass_rule": "能给出样例预测，或能明确指出一个最值得检查的位置。",
            "support_hint": "如果不会整段预测，就先说第一个循环结束后变量会变成什么。",
            "status": "pending",
        },
        {
            "step_id": "reflection_transfer",
            "title": "复盘迁移",
            "learning_goal": "说清这次为什么改对，以及下次怎样更早发现同类问题。",
            "student_task": "用一句话总结这次最关键的改动，再说一句下次如何更早发现。",
            "mentor_role": "Kanna",
            "evidence_type": "reflection",
            "pass_rule": "同时回答“这次为什么改对”和“下次怎样更早发现”。",
            "support_hint": "句式可以是：这次我改对，是因为……；下次我会先检查……",
            "status": "pending",
        },
    ]


def _evaluate_recommendation(behavior_metrics: dict, learner_state: dict, *, current_phase: str) -> tuple[dict, str]:
    consecutive_errors = _as_int(behavior_metrics.get("consecutiveErrors"))
    dwell_time = _as_int(behavior_metrics.get("dwellTime"))
    delete_ratio = _as_float(behavior_metrics.get("deleteRatio"))
    frustration_level = str(learner_state.get("frustration_level", "")).strip().lower()

    trigger_features: dict[str, int | float | str] = {
        "current_phase": current_phase,
        "consecutive_errors": consecutive_errors,
        "dwell_time": dwell_time,
        "delete_ratio": delete_ratio,
        "frustration_level": frustration_level,
    }

    reasons: list[str] = []
    if consecutive_errors >= 2:
        reasons.append("连续错误次数偏高")
    if dwell_time >= 600:
        reasons.append("停留时间较长但还没有形成稳定推进")
    if delete_ratio >= 0.35:
        reasons.append("代码处于频繁推翻重写状态")
    if frustration_level in {"medium", "high"}:
        reasons.append("学习者挫败感升高")

    if not reasons:
        return trigger_features, ""

    return trigger_features, "；".join(reasons) + "，建议先进入元认知陪练。"


def _evaluate_step_evidence(step: dict, event_data: dict, evidence_type: str) -> dict:
    response_text = str(event_data.get("response_text", "")).strip()
    sample_prediction = str(event_data.get("sample_prediction", "")).strip()
    code_snapshot_id = str(event_data.get("code_snapshot_id", "")).strip()
    expected_type = str(step.get("evidence_type", "")).strip().lower()

    if evidence_type != expected_type:
        return {
            "passed": False,
            "reason": f"当前步骤需要 {expected_type} 类型证据。",
        }

    if evidence_type in {"text", "reflection"}:
        passed = len(response_text) >= 12
        if evidence_type == "reflection" and passed:
            passed = ("因为" in response_text) or ("下次" in response_text) or ("先检查" in response_text)
        return {
            "passed": passed,
            "reason": "表述足够具体，可以进入下一步。" if passed else "请先把想法说完整一些，再继续。",
        }

    if evidence_type == "sample_prediction":
        passed = bool(sample_prediction or response_text)
        return {
            "passed": passed,
            "reason": "已经有自检证据，可以继续推进。" if passed else "请先预测一个样例或指出最可能出错的位置。",
        }

    passed = bool(code_snapshot_id or response_text)
    return {
        "passed": passed,
        "reason": "已经提供了最小实现证据。" if passed else "请先提交当前最小实现，再进入自检。",
    }


def _build_coordination_reasoning(current_phase: str, reason: str) -> str:
    if reason:
        return f"当前停在 {current_phase}，先用 5 步闭环把理解、实现、自检和复盘串起来。触发原因：{reason}"
    return f"当前停在 {current_phase}，先用 5 步闭环把理解、实现、自检和复盘串起来。"


def _build_checkpoint(step: dict) -> dict:
    return {
        "step_id": step.get("step_id", ""),
        "title": step.get("title", ""),
        "learning_goal": step.get("learning_goal", ""),
        "student_task": step.get("student_task", ""),
        "pass_rule": step.get("pass_rule", ""),
        "support_hint": step.get("support_hint", ""),
        "mentor_role": step.get("mentor_role", ""),
        "evidence_type": step.get("evidence_type", ""),
    }


def _get_step_by_index(steps: list[dict], index: int | None) -> dict | None:
    if index is None:
        return None
    if index < 0 or index >= len(steps):
        return None
    return steps[index]


def _shrink_support_hint(step: dict) -> str:
    title = str(step.get("title", "")).strip()
    if title == "任务表征":
        return "先只回答：题目最后要打印/返回什么？"
    if title == "思路外化":
        return "先说第一步打算做什么，不必一次讲完整个算法。"
    if title == "最小实现":
        return "先只写输入处理或核心循环的框架，不用补完整逻辑。"
    if title == "自检定位":
        return "先预测一个变量在第一轮循环后会变成什么。"
    return "先回答一句：这次最关键的改动是什么？"


def _clear_recommendation(state: TutorGraphState) -> TutorGraphState:
    return {
        **state,
        "plan_recommendation": {},
        "recommendation_reason": "",
        "recommended_by": "",
        "trigger_features": {},
        "plan_status": "idle" if not state.get("active_plan") else state.get("plan_status", "active"),
    }


def _fail(state: TutorGraphState, failure_bucket: str, last_error: str) -> TutorGraphState:
    return {
        **state,
        "runtime_state": "FAILED",
        "failure_bucket": failure_bucket,
        "last_error": last_error,
    }


def _as_int(value: object) -> int:
    if value is None:
        return 0
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, (int, float)):
        return int(value)
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return 0


def _as_float(value: object) -> float:
    if value is None:
        return 0.0
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(str(value).strip())
    except (TypeError, ValueError):
        return 0.0
