"""Phase 1 server.py 路由层 dispatch 集成测试。

只测路由分流契约（priority / stream / callback_url 三模式 + 互斥校验），
不跑真实判题（``JudgeServer.judge`` 被 monkeypatch 成返回固定数据的 stub）。

真实判题 (`_judger.run` + setuid + seccomp) 在判题镜像内单独跑。
"""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "judge_server"))

# Flask 是判题机的运行时依赖；host 上 `pip install --user flask requests` 后才能跑这些路由集成测试。
flask = pytest.importorskip("flask", reason="install flask + requests on host to run server dispatch tests")
pytest.importorskip("requests")

import server as server_module  # noqa: E402  conftest 已经 mock 了 _judger / pwd / grp / FileHandler


@pytest.fixture
def client(monkeypatch):
    server_module.app.config["TESTING"] = True
    return server_module.app.test_client()


@pytest.fixture
def auth_token():
    """test_client 上要带的 X-Judge-Server-Token 值，与 utils.token 保持一致。"""
    return hashlib.sha256(b"alethicode-host-test-token").hexdigest()


@pytest.fixture
def stub_judge(monkeypatch):
    """把 JudgeServer.judge 替换成可观察的 stub，便于断言入参。"""
    captured = {}

    def fake_judge(language_config, src, max_cpu_time, max_memory,
                    test_case_id=None, test_case=None,
                    spj_version=None, spj_config=None, spj_compile_config=None, spj_src=None,
                    output=False, io_mode=None,
                    priority="formal", on_case_done=None, rule_type="ACM"):
        captured["priority"] = priority
        captured["on_case_done_present"] = on_case_done is not None
        captured["test_case_id"] = test_case_id
        captured["rule_type"] = rule_type
        cases = [
            {"test_case": "1", "result": 0, "cpu_time": 10, "memory": 1024},
            {"test_case": "2", "result": 0, "cpu_time": 12, "memory": 2048},
        ]
        if on_case_done is not None:
            for c in cases:
                on_case_done(c)
        return cases

    monkeypatch.setattr(server_module.JudgeServer, "judge", staticmethod(fake_judge))
    return captured


def _judge_payload(**overrides):
    base = {
        "language_config": {"compile": None, "run": {"command": "echo", "seccomp_rule": None}},
        "src": "print(1)",
        "max_cpu_time": 1000,
        "max_memory": 134217728,
        "test_case_id": "fixture-1",
    }
    base.update(overrides)
    return base


def test_token_missing_returns_401(client):
    resp = client.post("/judge", json=_judge_payload())
    assert resp.status_code == 401
    body = resp.get_json()
    assert body["err"] == "TokenVerificationFailed"


def test_unknown_path_returns_404(client, auth_token):
    resp = client.post("/not-a-real-path", json={}, headers={"X-Judge-Server-Token": auth_token})
    assert resp.status_code == 404
    body = resp.get_json()
    assert body["err"] == "InvalidRequest"


def test_judge_sync_default_path_preserves_upstream_contract(client, auth_token, stub_judge):
    resp = client.post("/judge", json=_judge_payload(), headers={"X-Judge-Server-Token": auth_token})
    assert resp.status_code == 200
    body = resp.get_json()
    assert body["err"] is None
    assert isinstance(body["data"], list)
    assert len(body["data"]) == 2
    assert stub_judge["priority"] == "formal"
    # 默认同步模式不传 on_case_done
    assert stub_judge["on_case_done_present"] is False


def test_judge_priority_param_passed_through(client, auth_token, stub_judge):
    resp = client.post(
        "/judge",
        json=_judge_payload(priority="trace"),
        headers={"X-Judge-Server-Token": auth_token},
    )
    assert resp.status_code == 200
    assert stub_judge["priority"] == "trace"


def test_judge_invalid_priority_returns_400(client, auth_token, stub_judge):
    resp = client.post(
        "/judge",
        json=_judge_payload(priority="not-a-priority"),
        headers={"X-Judge-Server-Token": auth_token},
    )
    assert resp.status_code == 400
    body = resp.get_json()
    assert body["err"] == "JudgeClientError"
    assert "invalid priority" in body["data"]


def test_judge_stream_returns_sse_with_case_then_done(client, auth_token, stub_judge):
    resp = client.post(
        "/judge",
        json=_judge_payload(stream=True),
        headers={"X-Judge-Server-Token": auth_token},
    )
    assert resp.status_code == 200
    assert resp.mimetype == "text/event-stream"
    body = resp.get_data(as_text=True)
    blocks = [b for b in body.split("\n\n") if b.strip()]
    events = []
    for block in blocks:
        kv = {}
        for line in block.split("\n"):
            key, _, value = line.partition(": ")
            kv[key] = value
        events.append(kv["event"])
    assert events.count("case") == 2
    assert events[-1] == "done"
    assert stub_judge["on_case_done_present"] is True


def test_judge_callback_url_returns_202_immediately(client, auth_token, stub_judge, monkeypatch):
    """callback_url 模式必须立即 202 + accepted=true，并把 case/done 推到 callback。"""
    posted_events = []

    class _FakeResp:
        status_code = 200

    def fake_post(url, json=None, timeout=None, **kwargs):
        posted_events.append((url, json))
        return _FakeResp()

    monkeypatch.setattr(server_module.requests, "post", fake_post)

    resp = client.post(
        "/judge",
        json=_judge_payload(callback_url="http://example.local/cb", priority="debug"),
        headers={"X-Judge-Server-Token": auth_token},
    )
    assert resp.status_code == 202
    body = resp.get_json()
    assert body["err"] is None
    assert body["data"]["accepted"] is True
    assert body["data"]["priority"] == "debug"
    submission_id = body["data"]["submission_id"]
    assert isinstance(submission_id, str) and submission_id

    # 等 background thread 把 case + done 推完
    import time
    deadline = time.time() + 2
    while time.time() < deadline and len(posted_events) < 3:
        time.sleep(0.02)

    event_names = [evt["event"] for _, evt in posted_events]
    assert event_names.count("case") == 2
    assert event_names[-1] == "done"
    for url, evt in posted_events:
        assert url == "http://example.local/cb"
        assert evt["submission_id"] == submission_id
    assert stub_judge["priority"] == "debug"


def test_judge_stream_and_callback_are_mutually_exclusive(client, auth_token):
    resp = client.post(
        "/judge",
        json=_judge_payload(stream=True, callback_url="http://example.local/cb"),
        headers={"X-Judge-Server-Token": auth_token},
    )
    assert resp.status_code == 400
    body = resp.get_json()
    assert body["err"] == "JudgeClientError"
    assert "mutually exclusive" in body["data"]


def test_judge_business_error_maps_to_known_err_field(client, auth_token, monkeypatch):
    """JudgeClientError 子类必须按 ret={err, data} 协议返回，不能 500。"""
    from exception import JudgeClientError

    def boom(**kwargs):
        raise JudgeClientError("test case missing")

    monkeypatch.setattr(server_module.JudgeServer, "judge", staticmethod(boom))

    resp = client.post(
        "/judge",
        json=_judge_payload(),
        headers={"X-Judge-Server-Token": auth_token},
    )
    assert resp.status_code == 200
    body = resp.get_json()
    assert body["err"] == "JudgeClientError"
    assert body["data"] == "test case missing"


def test_judge_queue_full_returns_503(client, auth_token, monkeypatch):
    from worker_pool import QueueFull

    def boom(**kwargs):
        raise QueueFull("simulated overload")

    monkeypatch.setattr(server_module.JudgeServer, "judge", staticmethod(boom))

    resp = client.post(
        "/judge",
        json=_judge_payload(),
        headers={"X-Judge-Server-Token": auth_token},
    )
    assert resp.status_code == 503
    body = resp.get_json()
    assert body["err"] == "QueueFull"
    assert "simulated overload" in body["data"]


def test_ping_path_still_works_after_phase1_refactor(client, auth_token, monkeypatch):
    monkeypatch.setattr(
        server_module.JudgeServer,
        "ping",
        classmethod(lambda cls: {"hostname": "host-test", "action": "pong"}),
    )
    resp = client.post("/ping", json={}, headers={"X-Judge-Server-Token": auth_token})
    assert resp.status_code == 200
    body = resp.get_json()
    assert body["err"] is None
    assert body["data"]["action"] == "pong"
