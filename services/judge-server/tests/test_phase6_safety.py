"""Phase 6 完整自测：safety screener 静态规则 + AI 降级。

覆盖面：
- SafetyScreener：默认关 / 静态规则命中 fork bomb / 正常代码通过
- 敏感路径检测 / shell 逃逸检测 / ctypes 检测
- AI 不可用时降级 / 缓存命中
"""

from __future__ import annotations

import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "judge_server"))

from safety.screener import SafetyScreener


class TestSafetyScreener:
    def test_disabled_by_default(self):
        with patch.dict("os.environ", {"ENABLE_AI_SAFETY": "false"}, clear=False):
            s = SafetyScreener()
            assert not s.enabled
            r = s.screen("print('hello')")
            assert not r["is_risky"]

    def test_enabled_detects_fork_bomb_bash(self):
        with patch.dict("os.environ", {"ENABLE_AI_SAFETY": "true"}, clear=False):
            s = SafetyScreener()
            r = s.screen(":(){ :|:& };:")
            assert r["is_risky"]
            assert r["risk_type"] == "fork_bomb_bash"
            assert r["source"] == "static_rule"

    def test_enabled_detects_fork_bomb_python(self):
        with patch.dict("os.environ", {"ENABLE_AI_SAFETY": "true"}, clear=False):
            s = SafetyScreener()
            r = s.screen("import os\nos.fork()")
            assert r["is_risky"]
            assert r["risk_type"] == "fork_bomb_python"

    def test_enabled_detects_sensitive_path(self):
        with patch.dict("os.environ", {"ENABLE_AI_SAFETY": "true"}, clear=False):
            s = SafetyScreener()
            r = s.screen("open('/etc/shadow').read()")
            assert r["is_risky"]
            assert r["risk_type"] == "sensitive_path_access"

    def test_enabled_detects_shell_escape(self):
        with patch.dict("os.environ", {"ENABLE_AI_SAFETY": "true"}, clear=False):
            s = SafetyScreener()
            r = s.screen("import subprocess\nsubprocess.call('/bin/bash')")
            assert r["is_risky"]
            assert r["risk_type"] == "shell_escape"

    def test_enabled_detects_ctypes(self):
        with patch.dict("os.environ", {"ENABLE_AI_SAFETY": "true"}, clear=False):
            s = SafetyScreener()
            r = s.screen("import ctypes\nctypes.CDLL('libc.so.6')")
            assert r["is_risky"]
            assert r["risk_type"] == "native_lib_load"

    def test_normal_code_passes(self):
        with patch.dict("os.environ", {"ENABLE_AI_SAFETY": "true"}, clear=False):
            s = SafetyScreener()
            r = s.screen("for i in range(10):\n    print(i)")
            assert not r["is_risky"]

    def test_cache_hit(self):
        with patch.dict("os.environ", {"ENABLE_AI_SAFETY": "true"}, clear=False):
            s = SafetyScreener()
            r1 = s.screen("print('safe')")
            r2 = s.screen("print('safe')")
            assert r1 == r2

    def test_ai_disabled_falls_through_to_safe(self):
        with patch.dict("os.environ", {
            "ENABLE_AI_SAFETY": "true",
            "AI_SAFETY_ENDPOINT": "",
        }, clear=False):
            s = SafetyScreener()
            r = s.screen("import math\nprint(math.sqrt(4))")
            assert not r["is_risky"]
            assert r["source"] == "none"
