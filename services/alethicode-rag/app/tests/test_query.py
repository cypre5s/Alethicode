"""RAG 查询结果归一化契约测试。"""

from __future__ import annotations

from typing import Any

import app.routes.query as query_module


class _FakeQueryParam:
    top_k: int = 0


class _FakeRag:
    async def aquery_data(self, _query: str, *, param: Any) -> dict[str, Any]:
        return {
            "status": "success",
            "data": {
                "chunks": [
                    {
                        "chunk_id": "chunk-string-slice",
                        "content": "字符串的使用\n使用[]获取字符串中一个或多个字符",
                        "file_path": "language_pack/43/p68",
                    }
                ],
                "entities": [],
                "relationships": [],
                "references": [],
            },
        }


async def test_query_enriches_courseware_chunk_with_business_page_id(monkeypatch) -> None:
    async def fake_get_rag() -> _FakeRag:
        return _FakeRag()

    async def fake_load_track_ids(chunk_ids: list[str]) -> dict[str, str]:
        assert chunk_ids == ["chunk-string-slice"]
        return {"chunk-string-slice": "courseware-page:10867"}

    monkeypatch.setattr(query_module, "default_query_param", lambda: _FakeQueryParam())
    monkeypatch.setattr(query_module, "get_rag", fake_get_rag)
    monkeypatch.setattr(query_module, "_load_track_ids_for_chunks", fake_load_track_ids, raising=False)

    hits = await query_module._run_query("如何进行字符串对应元素的选取", top_k=8)

    assert len(hits.chunks) == 1
    metadata = hits.chunks[0].metadata
    assert metadata["language_pack_id"] == 43
    assert metadata["page_no"] == 68
    assert metadata["entity_id"] == 10867
    assert metadata["page_id"] == 10867
