"""SSE (Server-Sent Events) 串流工具。

SSE 协议格式：

  event: <event_type>\n
  data: <json string>\n
  \n

业务端订阅这个流后，可以在第一个测试点完成时立刻看到结果，而不必等所有
测试点跑完。
"""

from __future__ import annotations

import json
import logging
import queue
import threading
from typing import Any, Callable, Iterator, List

logger = logging.getLogger(__name__)


CASE_EVENT = "case"
DONE_EVENT = "done"
ERROR_EVENT = "error"


def format_sse_event(event_type: str, payload: Any) -> str:
    """把任意 JSON-serializable payload 编码成 SSE event 字符串。"""
    body = json.dumps(payload, ensure_ascii=False, default=str)
    return f"event: {event_type}\ndata: {body}\n\n"


class SseStreamBridge:
    """把判题函数的 ``on_case_done`` 回调适配成 SSE event generator。

    使用方式（在 server 路由层）：

    .. code-block:: python

        bridge = SseStreamBridge(
            judge_runner=lambda on_case_done: JudgeServer.judge(
                ..., on_case_done=on_case_done
            )
        )
        return Response(bridge.stream(), mimetype="text/event-stream")

    判题在后台 thread 运行；主线程从 queue 拿到 case 结果就立刻 yield SSE，
    实现"测试点逐个推送"的体验。
    """

    def __init__(self, judge_runner: Callable[[Callable[[dict], None]], List[dict]]):
        """
        :param judge_runner: 接收 ``on_case_done`` 回调、返回完整 data 列表
            的判题函数。在内部启动后台 thread 执行它。
        """
        self._judge_runner = judge_runner
        self._queue: "queue.Queue[tuple[str, Any]]" = queue.Queue()

    def stream(self) -> Iterator[str]:
        """供 ``flask.Response(generator)`` 直接消费的 SSE event 流。"""

        def _on_case_done(case_result: dict) -> None:
            self._queue.put(("case", case_result))

        def _runner_thread() -> None:
            try:
                final_data = self._judge_runner(_on_case_done)
                self._queue.put(("done", final_data))
            except BaseException as exc:  # noqa: BLE001
                logger.exception("sse judge runner failed: %s", exc)
                self._queue.put(("error", repr(exc)))

        thread = threading.Thread(
            target=_runner_thread,
            name="sse-judge-runner",
            daemon=True,
        )
        thread.start()

        while True:
            kind, payload = self._queue.get()
            if kind == "case":
                yield format_sse_event(CASE_EVENT, payload)
            elif kind == "done":
                yield format_sse_event(DONE_EVENT, payload)
                return
            else:  # error
                yield format_sse_event(ERROR_EVENT, {"error": payload})
                return
