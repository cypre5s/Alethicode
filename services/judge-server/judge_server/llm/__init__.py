"""判题机自持 LLM 客户端（OpenAI 兼容协议）。

diagnosis / explain / safety 三个模块各自独立配置（endpoint / model / api_key /
rate_limit / timeout），共用同一个 ``LlmClient`` 基类，避免互相影响。
"""

from .client import LlmClient

__all__ = ["LlmClient"]
