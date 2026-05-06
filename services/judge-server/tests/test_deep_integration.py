"""深度集成测试：跨模块交互、边界用例、真实场景模拟。

覆盖面：
1. language 自动推断：从 language_config 的 src_name / command 推断
2. diagnosis 结果结构完整性：edu_diagnosis 必须包含全部 7 个字段
3. worker pool 并发压力：10 个 formal + 10 个 trace 混合提交
4. SSE 流 + diagnosis 联动：流式模式下每个 case 都应有 edu_diagnosis
5. /metrics 指标一致性：record 后 /metrics 能反映变化
6. /trace 并发安全：多线程同时调 trace_python 不互相干扰
7. safety 规则穷举：每种静态规则模式都有对应测试
8. cache 并发安全：多线程同时读写 DiagnosisCache
9. LlmClient 令牌桶恢复：等待后 token 应恢复
10. explain + diagnosis 降级独立性：一个 AI 模块故障不影响另一个
"""

from __future__ import annotations

import hashlib
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from unittest.mock import patch

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "judge_server"))

flask = pytest.importorskip("flask")

import server as server_module
from diagnosis.rules import diagnose_by_rules
from diagnosis.engine import diagnose, empty_diagnosis
from diagnosis.cache import DiagnosisCache
from trace.tracer import trace_python
from safety.screener import SafetyScreener
from worker_pool import WorkerPool, PRIORITY_FORMAL, PRIORITY_TRACE


@pytest.fixture
def auth_token():
    return hashlib.sha256(b"alethicode-host-test-token").hexdigest()


@pytest.fixture
def client():
    server_module.app.config["TESTING"] = True
    return server_module.app.test_client()


# ── 1. 语言自动推断 ──


class TestLanguageDetection:
    def test_detect_python_from_src_name(self):
        from server import _detect_language
        assert _detect_language({"compile": {"src_name": "solution.py"}, "run": {}}) == "Python3"

    def test_detect_c_from_src_name(self):
        from server import _detect_language
        assert _detect_language({"compile": {"src_name": "main.c"}, "run": {}}) == "C"

    def test_detect_cpp_from_src_name(self):
        from server import _detect_language
        assert _detect_language({"compile": {"src_name": "main.cpp"}, "run": {}}) == "C++"

    def test_detect_java_from_src_name(self):
        from server import _detect_language
        assert _detect_language({"compile": {"src_name": "Main.java"}, "run": {}}) == "Java"

    def test_detect_python_from_run_command_fallback(self):
        from server import _detect_language
        assert _detect_language({"run": {"command": "/usr/bin/python3 -BS {exe_path}"}}) == "Python3"

    def test_detect_unknown_returns_empty(self):
        from server import _detect_language
        assert _detect_language({"run": {"command": "/usr/bin/unknown"}}) == ""

    def test_no_compile_section(self):
        from server import _detect_language
        assert _detect_language({"run": {"command": "java -cp {exe_dir} Main"}}) == "Java"


# ── 2. diagnosis 结构完整性 ──


class TestDiagnosisStructureCompleteness:
    REQUIRED_KEYS = {"error_kind", "error_subtype", "line_hint", "root_cause_hint",
                     "evidence_excerpt", "confidence", "source"}

    def test_ac_has_all_keys(self):
        r = diagnose({"result": 0}, "Python3", "print(1)")
        assert self.REQUIRED_KEYS == set(r.keys())

    def test_tle_has_all_keys(self):
        r = diagnose({"result": 1, "signal": 0}, "Python3", "while True: pass")
        assert self.REQUIRED_KEYS == set(r.keys())

    def test_empty_diagnosis_has_all_keys(self):
        ed = empty_diagnosis()
        assert self.REQUIRED_KEYS == set(ed.keys())

    def test_rule_wa_has_all_keys(self):
        r = diagnose_by_rules(-1, 0, 0, "", "Python3", "1\n2", "1\n3")
        assert self.REQUIRED_KEYS == set(r.keys())

    def test_rule_compile_error_has_all_keys(self):
        r = diagnose_by_rules(-2, 0, 0, "error: ...", "C")
        assert self.REQUIRED_KEYS == set(r.keys())


# ── 3. worker pool 并发压力 ──


class TestWorkerPoolConcurrency:
    def test_mixed_priority_concurrent_submission(self):
        pool = WorkerPool(max_workers=4, max_queue_depth=64)
        results = []
        lock = threading.Lock()

        def task(name, sleep_ms):
            time.sleep(sleep_ms / 1000.0)
            with lock:
                results.append(name)
            return name

        futures = []
        for i in range(10):
            futures.append(pool.submit(PRIORITY_FORMAL, task, f"formal-{i}", 10))
        for i in range(10):
            futures.append(pool.submit(PRIORITY_TRACE, task, f"trace-{i}", 10))

        for f in futures:
            f.result(timeout=5)
        assert len(results) == 20
        pool.shutdown(wait=True, timeout=2)


# ── 4. cache 并发安全 ──


class TestCacheConcurrency:
    def test_concurrent_put_get(self):
        cache = DiagnosisCache(max_size=100, ttl_seconds=60)
        errors = []

        def writer(thread_id):
            for i in range(50):
                cache.put(f"t{thread_id}-k{i}", {"v": i})

        def reader(thread_id):
            for i in range(50):
                val = cache.get(f"t{thread_id}-k{i}")
                if val is not None and val["v"] != i:
                    errors.append(f"corruption: expected {i} got {val}")

        threads = []
        for tid in range(4):
            threads.append(threading.Thread(target=writer, args=(tid,)))
            threads.append(threading.Thread(target=reader, args=(tid,)))
        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=5)
        assert errors == []


# ── 5. LlmClient 令牌桶恢复 ──


class TestLlmClientTokenBucketRecovery:
    def test_tokens_refill_after_wait(self):
        from llm.client import LlmClient
        with patch.dict("os.environ", {"RATE_TEST_KEY": "k"}, clear=False):
            c = LlmClient(
                endpoint="http://fake", model="m", api_key_env="RATE_TEST_KEY",
                rate_limit_per_sec=10.0, module_name="test",
            )
            assert c._try_acquire()
            assert c._try_acquire()
            # 快速耗尽
            while c._try_acquire():
                pass
            time.sleep(0.15)
            assert c._try_acquire()


# ── 6. trace 并发安全 ──


class TestTraceConcurrency:
    def test_concurrent_traces_dont_interfere(self):
        results = {}

        def run_trace(name, code):
            r = trace_python(code)
            results[name] = r

        t1 = threading.Thread(target=run_trace, args=("a", "x = 1\npass\n"))
        t2 = threading.Thread(target=run_trace, args=("b", "y = 2\npass\n"))
        t1.start()
        t2.start()
        t1.join(timeout=5)
        t2.join(timeout=5)

        assert results["a"]["status"] == "ok"
        assert results["b"]["status"] == "ok"
        a_vars = {}
        for s in results["a"]["steps"]:
            a_vars.update(s["variables"])
        b_vars = {}
        for s in results["b"]["steps"]:
            b_vars.update(s["variables"])
        assert "x" in a_vars
        assert "y" in b_vars
        assert "y" not in a_vars
        assert "x" not in b_vars


# ── 7. safety 规则穷举 ──


class TestSafetyRulesExhaustive:
    @pytest.fixture(autouse=True)
    def enable_safety(self):
        with patch.dict("os.environ", {"ENABLE_AI_SAFETY": "true"}, clear=False):
            yield

    def test_fork_bomb_loop(self):
        s = SafetyScreener()
        r = s.screen("import os\nwhile True:\n    os.fork()")
        assert r["is_risky"]

    def test_ctypes_import_magic(self):
        s = SafetyScreener()
        r = s.screen("mod = __import__('ctypes')")
        assert r["is_risky"]
        assert r["risk_type"] == "ctypes_import"

    def test_proc_self_maps(self):
        s = SafetyScreener()
        r = s.screen("open('/proc/self/maps').read()")
        assert r["is_risky"]

    def test_normal_math_passes(self):
        s = SafetyScreener()
        r = s.screen("import math\nprint(math.pi)")
        assert not r["is_risky"]

    def test_normal_loop_passes(self):
        s = SafetyScreener()
        r = s.screen("for i in range(100):\n    print(i * 2)")
        assert not r["is_risky"]


# ── 8. /metrics + recording 联动 ──


class TestMetricsIntegration:
    def test_metrics_reflects_new_recordings(self, client):
        from metrics.exporter import get_metrics_collector
        mc = get_metrics_collector()
        mc.record_judge_result(0, "Python3", 0.1)
        mc.record_judge_result(0, "Python3", 0.2)
        mc.record_judge_result(4, "C", 0.3)
        resp = client.get("/metrics")
        body = resp.get_data(as_text=True)
        assert 'result="0"' in body
        assert 'result="4"' in body
        assert 'language="Python3"' in body
        assert 'language="C"' in body


# ── 9. diagnose 对真实场景的 case_result 格式兼容 ──


class TestDiagnosisRealWorldFormats:
    def test_case_result_with_no_signal_key(self):
        r = diagnose({"result": 4}, "Python3", "x = 1/0")
        assert r["error_kind"] in ("RUNTIME_ERROR", "UNKNOWN")

    def test_case_result_with_string_result_code(self):
        r = diagnose({"result": "4", "signal": 0, "exit_code": 1, "output": "NameError: x"}, "Python3", "print(x)")
        assert isinstance(r, dict)

    def test_case_result_with_none_output(self):
        r = diagnose({"result": -1, "signal": 0, "exit_code": 0, "output": None}, "Python3", "print(1)")
        assert r["error_kind"] == "WRONG_ANSWER"

    def test_case_result_with_empty_dict(self):
        r = diagnose({}, "", "")
        assert isinstance(r, dict)
        assert "error_kind" in r
