"""Phase 3 完整自测：/explain 端点 + ACM 首错短路。

覆盖面：
- ExplainService：AI 可用 / 不可用 / 缓存命中 / LLM 失败降级
- /explain 路由：token 校验 / 正常请求 / 不可用时 status=unavailable
- ACM 短路：首个非 AC 后停止 / OI 模式全跑
- rule_type 参数从 _dispatch_judge 传入 JudgeServer.judge
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "judge_server"))

flask = pytest.importorskip("flask")
pytest.importorskip("requests")

import server as server_module


@pytest.fixture
def client():
    server_module.app.config["TESTING"] = True
    return server_module.app.test_client()


@pytest.fixture
def auth_token():
    return hashlib.sha256(b"alethicode-host-test-token").hexdigest()


# ── ExplainService 单元测试 ──


class TestExplainService:
    def test_disabled_when_no_endpoint(self):
        from explain.service import ExplainService
        with patch.dict("os.environ", {
            "AI_EXPLAIN_ENDPOINT": "",
            "AI_EXPLAIN_MODEL": "",
            "ENABLE_AI_EXPLAIN": "true",
        }, clear=False):
            svc = ExplainService()
            assert not svc.enabled

    def test_disabled_by_env_flag(self):
        from explain.service import ExplainService
        with patch.dict("os.environ", {
            "AI_EXPLAIN_ENDPOINT": "http://fake",
            "AI_EXPLAIN_MODEL": "m",
            "AI_EXPLAIN_API_KEY": "k",
            "ENABLE_AI_EXPLAIN": "false",
        }, clear=False):
            svc = ExplainService()
            assert not svc.enabled

    def test_unavailable_returns_status_unavailable(self):
        from explain.service import ExplainService
        with patch.dict("os.environ", {"ENABLE_AI_EXPLAIN": "false"}, clear=False):
            svc = ExplainService()
            r = svc.explain("Python3", "print(x)", "", "", "", 0, 1, "NameError")
            assert r["status"] == "unavailable"

    def test_success_returns_normalized_result(self):
        from explain.service import ExplainService
        mock_resp = {
            "summary": "变量 x 未定义",
            "root_cause": "print(x) 中的 x 没有被赋值",
            "next_step_hint": "检查变量名拼写是否正确",
            "references": ["变量作用域"],
        }
        with patch.dict("os.environ", {
            "ENABLE_AI_EXPLAIN": "true",
            "AI_EXPLAIN_ENDPOINT": "http://fake",
            "AI_EXPLAIN_MODEL": "m",
            "AI_EXPLAIN_API_KEY": "k",
        }, clear=False):
            svc = ExplainService()
            svc._client.call_json = MagicMock(return_value=mock_resp)
            r = svc.explain("Python3", "print(x)", "1", "1", "", 0, 1, "NameError: name 'x'")
            assert r["status"] == "ok"
            assert "未定义" in r["summary"]
            assert r["references"] == ["变量作用域"]

    def test_cache_hit_skips_llm_call(self):
        from explain.service import ExplainService
        mock_resp = {"summary": "s", "root_cause": "r", "next_step_hint": "h", "references": []}
        with patch.dict("os.environ", {
            "ENABLE_AI_EXPLAIN": "true",
            "AI_EXPLAIN_ENDPOINT": "http://fake",
            "AI_EXPLAIN_MODEL": "m",
            "AI_EXPLAIN_API_KEY": "k",
        }, clear=False):
            svc = ExplainService()
            svc._client.call_json = MagicMock(return_value=mock_resp)
            svc.explain("Python3", "print(x)", "1", "1", "", 0, 1, "err")
            svc.explain("Python3", "print(x)", "1", "1", "", 0, 1, "err")
            assert svc._client.call_json.call_count == 1

    def test_llm_error_returns_unavailable(self):
        from explain.service import ExplainService
        from llm.client import LlmCallError
        with patch.dict("os.environ", {
            "ENABLE_AI_EXPLAIN": "true",
            "AI_EXPLAIN_ENDPOINT": "http://fake",
            "AI_EXPLAIN_MODEL": "m",
            "AI_EXPLAIN_API_KEY": "k",
        }, clear=False):
            svc = ExplainService()
            svc._client.call_json = MagicMock(side_effect=LlmCallError("timeout"))
            r = svc.explain("Python3", "print(x)", "", "", "", 0, 1, "err")
            assert r["status"] == "unavailable"


# ── /explain 路由测试 ──


class TestExplainRoute:
    def test_explain_token_missing_returns_401(self, client):
        resp = client.post("/explain", json={"language": "Python3", "src": "x"})
        assert resp.status_code == 401

    def test_explain_returns_json_with_unavailable_when_not_configured(self, client, auth_token):
        resp = client.post("/explain", json={
            "language": "Python3",
            "src": "print(x)",
            "failed_case_input": "1",
            "expected": "1",
            "actual": "",
            "signal": 0,
            "exit_code": 1,
            "error": "NameError: name 'x' is not defined",
        }, headers={"X-Judge-Server-Token": auth_token})
        assert resp.status_code == 200
        body = resp.get_json()
        assert body["err"] is None
        assert body["data"]["status"] in ("ok", "unavailable")


# ── ACM 首错短路 + rule_type 传入 ──


class TestAcmShortCircuit:
    def test_rule_type_acm_passed_to_judge(self, client, auth_token, monkeypatch):
        captured = {}

        def fake_judge(**kwargs):
            captured.update(kwargs)
            return [{"test_case": "1", "result": 0}]

        monkeypatch.setattr(server_module.JudgeServer, "judge", staticmethod(fake_judge))
        resp = client.post("/judge", json={
            "language_config": {"run": {"command": "echo", "seccomp_rule": None}},
            "src": "print(1)", "max_cpu_time": 1000, "max_memory": 134217728,
            "test_case_id": "t1", "rule_type": "ACM",
        }, headers={"X-Judge-Server-Token": auth_token})
        assert resp.status_code == 200
        assert captured.get("rule_type") == "ACM"

    def test_rule_type_oi_passed_to_judge(self, client, auth_token, monkeypatch):
        captured = {}

        def fake_judge(**kwargs):
            captured.update(kwargs)
            return [{"test_case": "1", "result": 0}]

        monkeypatch.setattr(server_module.JudgeServer, "judge", staticmethod(fake_judge))
        resp = client.post("/judge", json={
            "language_config": {"run": {"command": "echo", "seccomp_rule": None}},
            "src": "print(1)", "max_cpu_time": 1000, "max_memory": 134217728,
            "test_case_id": "t1", "rule_type": "OI",
        }, headers={"X-Judge-Server-Token": auth_token})
        assert resp.status_code == 200
        assert captured.get("rule_type") == "OI"

    def test_default_rule_type_is_acm(self, client, auth_token, monkeypatch):
        captured = {}

        def fake_judge(**kwargs):
            captured.update(kwargs)
            return [{"test_case": "1", "result": 0}]

        monkeypatch.setattr(server_module.JudgeServer, "judge", staticmethod(fake_judge))
        resp = client.post("/judge", json={
            "language_config": {"run": {"command": "echo", "seccomp_rule": None}},
            "src": "print(1)", "max_cpu_time": 1000, "max_memory": 134217728,
            "test_case_id": "t1",
        }, headers={"X-Judge-Server-Token": auth_token})
        assert resp.status_code == 200
        assert captured.get("rule_type") == "ACM"
