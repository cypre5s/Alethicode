"""Phase 2 完整自测：diagnosis 规则 + AI 兜底 + 缓存 + 引擎整合。

覆盖面：
- rules.py：Python 7 种异常 / C segfault+abort+fpe / Java 3 种异常 / TLE / MLE / WA / CE / SE
- cache.py：put/get/TTL 过期 / LRU 淘汰 / make_key / hash_code
- ai_fallback.py：AI 可用时调用 / AI 不可用时返回 None / 缓存命中跳过 LLM
- engine.py：AC 路径 / 规则命中 / AI 兜底 / 全部失败降级空诊断 / 异常不阻塞
"""

from __future__ import annotations

import sys
import time
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "judge_server"))

from diagnosis.rules import diagnose_by_rules
from diagnosis.cache import DiagnosisCache
from diagnosis.engine import diagnose, empty_diagnosis


# ── rules.py ──


class TestRulesPythonExceptions:
    def test_wa_with_python_error_in_stderr_still_wa(self):
        r = diagnose_by_rules(-1, 0, 1, "NameError: name 'x' is not defined", "Python3")
        assert r is not None
        assert r["error_kind"] == "WRONG_ANSWER"

    def test_name_error_as_runtime_error(self):
        r = diagnose_by_rules(4, 0, 1, "Traceback...\nFile \"solution.py\", line 5\nNameError: name 'x' is not defined", "Python3")
        assert r is not None
        assert r["error_kind"] == "RUNTIME_ERROR"
        assert r["error_subtype"] == "NameError"
        assert r["source"] == "rule"
        assert r["confidence"] >= 0.8
        assert r["line_hint"] == 5
        assert "拼写" in r["root_cause_hint"] or "未定义" in r["root_cause_hint"]

    def test_index_error(self):
        r = diagnose_by_rules(4, 0, 1, "IndexError: list index out of range", "Python3")
        assert r["error_subtype"] == "IndexError"
        assert "越界" in r["root_cause_hint"]

    def test_type_error(self):
        r = diagnose_by_rules(4, 0, 1, "TypeError: unsupported operand type(s)", "Python3")
        assert r["error_subtype"] == "TypeError"

    def test_zero_division_error(self):
        r = diagnose_by_rules(4, 0, 1, "ZeroDivisionError: division by zero", "Python3")
        assert r["error_subtype"] == "ZeroDivisionError"
        assert "0" in r["root_cause_hint"]

    def test_recursion_error(self):
        r = diagnose_by_rules(4, 0, 1, "RecursionError: maximum recursion depth exceeded", "Python3")
        assert r["error_subtype"] == "RecursionError"
        assert "递归" in r["root_cause_hint"]

    def test_syntax_error(self):
        r = diagnose_by_rules(4, 0, 1, 'SyntaxError: invalid syntax', "Python3")
        assert r["error_subtype"] == "SyntaxError"


class TestRulesCCpp:
    def test_segfault_signal_11(self):
        r = diagnose_by_rules(4, 11, 0, "", "C")
        assert r["error_kind"] == "RUNTIME_ERROR"
        assert r["error_subtype"] == "SEGFAULT"
        assert "段错误" in r["root_cause_hint"]

    def test_abort_signal_6(self):
        r = diagnose_by_rules(4, 6, 0, "", "C++")
        assert r["error_subtype"] == "ABORT"

    def test_fpe_signal_8(self):
        r = diagnose_by_rules(4, 8, 0, "", "C")
        assert r["error_subtype"] == "FPE"
        assert "除以 0" in r["root_cause_hint"]

    def test_compile_error_with_line(self):
        err = "main.c:10:5: error: expected ';' before '}'"
        r = diagnose_by_rules(-2, 0, 0, err, "C")
        assert r["error_kind"] == "COMPILE_ERROR"
        assert r["line_hint"] == 10
        assert r["confidence"] >= 0.9


class TestRulesJava:
    def test_null_pointer(self):
        r = diagnose_by_rules(4, 0, 1, "java.lang.NullPointerException\n\tat Main.main(Main.java:15)", "Java")
        assert r["error_subtype"] == "NullPointerException"
        assert r["line_hint"] == 15
        assert "null" in r["root_cause_hint"]

    def test_array_index_out_of_bounds(self):
        r = diagnose_by_rules(4, 0, 1, "ArrayIndexOutOfBoundsException: Index 5 out of bounds", "Java")
        assert r["error_subtype"] == "ArrayIndexOutOfBoundsException"

    def test_stack_overflow(self):
        r = diagnose_by_rules(4, 0, 1, "StackOverflowError", "Java")
        assert r["error_subtype"] == "StackOverflowError"
        assert "递归" in r["root_cause_hint"]


class TestRulesGeneric:
    def test_tle_cpu(self):
        r = diagnose_by_rules(1, 0, 0, "", "Python3")
        assert r["error_kind"] == "TIME_LIMIT_EXCEEDED"
        assert r["confidence"] >= 0.8

    def test_tle_real(self):
        r = diagnose_by_rules(2, 0, 0, "", "C")
        assert r["error_kind"] == "TIME_LIMIT_EXCEEDED"

    def test_mle(self):
        r = diagnose_by_rules(3, 0, 0, "", "Python3")
        assert r["error_kind"] == "MEMORY_LIMIT_EXCEEDED"

    def test_system_error(self):
        r = diagnose_by_rules(5, 0, 0, "internal error", "C")
        assert r["error_kind"] == "SYSTEM_ERROR"
        assert r["confidence"] <= 0.6

    def test_wrong_answer_with_diff(self):
        r = diagnose_by_rules(-1, 0, 0, "", "Python3", expected_output="1\n2\n3", actual_output="1\n4\n3")
        assert r["error_kind"] == "WRONG_ANSWER"
        assert r["error_subtype"] == "OUTPUT_MISMATCH"
        assert "第 2 行" in r["root_cause_hint"]

    def test_wrong_answer_line_count_mismatch(self):
        r = diagnose_by_rules(-1, 0, 0, "", "Python3", expected_output="1\n2\n3", actual_output="1\n2")
        assert r["error_kind"] == "WRONG_ANSWER"
        assert r["error_subtype"] == "LINE_COUNT_MISMATCH"

    def test_wrong_answer_no_output_data(self):
        r = diagnose_by_rules(-1, 0, 0, "", "Python3")
        assert r["error_kind"] == "WRONG_ANSWER"
        assert r["confidence"] <= 0.7

    def test_success_returns_none(self):
        r = diagnose_by_rules(0, 0, 0, "", "Python3")
        assert r is None

    def test_compile_error_python(self):
        r = diagnose_by_rules(-2, 0, 0, 'File "solution.py", line 3\n  SyntaxError: invalid syntax', "Python3")
        assert r["error_kind"] == "COMPILE_ERROR"
        assert r["line_hint"] == 3


# ── cache.py ──


class TestDiagnosisCache:
    def test_put_and_get(self):
        cache = DiagnosisCache(max_size=10, ttl_seconds=60)
        cache.put("k1", {"error_kind": "TLE"})
        assert cache.get("k1") == {"error_kind": "TLE"}
        assert cache.stats()["hits"] == 1

    def test_miss(self):
        cache = DiagnosisCache(max_size=10, ttl_seconds=60)
        assert cache.get("nonexistent") is None
        assert cache.stats()["misses"] == 1

    def test_ttl_expiration(self):
        cache = DiagnosisCache(max_size=10, ttl_seconds=0.05)
        cache.put("k1", {"error_kind": "TLE"})
        time.sleep(0.1)
        assert cache.get("k1") is None

    def test_lru_eviction(self):
        cache = DiagnosisCache(max_size=2, ttl_seconds=60)
        cache.put("a", {"v": 1})
        cache.put("b", {"v": 2})
        cache.put("c", {"v": 3})
        assert cache.get("a") is None
        assert cache.get("b") == {"v": 2}
        assert cache.get("c") == {"v": 3}

    def test_make_key_deterministic(self):
        k1 = DiagnosisCache.make_key("abc123", "Python3:11:1:NameError")
        k2 = DiagnosisCache.make_key("abc123", "Python3:11:1:NameError")
        assert k1 == k2
        assert len(k1) == 32

    def test_hash_code_deterministic(self):
        h1 = DiagnosisCache.hash_code("print(1)")
        h2 = DiagnosisCache.hash_code("print(1)")
        assert h1 == h2
        assert len(h1) == 16

    def test_hash_code_differs_for_different_code(self):
        h1 = DiagnosisCache.hash_code("print(1)")
        h2 = DiagnosisCache.hash_code("print(2)")
        assert h1 != h2


# ── engine.py ──


class TestDiagnosisEngine:
    def test_ac_case_returns_accepted(self):
        result = diagnose({"result": 0}, "Python3", "print(1)")
        assert result["error_kind"] == "ACCEPTED"
        assert result["confidence"] == 1.0

    def test_tle_case_returns_rule_diagnosis(self):
        result = diagnose({"result": 1, "signal": 0, "exit_code": 0}, "Python3", "while True: pass")
        assert result["error_kind"] == "TIME_LIMIT_EXCEEDED"
        assert result["source"] == "rule"

    def test_python_name_error_returns_rule_diagnosis(self):
        result = diagnose(
            {"result": 4, "signal": 0, "exit_code": 1, "output": "NameError: name 'x' is not defined"},
            "Python3",
            "print(x)",
        )
        assert result["error_kind"] == "RUNTIME_ERROR"
        assert result["error_subtype"] == "NameError"

    def test_unknown_error_without_ai_returns_empty_diagnosis(self):
        result = diagnose(
            {"result": 4, "signal": 99, "exit_code": 42, "output": ""},
            "UnknownLang",
            "???",
        )
        assert result["error_kind"] in ("RUNTIME_ERROR", "UNKNOWN")
        assert result["confidence"] <= 0.6

    def test_empty_diagnosis_structure(self):
        ed = empty_diagnosis()
        required_keys = {"error_kind", "error_subtype", "line_hint", "root_cause_hint", "evidence_excerpt", "confidence", "source"}
        assert required_keys == set(ed.keys())
        assert ed["confidence"] == 0.0
        assert ed["source"] == "none"

    def test_diagnose_never_raises(self):
        """即使传入完全无效的数据，diagnose 也不 raise。"""
        result = diagnose({}, "", "")
        assert isinstance(result, dict)
        assert "error_kind" in result

    def test_diagnose_with_none_values(self):
        result = diagnose(
            {"result": -1, "signal": None, "exit_code": None, "output": None},
            None,
            None,
        )
        assert isinstance(result, dict)


# ── ai_fallback.py (mock LLM) ──


class TestAiFallback:
    def test_ai_disabled_returns_none(self):
        from diagnosis.ai_fallback import AiFallbackDiagnosis
        with patch.dict("os.environ", {"ENABLE_AI_DIAGNOSIS": "false"}):
            fb = AiFallbackDiagnosis()
            assert not fb.enabled
            result = fb.diagnose("Python3", "print(x)", "", "", "", 0, 1, "NameError: ...")
            assert result is None

    def test_ai_no_endpoint_returns_none(self):
        from diagnosis.ai_fallback import AiFallbackDiagnosis
        with patch.dict("os.environ", {
            "ENABLE_AI_DIAGNOSIS": "true",
            "AI_DIAGNOSIS_ENDPOINT": "",
            "AI_DIAGNOSIS_MODEL": "",
        }, clear=False):
            fb = AiFallbackDiagnosis()
            assert not fb.enabled

    def test_ai_success_returns_normalized_diagnosis(self):
        from diagnosis.ai_fallback import AiFallbackDiagnosis
        mock_response = {
            "error_kind": "RUNTIME_ERROR",
            "error_subtype": "NameError",
            "line_hint": 3,
            "root_cause_guess": "变量 x 未定义",
            "evidence_excerpt": "NameError: name 'x' is not defined",
        }
        with patch.dict("os.environ", {
            "ENABLE_AI_DIAGNOSIS": "true",
            "AI_DIAGNOSIS_ENDPOINT": "http://fake.local",
            "AI_DIAGNOSIS_MODEL": "test-model",
            "AI_DIAGNOSIS_API_KEY": "test-key",
        }, clear=False):
            fb = AiFallbackDiagnosis()
            fb._client.call_json = MagicMock(return_value=mock_response)
            result = fb.diagnose("Python3", "print(x)", "1", "1", "", 0, 1, "NameError: name 'x' is not defined")
            assert result is not None
            assert result["error_kind"] == "RUNTIME_ERROR"
            assert result["source"] == "ai"
            assert result["confidence"] == 0.7

    def test_ai_cache_hit_skips_llm(self):
        from diagnosis.ai_fallback import AiFallbackDiagnosis
        mock_response = {
            "error_kind": "RUNTIME_ERROR",
            "error_subtype": "IndexError",
            "line_hint": None,
            "root_cause_guess": "越界",
            "evidence_excerpt": "IndexError",
        }
        with patch.dict("os.environ", {
            "ENABLE_AI_DIAGNOSIS": "true",
            "AI_DIAGNOSIS_ENDPOINT": "http://fake.local",
            "AI_DIAGNOSIS_MODEL": "test-model",
            "AI_DIAGNOSIS_API_KEY": "test-key",
        }, clear=False):
            fb = AiFallbackDiagnosis()
            fb._client.call_json = MagicMock(return_value=mock_response)
            r1 = fb.diagnose("Python3", "a[10]", "", "", "", 0, 1, "IndexError: ...")
            r2 = fb.diagnose("Python3", "a[10]", "", "", "", 0, 1, "IndexError: ...")
            assert fb._client.call_json.call_count == 1
            assert r1 == r2

    def test_ai_llm_error_returns_none(self):
        from diagnosis.ai_fallback import AiFallbackDiagnosis
        from llm.client import LlmCallError
        with patch.dict("os.environ", {
            "ENABLE_AI_DIAGNOSIS": "true",
            "AI_DIAGNOSIS_ENDPOINT": "http://fake.local",
            "AI_DIAGNOSIS_MODEL": "test-model",
            "AI_DIAGNOSIS_API_KEY": "test-key",
        }, clear=False):
            fb = AiFallbackDiagnosis()
            fb._client.call_json = MagicMock(side_effect=LlmCallError("timeout"))
            result = fb.diagnose("Python3", "print(x)", "", "", "", 0, 1, "NameError")
            assert result is None


# ── llm/client.py ──


class TestLlmClient:
    def test_disabled_when_no_endpoint(self):
        from llm.client import LlmClient
        c = LlmClient(endpoint="", model="m", api_key_env="NONEXISTENT_KEY_XYZ")
        assert not c.enabled

    def test_disabled_when_no_api_key(self):
        from llm.client import LlmClient
        c = LlmClient(endpoint="http://x", model="m", api_key_env="NONEXISTENT_KEY_XYZ")
        assert not c.enabled

    def test_rate_limiter_blocks_after_burst(self):
        from llm.client import LlmClient, LlmCallError
        with patch.dict("os.environ", {"TEST_RATE_KEY": "k"}, clear=False):
            c = LlmClient(
                endpoint="http://fake", model="m", api_key_env="TEST_RATE_KEY",
                rate_limit_per_sec=1.0, module_name="test",
            )
            assert c._try_acquire()
            assert not c._try_acquire()

    def test_stats_structure(self):
        from llm.client import LlmClient
        c = LlmClient(endpoint="", model="", api_key_env="X", module_name="test")
        s = c.stats()
        assert s["module"] == "test"
        assert s["total_calls"] == 0
