"""LLM 兜底诊断（Phase 2 引入）。

当规则不命中或 ``confidence < 0.6`` 时，用 LLM 生成 ``edu_diagnosis``。

设计约束：
- AI 调用失败（超时 / 502 / 限流）返回 ``confidence=0`` 空诊断，不阻塞主判题。
- 缓存 ``(code_hash, error_signature)``，10 分钟 LRU。
- 限流：每节点每秒 ``AI_DIAGNOSIS_RATE_LIMIT`` 次（默认 5）。
"""

from __future__ import annotations

import logging
import os
from typing import Any, Dict, Optional

from diagnosis.cache import DiagnosisCache
from llm.client import LlmCallError, LlmClient

logger = logging.getLogger(__name__)

_SYSTEM_PROMPT = """\
你是 Alethicode 判题机的错误诊断助手。给定编程语言、源码片段和测试点的判题证据，\
你需要判断代码失败的根本原因，输出 JSON：
{
  "error_kind": "类型（如 RUNTIME_ERROR / WRONG_ANSWER / TIME_LIMIT_EXCEEDED / MEMORY_LIMIT_EXCEEDED / COMPILE_ERROR）",
  "error_subtype": "细分子类型（如 IndexError / segfault / 输出格式错误），可为 null",
  "line_hint": "最可能出错的行号（整数），无法判断时为 null",
  "root_cause_guess": "用中文简短描述根因（≤100字）",
  "evidence_excerpt": "从输入材料中摘出的关键证据片段（≤200字）"
}
只输出 JSON，不要任何其他文字。\
"""

_USER_TEMPLATE = """\
【语言】{language}
【源码片段（前 60 行）】
```
{src_excerpt}
```
【测试点输入】
{input_excerpt}
【期望输出】
{expected_excerpt}
【实际输出】
{actual_excerpt}
【signal】{signal}
【exit_code】{exit_code}
【error 输出】
{error_excerpt}\
"""


class AiFallbackDiagnosis:
    def __init__(self):
        endpoint = os.environ.get("AI_DIAGNOSIS_ENDPOINT", "")
        model = os.environ.get("AI_DIAGNOSIS_MODEL", "")
        rate_limit = float(os.environ.get("AI_DIAGNOSIS_RATE_LIMIT", "5"))
        cache_ttl = float(os.environ.get("AI_DIAGNOSIS_CACHE_TTL", "600"))

        self._client = LlmClient(
            endpoint=endpoint,
            model=model,
            api_key_env="AI_DIAGNOSIS_API_KEY",
            rate_limit_per_sec=rate_limit,
            timeout_seconds=15.0,
            module_name="diagnosis",
        )
        self._cache = DiagnosisCache(max_size=2048, ttl_seconds=cache_ttl)
        self._enabled = os.environ.get("ENABLE_AI_DIAGNOSIS", "true").lower() in ("true", "1", "yes")

    @property
    def enabled(self) -> bool:
        return self._enabled and self._client.enabled

    def diagnose(
        self,
        language: str,
        src: str,
        failed_case_input: str,
        expected: str,
        actual: str,
        signal: int,
        exit_code: int,
        error: str,
    ) -> Optional[Dict[str, Any]]:
        """尝试 AI 诊断。失败时返回 None（调用方降级为空诊断）。"""
        if not self.enabled:
            return None

        code_hash = DiagnosisCache.hash_code(src)
        error_sig = f"{language}:{signal}:{exit_code}:{(error or '')[:100]}"
        cache_key = DiagnosisCache.make_key(code_hash, error_sig)

        cached = self._cache.get(cache_key)
        if cached is not None:
            return cached

        src_excerpt = "\n".join(src.splitlines()[:60])
        user_prompt = _USER_TEMPLATE.format(
            language=language,
            src_excerpt=src_excerpt[:3000],
            input_excerpt=(failed_case_input or "")[:500],
            expected_excerpt=(expected or "")[:500],
            actual_excerpt=(actual or "")[:500],
            signal=signal,
            exit_code=exit_code,
            error_excerpt=(error or "")[:1000],
        )

        try:
            raw = self._client.call_json(_SYSTEM_PROMPT, user_prompt)
        except LlmCallError as exc:
            logger.warning("AI diagnosis fallback failed: %s", exc)
            return None

        result = _normalize(raw)
        self._cache.put(cache_key, result)
        return result

    def stats(self) -> Dict[str, Any]:
        return {
            "llm": self._client.stats(),
            "cache": self._cache.stats(),
            "enabled": self.enabled,
        }


def _normalize(raw: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "error_kind": str(raw.get("error_kind", "UNKNOWN")),
        "error_subtype": raw.get("error_subtype"),
        "line_hint": raw.get("line_hint"),
        "root_cause_hint": str(raw.get("root_cause_guess", ""))[:200],
        "evidence_excerpt": str(raw.get("evidence_excerpt", ""))[:300],
        "confidence": 0.7,
        "source": "ai",
    }
