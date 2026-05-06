"""Prometheus exporter（Phase 4 引入）。

不引入 ``prometheus_client`` 第三方库（避免在 host 上安装），手工输出
Prometheus text format 即可满足 scrape 需求。

指标分四类：
1. Worker Pool：running / queued / available_slots
2. 判题结果分布：result_total counter（按 result 标签）
3. AI 调用：diagnosis / explain / safety 各自的 total / error / rate_limited / cache_hit
4. 节点资源：cpu_usage / memory_usage（/proc 读取，仅 Linux）

心跳保持原有字段不变，新指标只走 ``GET /metrics``。
"""

from __future__ import annotations

import logging
import os
import time
import threading
from typing import Any, Dict, List, Optional, Tuple

logger = logging.getLogger(__name__)


class MetricsCollector:
    """收集判题机全局指标（线程安全）。"""

    def __init__(self):
        self._lock = threading.Lock()
        self._result_counters: Dict[str, int] = {}
        self._language_counters: Dict[str, int] = {}
        self._judge_durations: List[float] = []
        self._start_time = time.monotonic()

    def record_judge_result(self, result_code: int, language: str, duration_seconds: float) -> None:
        with self._lock:
            key = str(result_code)
            self._result_counters[key] = self._result_counters.get(key, 0) + 1
            lang_key = language or "unknown"
            self._language_counters[lang_key] = self._language_counters.get(lang_key, 0) + 1
            self._judge_durations.append(duration_seconds)
            if len(self._judge_durations) > 10000:
                self._judge_durations = self._judge_durations[-5000:]

    def snapshot(self) -> Dict[str, Any]:
        with self._lock:
            return {
                "result_counters": dict(self._result_counters),
                "language_counters": dict(self._language_counters),
                "judge_count": sum(self._result_counters.values()),
                "uptime_seconds": time.monotonic() - self._start_time,
            }


_GLOBAL_COLLECTOR: Optional[MetricsCollector] = None


def get_metrics_collector() -> MetricsCollector:
    global _GLOBAL_COLLECTOR
    if _GLOBAL_COLLECTOR is None:
        _GLOBAL_COLLECTOR = MetricsCollector()
    return _GLOBAL_COLLECTOR


def _read_proc_cpu_usage() -> Optional[float]:
    try:
        import psutil
        return psutil.cpu_percent(interval=None) / 100.0
    except Exception:
        return None


def _read_proc_memory_usage() -> Optional[float]:
    try:
        import psutil
        mem = psutil.virtual_memory()
        return mem.percent / 100.0
    except Exception:
        return None


def _format_prometheus_text() -> str:
    """输出 Prometheus text exposition format。"""
    lines: List[str] = []

    def _gauge(name: str, help_text: str, value, labels: str = ""):
        lines.append(f"# HELP {name} {help_text}")
        lines.append(f"# TYPE {name} gauge")
        if labels:
            lines.append(f'{name}{{{labels}}} {value}')
        else:
            lines.append(f"{name} {value}")

    def _counter(name: str, help_text: str, value, labels: str = ""):
        lines.append(f"# HELP {name} {help_text}")
        lines.append(f"# TYPE {name} counter")
        if labels:
            lines.append(f'{name}{{{labels}}} {value}')
        else:
            lines.append(f"{name} {value}")

    try:
        from worker_pool import get_worker_pool
        pool_stats = get_worker_pool().stats()
        _gauge("judge_node_max_workers", "Worker pool max workers", pool_stats["max_workers"])
        _gauge("judge_node_available_slots", "Available worker slots", pool_stats["available_slots"])
        _gauge("judge_node_running_total", "Running tasks total", pool_stats["running_total"])
        _gauge("judge_node_queued_total", "Queued tasks total", pool_stats["queued_total"])
        for priority, count in pool_stats["running"].items():
            _gauge("judge_node_running_tasks", "Running tasks by priority", count, f'priority="{priority}"')
        for priority, count in pool_stats["queued"].items():
            _gauge("judge_node_queued_tasks", "Queued tasks by priority", count, f'priority="{priority}"')
    except Exception:
        logger.debug("worker_pool metrics unavailable")

    collector = get_metrics_collector()
    snap = collector.snapshot()
    for result_code, count in snap["result_counters"].items():
        _counter("judge_node_result_total", "Judge results by result code", count, f'result="{result_code}"')
    for lang, count in snap["language_counters"].items():
        _counter("judge_node_language_total", "Judge tasks by language", count, f'language="{lang}"')
    _counter("judge_node_tasks_completed_total", "Total completed judge tasks", snap["judge_count"])
    _gauge("judge_node_uptime_seconds", "Node uptime in seconds", f"{snap['uptime_seconds']:.1f}")

    cpu = _read_proc_cpu_usage()
    if cpu is not None:
        _gauge("judge_node_cpu_usage_ratio", "CPU usage ratio 0-1", f"{cpu:.4f}")
    mem = _read_proc_memory_usage()
    if mem is not None:
        _gauge("judge_node_memory_usage_ratio", "Memory usage ratio 0-1", f"{mem:.4f}")

    try:
        from diagnosis.engine import _get_ai_fallback
        ai = _get_ai_fallback()
        if ai is not None:
            ai_stats = ai.stats()
            llm = ai_stats.get("llm", {})
            _counter("judge_ai_diagnosis_total", "AI diagnosis calls", llm.get("total_calls", 0), 'source="ai"')
            _counter("judge_ai_diagnosis_errors", "AI diagnosis errors", llm.get("total_errors", 0))
            _counter("judge_ai_diagnosis_rate_limited", "AI diagnosis rate limited", llm.get("total_rate_limited", 0))
            cache = ai_stats.get("cache", {})
            _counter("judge_ai_cache_hit_total", "AI cache hits", cache.get("hits", 0), 'module="diagnosis"')
    except Exception:
        pass

    try:
        from explain.service import _GLOBAL_EXPLAIN
        if _GLOBAL_EXPLAIN is not None:
            ex_stats = _GLOBAL_EXPLAIN.stats()
            llm = ex_stats.get("llm", {})
            _counter("judge_ai_explain_total", "AI explain calls", llm.get("total_calls", 0))
            _counter("judge_ai_explain_errors", "AI explain errors", llm.get("total_errors", 0))
            cache = ex_stats.get("cache", {})
            _counter("judge_ai_cache_hit_total", "AI cache hits", cache.get("hits", 0), 'module="explain"')
    except Exception:
        pass

    lines.append("")
    return "\n".join(lines)


def build_metrics_handler(app):
    """在 Flask app 上注册 GET /metrics 路由。"""
    from flask import Response

    @app.route("/metrics", methods=["GET"])
    def metrics_route():
        body = _format_prometheus_text()
        return Response(body, mimetype="text/plain; version=0.0.4; charset=utf-8")
