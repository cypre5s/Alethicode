"""Phase 5 完整自测：trace_python 函数 + /trace 路由。

覆盖面：
- trace_python：简单程序 / for 循环变量快照 / if 分支 / 输出捕获
- 大循环 max_steps 截断 / 异常程序 status=error / 空程序
- /trace 路由：token 校验 / unsupported language / 正常 Python / 缓存命中
"""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "judge_server"))

from trace.tracer import trace_python

flask = pytest.importorskip("flask")
import server as server_module


@pytest.fixture
def client():
    server_module.app.config["TESTING"] = True
    return server_module.app.test_client()


@pytest.fixture
def auth_token():
    return hashlib.sha256(b"alethicode-host-test-token").hexdigest()


class TestTracePython:
    @staticmethod
    def _all_vars(steps):
        """合并所有步骤中出现过的变量最终值。"""
        merged = {}
        for s in steps:
            merged.update(s["variables"])
        return merged

    def test_simple_assignment(self):
        r = trace_python("x = 1\ny = 2\nz = x + y\npass\n")
        assert r["status"] == "ok"
        assert r["total_steps"] >= 3
        merged = self._all_vars(r["steps"])
        assert merged.get("z") == 3

    def test_for_loop_variables(self):
        r = trace_python("total = 0\nfor i in range(3):\n    total += i\n")
        assert r["status"] == "ok"
        assert any(s["variables"].get("i") == 2 for s in r["steps"])

    def test_if_branch(self):
        r = trace_python("x = 5\nif x > 3:\n    y = 'big'\nelse:\n    y = 'small'\npass\n")
        assert r["status"] == "ok"
        merged = self._all_vars(r["steps"])
        assert merged.get("y") == "big"

    def test_stdout_capture(self):
        r = trace_python("x = 1\nprint('hello')\npass\n")
        assert r["status"] == "ok"
        all_output = "".join(s["output"] for s in r["steps"])
        assert "hello" in all_output

    def test_stdin_input(self):
        r = trace_python("x = input()\npass\n", stdin_text="42\n")
        assert r["status"] == "ok"
        merged = self._all_vars(r["steps"])
        assert merged.get("x") == "42"

    def test_max_steps_truncation(self):
        r = trace_python("for i in range(10000):\n    pass\n", max_steps=50)
        assert r["status"] == "truncated"
        assert r["total_steps"] <= 50

    def test_error_program(self):
        r = trace_python("x = 1 / 0\n")
        assert r["status"] == "error"
        assert "ZeroDivisionError" in r["error"]

    def test_empty_program(self):
        r = trace_python("")
        assert r["status"] == "ok"
        assert r["total_steps"] <= 1

    def test_variable_filtering_large_list(self):
        r = trace_python("big = list(range(100))\npass\n")
        assert r["status"] == "ok"
        merged = self._all_vars(r["steps"])
        big_val = merged.get("big")
        assert isinstance(big_val, list)
        assert len(big_val) <= 11


class TestTraceRoute:
    def test_trace_token_missing_returns_401(self, client):
        resp = client.post("/trace", json={"language": "Python3", "src": "x=1"})
        assert resp.status_code == 401

    def test_trace_unsupported_language(self, client, auth_token):
        resp = client.post("/trace", json={"language": "C++", "src": "int main(){}"}, headers={"X-Judge-Server-Token": auth_token})
        assert resp.status_code == 200
        body = resp.get_json()
        assert body["data"]["status"] == "unsupported"

    def test_trace_python_returns_steps(self, client, auth_token):
        resp = client.post("/trace", json={
            "language": "Python3",
            "src": "x = 1\ny = x + 1\n",
            "input": "",
        }, headers={"X-Judge-Server-Token": auth_token})
        assert resp.status_code == 200
        body = resp.get_json()
        assert body["err"] is None
        assert body["data"]["status"] == "ok"
        assert len(body["data"]["steps"]) >= 2

    def test_trace_cache_hit(self, client, auth_token):
        payload = {"language": "Python3", "src": "a = 42\n", "input": ""}
        headers = {"X-Judge-Server-Token": auth_token}
        r1 = client.post("/trace", json=payload, headers=headers)
        r2 = client.post("/trace", json=payload, headers=headers)
        assert r1.get_json() == r2.get_json()
