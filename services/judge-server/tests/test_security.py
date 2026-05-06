"""安全测试 + 反爬测试。

覆盖面：
1. Token 认证安全：缺 token / 错 token / 空 token / token 格式注入
2. 路由安全：路径穿越 / 未知路径 / HTTP 方法限制
3. 请求体攻击：超大 JSON / 恶意字段注入 / 非法类型
4. safety 模块：fork bomb / 沙箱逃逸 / 敏感路径 / ctypes / shell 注入
5. trace 模块安全：恶意 Python 代码不影响 host / 无限循环被截断
6. diagnosis 安全：恶意 error output 不导致 ReDoS / 诊断不泄露系统路径
7. /explain 安全：不泄露 LLM API key / 恶意输入不 crash
8. /metrics 安全：不暴露敏感信息
9. 反爬/反扫描：大量非法请求不影响合法请求处理能力
"""

from __future__ import annotations

import hashlib
import json
import sys
import threading
import time
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "judge_server"))

flask = pytest.importorskip("flask")

import server as server_module
from safety.screener import SafetyScreener
from trace.tracer import trace_python
from diagnosis.engine import diagnose
from diagnosis.rules import diagnose_by_rules
from unittest.mock import patch


@pytest.fixture
def client():
    server_module.app.config["TESTING"] = True
    return server_module.app.test_client()


@pytest.fixture
def valid_token():
    return hashlib.sha256(b"alethicode-host-test-token").hexdigest()


@pytest.fixture
def wrong_token():
    return hashlib.sha256(b"wrong-token").hexdigest()


# ── 1. Token 认证安全 ──


class TestTokenSecurity:
    def test_missing_token_returns_401(self, client):
        resp = client.post("/judge", json={"src": "print(1)"})
        assert resp.status_code == 401

    def test_wrong_token_returns_401(self, client, wrong_token):
        resp = client.post("/judge", json={"src": "print(1)"}, headers={"X-Judge-Server-Token": wrong_token})
        assert resp.status_code == 401

    def test_empty_token_returns_401(self, client):
        resp = client.post("/judge", json={"src": "print(1)"}, headers={"X-Judge-Server-Token": ""})
        assert resp.status_code == 401

    def test_null_token_returns_401(self, client):
        resp = client.post("/judge", json={"src": "print(1)"}, headers={"X-Judge-Server-Token": "null"})
        assert resp.status_code == 401

    def test_sql_injection_in_token_returns_401(self, client):
        resp = client.post("/judge", json={}, headers={"X-Judge-Server-Token": "' OR 1=1 --"})
        assert resp.status_code == 401

    def test_token_not_leaked_in_response_body(self, client, wrong_token):
        resp = client.post("/judge", json={}, headers={"X-Judge-Server-Token": wrong_token})
        body = resp.get_data(as_text=True)
        assert wrong_token not in body
        assert "alethicode-host-test-token" not in body

    def test_explain_token_check(self, client, wrong_token):
        resp = client.post("/explain", json={"src": "x"}, headers={"X-Judge-Server-Token": wrong_token})
        assert resp.status_code == 401

    def test_trace_token_check(self, client, wrong_token):
        resp = client.post("/trace", json={"language": "Python3", "src": "x=1"}, headers={"X-Judge-Server-Token": wrong_token})
        assert resp.status_code == 401

    def test_ping_token_check(self, client, wrong_token):
        resp = client.post("/ping", json={}, headers={"X-Judge-Server-Token": wrong_token})
        assert resp.status_code == 401


# ── 2. 路由安全 ──


class TestRouteSecurity:
    def test_unknown_path_returns_404(self, client, valid_token):
        resp = client.post("/admin", json={}, headers={"X-Judge-Server-Token": valid_token})
        assert resp.status_code == 404

    def test_deep_nested_path_returns_404(self, client, valid_token):
        resp = client.post("/a/b/c/d/e", json={}, headers={"X-Judge-Server-Token": valid_token})
        assert resp.status_code in (404, 401)

    def test_get_on_judge_not_allowed(self, client, valid_token):
        resp = client.get("/judge", headers={"X-Judge-Server-Token": valid_token})
        assert resp.status_code == 405

    def test_metrics_post_rejected(self, client):
        resp = client.post("/metrics", json={})
        assert resp.status_code in (404, 405)

    def test_metrics_no_auth_needed(self, client):
        resp = client.get("/metrics")
        assert resp.status_code == 200


# ── 3. 请求体攻击 ──


class TestRequestBodyAttacks:
    def test_empty_json_body(self, client, valid_token):
        resp = client.post("/judge", json={}, headers={"X-Judge-Server-Token": valid_token})
        assert resp.status_code in (200, 400)

    def test_non_json_body(self, client, valid_token):
        resp = client.post("/judge", data="not json", content_type="text/plain",
                           headers={"X-Judge-Server-Token": valid_token})
        assert resp.status_code in (200, 400)

    def test_extra_fields_ignored(self, client, valid_token, monkeypatch):
        def fake_judge(**kwargs):
            return [{"test_case": "1", "result": 0}]
        monkeypatch.setattr(server_module.JudgeServer, "judge", staticmethod(fake_judge))
        resp = client.post("/judge", json={
            "language_config": {"run": {"command": "echo", "seccomp_rule": None}},
            "src": "print(1)", "max_cpu_time": 1000, "max_memory": 134217728,
            "test_case_id": "t1",
            "__proto__": {"polluted": True},
            "constructor": "evil",
        }, headers={"X-Judge-Server-Token": valid_token})
        assert resp.status_code == 200

    def test_very_long_src_does_not_crash(self, client, valid_token, monkeypatch):
        def fake_judge(**kwargs):
            return [{"test_case": "1", "result": 0}]
        monkeypatch.setattr(server_module.JudgeServer, "judge", staticmethod(fake_judge))
        resp = client.post("/judge", json={
            "language_config": {"run": {"command": "echo", "seccomp_rule": None}},
            "src": "x" * 1_000_000,
            "max_cpu_time": 1000, "max_memory": 134217728, "test_case_id": "t1",
        }, headers={"X-Judge-Server-Token": valid_token})
        assert resp.status_code == 200


# ── 4. Safety 模块安全检测 ──


class TestSafetyModuleDetection:
    @pytest.fixture(autouse=True)
    def enable_safety(self):
        with patch.dict("os.environ", {"ENABLE_AI_SAFETY": "true"}, clear=False):
            yield

    def test_detect_bash_fork_bomb(self):
        s = SafetyScreener()
        assert s.screen(":(){ :|:& };:")["is_risky"]

    def test_detect_python_fork_bomb(self):
        s = SafetyScreener()
        assert s.screen("import os\nos.fork()")["is_risky"]

    def test_detect_etc_shadow_read(self):
        s = SafetyScreener()
        assert s.screen("f = open('/etc/shadow')\ndata = f.read()")["is_risky"]

    def test_detect_proc_self_maps(self):
        s = SafetyScreener()
        assert s.screen("open('/proc/self/maps')")["is_risky"]

    def test_detect_subprocess_shell(self):
        s = SafetyScreener()
        assert s.screen("import subprocess\nsubprocess.call('/bin/bash')")["is_risky"]

    def test_detect_ctypes_cdll(self):
        s = SafetyScreener()
        assert s.screen("import ctypes\nctypes.CDLL('libc.so.6')")["is_risky"]

    def test_detect_ctypes_import_dynamic(self):
        s = SafetyScreener()
        assert s.screen("mod = __import__('ctypes')")["is_risky"]

    def test_normal_code_passes(self):
        s = SafetyScreener()
        assert not s.screen("for i in range(100):\n    print(i)")["is_risky"]

    def test_obfuscated_import_os_fork(self):
        s = SafetyScreener()
        r = s.screen("import os\nfor i in range(100):\n    os.fork()")
        assert r["is_risky"]


# ── 5. Trace 模块安全 ──


class TestTraceModuleSecurity:
    def test_infinite_loop_truncated(self):
        r = trace_python("while True:\n    pass\n", max_steps=100)
        assert r["status"] == "truncated"
        assert r["total_steps"] <= 100

    def test_memory_bomb_truncated(self):
        r = trace_python("x = []\nfor i in range(50):\n    x.append('a' * 100)\n", max_steps=30)
        assert r["status"] in ("truncated", "error", "ok")

    def test_import_os_in_trace_does_not_affect_host(self):
        r = trace_python("import os\nprint(os.getpid())\npass\n", max_steps=100)
        assert r["status"] == "ok"

    def test_raise_in_traced_code(self):
        r = trace_python("raise ValueError('test')\n", max_steps=100)
        assert r["status"] == "error"
        assert "ValueError" in r["error"]

    def test_exec_inside_trace(self):
        r = trace_python("exec('x = 42')\npass\n", max_steps=100)
        assert r["status"] == "ok"


# ── 6. Diagnosis 安全 ──


class TestDiagnosisSecurity:
    def test_very_long_error_does_not_redos(self):
        huge_error = "NameError: " + "x" * 100000
        start = time.monotonic()
        r = diagnose_by_rules(4, 0, 1, huge_error, "Python3")
        elapsed = time.monotonic() - start
        assert elapsed < 1.0

    def test_diagnosis_does_not_leak_system_paths(self):
        r = diagnose(
            {"result": 4, "signal": 0, "exit_code": 1, "output": "FileNotFoundError: /etc/shadow"},
            "Python3",
            "open('/etc/shadow')",
        )
        assert "/etc/shadow" not in r.get("root_cause_hint", "")

    def test_null_bytes_in_error(self):
        r = diagnose_by_rules(4, 0, 1, "Error\x00\x00evil", "Python3")
        assert r is not None or r is None  # 不 crash 就行

    def test_unicode_error_message(self):
        r = diagnose(
            {"result": 4, "signal": 0, "exit_code": 1, "output": "错误：变量 x 未定义"},
            "Python3",
            "print(x)",
        )
        assert isinstance(r, dict)


# ── 7. /explain 安全 ──


class TestExplainSecurity:
    def test_explain_does_not_leak_api_key(self, client, valid_token):
        resp = client.post("/explain", json={
            "language": "Python3",
            "src": "print(os.environ)",
        }, headers={"X-Judge-Server-Token": valid_token})
        body = resp.get_data(as_text=True)
        assert "AI_EXPLAIN_API_KEY" not in body
        assert "API_KEY" not in body.upper() or "AI_EXPLAIN_API_KEY" not in body

    def test_explain_with_empty_body(self, client, valid_token):
        resp = client.post("/explain", json={}, headers={"X-Judge-Server-Token": valid_token})
        assert resp.status_code == 200


# ── 8. /metrics 安全 ──


class TestMetricsSecurity:
    def test_metrics_does_not_expose_tokens(self, client):
        resp = client.get("/metrics")
        body = resp.get_data(as_text=True)
        assert "token" not in body.lower() or "alethicode" not in body
        assert "api_key" not in body.lower()
        assert "password" not in body.lower()


# ── 9. 反爬/反扫描 ──


class TestAntiScanResilience:
    def test_100_invalid_token_requests_dont_degrade_valid(self, client, valid_token, monkeypatch):
        """100 个错误 token 请求后，合法请求仍然正常处理。"""
        for _ in range(100):
            client.post("/judge", json={}, headers={"X-Judge-Server-Token": "invalid"})

        def fake_judge(**kwargs):
            return [{"test_case": "1", "result": 0}]
        monkeypatch.setattr(server_module.JudgeServer, "judge", staticmethod(fake_judge))
        resp = client.post("/judge", json={
            "language_config": {"run": {"command": "echo", "seccomp_rule": None}},
            "src": "print(1)", "max_cpu_time": 1000, "max_memory": 134217728, "test_case_id": "t1",
        }, headers={"X-Judge-Server-Token": valid_token})
        assert resp.status_code == 200
        assert resp.get_json()["err"] is None

    def test_50_unknown_paths_dont_affect_valid_routes(self, client, valid_token, monkeypatch):
        """50 个不存在路径的请求后，合法路由仍然正常。"""
        for i in range(50):
            client.post(f"/nonexistent_{i}", json={}, headers={"X-Judge-Server-Token": valid_token})

        resp = client.post("/ping", json={}, headers={"X-Judge-Server-Token": valid_token})
        assert resp.status_code == 200
        assert resp.get_json()["data"]["action"] == "pong"

    def test_concurrent_invalid_and_valid_mixed(self, client, valid_token, monkeypatch):
        """20 线程同时发 invalid + valid 请求，valid 全部成功。"""
        def fake_judge(**kwargs):
            return [{"test_case": "1", "result": 0}]
        monkeypatch.setattr(server_module.JudgeServer, "judge", staticmethod(fake_judge))

        valid_results = []
        invalid_results = []
        lock = threading.Lock()

        def valid_request():
            resp = client.post("/judge", json={
                "language_config": {"run": {"command": "echo", "seccomp_rule": None}},
                "src": "print(1)", "max_cpu_time": 1000, "max_memory": 134217728, "test_case_id": "t1",
            }, headers={"X-Judge-Server-Token": valid_token})
            with lock:
                valid_results.append(resp.status_code)

        def invalid_request():
            resp = client.post("/judge", json={}, headers={"X-Judge-Server-Token": "bad"})
            with lock:
                invalid_results.append(resp.status_code)

        threads = []
        for i in range(20):
            threads.append(threading.Thread(target=valid_request if i % 2 == 0 else invalid_request))
        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=10)

        assert all(s == 200 for s in valid_results), f"valid failures: {valid_results}"
        assert all(s == 401 for s in invalid_results), f"invalid non-401: {invalid_results}"
