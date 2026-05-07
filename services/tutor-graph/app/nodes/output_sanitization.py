"""在校验和投影复用前清理持久化节点输出。"""

from __future__ import annotations

from copy import deepcopy

PROJECTION_VISUALIZE_HELPER_KEYS = frozenset({
    "visualize",
    "visualize_card_id",
    "visualize_failed",
})


def strip_projection_visualize_helpers(node_outputs: dict | None) -> dict:
    """移除仅供投影使用的可视化辅助字段。"""
    if not isinstance(node_outputs, dict):
        return {}

    sanitized = deepcopy(node_outputs)
    for node_name, payload in list(sanitized.items()):
        if node_name == "visualize" or not isinstance(payload, dict):
            continue
        for helper_key in PROJECTION_VISUALIZE_HELPER_KEYS:
            payload.pop(helper_key, None)
    return sanitized
