"""alethicode-rag 测试夹具。"""

from __future__ import annotations

import pytest


@pytest.fixture(autouse=True)
def stub_lifespan_llm_smoke(monkeypatch: pytest.MonkeyPatch) -> None:
    """测试环境不依赖外部 LLM key，只验证 FastAPI 路由自身契约。"""
    import app.main as main_module

    async def fake_llm(*_args, **_kwargs) -> str:
        return "ok"

    monkeypatch.setattr(main_module, "build_llm_callable", lambda _settings: fake_llm)
    main_module.LLM_SMOKE_OK = False
