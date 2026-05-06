"""Phase 1 单元测试：worker pool 三级优先队列 + 过载快速失败 + stats。

只测纯 Python 行为，不依赖判题镜像。
"""

from __future__ import annotations

import sys
import threading
import time
from concurrent.futures import wait
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "judge_server"))

from worker_pool import (  # noqa: E402
    PRIORITY_DEBUG,
    PRIORITY_FORMAL,
    PRIORITY_TRACE,
    QueueFull,
    VALID_PRIORITIES,
    WorkerPool,
)


@pytest.fixture
def pool_factory():
    pools = []

    def _make(max_workers=2, max_queue_depth=8):
        pool = WorkerPool(max_workers=max_workers, max_queue_depth=max_queue_depth)
        pools.append(pool)
        return pool

    yield _make

    for pool in pools:
        pool.shutdown(wait=False)


def test_priority_constants_match_valid_priorities():
    assert set(VALID_PRIORITIES) == {PRIORITY_FORMAL, PRIORITY_DEBUG, PRIORITY_TRACE}
    assert PRIORITY_FORMAL == "formal"
    assert PRIORITY_DEBUG == "debug"
    assert PRIORITY_TRACE == "trace"


def test_submit_runs_callable_and_returns_result(pool_factory):
    pool = pool_factory(max_workers=2)
    f = pool.submit(PRIORITY_FORMAL, lambda x, y: x + y, 2, 3)
    assert f.result(timeout=2) == 5


def test_submit_propagates_exception(pool_factory):
    pool = pool_factory(max_workers=1)

    def boom():
        raise RuntimeError("boom")

    f = pool.submit(PRIORITY_FORMAL, boom)
    with pytest.raises(RuntimeError, match="boom"):
        f.result(timeout=2)


def test_submit_rejects_invalid_priority(pool_factory):
    pool = pool_factory(max_workers=1)
    with pytest.raises(ValueError, match="invalid priority"):
        pool.submit("invalid", lambda: None)


def _wait_until_running(pool, priority, expected_count, timeout=2.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if pool.stats()["running"][priority] >= expected_count:
            return
        time.sleep(0.01)
    raise AssertionError(
        f"timeout waiting for {expected_count} running task(s) at priority={priority!r}; "
        f"stats={pool.stats()}"
    )


def test_submit_raises_queue_full_when_queue_saturated(pool_factory):
    """先把 worker 全部占住，再把队列填满，再 submit 一定 QueueFull。"""
    pool = pool_factory(max_workers=1, max_queue_depth=2)

    block = threading.Event()

    def blocker():
        block.wait(timeout=5)

    occupy_running = pool.submit(PRIORITY_FORMAL, blocker)
    _wait_until_running(pool, PRIORITY_FORMAL, 1)

    queued_one = pool.submit(PRIORITY_FORMAL, lambda: 1)
    queued_two = pool.submit(PRIORITY_FORMAL, lambda: 2)

    with pytest.raises(QueueFull):
        pool.submit(PRIORITY_FORMAL, lambda: 3)

    block.set()
    wait([occupy_running, queued_one, queued_two], timeout=5)
    assert queued_one.result() == 1
    assert queued_two.result() == 2


def test_priority_ordering_runs_formal_before_trace(pool_factory):
    """单 worker + 已被占住时，后续按优先级 formal > debug > trace 出队。"""
    pool = pool_factory(max_workers=1, max_queue_depth=16)
    block = threading.Event()
    completed_order = []
    completed_lock = threading.Lock()

    def occupy():
        block.wait(timeout=5)

    occupy_future = pool.submit(PRIORITY_FORMAL, occupy)
    _wait_until_running(pool, PRIORITY_FORMAL, 1)

    def make_task(name):
        def _task():
            with completed_lock:
                completed_order.append(name)
            return name
        return _task

    f_trace = pool.submit(PRIORITY_TRACE, make_task("trace-1"))
    f_debug = pool.submit(PRIORITY_DEBUG, make_task("debug-1"))
    f_formal = pool.submit(PRIORITY_FORMAL, make_task("formal-1"))

    block.set()
    wait([occupy_future, f_trace, f_debug, f_formal], timeout=5)

    assert completed_order == ["formal-1", "debug-1", "trace-1"]


def test_stats_reflects_running_and_queued_counts(pool_factory):
    pool = pool_factory(max_workers=1, max_queue_depth=4)
    block = threading.Event()

    def hold():
        block.wait(timeout=5)

    running_future = pool.submit(PRIORITY_FORMAL, hold)
    _wait_until_running(pool, PRIORITY_FORMAL, 1)

    queued_a = pool.submit(PRIORITY_TRACE, lambda: None)
    queued_b = pool.submit(PRIORITY_DEBUG, lambda: None)

    snap = pool.stats()
    assert snap["max_workers"] == 1
    assert snap["max_queue_depth"] == 4
    assert snap["running"]["formal"] == 1
    assert snap["running"]["debug"] == 0
    assert snap["running"]["trace"] == 0
    assert snap["running_total"] == 1
    assert snap["queued"]["debug"] == 1
    assert snap["queued"]["trace"] == 1
    assert snap["queued_total"] == 2
    assert snap["available_slots"] == 0

    block.set()
    wait([running_future, queued_a, queued_b], timeout=5)

    final_snap = pool.stats()
    assert final_snap["running_total"] == 0
    assert final_snap["queued_total"] == 0
    assert final_snap["available_slots"] == 1


def test_invalid_constructor_args():
    with pytest.raises(ValueError, match="max_workers"):
        WorkerPool(max_workers=0, max_queue_depth=4)
    with pytest.raises(ValueError, match="max_queue_depth"):
        WorkerPool(max_workers=1, max_queue_depth=0)


def test_get_worker_pool_singleton(monkeypatch):
    """get_worker_pool 是 lazy 单例，且 reset_global_pool 能重建。"""
    import worker_pool as wp

    monkeypatch.setenv("JUDGE_MAX_WORKERS", "3")
    monkeypatch.setenv("JUDGE_MAX_QUEUE_DEPTH", "11")
    wp.reset_global_pool()

    pool_one = wp.get_worker_pool()
    pool_two = wp.get_worker_pool()
    assert pool_one is pool_two
    assert pool_one.stats()["max_workers"] == 3
    assert pool_one.stats()["max_queue_depth"] == 11

    wp.reset_global_pool()
    pool_three = wp.get_worker_pool()
    assert pool_three is not pool_one
    wp.reset_global_pool()
