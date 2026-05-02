"""Internal-token auth contract.

Index and query endpoints are inside the trust boundary; every request
must carry `X-Internal-Token` matching `RAG_INTERNAL_TOKEN`. Missing or
mismatched headers must return 401 with a problem+json-shaped detail.
"""

from __future__ import annotations

from fastapi.testclient import TestClient

from app.auth import _valid_token
from app.main import app


def test_index_endpoint_rejects_missing_token() -> None:
    with TestClient(app) as client:
        resp = client.post(
            "/v1/rag/index/courseware-page",
            json={"entity_id": "1", "content": "x", "metadata": {}},
        )
        assert resp.status_code == 401


def test_query_endpoint_rejects_wrong_token() -> None:
    with TestClient(app) as client:
        resp = client.post(
            "/v1/rag/query/courseware",
            headers={"X-Internal-Token": "wrong"},
            json={"query": "x"},
        )
        assert resp.status_code == 401


def test_current_token_is_accepted() -> None:
    assert _valid_token("current-rag-token", "current-rag-token", "") is True


def test_previous_token_is_accepted_during_rotation() -> None:
    assert _valid_token("previous-rag-token", "current-rag-token", "previous-rag-token") is True


def test_blank_and_wrong_tokens_are_rejected() -> None:
    assert _valid_token(None, "current-rag-token", "previous-rag-token") is False
    assert _valid_token("", "current-rag-token", "previous-rag-token") is False
    assert _valid_token("wrong", "current-rag-token", "previous-rag-token") is False
