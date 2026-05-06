"""基于正则的错误诊断规则（Phase 2 引入）。

按语言覆盖常见错误模式：
- Python：NameError / IndexError / KeyError / TypeError / ZeroDivisionError / RecursionError / SyntaxError
- C/C++：segfault (signal 11) / abort (signal 6) / fpe (signal 8) + 编译错误
- Java：NullPointerException / ArrayIndexOutOfBoundsException / StackOverflowError
- 通用：TLE / MLE / WA（首行 diff）

每条规则输出 ``{error_kind, error_subtype, line_hint, root_cause_hint, evidence_excerpt, confidence, source}``。
"""

from __future__ import annotations

import re
from typing import Any, Dict, List, Optional, Tuple

_RESULT_SUCCESS = 0
_RESULT_WA = -1
_RESULT_CPU_TLE = 1
_RESULT_REAL_TLE = 2
_RESULT_MLE = 3
_RESULT_RE = 4
_RESULT_SE = 5
_RESULT_CE = -2


def _extract_line(text: str, pattern: re.Pattern) -> Optional[int]:
    m = pattern.search(text)
    if m:
        try:
            return int(m.group(1))
        except (ValueError, IndexError):
            pass
    return None


_PYTHON_EXCEPTION_RE = re.compile(
    r"(?P<type>NameError|IndexError|KeyError|TypeError|ZeroDivisionError|"
    r"RecursionError|ValueError|AttributeError|FileNotFoundError|"
    r"ImportError|ModuleNotFoundError|SyntaxError|IndentationError|"
    r"TabError|StopIteration|RuntimeError|OverflowError|"
    r"UnboundLocalError|UnicodeDecodeError|UnicodeEncodeError)"
    r":\s*(?P<msg>.+)",
    re.MULTILINE,
)
_PYTHON_LINE_RE = re.compile(r'File\s+"[^"]*",\s+line\s+(\d+)')

_C_ERROR_RE = re.compile(r"(?:error|fatal error):\s*(.+)", re.IGNORECASE)
_C_LINE_RE = re.compile(r":(\d+):\d+:\s*(?:error|warning)")

_JAVA_EXCEPTION_RE = re.compile(
    r"(?P<type>NullPointerException|ArrayIndexOutOfBoundsException|"
    r"StackOverflowError|ArithmeticException|ClassCastException|"
    r"NumberFormatException|StringIndexOutOfBoundsException|"
    r"ConcurrentModificationException|OutOfMemoryError)"
    r"(?::\s*(?P<msg>.+))?",
    re.MULTILINE,
)
_JAVA_LINE_RE = re.compile(r"\.java:(\d+)\)")

_PYTHON_HINTS: Dict[str, str] = {
    "NameError": "变量或函数名拼写错误，或在使用前未定义",
    "IndexError": "列表或字符串下标越界",
    "KeyError": "字典中不存在这个键",
    "TypeError": "数据类型不匹配，比如把字符串和数字相加",
    "ZeroDivisionError": "除数为 0",
    "RecursionError": "递归调用层数太深，可能缺少终止条件",
    "ValueError": "值不在预期范围内，比如 int('abc')",
    "AttributeError": "对象没有这个属性或方法",
    "SyntaxError": "语法错误，检查括号、冒号、缩进是否正确",
    "IndentationError": "缩进不一致，Python 对空格和 Tab 敏感",
    "ImportError": "找不到要导入的模块",
    "ModuleNotFoundError": "模块不存在（可能不在标准库范围内）",
    "UnboundLocalError": "在赋值前使用了局部变量",
}

_JAVA_HINTS: Dict[str, str] = {
    "NullPointerException": "访问了 null 引用的属性或方法",
    "ArrayIndexOutOfBoundsException": "数组下标越界",
    "StackOverflowError": "递归调用层数太深",
    "ArithmeticException": "算术异常（常见：除以 0）",
    "NumberFormatException": "字符串无法转成数字",
    "StringIndexOutOfBoundsException": "字符串下标越界",
    "OutOfMemoryError": "JVM 堆内存不足",
}


def _shorten(text: str, max_len: int = 200) -> str:
    text = text.strip()
    if len(text) <= max_len:
        return text
    return text[:max_len] + "..."


def diagnose_by_rules(
    result_code: int,
    signal: int,
    exit_code: int,
    error_output: str,
    language: str,
    expected_output: Optional[str] = None,
    actual_output: Optional[str] = None,
) -> Optional[Dict[str, Any]]:
    """尝试用规则诊断单个测试点的失败原因。

    成功匹配返回 ``edu_diagnosis`` 字典；不匹配返回 ``None``（交给 AI 兜底）。
    """
    lang = (language or "").strip().lower()
    err = error_output or ""

    if result_code == _RESULT_CE:
        return _diagnose_compile_error(err, lang)
    if result_code == _RESULT_CPU_TLE or result_code == _RESULT_REAL_TLE:
        return _make("TIME_LIMIT_EXCEEDED", None, None,
                      "程序运行时间超过限制，可能存在死循环或算法复杂度过高",
                      _shorten(err) if err else "cpu/real time exceeded",
                      0.9)
    if result_code == _RESULT_MLE:
        return _make("MEMORY_LIMIT_EXCEEDED", None, None,
                      "程序使用内存超过限制，可能存在无限增长的数据结构",
                      _shorten(err) if err else "memory limit exceeded",
                      0.9)
    if result_code == _RESULT_RE:
        return _diagnose_runtime_error(err, signal, exit_code, lang)
    if result_code == _RESULT_WA:
        return _diagnose_wrong_answer(expected_output, actual_output)
    if result_code == _RESULT_SE:
        return _make("SYSTEM_ERROR", None, None,
                      "系统内部错误（非学生代码问题），请联系管理员",
                      _shorten(err),
                      0.5)
    return None


def _diagnose_compile_error(err: str, lang: str) -> Dict[str, Any]:
    line_hint = None
    evidence = _shorten(err)
    if lang in ("c", "c++", "cpp"):
        line_hint = _extract_line(err, _C_LINE_RE)
        m = _C_ERROR_RE.search(err)
        if m:
            evidence = _shorten(m.group(1))
    elif lang in ("java",):
        line_hint = _extract_line(err, _JAVA_LINE_RE)
    elif lang in ("python3", "python"):
        line_hint = _extract_line(err, _PYTHON_LINE_RE)
    return _make("COMPILE_ERROR", None, line_hint,
                  "编译失败，请仔细检查语法错误",
                  evidence, 0.95)


def _diagnose_runtime_error(err: str, signal: int, exit_code: int, lang: str) -> Optional[Dict[str, Any]]:
    if lang in ("python3", "python"):
        m = _PYTHON_EXCEPTION_RE.search(err)
        if m:
            exc_type = m.group("type")
            hint = _PYTHON_HINTS.get(exc_type, f"Python 运行时异常：{exc_type}")
            line_hint = _extract_line(err, _PYTHON_LINE_RE)
            return _make("RUNTIME_ERROR", exc_type, line_hint, hint,
                          _shorten(m.group(0)), 0.9)
    elif lang in ("java",):
        m = _JAVA_EXCEPTION_RE.search(err)
        if m:
            exc_type = m.group("type")
            hint = _JAVA_HINTS.get(exc_type, f"Java 运行时异常：{exc_type}")
            line_hint = _extract_line(err, _JAVA_LINE_RE)
            return _make("RUNTIME_ERROR", exc_type, line_hint, hint,
                          _shorten(m.group(0)), 0.85)

    if signal == 11:
        return _make("RUNTIME_ERROR", "SEGFAULT", None,
                      "段错误（访问了非法内存地址），常见于数组越界或空指针",
                      f"signal={signal}", 0.85)
    if signal == 6:
        return _make("RUNTIME_ERROR", "ABORT", None,
                      "程序异常终止（abort），可能是断言失败或 double free",
                      f"signal={signal}", 0.8)
    if signal == 8:
        return _make("RUNTIME_ERROR", "FPE", None,
                      "浮点异常（常见：整数除以 0）",
                      f"signal={signal}", 0.85)

    return _make("RUNTIME_ERROR", None, None,
                  "运行时错误",
                  _shorten(err) or f"signal={signal} exit_code={exit_code}",
                  0.5)


def _diagnose_wrong_answer(expected: Optional[str], actual: Optional[str]) -> Dict[str, Any]:
    if expected and actual:
        exp_lines = expected.strip().splitlines()
        act_lines = actual.strip().splitlines()
        for i, (e, a) in enumerate(zip(exp_lines, act_lines), 1):
            if e != a:
                return _make("WRONG_ANSWER", "OUTPUT_MISMATCH", None,
                              f"输出第 {i} 行与期望不同",
                              f"期望: {_shorten(e, 80)} | 实际: {_shorten(a, 80)}",
                              0.85)
        if len(exp_lines) != len(act_lines):
            return _make("WRONG_ANSWER", "LINE_COUNT_MISMATCH", None,
                          f"输出行数不一致（期望 {len(exp_lines)} 行，实际 {len(act_lines)} 行）",
                          f"expected_lines={len(exp_lines)} actual_lines={len(act_lines)}",
                          0.8)
    return _make("WRONG_ANSWER", None, None,
                  "输出结果与期望不一致，请检查算法逻辑和输出格式",
                  "", 0.6)


def _make(
    error_kind: str,
    error_subtype: Optional[str],
    line_hint: Optional[int],
    root_cause_hint: str,
    evidence_excerpt: str,
    confidence: float,
) -> Dict[str, Any]:
    return {
        "error_kind": error_kind,
        "error_subtype": error_subtype,
        "line_hint": line_hint,
        "root_cause_hint": root_cause_hint,
        "evidence_excerpt": evidence_excerpt,
        "confidence": confidence,
        "source": "rule",
    }
