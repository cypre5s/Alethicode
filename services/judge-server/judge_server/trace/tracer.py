"""Python sys.settrace 运行轨迹（Phase 5 引入）。

在沙箱内用 ``sys.settrace`` 捕获每行执行 + 局部变量快照，面向初学者的
"可视化代码执行过程"。

设计约束：
- 仅 Python 优先；C/C++/Java 留作后续。
- ``max_steps`` 截断保护，避免大循环 OOM。
- 局部变量做"教学过滤"：保留基本类型 / 短字符串 / 容器摘要，过滤大对象。
- 缓存 ``(code_hash, input_hash)`` → trace，5 分钟 LRU。
"""

from __future__ import annotations

import hashlib
import io
import json
import logging
import os
import sys
import threading
from contextlib import redirect_stdout
from typing import Any, Dict, List, Optional

from diagnosis.cache import DiagnosisCache

logger = logging.getLogger(__name__)

_MAX_STEPS_DEFAULT = 2000
_MAX_VAR_STR_LEN = 200
_MAX_CONTAINER_ITEMS = 10
_TRACE_LOCK = threading.Lock()


class _TraceTruncated(Exception):
    """内部控制流异常：达到 max_steps 后立即终止被跟踪代码。"""


def _safe_repr(value: Any, depth: int = 0) -> Any:
    """把变量值转换为初学者可理解的安全表示。"""
    if depth > 2:
        return "..."
    if value is None:
        return None
    if isinstance(value, (bool, int, float)):
        return value
    if isinstance(value, str):
        if len(value) > _MAX_VAR_STR_LEN:
            return value[:_MAX_VAR_STR_LEN] + f"...({len(value)} chars)"
        return value
    if isinstance(value, (list, tuple)):
        items = [_safe_repr(v, depth + 1) for v in value[:_MAX_CONTAINER_ITEMS]]
        if len(value) > _MAX_CONTAINER_ITEMS:
            items.append(f"...({len(value)} items)")
        return items
    if isinstance(value, dict):
        items = {str(k): _safe_repr(v, depth + 1) for k, v in list(value.items())[:_MAX_CONTAINER_ITEMS]}
        if len(value) > _MAX_CONTAINER_ITEMS:
            items["..."] = f"({len(value)} keys)"
        return items
    if isinstance(value, set):
        items = [_safe_repr(v, depth + 1) for v in list(value)[:_MAX_CONTAINER_ITEMS]]
        if len(value) > _MAX_CONTAINER_ITEMS:
            items.append(f"...({len(value)} items)")
        return {"__set__": items}
    type_name = type(value).__name__
    return f"<{type_name}>"


def trace_python(
    src: str,
    stdin_text: str = "",
    max_steps: int = _MAX_STEPS_DEFAULT,
) -> Dict[str, Any]:
    """在当前进程内用 ``sys.settrace`` 执行 Python 代码并收集轨迹。

    返回 ``{steps, status, total_lines}``。仅用于教学展示，不用于判题裁决。
    """
    code_lines = src.splitlines()
    steps: List[Dict[str, Any]] = []
    step_count = [0]
    truncated = [False]
    stdout_buf = io.StringIO()
    prev_stdout_pos = [0]

    def _trace_func(frame, event, arg):
        if step_count[0] >= max_steps:
            truncated[0] = True
            raise _TraceTruncated()
        if frame.f_code.co_filename != "<trace_exec>":
            return _trace_func
        if event == "line":
            lineno = frame.f_lineno
            variables = {}
            for k, v in frame.f_locals.items():
                if k.startswith("_") or k in ("__builtins__",):
                    continue
                variables[k] = _safe_repr(v)

            current_pos = stdout_buf.tell()
            stdout_buf.seek(prev_stdout_pos[0])
            new_output = stdout_buf.read()
            prev_stdout_pos[0] = current_pos
            stdout_buf.seek(current_pos)

            code_text = code_lines[lineno - 1] if 0 < lineno <= len(code_lines) else ""
            steps.append({
                "step_index": step_count[0],
                "line_number": lineno,
                "code": code_text,
                "variables": variables,
                "output": new_output,
            })
            step_count[0] += 1
        return _trace_func

    status = "ok"
    error_msg = ""
    with _TRACE_LOCK:
        try:
            compiled = compile(src, "<trace_exec>", "exec")
            old_stdin = sys.stdin
            sys.stdin = io.StringIO(stdin_text)
            try:
                sys.settrace(_trace_func)
                with redirect_stdout(stdout_buf):
                    exec(compiled, {"__builtins__": __builtins__, "__name__": "__main__"})
            finally:
                sys.settrace(None)
                sys.stdin = old_stdin
        except Exception as exc:
            if isinstance(exc, _TraceTruncated):
                status = "truncated"
                error_msg = ""
            else:
                status = "error"
                error_msg = f"{type(exc).__name__}: {exc}"
        finally:
            sys.settrace(None)

    if truncated[0]:
        status = "truncated"

    return {
        "steps": steps,
        "status": status,
        "error": error_msg,
        "total_steps": len(steps),
        "max_steps": max_steps,
        "total_lines": len(code_lines),
    }


_trace_cache = DiagnosisCache(max_size=512, ttl_seconds=float(os.environ.get("TRACE_CACHE_TTL", "300")))


def build_trace_handler(app):
    """在 Flask app 上注册 POST /trace 路由。"""
    from flask import request, Response
    from utils import token as server_token

    @app.route("/trace", methods=["POST"])
    def trace_route():
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

        language = (data.get("language") or "").strip()
        if language.lower() not in ("python3", "python"):
            return Response(
                json.dumps({"err": None, "data": {"status": "unsupported", "reason": f"trace not supported for {language}"}}),
                mimetype="application/json",
            )

        src = data.get("src", "")
        stdin_text = data.get("input", "")
        max_steps = int(data.get("max_steps", _MAX_STEPS_DEFAULT))

        code_hash = hashlib.sha256(src.encode("utf-8")).hexdigest()[:16]
        input_hash = hashlib.sha256(stdin_text.encode("utf-8")).hexdigest()[:16]
        cache_key = DiagnosisCache.make_key(code_hash, input_hash)

        cached = _trace_cache.get(cache_key)
        if cached is not None:
            return Response(json.dumps({"err": None, "data": cached}), mimetype="application/json")

        result = trace_python(src, stdin_text, max_steps)
        _trace_cache.put(cache_key, result)
        return Response(json.dumps({"err": None, "data": result}), mimetype="application/json")
