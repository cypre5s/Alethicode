"""失败信号教学化模块（Phase 2 引入）。

把 ``result / signal / exit_code / error / output_diff`` 等原始判题信号加工
成结构化的 ``edu_diagnosis``，附在 ``data[i]`` 里。

- ``rules.py``：按语言写正则规则，覆盖 ~70% 常见错误。
- ``ai_fallback.py``：规则不命中或 ``confidence < 0.6`` 时调 LLM 兜底。
- ``cache.py``：``(code_hash, error_signature)`` → diagnosis 的 LRU 缓存。

AI 故障时降级为 ``confidence=0`` 空诊断，不阻塞主判题路径。
"""

from .engine import diagnose, empty_diagnosis

__all__ = ["diagnose", "empty_diagnosis"]
