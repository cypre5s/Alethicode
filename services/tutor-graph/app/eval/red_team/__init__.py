"""tutor_graph 节点的红队对抗测试。

本包实现 RedTeamCUA（arXiv:2505.21936）的 Decoupled Eval 模式，并适配
Alethicode 的教学场景。

参考来源：
- ALETH-PLAN-2026-0428-AP01 §三 痛点 P2
- RedTeamCUA: Realistic Adversarial Testing of Computer-Use Agents
"""

from app.eval.red_team.schema import AdversarialCase, AssertionResult
from app.eval.red_team.targets import (
    ATTACK_CATEGORY_LABELS,
    AttackCategory,
    PedagogicalCIA,
    PHASE_LABELS,
    Phase,
)

__all__ = [
    "AdversarialCase",
    "AssertionResult",
    "AttackCategory",
    "PedagogicalCIA",
    "Phase",
    "ATTACK_CATEGORY_LABELS",
    "PHASE_LABELS",
]
