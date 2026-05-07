"""从 case_definitions.py 构建 adversarial_dataset.jsonl。

幂等：同一份源文件每次都会生成相同 JSONL。JSONL 与 case_definitions.py
一起提交，便于非 Python 工具（例如人工评审和未来 LLM 微调）直接消费。

用法：
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
    """将 ALL_CASES 渲染为每行一个 JSON 对象，并稳定排序 key。"""
    lines: list[str] = []
    for case in ALL_CASES:
        # Pydantic v2 的 model_dump 返回 dict；这里保留空默认值保证数据集完整。
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
