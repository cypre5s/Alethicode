"""Red team adversarial testing for tutor_graph nodes.

This package implements the Decoupled Eval pattern from RedTeamCUA
(arXiv:2505.21936) adapted to Alethicode's pedagogical scenarios.

Inspired by:
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
