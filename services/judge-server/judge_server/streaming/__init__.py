"""流式判题反馈（Phase 1 引入）。

业务端可以通过两种方式订阅"测试点逐个完成"事件：

- ``stream=true``：HTTP 响应改为 ``text/event-stream`` (SSE)
- ``callback_url=...``：判题机异步执行 + 每个测试点完成时 POST 推送

不传这两个参数时，``/judge`` 仍走老的同步阻塞模式，与上游行为完全一致。
"""

from __future__ import annotations

from .sse import (
    CASE_EVENT,
    DONE_EVENT,
    ERROR_EVENT,
    SseStreamBridge,
    format_sse_event,
)

__all__ = [
    "CASE_EVENT",
    "DONE_EVENT",
    "ERROR_EVENT",
    "SseStreamBridge",
    "format_sse_event",
]
