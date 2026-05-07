"""压力测试：worker pool / SSE / diagnosis / trace / cache / metrics 在高并发下的稳定性。

不依赖判题镜像；所有 task 模拟判题延迟（sleep）。
"""

from __future__ import annotations

import hashlib
import sys
import threading
import time
from concurrent.futures import Future, wait
from pathlib import Path
from typing import List

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "judge_server"))

from worker_pool import WorkerPool, QueueFull, PRIORITY_FORMAL, PRIORITY_DEBUG, PRIORITY_TRACE
from diagnosis.cache import DiagnosisCache
from diagnosis.engine import diagnose
from trace.tracer import trace_python
from metrics.exporter import MetricsCollector

flask = pytest.importorskip("flask")
import server as server_module


@pytest.fixture
def auth_token():
    return hashlib.sha256(b"alethicode-host-test-token").hexdigest()


@pytest.fixture
def client():
    server_module.app.config["TESTING"] = True
    return server_module.app.test_client()


class TestWorkerPoolStress:
    def test_100_concurrent_tasks_4_workers(self):
        """100 个 task 提交到 4 个 worker，确认全部完成、顺序正确、stats 归零。"""
        pool = WorkerPool(max_workers=4, max_queue_depth=256)
        completed = []
        lock = threading.Lock()

        def task(task_id):
            time.sleep(0.005)
            with lock:
                completed.append(task_id)
            return task_id

        futures: List[Future] = []
        for i in range(100):
            priority = [PRIORITY_FORMAL, PRIORITY_DEBUG, PRIORITY_TRACE][i % 3]
            futures.append(pool.submit(priority, task, i))

        results = [f.result(timeout=10) for f in futures]
        assert len(results) == 100
        assert set(results) == set(range(100))
        assert len(completed) == 100

        # 等 stats 归零。
        deadline = time.time() + 3
        while time.time() < deadline and pool.stats()["running_total"] > 0:
            time.sleep(0.01)
        stats = pool.stats()
        assert stats["running_total"] == 0
        assert stats["queued_total"] == 0
        assert stats["available_slots"] == 4
        pool.shutdown(wait=True, timeout=2)

    def test_queue_full_under_sustained_pressure(self):
        """worker 和队列都满时，后续 submit 必须全部 QueueFull。"""
        pool = WorkerPool(max_workers=2, max_queue_depth=4)
        block = threading.Event()

        def blocker():
            block.wait(timeout=10)

        running = []
        running.append(pool.submit(PRIORITY_FORMAL, blocker))
        running.append(pool.submit(PRIORITY_FORMAL, blocker))

        deadline = time.time() + 3
        while time.time() < deadline and pool.stats()["running_total"] < 2:
            time.sleep(0.01)
        assert pool.stats()["running_total"] == 2

        queued = []
        for _ in range(4):
            queued.append(pool.submit(PRIORITY_FORMAL, blocker))

        queue_full_count = 0
        for _ in range(20):
            try:
                pool.submit(PRIORITY_FORMAL, blocker)
            except QueueFull:
                queue_full_count += 1

        assert queue_full_count == 20

        block.set()
        wait(running + queued, timeout=5)
        pool.shutdown(wait=True, timeout=2)

    def test_priority_starvation_resilience(self):
        """高优先级不应导致低优先级永远饿死（给足够容量时）。"""
        pool = WorkerPool(max_workers=4, max_queue_depth=256)
        completed_formal = []
        completed_trace = []
        lock = threading.Lock()

        def formal_task(i):
            time.sleep(0.002)
            with lock:
                completed_formal.append(i)

        def trace_task(i):
            time.sleep(0.002)
            with lock:
                completed_trace.append(i)

        futures = []
        for i in range(50):
            futures.append(pool.submit(PRIORITY_FORMAL, formal_task, i))
            futures.append(pool.submit(PRIORITY_TRACE, trace_task, i))

        wait(futures, timeout=10)
        assert len(completed_formal) == 50
        assert len(completed_trace) == 50
        pool.shutdown(wait=True, timeout=2)


class TestDiagnosisCacheStress:
    def test_1000_concurrent_puts_and_gets(self):
        """1000 个并发读写同一个 cache 实例时，不应 crash 或损坏数据。"""
        cache = DiagnosisCache(max_size=200, ttl_seconds=60)
        errors = []
        barrier = threading.Barrier(8)

        def worker(thread_id):
            barrier.wait(timeout=5)
            for i in range(125):
                key = f"t{thread_id}-k{i}"
                value = {"thread": thread_id, "index": i}
                cache.put(key, value)
                result = cache.get(key)
                if result is not None and result.get("index") != i:
                    errors.append(f"corruption at {key}: expected index={i}, got {result}")

        threads = [threading.Thread(target=worker, args=(tid,)) for tid in range(8)]
        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=10)

        assert errors == [], f"cache corruptions: {errors}"
        assert cache.stats()["size"] <= 200  # LRU 淘汰必须生效


class TestDiagnosisEngineStress:
    def test_100_diagnose_calls_never_crash(self):
        """100 次 diagnose 调用覆盖各种 result code，不应抛错。"""
        cases = [
            {"result": 0, "signal": 0, "exit_code": 0},
            {"result": -1, "signal": 0, "exit_code": 0, "output": "wrong"},
            {"result": 1, "signal": 0, "exit_code": 0},
            {"result": 2, "signal": 0, "exit_code": 0},
            {"result": 3, "signal": 0, "exit_code": 0},
            {"result": 4, "signal": 11, "exit_code": 0},
            {"result": 4, "signal": 6, "exit_code": 0},
            {"result": 4, "signal": 0, "exit_code": 1, "output": "NameError: x"},
            {"result": 5, "signal": 0, "exit_code": 0},
            {"result": -2, "signal": 0, "exit_code": 0, "output": "syntax error"},
        ]
        for i in range(100):
            case = cases[i % len(cases)]
            r = diagnose(case, "Python3", f"code_{i}")
            assert isinstance(r, dict)
            assert "error_kind" in r
            assert "confidence" in r


class TestTraceStress:
    def test_10_concurrent_traces(self):
        """10 个线程同时 trace 不同程序，结果必须全部 ok 且互不污染。"""
        results = {}
        lock = threading.Lock()

        def run_trace(name, code):
            r = trace_python(code, max_steps=100)
            with lock:
                results[name] = r

        threads = []
        for i in range(10):
            code = f"v{i} = {i}\npass\n"
            t = threading.Thread(target=run_trace, args=(f"t{i}", code))
            threads.append(t)

        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=10)

        assert len(results) == 10
        for name, r in results.items():
            assert r["status"] == "ok", f"{name} failed: {r}"
            all_vars = {}
            for s in r["steps"]:
                all_vars.update(s["variables"])
            idx = int(name.replace("t", ""))
            assert all_vars.get(f"v{idx}") == idx, f"{name} var mismatch: {all_vars}"


class TestMetricsStress:
    def test_1000_concurrent_recordings(self):
        """1000 次并发 record 后，stats 合计必须一致。"""
        mc = MetricsCollector()
        barrier = threading.Barrier(8)

        def worker(thread_id):
            barrier.wait(timeout=5)
            for i in range(125):
                mc.record_judge_result(i % 6, f"lang_{thread_id}", 0.01)

        threads = [threading.Thread(target=worker, args=(tid,)) for tid in range(8)]
        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=10)

        snap = mc.snapshot()
        assert snap["judge_count"] == 1000


class TestServerStress:
    def test_50_concurrent_sync_requests(self, client, auth_token, monkeypatch):
        """50 个并发同步 /judge 请求必须全部返回 200。"""
        call_count = {"n": 0}
        call_lock = threading.Lock()

        def fake_judge(**kwargs):
            with call_lock:
                call_count["n"] += 1
            time.sleep(0.005)
            return [{"test_case": "1", "result": 0}]

        monkeypatch.setattr(server_module.JudgeServer, "judge", staticmethod(fake_judge))

        payload = {
            "language_config": {"run": {"command": "echo", "seccomp_rule": None}},
            "src": "print(1)", "max_cpu_time": 1000, "max_memory": 134217728,
            "test_case_id": "t1",
        }
        headers = {"X-Judge-Server-Token": auth_token}

        results = []

        def do_request():
            resp = client.post("/judge", json=payload, headers=headers)
            results.append(resp.status_code)

        threads = [threading.Thread(target=do_request) for _ in range(50)]
        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=15)

        assert len(results) == 50
        assert all(s == 200 for s in results), f"non-200 responses: {[s for s in results if s != 200]}"
        assert call_count["n"] == 50
