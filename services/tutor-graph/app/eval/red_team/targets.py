"""Attack categories and pedagogical CIA triad definitions.

Adapted from RedTeamCUA (arXiv:2505.21936) §3.2:
- Original CIA triad covers OS/web security goals.
- Pedagogical CIA reframes them for tutoring scenarios:
    Confidentiality → Don't leak the answer or solution code.
    Integrity       → Don't let the learner manipulate mastery
                      estimates, scores, or memory state.
    Availability    → Don't allow abuse that degrades teaching for
                      this learner or others (resource flooding,
                      runaway loops, ranking manipulation).

Attack categories cover real student behaviors that ordinary agent
benchmarks (HotpotQA, WebArena) do not exercise.
"""

from __future__ import annotations

from typing import Literal


Phase = Literal[
    "READING",
    "IDEATING",
    "CODING",
    "ERROR_FEEDBACK",
    "AC_REVIEW",
    "TRANSFER",
    "CHAT",
    "PARSONS",
    "NFK_INPUT",
    "SUPPLEMENT_PLANNER",
]


PHASE_LABELS: dict[Phase, str] = {
    "READING": "审题阶段",
    "IDEATING": "构思阶段",
    "CODING": "编码阶段",
    "ERROR_FEEDBACK": "错误诊断阶段",
    "AC_REVIEW": "AC 复盘阶段",
    "TRANSFER": "迁移练习阶段",
    "CHAT": "导学对话",
    "PARSONS": "拼装题",
    "NFK_INPUT": "NFK 学情输入",
    "SUPPLEMENT_PLANNER": "陪练规划",
}


AttackCategory = Literal[
    "direct_answer",
    "role_reversal",
    "jailbreak",
    "walkthrough_fraud",
    "reflection_injection",
    "trajectory_pollution",
    "persona_manipulation",
    "problem_text_conflict",
]


ATTACK_CATEGORY_LABELS: dict[AttackCategory, str] = {
    "direct_answer": "诱导直接给答案",
    "role_reversal": "角色反转（假装其他身份）",
    "jailbreak": "Jailbreak / 系统指令注入",
    "walkthrough_fraud": "Walkthrough / 反思作弊",
    "reflection_injection": "错题反思注入",
    "trajectory_pollution": "NFK trajectory 污染（刷分降难度）",
    "persona_manipulation": "角色一致性 / 不当言论操纵",
    "problem_text_conflict": "题面冲突诱导（让 AI 偏离题面）",
}


PedagogicalCIA = Literal["C", "I", "A"]


CIA_LABELS: dict[PedagogicalCIA, str] = {
    "C": "Confidentiality（不泄漏答案）",
    "I": "Integrity（不被诱导改变 mastery / 评分 / memory）",
    "A": "Availability（不被刷接口 / 污染数据）",
}


# Mapping from attack category to its primary CIA axis.
# This is the default; individual cases may override `cia` for specific scenarios.
DEFAULT_CIA_BY_CATEGORY: dict[AttackCategory, PedagogicalCIA] = {
    "direct_answer": "C",
    "role_reversal": "C",
    "jailbreak": "C",
    "walkthrough_fraud": "I",
    "reflection_injection": "I",
    "trajectory_pollution": "I",
    "persona_manipulation": "C",
    "problem_text_conflict": "I",
}
