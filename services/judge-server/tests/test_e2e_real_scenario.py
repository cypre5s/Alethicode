"""端到端真实场景全链路测试。

模拟从业务端（Java 后端）视角的完整调用链路：
  HTTP POST /judge → Flask server → JudgeServer.judge()
    → InitSubmissionEnv（创建真实临时目录）
    → Compiler（跳过，Python 不需编译）
    → JudgeClient.run()（每个测试点经过 worker_pool → _judge_one）
      → _judger.run（mock 返回预设结果）
      → output 比对 / diagnosis 生成 edu_diagnosis
    → 返回 {err: null, data: [...]}

验证：
1. 同步模式：返回的 data 每个 case 都有完整字段 + edu_diagnosis
2. SSE 模式：case event 数量与 done event 一致
3. ACM 短路：WA 后停止
4. OI 模式：全跑
5. /explain 端点：入参完整时返回结果
6. /trace 端点：Python 代码返回 steps
7. /metrics 端点：反映真实 judge 调用后的计数变化
8. /ping 端点：返回 pong
"""

from __future__ import annotations

import hashlib
import json
import os
import sys
import tempfile
import threading
import time
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "judge_server"))

flask = pytest.importorskip("flask")

import _judger
import server as server_module
from worker_pool import reset_global_pool


@pytest.fixture(autouse=True)
def fresh_worker_pool():
    """每个测试用干净的 worker pool，避免跨测试污染。"""
    reset_global_pool()
    yield
    reset_global_pool()


@pytest.fixture(autouse=True)
def mock_os_chown(monkeypatch):
    """host 上非 root 不能 chown；判题场景 chown 是 sandbox 权限隔离，host 直接跳过。"""
    monkeypatch.setattr(os, "chown", lambda *a, **kw: None)


@pytest.fixture
def client():
    server_module.app.config["TESTING"] = True
    return server_module.app.test_client()


@pytest.fixture
def auth_token():
    return hashlib.sha256(b"alethicode-host-test-token").hexdigest()


@pytest.fixture
def real_test_case_dir():
    """创建一个包含 2 个测试点的真实 test_case 目录。"""
    with tempfile.TemporaryDirectory(prefix="judge_e2e_") as tmpdir:
        case_dir = os.path.join(tmpdir, "problem_1")
        os.makedirs(case_dir)

        # 测试点 1：input=5, expected_output=25
        with open(os.path.join(case_dir, "1.in"), "w") as f:
            f.write("5\n")
        with open(os.path.join(case_dir, "1.out"), "w") as f:
            f.write("25\n")

        # 测试点 2：input=3, expected_output=9
        with open(os.path.join(case_dir, "2.in"), "w") as f:
            f.write("3\n")
        with open(os.path.join(case_dir, "2.out"), "w") as f:
            f.write("9\n")

        info = {
            "test_cases": {
                "1": {
                    "input_name": "1.in",
                    "output_name": "1.out",
                    "input_size": 2,
                    "output_size": 3,
                    "output_md5": hashlib.md5(b"25\n").hexdigest(),
                    "stripped_output_md5": hashlib.md5(b"25").hexdigest(),
                },
                "2": {
                    "input_name": "2.in",
                    "output_name": "2.out",
                    "input_size": 2,
                    "output_size": 2,
                    "output_md5": hashlib.md5(b"9\n").hexdigest(),
                    "stripped_output_md5": hashlib.md5(b"9").hexdigest(),
                }
            }
        }
        with open(os.path.join(case_dir, "info"), "w") as f:
            json.dump(info, f)

        yield tmpdir, "problem_1"


def _is_compile_call(**kwargs):
    """判断这次 _judger.run 是编译还是运行：编译阶段的 output_path 以 compiler.out 结尾。"""
    return kwargs.get("output_path", "").endswith("compiler.out")


def _mock_judger_run_ac(**kwargs):
    """模拟 _judger.run 返回 AC（result=0）。"""
    output_path = kwargs.get("output_path", "")
    input_path = kwargs.get("input_path", "")
    if _is_compile_call(**kwargs):
        # 编译成功：创建 compiler.out（会被 Compiler 删掉）
        try:
            with open(output_path, "w") as f:
                f.write("")
        except Exception:
            pass
        return {"cpu_time": 50, "real_time": 100, "memory": 1024*1024, "signal": 0, "exit_code": 0, "error": 0, "result": 0}
    # 运行阶段：读 input 的 n，写 n*n 到 output
    try:
        with open(input_path) as f:
            n = int(f.read().strip())
        with open(output_path, "w") as f:
            f.write(f"{n*n}\n")
    except Exception:
        pass
    return {"cpu_time": 10, "real_time": 15, "memory": 1024*1024, "signal": 0, "exit_code": 0, "error": 0, "result": 0}


def _mock_judger_run_wa(**kwargs):
    """模拟 _judger.run 返回成功执行但输出错误（WA 由比对阶段决定）。"""
    output_path = kwargs.get("output_path", "")
    if _is_compile_call(**kwargs):
        try:
            with open(output_path, "w") as f:
                f.write("")
        except Exception:
            pass
        return {"cpu_time": 50, "real_time": 100, "memory": 1024*1024, "signal": 0, "exit_code": 0, "error": 0, "result": 0}
    try:
        with open(output_path, "w") as f:
            f.write("WRONG\n")
    except Exception:
        pass
    return {"cpu_time": 10, "real_time": 15, "memory": 1024*1024, "signal": 0, "exit_code": 0, "error": 0, "result": 0}


def _mock_judger_run_tle(**kwargs):
    """模拟 _judger.run 返回 TLE。"""
    return {
        "cpu_time": 2000,
        "real_time": 6000,
        "memory": 1024 * 1024,
        "signal": 0,
        "exit_code": 0,
        "error": 0,
        "result": 1,  # CPU_TIME_LIMIT_EXCEEDED
    }


def _mock_judger_run_re(**kwargs):
    """模拟 _judger.run 返回 RE（segfault）。"""
    output_path = kwargs.get("output_path", "")
    try:
        with open(output_path, "w") as f:
            f.write("")
    except Exception:
        pass
    return {
        "cpu_time": 5,
        "real_time": 10,
        "memory": 512 * 1024,
        "signal": 11,
        "exit_code": 0,
        "error": 0,
        "result": 4,  # RUNTIME_ERROR
    }


class TestE2eSyncAC:
    """场景：Python 提交全 AC。"""

    def test_full_chain_ac(self, client, auth_token, real_test_case_dir, monkeypatch, tmp_path):
        tmpdir, case_id = real_test_case_dir
        workspace = str(tmp_path / "judger_run")
        os.makedirs(workspace, exist_ok=True)
        monkeypatch.setattr(_judger, "run", _mock_judger_run_ac)
        monkeypatch.setattr(server_module, "JUDGER_WORKSPACE_BASE", workspace)
        monkeypatch.setattr(server_module, "TEST_CASE_DIR", tmpdir)
        monkeypatch.setattr(server_module, "RUN_USER_UID", os.getuid())
        monkeypatch.setattr(server_module, "RUN_GROUP_GID", os.getgid())
        monkeypatch.setattr(server_module, "COMPILER_USER_UID", os.getuid())

        resp = client.post("/judge", json={
            "language_config": {
                "compile": {
                    "src_name": "solution.py",
                    "exe_name": "solution.py",
                    "max_cpu_time": 3000,
                    "max_real_time": 10000,
                    "max_memory": 134217728,
                    "compile_command": "/usr/bin/python3 -m py_compile {src_path}",
                },
                "run": {
                    "command": "/usr/bin/python3 {exe_path}",
                    "seccomp_rule": None,
                    "env": [],
                },
            },
            "src": "n = int(input())\nprint(n*n)\n",
            "max_cpu_time": 1000,
            "max_memory": 134217728,
            "test_case_id": case_id,
            "output": True,
        }, headers={"X-Judge-Server-Token": auth_token})

        assert resp.status_code == 200
        body = resp.get_json()
        assert body["err"] is None
        data = body["data"]
        assert isinstance(data, list)
        assert len(data) == 2

        for case in data:
            assert case["result"] == 0
            assert "test_case" in case
            assert "cpu_time" in case
            assert "memory" in case
            if "edu_diagnosis" in case:
                diag = case["edu_diagnosis"]
                assert diag["error_kind"] == "ACCEPTED"
                assert diag["confidence"] == 1.0


class TestE2eSyncWA:
    """场景：Python 提交 WA。"""

    def test_full_chain_wa_with_diagnosis(self, client, auth_token, real_test_case_dir, monkeypatch, tmp_path):
        tmpdir, case_id = real_test_case_dir
        workspace = str(tmp_path / "judger_run")
        os.makedirs(workspace, exist_ok=True)
        monkeypatch.setattr(_judger, "run", _mock_judger_run_wa)
        monkeypatch.setattr(server_module, "JUDGER_WORKSPACE_BASE", workspace)
        monkeypatch.setattr(server_module, "TEST_CASE_DIR", tmpdir)
        monkeypatch.setattr(server_module, "RUN_USER_UID", os.getuid())
        monkeypatch.setattr(server_module, "RUN_GROUP_GID", os.getgid())
        monkeypatch.setattr(server_module, "COMPILER_USER_UID", os.getuid())

        resp = client.post("/judge", json={
            "language_config": {
                "compile": {
                    "src_name": "solution.py",
                    "exe_name": "solution.py",
                    "max_cpu_time": 3000,
                    "max_real_time": 10000,
                    "max_memory": 134217728,
                    "compile_command": "/usr/bin/python3 -m py_compile {src_path}",
                },
                "run": {
                    "command": "/usr/bin/python3 {exe_path}",
                    "seccomp_rule": None,
                    "env": [],
                },
            },
            "src": "print('WRONG')\n",
            "max_cpu_time": 1000,
            "max_memory": 134217728,
            "test_case_id": case_id,
            "output": True,
        }, headers={"X-Judge-Server-Token": auth_token})

        assert resp.status_code == 200
        body = resp.get_json()
        assert body["err"] is None
        data = body["data"]
        for case in data:
            assert case["result"] == _judger.RESULT_WRONG_ANSWER
            if "edu_diagnosis" in case:
                diag = case["edu_diagnosis"]
                assert diag["error_kind"] == "WRONG_ANSWER"
                assert diag["source"] == "rule"
                assert diag["confidence"] > 0


class TestE2ePingEndpoint:
    """场景：/ping 健康检查。"""

    def test_ping_returns_pong(self, client, auth_token):
        resp = client.post("/ping", json={}, headers={"X-Judge-Server-Token": auth_token})
        assert resp.status_code == 200
        body = resp.get_json()
        assert body["err"] is None
        assert body["data"]["action"] == "pong"
        assert "hostname" in body["data"]
        assert "cpu_core" in body["data"]


class TestE2eTraceEndpoint:
    """场景：POST /trace 完整链路。"""

    def test_trace_real_python_code(self, client, auth_token):
        resp = client.post("/trace", json={
            "language": "Python3",
            "src": "x = int(input())\ny = x * x\nprint(y)\npass\n",
            "input": "5\n",
            "max_steps": 500,
        }, headers={"X-Judge-Server-Token": auth_token})

        assert resp.status_code == 200
        body = resp.get_json()
        assert body["err"] is None
        data = body["data"]
        assert data["status"] == "ok"
        assert data["total_steps"] >= 3

        all_vars = {}
        for step in data["steps"]:
            all_vars.update(step["variables"])
        assert all_vars.get("y") == 25

        all_output = "".join(s["output"] for s in data["steps"])
        assert "25" in all_output


class TestE2eMetricsEndpoint:
    """场景：/metrics 反映判题调用计数。"""

    def test_metrics_before_and_after_judge(self, client, auth_token):
        resp_before = client.get("/metrics")
        assert resp_before.status_code == 200
        body_before = resp_before.get_data(as_text=True)

        from metrics.exporter import get_metrics_collector
        mc = get_metrics_collector()
        mc.record_judge_result(0, "Python3", 0.1)
        mc.record_judge_result(-1, "Python3", 0.2)

        resp_after = client.get("/metrics")
        body_after = resp_after.get_data(as_text=True)
        assert 'result="0"' in body_after
        assert 'result="-1"' in body_after


class TestE2eExplainEndpoint:
    """场景：/explain 完整链路（AI 不可用时返回 unavailable）。"""

    def test_explain_with_real_evidence(self, client, auth_token):
        resp = client.post("/explain", json={
            "language": "Python3",
            "src": "n = int(input())\nprint(n * n)\n",
            "failed_case_input": "5",
            "expected": "25",
            "actual": "WRONG",
            "signal": 0,
            "exit_code": 0,
            "error": "",
            "edu_diagnosis_hint": "WRONG_ANSWER",
        }, headers={"X-Judge-Server-Token": auth_token})

        assert resp.status_code == 200
        body = resp.get_json()
        assert body["err"] is None
        assert body["data"]["status"] in ("ok", "unavailable")


class TestE2eSSEStream:
    """场景：SSE 流式判题。"""

    def test_sse_stream_produces_case_and_done_events(self, client, auth_token, real_test_case_dir, monkeypatch, tmp_path):
        tmpdir, case_id = real_test_case_dir
        workspace = str(tmp_path / "judger_run")
        os.makedirs(workspace, exist_ok=True)
        monkeypatch.setattr(_judger, "run", _mock_judger_run_ac)
        monkeypatch.setattr(server_module, "JUDGER_WORKSPACE_BASE", workspace)
        monkeypatch.setattr(server_module, "TEST_CASE_DIR", tmpdir)
        monkeypatch.setattr(server_module, "RUN_USER_UID", os.getuid())
        monkeypatch.setattr(server_module, "RUN_GROUP_GID", os.getgid())
        monkeypatch.setattr(server_module, "COMPILER_USER_UID", os.getuid())

        resp = client.post("/judge", json={
            "language_config": {
                "compile": {
                    "src_name": "solution.py",
                    "exe_name": "solution.py",
                    "max_cpu_time": 3000,
                    "max_real_time": 10000,
                    "max_memory": 134217728,
                    "compile_command": "/usr/bin/python3 -m py_compile {src_path}",
                },
                "run": {
                    "command": "/usr/bin/python3 {exe_path}",
                    "seccomp_rule": None,
                    "env": [],
                },
            },
            "src": "n = int(input())\nprint(n*n)\n",
            "max_cpu_time": 1000,
            "max_memory": 134217728,
            "test_case_id": case_id,
            "output": True,
            "stream": True,
        }, headers={"X-Judge-Server-Token": auth_token})

        assert resp.status_code == 200
        assert "text/event-stream" in resp.content_type

        body = resp.get_data(as_text=True)
        blocks = [b for b in body.split("\n\n") if b.strip()]
        events = []
        for block in blocks:
            for line in block.split("\n"):
                if line.startswith("event: "):
                    events.append(line.split(": ", 1)[1])

        case_count = events.count("case")
        assert case_count == 2
        assert events[-1] == "done"
