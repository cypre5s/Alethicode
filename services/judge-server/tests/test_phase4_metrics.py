"""Phase 4 完整自测：/metrics Prometheus exporter。

覆盖面：
- MetricsCollector：record_judge_result / snapshot
- /metrics 路由：返回 text/plain Prometheus 格式 / 包含 worker pool 指标 / 包含 result counter
"""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "judge_server"))

flask = pytest.importorskip("flask")

import server as server_module
from metrics.exporter import MetricsCollector, get_metrics_collector


@pytest.fixture
def client():
    server_module.app.config["TESTING"] = True
    return server_module.app.test_client()


@pytest.fixture
def auth_token():
    return hashlib.sha256(b"alethicode-host-test-token").hexdigest()


class TestMetricsCollector:
    def test_record_and_snapshot(self):
        mc = MetricsCollector()
        mc.record_judge_result(0, "Python3", 0.5)
        mc.record_judge_result(-1, "Python3", 0.3)
        mc.record_judge_result(0, "C", 0.1)
        snap = mc.snapshot()
        assert snap["result_counters"]["0"] == 2
        assert snap["result_counters"]["-1"] == 1
        assert snap["language_counters"]["Python3"] == 2
        assert snap["language_counters"]["C"] == 1
        assert snap["judge_count"] == 3

    def test_empty_snapshot(self):
        mc = MetricsCollector()
        snap = mc.snapshot()
        assert snap["judge_count"] == 0
        assert snap["uptime_seconds"] >= 0

    def test_get_metrics_collector_singleton(self):
        c1 = get_metrics_collector()
        c2 = get_metrics_collector()
        assert c1 is c2


class TestMetricsRoute:
    def test_metrics_endpoint_returns_prometheus_text(self, client):
        resp = client.get("/metrics")
        assert resp.status_code == 200
        assert "text/plain" in resp.content_type
        body = resp.get_data(as_text=True)
        assert "judge_node" in body

    def test_metrics_contains_worker_pool_gauges(self, client):
        resp = client.get("/metrics")
        body = resp.get_data(as_text=True)
        assert "judge_node_max_workers" in body
        assert "judge_node_available_slots" in body

    def test_metrics_contains_uptime(self, client):
        resp = client.get("/metrics")
        body = resp.get_data(as_text=True)
        assert "judge_node_uptime_seconds" in body

    def test_metrics_no_auth_required(self, client):
        resp = client.get("/metrics")
        assert resp.status_code == 200

    def test_metrics_after_recording_results(self, client):
        mc = get_metrics_collector()
        mc.record_judge_result(0, "Python3", 0.5)
        mc.record_judge_result(4, "Python3", 0.2)
        resp = client.get("/metrics")
        body = resp.get_data(as_text=True)
        assert 'result="0"' in body
        assert 'result="4"' in body
        assert 'language="Python3"' in body
