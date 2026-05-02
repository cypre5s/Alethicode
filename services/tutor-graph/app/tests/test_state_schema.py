"""Tests for TutorGraphState schema and ingest node."""

from app.graph.state import TutorGraphState
from app.nodes.ingest import ingest_event


class TestIngestEvent:
    def test_happy_path(self):
        state: TutorGraphState = {
            "client_event": "READING",
            "language": "Python3",
            "session_id": "s1",
            "thread_id": "t1",
            "run_id": "r1",
            "node_outputs": {},
        }
        result = ingest_event(state)
        assert result["runtime_state"] == "RUNNING"
        assert result["client_event"] == "READING"
        assert result["trace_id"].startswith("trace_")
        assert result["node_outputs"]["last_event"]["event"] == "READING"

    def test_missing_event(self):
        state: TutorGraphState = {
            "client_event": "",
            "language": "Python3",
            "node_outputs": {},
        }
        result = ingest_event(state)
        assert result["runtime_state"] == "FAILED"
        assert result["failure_bucket"] == "SCHEMA_VIOLATION"

    def test_missing_language(self):
        state: TutorGraphState = {
            "client_event": "READING",
            "language": "",
            "node_outputs": {},
        }
        result = ingest_event(state)
        assert result["runtime_state"] == "FAILED"

    def test_case_normalization(self):
        state: TutorGraphState = {
            "client_event": "reading",
            "language": "Python3",
            "node_outputs": {},
        }
        result = ingest_event(state)
        assert result["client_event"] == "READING"
