"""提供 thread 最近检查点列表工具。"""

from __future__ import annotations

from langgraph.checkpoint.base import BaseCheckpointSaver


async def list_checkpoints(
    checkpointer: BaseCheckpointSaver,
    thread_id: str,
    *,
    limit: int = 20,
) -> list[dict]:
    """返回带业务标签的最近检查点。"""
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
