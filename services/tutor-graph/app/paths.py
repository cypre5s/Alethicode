"""集中处理 tutor-graph 在本地与容器中的运行时路径。"""

from __future__ import annotations

from pathlib import Path


def locate_card_schema_dir() -> Path:
    """定位本地与容器布局中的卡片 schema 目录。

    通过向上搜索同时支持源码仓库和容器布局，避免硬编码父目录层级。
    """
    here = Path(__file__).resolve()
    for parent in here.parents:
        candidate = parent / "contracts" / "tutor_workflow" / "cards"
        if candidate.is_dir():
            return candidate
    raise FileNotFoundError(
        "contracts/tutor_workflow/cards directory not found searching upward "
        f"from {here}; check Dockerfile COPY directives or the repo layout."
    )


CARD_SCHEMA_DIR: Path = locate_card_schema_dir()
