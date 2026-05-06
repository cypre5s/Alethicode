"""编译前 LLM 静态安全分析（Phase 6 引入，默认关）。

检测已知恶意模式：fork bomb / 沙箱逃逸 / 敏感路径访问。
高风险 → 跳过 Judger 执行，返回 ``risk_blocked``。

设计约束：
- 默认 ``ENABLE_AI_SAFETY=false``，避免误杀正常题目。
- 独立 LLM 客户端。
- 缓存 ``code_hash`` → verdict，1 小时。
"""

from __future__ import annotations

import hashlib
import logging
import os
import re
from typing import Any, Dict, Optional

from diagnosis.cache import DiagnosisCache
from llm.client import LlmCallError, LlmClient

logger = logging.getLogger(__name__)

_STATIC_PATTERNS = [
    (re.compile(r":\(\)\s*\{\s*:\s*\|\s*:\s*&\s*\}\s*;\s*:", re.DOTALL), "fork_bomb_bash"),
    (re.compile(r"os\.fork\s*\("), "fork_bomb_python"),
    (re.compile(r"while\s+True\s*:\s*os\.fork\s*\("), "fork_bomb_loop"),
    (re.compile(r"/etc/shadow|/etc/passwd|/proc/self/maps", re.IGNORECASE), "sensitive_path_access"),
    (re.compile(r"subprocess\.(?:call|run|Popen)\s*\(.*(?:sh|bash|/bin/)", re.DOTALL), "shell_escape"),
    (re.compile(r"ctypes\.CDLL|ctypes\.cdll", re.IGNORECASE), "native_lib_load"),
    (re.compile(r"__import__\s*\(\s*['\"]ctypes['\"]\s*\)"), "ctypes_import"),
]

_SYSTEM_PROMPT = """\
你是代码安全审计器。分析给定代码是否包含以下恶意行为：
1. fork bomb（进程炸弹）
2. 沙箱逃逸（读写 /etc/shadow、/proc/self/maps 等）
3. 网络外联（socket、requests）
4. 文件系统破坏（rm -rf、rmtree 非工作目录）
5. 资源耗尽（无限循环申请内存）

输出 JSON：
{"is_risky": true/false, "risk_type": "类型或null", "evidence": "摘录或空", "confidence": 0.0-1.0}
只输出 JSON。\
"""


class SafetyScreener:
    def __init__(self):
        self._enabled = os.environ.get("ENABLE_AI_SAFETY", "false").lower() in ("true", "1", "yes")
        endpoint = os.environ.get("AI_SAFETY_ENDPOINT", "")
        model = os.environ.get("AI_SAFETY_MODEL", "")
        cache_ttl = float(os.environ.get("AI_SAFETY_CACHE_TTL", "3600"))

        self._client = LlmClient(
            endpoint=endpoint,
            model=model,
            api_key_env="AI_SAFETY_API_KEY",
            rate_limit_per_sec=5.0,
            timeout_seconds=10.0,
            module_name="safety",
        )
        self._cache = DiagnosisCache(max_size=2048, ttl_seconds=cache_ttl)

    @property
    def enabled(self) -> bool:
        return self._enabled

    def screen(self, src: str) -> Dict[str, Any]:
        """筛查代码安全性。返回 ``{is_risky, risk_type, evidence, source}``。"""
        if not self._enabled:
            return _safe()

        code_hash = hashlib.sha256(src.encode("utf-8")).hexdigest()[:16]
        cached = self._cache.get(code_hash)
        if cached is not None:
            return cached

        for pattern, risk_type in _STATIC_PATTERNS:
            m = pattern.search(src)
            if m:
                result = {
                    "is_risky": True,
                    "risk_type": risk_type,
                    "evidence": m.group(0)[:200],
                    "confidence": 0.95,
                    "source": "static_rule",
                }
                self._cache.put(code_hash, result)
                return result

        if self._client.enabled:
            try:
                raw = self._client.call_json(_SYSTEM_PROMPT, src[:5000])
                result = _normalize_ai(raw)
                self._cache.put(code_hash, result)
                return result
            except LlmCallError as exc:
                logger.warning("safety AI call failed: %s", exc)

        result = _safe()
        self._cache.put(code_hash, result)
        return result


def _safe() -> Dict[str, Any]:
    return {"is_risky": False, "risk_type": None, "evidence": "", "confidence": 0.0, "source": "none"}


def _normalize_ai(raw: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "is_risky": bool(raw.get("is_risky", False)),
        "risk_type": raw.get("risk_type"),
        "evidence": str(raw.get("evidence", ""))[:300],
        "confidence": float(raw.get("confidence", 0.5)),
        "source": "ai",
    }
