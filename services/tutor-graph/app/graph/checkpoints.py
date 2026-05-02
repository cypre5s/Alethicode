"""Checkpoint utilities — list recent labeled checkpoints for a thread."""

from __future__ import annotations

from langgraph.checkpoint.base import BaseCheckpointSaver


async def list_checkpoints(
    checkpointer: BaseCheckpointSaver,
    thread_id: str,
    *,
    limit: int = 20,
) -> list[dict]:
    """Return up to *limit* most recent checkpoints with business labels."""
    config = {"configurable": {"thread_id": thread_id}}
    results: list[dict] = []
    async for checkpoint_tuple in checkpointer.alist(config, limit=limit):
        metadata = checkpoint_tuple.metadata or {}
        label = metadata.get("label", "")
        if not label:
            continue
        results.append({
            "checkpoint_id": checkpoint_tuple.checkpoint["id"],
            "phase": metadata.get("phase", ""),
            "label": label,
            "created_at": metadata.get("created_at", ""),
        })
    return results
