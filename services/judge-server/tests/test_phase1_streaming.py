"""Phase 1 单元测试：SSE 串流工具。

只测协议格式与 SseStreamBridge 的事件顺序契约。
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "judge_server"))

from streaming import (  # noqa: E402
    CASE_EVENT,
    DONE_EVENT,
    ERROR_EVENT,
    SseStreamBridge,
    format_sse_event,
)


def _parse_sse_event(text):
    lines = [line for line in text.split("\n") if line]
    assert lines, "empty SSE event block"
    parsed = {}
    for line in lines:
        key, _, value = line.partition(": ")
        parsed[key] = value
    return parsed


def test_format_sse_event_matches_protocol():
    raw = format_sse_event(CASE_EVENT, {"test_case": 1, "result": 0})
    assert raw.endswith("\n\n")
    parsed = _parse_sse_event(raw)
    assert parsed["event"] == "case"
    assert json.loads(parsed["data"]) == {"test_case": 1, "result": 0}


def test_format_sse_event_handles_chinese_payload():
    raw = format_sse_event(DONE_EVENT, {"msg": "判题完成"})
    parsed = _parse_sse_event(raw)
    assert parsed["event"] == "done"
    assert json.loads(parsed["data"]) == {"msg": "判题完成"}


def test_sse_stream_bridge_emits_case_then_done_in_order():
    """SseStreamBridge 必须按“case 多次 → done 一次”的顺序产生 SSE event。"""

    def runner(on_case_done):
        results = []
        for i in range(3):
            payload = {"test_case": i + 1, "result": 0}
            on_case_done(payload)
            results.append(payload)
        return results

    bridge = SseStreamBridge(judge_runner=runner)
    events = list(bridge.stream())

    assert len(events) == 4  # 3 case + 1 done
    parsed = [_parse_sse_event(e) for e in events]
    assert [p["event"] for p in parsed] == [CASE_EVENT, CASE_EVENT, CASE_EVENT, DONE_EVENT]

    case_payloads = [json.loads(p["data"]) for p in parsed[:3]]
    assert [c["test_case"] for c in case_payloads] == [1, 2, 3]

    done_payload = json.loads(parsed[3]["data"])
    assert len(done_payload) == 3


def test_sse_stream_bridge_emits_error_event_when_runner_raises():
    def runner(on_case_done):
        on_case_done({"test_case": 1, "result": 0})
        raise RuntimeError("boom")

    bridge = SseStreamBridge(judge_runner=runner)
    events = list(bridge.stream())

    parsed = [_parse_sse_event(e) for e in events]
    # case 事件可能在 error 之前到达；最后一个一定是 error，且总 event 数为 2。
    assert parsed[-1]["event"] == ERROR_EVENT
    error_payload = json.loads(parsed[-1]["data"])
    assert "boom" in error_payload["error"]
    assert {p["event"] for p in parsed[:-1]} <= {CASE_EVENT}
