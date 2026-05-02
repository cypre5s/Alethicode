"""Build adversarial_dataset.jsonl from case_definitions.py.

Idempotent: running this script always produces the same JSONL given the
same source. The JSONL is committed alongside case_definitions.py so that
non-Python tooling (e.g., human reviewers, future LLM fine-tuning) can
consume it directly.

Usage:
    python -m app.eval.red_team.build_dataset
"""

from __future__ import annotations

import json
from pathlib import Path

from app.eval.red_team.case_definitions import (
    ALL_CASES,
    case_count_by_category,
    case_count_by_phase,
)


def serialize() -> str:
    """Render ALL_CASES as one JSON object per line, sorted keys for stability."""
    lines: list[str] = []
    for case in ALL_CASES:
        # Pydantic v2: model_dump returns dict; we also strip empty defaults.
        payload = case.model_dump(mode="json", exclude_defaults=False)
        lines.append(json.dumps(payload, ensure_ascii=False, sort_keys=True))
    return "\n".join(lines) + "\n"


def main() -> None:
    target = Path(__file__).parent / "adversarial_dataset.jsonl"
    target.write_text(serialize(), encoding="utf-8")
    print(f"Wrote {len(ALL_CASES)} cases to {target}")
    print(f"  by_category: {case_count_by_category()}")
    print(f"  by_phase:    {case_count_by_phase()}")


if __name__ == "__main__":
    main()
