"""失败 AI 解释端点 /explain（Phase 3 引入）。"""

from .service import build_explain_handler, ExplainService

__all__ = ["build_explain_handler", "ExplainService"]
