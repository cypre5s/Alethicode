"""Indexing endpoints — `/v1/rag/index/{entity_type}` and DELETE.

Phase 0 wires the actual LightRAG `ainsert` / `adelete_by_doc_id` calls.
The metadata is stored as a `track_id` (entity_type + entity_id) so
queries can later filter / surface the originating business object.

LightRAG's `track_id` is the safest mapping for our purpose: business
ids never collide with the SHA-based doc ids LightRAG generates
internally, and `track_id` is preserved end-to-end through chunk +
entity tables, allowing Java-side reverse lookup of `page_id` /
`notebook_id` / `memory_key`.
"""

from __future__ import annotations

import logging
import uuid

from fastapi import APIRouter, Depends, HTTPException, Path, status

from ..auth import require_internal_token
from ..rag.builder import get_rag
from ..schemas import EntityType, IndexAccepted, IndexRequest

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/v1/rag/index",
    tags=["index"],
    dependencies=[Depends(require_internal_token)],
)


def _track_id(entity_type: str, entity_id: str) -> str:
    return f"{entity_type}:{entity_id}"


@router.post(
    "/{entity_type}",
    response_model=IndexAccepted,
    status_code=status.HTTP_202_ACCEPTED,
)
async def submit_index(
    payload: IndexRequest,
    entity_type: EntityType = Path(...),
) -> IndexAccepted:
    if not payload.content.strip():
        raise HTTPException(
            status_code=422,
            detail={"message": "content must not be blank"},
        )

    rag = await get_rag()
    track_id = _track_id(entity_type.value, payload.entity_id)

    # LightRAG 1.4.15 `ainsert` only accepts `(input, ids, file_paths,
    # track_id, split_by_character[_only])`. There is no first-class
    # metadata channel: `track_id` is what we use to round-trip business
    # ids end-to-end (chunks + entities + doc_status all carry it). Phase 1
    # will add a sidecar PG table keyed by `track_id` if we need richer
    # filtering (kc_ids, language_pack_id) on the retrieval path.
    file_path = (
        str(payload.metadata.get("source_path"))
        if payload.metadata.get("source_path")
        else f"{entity_type.value}/{payload.entity_id}"
    )
    await rag.ainsert(
        input=payload.content,
        file_paths=file_path,
        track_id=track_id,
    )

    return IndexAccepted(
        indexing_task_id=str(uuid.uuid4()),
        entity_type=entity_type.value,
        entity_id=payload.entity_id,
    )


@router.delete(
    "/{entity_type}/{entity_id}",
    status_code=status.HTTP_204_NO_CONTENT,
)
async def delete_index(
    entity_type: EntityType = Path(...),
    entity_id: str = Path(..., min_length=1),
) -> None:
    rag = await get_rag()
    track_id = _track_id(entity_type.value, entity_id)
    try:
        await rag.adelete_by_doc_id(track_id)
    except AttributeError:
        # LightRAG renames this method across versions; try alternates.
        await rag.adelete_by_track_id(track_id)  # type: ignore[attr-defined]
    return None


@router.post(
    "/pipeline/drain",
    status_code=status.HTTP_202_ACCEPTED,
)
async def drain_pipeline() -> dict[str, bool]:
    """Trigger LightRAG to drain all pending docs immediately instead of
    waiting for the 30s outbox worker heartbeat. LightRAG's internal
    ``aprocess_enqueue_documents`` has its own lock so repeated calls
    are safe and serialized.
    """
    rag = await get_rag()
    if hasattr(rag, 'apipeline_process_enqueue_documents'):
        await rag.apipeline_process_enqueue_documents()
    elif hasattr(rag, 'aprocess_enqueue_documents'):
        await rag.aprocess_enqueue_documents()
    else:
        logger.warning("LightRAG instance has no drain method, skipping")
    return {"accepted": True}
