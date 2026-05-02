"""Sanitize persisted node outputs before validation / projection reuse."""

from __future__ import annotations

from copy import deepcopy

PROJECTION_VISUALIZE_HELPER_KEYS = frozenset({
    "visualize",
    "visualize_card_id",
    "visualize_failed",
})


def strip_projection_visualize_helpers(node_outputs: dict | None) -> dict:
    """Remove projection-only visualize helper fields from schema-owned card payloads.

    These fields are transport metadata created after card schema validation and
    therefore must never be persisted back into canonical card payloads. If they
    leak into a later run's checkpointed state, the next schema validation pass
    fails before any new card is generated.
    """
    if not isinstance(node_outputs, dict):
        return {}

    sanitized = deepcopy(node_outputs)
    for node_name, payload in list(sanitized.items()):
        if node_name == "visualize" or not isinstance(payload, dict):
            continue
        for helper_key in PROJECTION_VISUALIZE_HELPER_KEYS:
            payload.pop(helper_key, None)
    return sanitized
