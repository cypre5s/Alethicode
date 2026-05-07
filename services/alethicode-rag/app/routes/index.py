"""提供 LightRAG 索引写入与删除端点。"""

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

    # LightRAG 无一等 metadata 通道，`track_id` 承载业务对象 id 并贯穿 chunk / entity / doc_status。
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
        # LightRAG 版本间方法名不稳定，此处显式兼容已知命名。
        await rag.adelete_by_track_id(track_id)  # type: ignore[attr-defined]
    return None


@router.post(
    "/pipeline/drain",
    status_code=status.HTTP_202_ACCEPTED,
)
async def drain_pipeline() -> dict[str, bool]:
    """立即触发 LightRAG 处理待入库文档。"""
    rag = await get_rag()
    if hasattr(rag, 'apipeline_process_enqueue_documents'):
        await rag.apipeline_process_enqueue_documents()
    elif hasattr(rag, 'aprocess_enqueue_documents'):
        await rag.aprocess_enqueue_documents()
    else:
        logger.warning("LightRAG instance has no drain method, skipping")
    return {"accepted": True}
