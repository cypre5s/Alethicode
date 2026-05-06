"""诊断引擎：规则优先、AI 兜底、空诊断降级（Phase 2 引入）。

每个测试点完成后由 ``judge_client.py`` 调用 ``diagnose()``，把结构化的
``edu_diagnosis`` 附在 ``case_result`` 上。

调用链：
  ``judge_client._judge_one()`` → ``diagnose(case_result, language, src)``
  → 规则命中？用规则
  → 规则未命中或 confidence < 0.6？尝试 AI
  → AI 也失败？返回空诊断（confidence=0）
"""

from __future__ import annotations

import logging
from typing import Any, Dict, Optional

from diagnosis.rules import diagnose_by_rules

logger = logging.getLogger(__name__)

_CONFIDENCE_THRESHOLD = 0.6

_ai_fallback = None
_ai_fallback_initialized = False


def _get_ai_fallback():
    global _ai_fallback, _ai_fallback_initialized
    if not _ai_fallback_initialized:
        try:
            from diagnosis.ai_fallback import AiFallbackDiagnosis
            _ai_fallback = AiFallbackDiagnosis()
        except Exception:  # noqa: BLE001
            logger.exception("Failed to initialize AI fallback diagnosis")
            _ai_fallback = None
        _ai_fallback_initialized = True
    return _ai_fallback


def empty_diagnosis() -> Dict[str, Any]:
    """空诊断：AI 和规则都无法识别时的降级返回。"""
    return {
        "error_kind": "UNKNOWN",
        "error_subtype": None,
        "line_hint": None,
        "root_cause_hint": "",
        "evidence_excerpt": "",
        "confidence": 0.0,
        "source": "none",
    }


def diagnose(
    case_result: Dict[str, Any],
    language: str,
    src: str,
    expected_output: Optional[str] = None,
    failed_case_input: Optional[str] = None,
) -> Dict[str, Any]:
    """为单个测试点生成 ``edu_diagnosis``。

    永远不 raise——任何异常都降级为空诊断，不阻塞主判题路径。
    """
    result_code = case_result.get("result", 0)
    if result_code == 0:
        return {
            "error_kind": "ACCEPTED",
            "error_subtype": None,
            "line_hint": None,
            "root_cause_hint": "",
            "evidence_excerpt": "",
            "confidence": 1.0,
            "source": "rule",
        }

    signal = case_result.get("signal", 0)
    exit_code = case_result.get("exit_code", 0)
    error_output = case_result.get("output", "") or case_result.get("error", "") or ""
    actual_output = case_result.get("output", "")

    try:
        rule_result = diagnose_by_rules(
            result_code=result_code,
            signal=signal,
            exit_code=exit_code,
            error_output=error_output,
            language=language,
            expected_output=expected_output,
            actual_output=actual_output,
        )
    except Exception:  # noqa: BLE001
        logger.exception("rule diagnosis raised exception")
        rule_result = None

    if rule_result is not None and rule_result.get("confidence", 0) >= _CONFIDENCE_THRESHOLD:
        return rule_result

    ai = _get_ai_fallback()
    if ai is not None and ai.enabled:
        try:
            ai_result = ai.diagnose(
                language=language,
                src=src,
                failed_case_input=failed_case_input or "",
                expected=expected_output or "",
                actual=actual_output or "",
                signal=signal,
                exit_code=exit_code,
                error=error_output,
            )
            if ai_result is not None:
                return ai_result
        except Exception:  # noqa: BLE001
            logger.exception("AI fallback diagnosis raised exception")

    if rule_result is not None:
        return rule_result

    return empty_diagnosis()
