"""OpenAI 兼容 LLM 客户端（Phase 2 引入）。

设计约束：
- 判题机自包含：不依赖 Java backend 的 ``AiModelGateway``，不依赖
  ``services/tutor-graph`` 的 ``langchain``。
- 三模块独立：diagnosis / explain / safety 各自实例化，互不共享 rate limiter。
- 故障降级：超时 / 502 / 限流时 raise ``LlmCallError``，调用方自行决定降级
  策略（Phase 2 的 diagnosis → ``confidence=0`` 空诊断，不阻塞主判题）。
"""

from __future__ import annotations

import json
import logging
import os
import threading
import time
from typing import Any, Dict, Optional

import requests

logger = logging.getLogger(__name__)


class LlmCallError(Exception):
    """LLM 调用失败（网络 / 限流 / 格式错误），调用方应做降级处理。"""


class LlmClient:
    """OpenAI chat/completions 兼容客户端 + 令牌桶限流。"""

    def __init__(
        self,
        endpoint: str,
        model: str,
        api_key_env: str,
        rate_limit_per_sec: float = 5.0,
        timeout_seconds: float = 15.0,
        module_name: str = "llm",
    ):
        self._endpoint = endpoint.rstrip("/") if endpoint else ""
        self._model = model
        self._api_key = os.environ.get(api_key_env, "")
        self._rate_limit_per_sec = rate_limit_per_sec
        self._timeout = timeout_seconds
        self._module_name = module_name

        self._tokens = rate_limit_per_sec
        self._last_refill = time.monotonic()
        self._lock = threading.Lock()

        self._total_calls = 0
        self._total_errors = 0
        self._total_rate_limited = 0

    @property
    def enabled(self) -> bool:
        return bool(self._endpoint) and bool(self._api_key) and bool(self._model)

    def call_json(
        self,
        system_prompt: str,
        user_prompt: str,
    ) -> Dict[str, Any]:
        """调用 LLM 并解析 JSON 响应。

        :raises LlmCallError: 任何失败（网络 / 限流 / 格式）。
        """
        if not self.enabled:
            raise LlmCallError(f"[{self._module_name}] LLM not configured")
        if not self._try_acquire():
            self._total_rate_limited += 1
            raise LlmCallError(f"[{self._module_name}] rate limited")

        self._total_calls += 1
        url = f"{self._endpoint}/chat/completions"
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self._api_key}",
        }
        payload = {
            "model": self._model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "temperature": 0.2,
            "response_format": {"type": "json_object"},
        }
        try:
            resp = requests.post(url, json=payload, headers=headers, timeout=self._timeout)
            resp.raise_for_status()
            body = resp.json()
            content = body["choices"][0]["message"]["content"]
            return json.loads(content)
        except requests.exceptions.Timeout:
            self._total_errors += 1
            raise LlmCallError(f"[{self._module_name}] timeout after {self._timeout}s")
        except requests.exceptions.RequestException as exc:
            self._total_errors += 1
            raise LlmCallError(f"[{self._module_name}] request failed: {exc}") from exc
        except (KeyError, IndexError, json.JSONDecodeError) as exc:
            self._total_errors += 1
            raise LlmCallError(f"[{self._module_name}] bad response: {exc}") from exc

    def stats(self) -> Dict[str, Any]:
        return {
            "module": self._module_name,
            "enabled": self.enabled,
            "total_calls": self._total_calls,
            "total_errors": self._total_errors,
            "total_rate_limited": self._total_rate_limited,
        }

    def _try_acquire(self) -> bool:
        with self._lock:
            now = time.monotonic()
            elapsed = now - self._last_refill
            self._tokens = min(
                self._rate_limit_per_sec,
                self._tokens + elapsed * self._rate_limit_per_sec,
            )
            self._last_refill = now
            if self._tokens >= 1.0:
                self._tokens -= 1.0
                return True
            return False
