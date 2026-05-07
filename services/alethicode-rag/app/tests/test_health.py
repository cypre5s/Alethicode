"""健康检查端点契约测试。

健康检查不要求内部认证，需覆盖 PG / Memgraph 均可达和部分不可达两类状态。
"""

from __future__ import annotations

from fastapi.testclient import TestClient


def test_health_returns_status_envelope(monkeypatch) -> None:
    # 通过 stub 底层驱动让两个探针都返回 ok。
    import app.routes.health as health_module

    class _FakeConn:
        async def execute(self, *_a, **_kw):  # noqa: D401 - stub
            return None

        async def close(self):
            return None

    async def _fake_pg_connect(**_kwargs):
        return _FakeConn()

    class _FakeSession:
        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, tb):
            return None

        async def run(self, *_a, **_kw):
            return None

    class _FakeDriver:
        def session(self, **_kw):
            return _FakeSession()

        async def close(self):
            return None

    class _FakeAsyncGraphDatabase:
        @staticmethod
        def driver(*_a, **_kw):
            return _FakeDriver()

    monkeypatch.setattr(health_module.asyncpg, "connect", _fake_pg_connect)
    monkeypatch.setattr(
        health_module, "AsyncGraphDatabase", _FakeAsyncGraphDatabase
    )

    from app.main import app

    with TestClient(app) as client:
        resp = client.get("/health")
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "ok"
        assert body["postgres"] == "ok"
        assert body["memgraph"] == "ok"
        assert body["rag_initialized"] is False


def test_health_reports_degraded_when_pg_down(monkeypatch) -> None:
    import app.routes.health as health_module

    async def _broken_connect(**_kwargs):
        raise OSError("Connection refused")

    class _FakeSession:
        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, tb):
            return None

        async def run(self, *_a, **_kw):
            return None

    class _FakeDriver:
        def session(self, **_kw):
            return _FakeSession()

        async def close(self):
            return None

    class _FakeAsyncGraphDatabase:
        @staticmethod
        def driver(*_a, **_kw):
            return _FakeDriver()

    monkeypatch.setattr(health_module.asyncpg, "connect", _broken_connect)
    monkeypatch.setattr(health_module, "AsyncGraphDatabase", _FakeAsyncGraphDatabase)

    from app.main import app

    with TestClient(app) as client:
        resp = client.get("/health")
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "degraded"
        assert body["postgres"].startswith("down")
        assert body["memgraph"] == "ok"
