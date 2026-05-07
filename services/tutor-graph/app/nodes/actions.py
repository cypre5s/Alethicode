"""根据阶段、待处理动作和学习者状态决定可用动作。"""

from __future__ import annotations

from app.graph.state import TutorGraphState

PHASE_ACTIONS: dict[str, list[dict]] = {
    "READING": [
        {"key": "problem_guide", "label": "题目导读", "event": "READING", "agent_id": 1},
        {"key": "ideate", "label": "思路分析", "event": "IDEATING", "agent_id": 2},
        {"key": "skeleton", "label": "骨架代码", "event": "SKELETON", "agent_id": 2},
        {"key": "visualize", "label": "画一下", "event": "VISUALIZE", "agent_id": 7},
        {"key": "parsons", "label": "拼装挑战", "event": "PARSONS", "agent_id": 2},
    ],
    "IDEATING": [
        {"key": "ideate", "label": "继续思路分析", "event": "IDEATING", "agent_id": 2},
        {"key": "skeleton", "label": "骨架代码", "event": "SKELETON", "agent_id": 2},
        {"key": "coding", "label": "开始编码", "event": "CODING", "agent_id": 0},
        {"key": "visualize", "label": "画一下", "event": "VISUALIZE", "agent_id": 7},
        {"key": "parsons", "label": "拼装挑战", "event": "PARSONS", "agent_id": 2},
    ],
    "CODING": [
        {"key": "coding", "label": "编码", "event": "CODING", "agent_id": 0},
        {"key": "visualize", "label": "画一下", "event": "VISUALIZE", "agent_id": 7},
        {"key": "parsons", "label": "拼装挑战", "event": "PARSONS", "agent_id": 2},
    ],
    "ERROR_FEEDBACK": [
        {"key": "error_chain", "label": "错误诊断", "event": "ERROR_FEEDBACK", "agent_id": 4},
        {"key": "re_read", "label": "重新审题", "event": "READING", "agent_id": 1},
        {"key": "re_ideate", "label": "重新梳理思路", "event": "IDEATING", "agent_id": 2},
        {"key": "visualize", "label": "画一下", "event": "VISUALIZE", "agent_id": 7},
        {"key": "parsons", "label": "拼装挑战", "event": "PARSONS", "agent_id": 2},
    ],
    "AC_REVIEW": [
        {"key": "ac_review", "label": "AC 复盘", "event": "AC_REVIEW", "agent_id": 5},
        {"key": "transfer", "label": "迁移练习", "event": "TRANSFER", "agent_id": 6},
        {"key": "visualize", "label": "画一下", "event": "VISUALIZE", "agent_id": 7},
        {"key": "parsons", "label": "拼装挑战", "event": "PARSONS", "agent_id": 2},
    ],
    "TRANSFER": [
        {"key": "transfer", "label": "重新生成迁移题", "event": "TRANSFER", "agent_id": 6},
        {"key": "coding", "label": "返回编码", "event": "CODING", "agent_id": 0},
        {"key": "visualize", "label": "画一下", "event": "VISUALIZE", "agent_id": 7},
        {"key": "parsons", "label": "拼装挑战", "event": "PARSONS", "agent_id": 2},
    ],
}


def decide_available_actions(state: TutorGraphState) -> TutorGraphState:
    phase = state.get("current_phase", "READING")
    pending = state.get("pending_human_action", "")

    actions = list(PHASE_ACTIONS.get(phase, PHASE_ACTIONS["READING"]))

    if pending == "confirm_transfer":
        actions = [a for a in actions if a["event"] != "CODING"]

    return {**state, "available_actions": actions}
