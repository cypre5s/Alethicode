"""POST /explain 端点实现（Phase 3 引入）。

独立于 ``/judge`` 主链路，业务端按需调用。输入一个失败测试点的证据，
输出 ``{summary, root_cause, next_step_hint, references}``。

设计约束：
- 独立 LLM 配置（``AI_EXPLAIN_*``），与 diagnosis 隔离。
- 缓存 ``(code_hash, input_hash)`` → result，15 分钟 LRU。
- 限流：每节点每秒 ``AI_EXPLAIN_RATE_LIMIT`` 次（默认 2）。
- 配置 ``ENABLE_AI_EXPLAIN=true/false``（默认 true）。
"""

from __future__ import annotations

import hashlib
import json
import logging
import os
from typing import Any, Dict, Optional

from diagnosis.cache import DiagnosisCache
from llm.client import LlmCallError, LlmClient

logger = logging.getLogger(__name__)

_SYSTEM_PROMPT = """\
你是 Alethicode 的编程学习助手，面向 Python 初学者。给定一个失败提交的证据，\
你需要用简洁易懂的中文解释：
1. **summary**：一句话总结失败原因（≤50字）
2. **root_cause**：详细根因分析（100-200字），用初学者能理解的语言
3. **next_step_hint**：给学生的下一步行动建议（≤100字），不要直接给出答案代码
4. **references**：如果有可参考的知识点或概念名称，列出 1-3 个（可为空列表）

输出 JSON：
{
  "summary": "...",
  "root_cause": "...",
  "next_step_hint": "...",
  "references": ["..."]
}
只输出 JSON，不要任何其他文字。\
"""

_USER_TEMPLATE = """\
【语言】{language}
【源码】
```
{src}
```
【失败测试点输入】
{input}
【期望输出】
{expected}
【实际输出】
{actual}
【signal】{signal}
【exit_code】{exit_code}
【错误信息】
{error}
【edu_diagnosis 提示】
{diagnosis_hint}\
"""


class ExplainService:
    def __init__(self):
        endpoint = os.environ.get("AI_EXPLAIN_ENDPOINT", "")
        model = os.environ.get("AI_EXPLAIN_MODEL", "")
        rate_limit = float(os.environ.get("AI_EXPLAIN_RATE_LIMIT", "2"))
        cache_ttl = float(os.environ.get("AI_EXPLAIN_CACHE_TTL", "900"))

        self._client = LlmClient(
            endpoint=endpoint,
            model=model,
            api_key_env="AI_EXPLAIN_API_KEY",
            rate_limit_per_sec=rate_limit,
            timeout_seconds=20.0,
            module_name="explain",
        )
        self._cache = DiagnosisCache(max_size=1024, ttl_seconds=cache_ttl)
        self._enabled = os.environ.get("ENABLE_AI_EXPLAIN", "true").lower() in ("true", "1", "yes")

    @property
    def enabled(self) -> bool:
        return self._enabled and self._client.enabled

    def explain(
        self,
        language: str,
        src: str,
        failed_case_input: str,
        expected: str,
        actual: str,
        signal: int,
        exit_code: int,
        error: str,
        edu_diagnosis_hint: str = "",
    ) -> Dict[str, Any]:
        if not self.enabled:
            return _unavailable("explain service not configured")

        code_hash = hashlib.sha256(src.encode("utf-8")).hexdigest()[:16]
        input_hash = hashlib.sha256((failed_case_input or "").encode("utf-8")).hexdigest()[:16]
        cache_key = DiagnosisCache.make_key(code_hash, input_hash)

        cached = self._cache.get(cache_key)
        if cached is not None:
            return cached

        user_prompt = _USER_TEMPLATE.format(
            language=language or "",
            src=(src or "")[:5000],
            input=(failed_case_input or "")[:500],
            expected=(expected or "")[:500],
            actual=(actual or "")[:500],
            signal=signal,
            exit_code=exit_code,
            error=(error or "")[:1000],
            diagnosis_hint=(edu_diagnosis_hint or "")[:300],
        )

        try:
            raw = self._client.call_json(_SYSTEM_PROMPT, user_prompt)
        except LlmCallError as exc:
            logger.warning("explain LLM call failed: %s", exc)
            return _unavailable(str(exc))

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
        "summary": str(raw.get("summary", ""))[:200],
        "root_cause": str(raw.get("root_cause", ""))[:500],
        "next_step_hint": str(raw.get("next_step_hint", ""))[:300],
        "references": list(raw.get("references", []))[:5],
        "status": "ok",
    }


def _unavailable(reason: str) -> Dict[str, Any]:
    return {
        "summary": "",
        "root_cause": "",
        "next_step_hint": "",
        "references": [],
        "status": "unavailable",
        "reason": reason,
    }


_GLOBAL_EXPLAIN: Optional[ExplainService] = None


def build_explain_handler(app):
    """在 Flask app 上注册 POST /explain 路由。"""
    from flask import request, Response
    from utils import token as server_token

    global _GLOBAL_EXPLAIN
    if _GLOBAL_EXPLAIN is None:
        _GLOBAL_EXPLAIN = ExplainService()

    @app.route("/explain", methods=["POST"])
    def explain_route():
        _token = request.headers.get("X-Judge-Server-Token")
        if _token != server_token:
            return Response(
                json.dumps({"err": "TokenVerificationFailed", "data": "invalid token"}),
                mimetype="application/json",
                status=401,
            )
        try:
            data = request.json or {}
        except Exception:
            data = {}

        result = _GLOBAL_EXPLAIN.explain(
            language=data.get("language", ""),
            src=data.get("src", ""),
            failed_case_input=data.get("failed_case_input", ""),
            expected=data.get("expected", ""),
            actual=data.get("actual", ""),
            signal=int(data.get("signal", 0)),
            exit_code=int(data.get("exit_code", 0)),
            error=data.get("error", ""),
            edu_diagnosis_hint=data.get("edu_diagnosis_hint", ""),
        )
        return Response(
            json.dumps({"err": None, "data": result}),
            mimetype="application/json",
        )
