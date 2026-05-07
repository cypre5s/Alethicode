"""组装追加到节点 SYSTEM_PROMPT 的学习者画像块。"""

from __future__ import annotations

from typing import Any

MAX_LEARNER_BLOCK_CHARS = 1500
MAX_NARRATIVE_CHARS = 500
MAX_MEMORY_REFS = 5
MAX_WEAK_KCS = 5
MAX_STRONG_KCS = 3

LEARNER_BLOCK_TEMPLATE = """
[Learner Profile]
{narrative_summary_block}
[Teaching Style Preference]
{teaching_style_block}
[Recalled Relevant Memories]
{memory_refs_block}
[Key KC Mastery]
{mastery_block}
""".strip()


def assemble_learner_block(learner_state: dict[str, Any] | None) -> str:
    """生成可追加到 SYSTEM_PROMPT 的学习者画像文本。"""
    if not learner_state:
        return ""
    if not learner_state.get("personalization_enabled", True):
        return ""

    narrative = (learner_state.get("narrative_summary") or "").strip()
    if len(narrative) > MAX_NARRATIVE_CHARS:
        narrative = narrative[:MAX_NARRATIVE_CHARS] + "..."

    bias = learner_state.get("recommended_action_bias", {}) or {}
    style_prompt = (bias.get("teaching_style_prompt") or "").strip()

    memory_refs = learner_state.get("memory_refs", []) or []
    weak_kcs = learner_state.get("weak_kcs", []) or []
    mastery = learner_state.get("mastery_by_kc", {}) or {}

    block = LEARNER_BLOCK_TEMPLATE.format(
        narrative_summary_block=narrative or "(no profile yet)",
        teaching_style_block=style_prompt or "(default step_by_step)",
        memory_refs_block=_format_memory_refs(memory_refs) or "(none)",
        mastery_block=_format_mastery(mastery, weak_kcs) or "(none)",
    )
    if len(block) > MAX_LEARNER_BLOCK_CHARS:
        block = block[:MAX_LEARNER_BLOCK_CHARS] + "...(truncated)"
    return block


def _format_memory_refs(refs: list[dict]) -> str:
    if not refs:
        return ""
    lines = []
    for index, ref in enumerate(refs[:MAX_MEMORY_REFS], start=1):
        confidence = ref.get("confidence", 0.0)
        distance = ref.get("distance")
        summary = (ref.get("memory_summary") or ref.get("memory_value") or "").strip()
        if not summary:
            continue
        if len(summary) > 200:
            summary = summary[:200] + "..."
        if distance is not None:
            lines.append(f"{index}. (conf {confidence:.2f}, dist {distance:.2f}) {summary}")
        else:
            lines.append(f"{index}. (conf {confidence:.2f}) {summary}")
    return "\n".join(lines)


def _format_mastery(mastery: dict[str, float], weak_kcs: list[str]) -> str:
    if not mastery and not weak_kcs:
        return ""
    weak_view = []
    strong_view = []
    for kc, score in mastery.items():
        try:
            value = float(score)
        except (TypeError, ValueError):
            continue
        if value < 0.6:
            weak_view.append((kc, value))
        elif value >= 0.7:
            strong_view.append((kc, value))
    weak_view.sort(key=lambda x: x[1])
    strong_view.sort(key=lambda x: x[1], reverse=True)
    weak_str = "weak: " + ", ".join(f"{kc}({score:.2f})" for kc, score in weak_view[:MAX_WEAK_KCS]) if weak_view else ""
    strong_str = "strong: " + ", ".join(f"{kc}({score:.2f})" for kc, score in strong_view[:MAX_STRONG_KCS]) if strong_view else ""
    parts = [s for s in (weak_str, strong_str) if s]
    return " | ".join(parts) if parts else ""
