"""判题机 Worker Pool（Phase 1 引入）。

替代上游 ``judge_client.py`` 里 ``multiprocessing.Pool(psutil.cpu_count())``
"每个判题请求都新建 cpu_count() 个 worker" 的模式。

关键约束：

- **固定大小**：节点 worker 数量由 ``JUDGE_MAX_WORKERS`` 决定，不再随并发请求
  线性膨胀。
- **三级优先队列**：``formal > debug > trace``。trace / debug 在容量紧张时
  会自动让位给正式提交，与 plan 0.6 节"AI 不抢正式判题容量"约束一致。
- **过载快速失败**：队列深度超过 ``JUDGE_MAX_QUEUE_DEPTH`` 时直接 raise
  ``QueueFull``，由 server 层映射为 503，不允许无限堆积导致雪崩。

为什么是 ``ThreadPoolExecutor`` 风格而不是 ``ProcessPoolExecutor``？
真正的判题工作发生在 Judger C 内核的子进程里（``_judger.run`` 通过
``subprocess.Popen`` 启动 ``libjudger.so``），Python 端只是阻塞等待。GIL 不
是瓶颈，threads 共享内存反而便于实现优先队列、回调和取消。
"""

from __future__ import annotations

import logging
import os
import queue
import threading
from concurrent.futures import Future
from typing import Any, Callable, Dict, Optional

logger = logging.getLogger(__name__)


PRIORITY_FORMAL = "formal"
PRIORITY_DEBUG = "debug"
PRIORITY_TRACE = "trace"

VALID_PRIORITIES = (PRIORITY_FORMAL, PRIORITY_DEBUG, PRIORITY_TRACE)

# 数值越小优先级越高（与 queue.PriorityQueue 语义一致）
_PRIORITY_VALUES: Dict[str, int] = {
    PRIORITY_FORMAL: 0,
    PRIORITY_DEBUG: 1,
    PRIORITY_TRACE: 2,
}


class QueueFull(Exception):
    """worker pool 队列已满，应快速失败（不堆积、不重试）。"""


class WorkerPool:
    """固定大小 + 三级优先队列的 worker pool。

    线程安全：``submit`` / ``stats`` / ``shutdown`` 可并发调用。
    """

    def __init__(self, max_workers: int, max_queue_depth: int):
        if max_workers < 1:
            raise ValueError("max_workers must be >= 1")
        if max_queue_depth < 1:
            raise ValueError("max_queue_depth must be >= 1")

        self._max_workers = max_workers
        self._max_queue_depth = max_queue_depth
        self._queue: queue.PriorityQueue = queue.PriorityQueue(maxsize=max_queue_depth)
        self._stop_event = threading.Event()
        self._counter = 0
        self._counter_lock = threading.Lock()
        self._stats_lock = threading.Lock()
        self._queued_by_priority: Dict[str, int] = {name: 0 for name in VALID_PRIORITIES}
        self._running_by_priority: Dict[str, int] = {name: 0 for name in VALID_PRIORITIES}
        self._workers: list[threading.Thread] = []
        for i in range(max_workers):
            t = threading.Thread(
                target=self._worker_loop,
                name=f"judge-worker-{i}",
                daemon=True,
            )
            t.start()
            self._workers.append(t)
        logger.info(
            "WorkerPool initialized: max_workers=%s max_queue_depth=%s priorities=%s",
            max_workers,
            max_queue_depth,
            VALID_PRIORITIES,
        )

    def submit(
        self,
        priority: str,
        fn: Callable[..., Any],
        *args: Any,
        **kwargs: Any,
    ) -> Future:
        """把任务提交到 worker pool。

        :raises ValueError: ``priority`` 不在 ``VALID_PRIORITIES`` 中。
        :raises QueueFull: 队列已满。
        """
        if priority not in _PRIORITY_VALUES:
            raise ValueError(
                f"invalid priority: {priority!r}; must be one of {list(VALID_PRIORITIES)}"
            )
        priority_value = _PRIORITY_VALUES[priority]

        future: Future = Future()
        with self._counter_lock:
            seq = self._counter
            self._counter += 1

        try:
            self._queue.put_nowait((priority_value, seq, future, fn, args, kwargs, priority))
        except queue.Full as exc:
            raise QueueFull(
                f"worker pool queue full (max_queue_depth={self._max_queue_depth})"
            ) from exc

        with self._stats_lock:
            self._queued_by_priority[priority] += 1
        return future

    def stats(self) -> Dict[str, Any]:
        """返回 worker pool 实时状态（用于 /metrics 与节点心跳扩展）。"""
        with self._stats_lock:
            running_total = sum(self._running_by_priority.values())
            return {
                "max_workers": self._max_workers,
                "max_queue_depth": self._max_queue_depth,
                "running": dict(self._running_by_priority),
                "queued": dict(self._queued_by_priority),
                "running_total": running_total,
                "queued_total": sum(self._queued_by_priority.values()),
                "available_slots": max(0, self._max_workers - running_total),
            }

    def shutdown(self, wait: bool = True, timeout: Optional[float] = None) -> None:
        """关闭 pool。生产代码一般不调用（pool 全程随进程存在）。"""
        self._stop_event.set()
        if wait:
            for t in self._workers:
                t.join(timeout=timeout)

    def _worker_loop(self) -> None:
        while not self._stop_event.is_set():
            try:
                priority_value, _, future, fn, args, kwargs, priority = self._queue.get(timeout=0.5)
            except queue.Empty:
                continue
            with self._stats_lock:
                self._queued_by_priority[priority] -= 1
                self._running_by_priority[priority] += 1
            if not future.set_running_or_notify_cancel():
                with self._stats_lock:
                    self._running_by_priority[priority] -= 1
                continue
            try:
                result = fn(*args, **kwargs)
                future.set_result(result)
            except BaseException as exc:  # noqa: BLE001
                future.set_exception(exc)
                logger.exception("worker task failed (priority=%s): %s", priority, exc)
            finally:
                with self._stats_lock:
                    self._running_by_priority[priority] -= 1


_GLOBAL_POOL: Optional[WorkerPool] = None
_GLOBAL_POOL_LOCK = threading.Lock()


def _resolve_default_workers() -> int:
    raw = os.environ.get("JUDGE_MAX_WORKERS")
    if raw and raw.strip():
        try:
            value = int(raw)
            if value > 0:
                return value
        except ValueError:
            logger.warning("JUDGE_MAX_WORKERS=%r is not a positive integer; falling back to cpu_count()", raw)
    try:
        import psutil  # delayed import：方便 host 单测在没装 psutil 的环境跑
        cpu = psutil.cpu_count(logical=True)
        if cpu and cpu > 0:
            return cpu
    except Exception:  # noqa: BLE001
        pass
    return 4


def _resolve_default_queue_depth() -> int:
    raw = os.environ.get("JUDGE_MAX_QUEUE_DEPTH")
    if raw and raw.strip():
        try:
            value = int(raw)
            if value > 0:
                return value
        except ValueError:
            logger.warning("JUDGE_MAX_QUEUE_DEPTH=%r is not a positive integer; falling back to 256", raw)
    return 256


def get_worker_pool() -> WorkerPool:
    """全局 lazy-init worker pool。生产路径在 server import 时间接初始化。"""
    global _GLOBAL_POOL
    if _GLOBAL_POOL is None:
        with _GLOBAL_POOL_LOCK:
            if _GLOBAL_POOL is None:
                max_workers = _resolve_default_workers()
                max_queue_depth = _resolve_default_queue_depth()
                _GLOBAL_POOL = WorkerPool(
                    max_workers=max_workers,
                    max_queue_depth=max_queue_depth,
                )
    return _GLOBAL_POOL


def reset_global_pool() -> None:
    """测试用：销毁全局 pool；生产代码请勿调用。"""
    global _GLOBAL_POOL
    with _GLOBAL_POOL_LOCK:
        if _GLOBAL_POOL is not None:
            _GLOBAL_POOL.shutdown(wait=False)
            _GLOBAL_POOL = None
