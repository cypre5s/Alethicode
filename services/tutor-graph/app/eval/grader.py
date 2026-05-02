"""Eval grader — scores tutor workflow outputs on pedagogical dimensions."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

import jsonschema

from app.paths import CARD_SCHEMA_DIR as SCHEMA_DIR


class EvalResult:
    def __init__(self) -> None:
        self.dimensions: dict[str, float] = {}
        self.notes: dict[str, str] = {}
        self.passed = True

    def score(self, dimension: str, value: float, note: str = "") -> None:
        self.dimensions[dimension] = value
        if note:
            self.notes[dimension] = note
        if value < 0.5:
            self.passed = False

    def to_dict(self) -> dict:
        return {
            "passed": self.passed,
            "dimensions": self.dimensions,
            "notes": self.notes,
            "overall": sum(self.dimensions.values()) / max(len(self.dimensions), 1),
        }


def grade_run(
    event: str,
    node_outputs: dict[str, Any],
    *,
    learner_state: dict | None = None,
    evidence_pack: dict | None = None,
) -> dict:
    result = EvalResult()

    _grade_schema_pass(result, event, node_outputs)
    _grade_answer_leakage(result, event, node_outputs)
    _grade_pedagogy_fit(result, event, node_outputs)
    _grade_action_appropriateness(result, event, node_outputs)
    _grade_learner_fit(result, event, node_outputs, learner_state)

    return result.to_dict()


def _grade_schema_pass(result: EvalResult, event: str, outputs: dict) -> None:
    key_schema_map = {
        "READING": ("problem_guide", "problem_guide.schema.json"),
        "IDEATING": ("ideate", "ideate_analysis.schema.json"),
        "ERROR_FEEDBACK": ("error_diagnosis", "error_diagnosis.schema.json"),
        "AC_REVIEW": ("post_ac", "post_ac.schema.json"),
        "TRANSFER": ("transfer", "transfer_problem.schema.json"),
        "CHAT": ("chat", "ai_reply.schema.json"),
        "KNOWLEDGE_REVIEW": ("knowledge_review", "knowledge_review.schema.json"),
    }

    entry = key_schema_map.get(event.upper())
    if not entry:
        result.score("schema_pass", 1.0, "no schema for this event")
        return

    output_key, schema_file = entry
    payload = outputs.get(output_key)
    if payload is None:
        result.score("schema_pass", 0.0, f"missing output key: {output_key}")
        return

    schema_path = SCHEMA_DIR / schema_file
    if not schema_path.exists():
        result.score("schema_pass", 0.5, f"schema file not found: {schema_file}")
        return

    with open(schema_path) as f:
        schema = json.load(f)

    try:
        jsonschema.validate(instance=payload, schema=schema)
        result.score("schema_pass", 1.0)
    except jsonschema.ValidationError as e:
        result.score("schema_pass", 0.0, e.message)


def _grade_answer_leakage(result: EvalResult, event: str, outputs: dict) -> None:
    """Check if the output leaks complete code solutions."""
    text = json.dumps(outputs, ensure_ascii=False)

    code_patterns = [
        r"```python\n.*\n```",
        r"def\s+\w+\(.*\):\s*\n\s+.*return",
        r"print\(.*\)\s*\n.*input\(",
    ]

    for pattern in code_patterns:
        if re.search(pattern, text, re.DOTALL):
            if event.upper() in ("TRANSFER",):
                result.score("answer_leakage", 0.8, "acceptable for transfer problem")
            else:
                result.score("answer_leakage", 0.3, f"potential code leakage detected")
            return

    result.score("answer_leakage", 1.0)


def _grade_pedagogy_fit(result: EvalResult, event: str, outputs: dict) -> None:
    """Basic pedagogy fit check — does the output match the pedagogical intent?"""
    event_upper = event.upper()
    output_key_map = {
        "READING": "problem_guide",
        "IDEATING": "ideate",
        "ERROR_FEEDBACK": "error_diagnosis",
        "AC_REVIEW": "post_ac",
    }

    key = output_key_map.get(event_upper)
    if not key:
        result.score("pedagogy_fit", 0.8, "non-core event, basic pass")
        return

    payload = outputs.get(key, {})
    if not payload:
        result.score("pedagogy_fit", 0.0, "empty output")
        return

    text_len = len(json.dumps(payload, ensure_ascii=False))
    if text_len < 20:
        result.score("pedagogy_fit", 0.3, "output too short")
    elif text_len > 5000:
        result.score("pedagogy_fit", 0.6, "output may be overly verbose")
    else:
        result.score("pedagogy_fit", 1.0)


def _grade_action_appropriateness(result: EvalResult, event: str, outputs: dict) -> None:
    result.score("action_appropriateness", 1.0)


def _grade_learner_fit(result: EvalResult, event: str, outputs: dict, learner: dict | None) -> None:
    if not learner:
        result.score("learner_fit", 0.7, "no learner state available")
        return
    result.score("learner_fit", 1.0)
