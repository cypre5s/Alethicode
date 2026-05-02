"""Validate card output against JSON Schema — fail-fast, no field patching."""

from __future__ import annotations

import json
from pathlib import Path

import jsonschema

from app.graph.state import TutorGraphState
from app.nodes.output_sanitization import strip_projection_visualize_helpers
from app.paths import CARD_SCHEMA_DIR as SCHEMA_DIR

CARD_KEY_TO_SCHEMA: dict[str, str] = {
    "problem_guide": "problem_guide.schema.json",
    "ideate": "ideate_analysis.schema.json",
    "skeleton_code": "skeleton_code.schema.json",
    "error_diagnosis": "error_diagnosis.schema.json",
    "post_ac": "post_ac.schema.json",
    "transfer": "transfer_problem.schema.json",
    "chat": "ai_reply.schema.json",
    "knowledge_review": "knowledge_review.schema.json",
    "execution_trace_explainer": "execution_trace_explainer.schema.json",
    "visualize": "visualize.schema.json",
}

_schema_cache: dict[str, dict] = {}


def _load_schema(filename: str) -> dict:
    if filename not in _schema_cache:
        path = SCHEMA_DIR / filename
        if not path.exists():
            raise FileNotFoundError(f"Card schema not found: {path}")
        with open(path) as f:
            _schema_cache[filename] = json.load(f)
    return _schema_cache[filename]


def validate_card_schema(state: TutorGraphState) -> TutorGraphState:
    node_outputs = _strip_null_fields(strip_projection_visualize_helpers(state.get("node_outputs", {})))

    for card_key, schema_file in CARD_KEY_TO_SCHEMA.items():
        payload = node_outputs.get(card_key)
        if payload is None:
            continue

        try:
            schema = _load_schema(schema_file)
            jsonschema.validate(instance=payload, schema=schema)
        except jsonschema.ValidationError as e:
            return {
                **state,
                "node_outputs": node_outputs,
                "runtime_state": "FAILED",
                "failure_bucket": "SCHEMA_VIOLATION",
                "last_error": f"Card schema validation failed for '{card_key}': {e.message}",
            }
        except FileNotFoundError as e:
            return {
                **state,
                "node_outputs": node_outputs,
                "runtime_state": "FAILED",
                "failure_bucket": "SYSTEM_ERROR",
                "last_error": str(e),
            }

    return {**state, "node_outputs": node_outputs}


def _strip_null_fields(value):
    if isinstance(value, dict):
        return {
            key: _strip_null_fields(item)
            for key, item in value.items()
            if item is not None
        }
    if isinstance(value, list):
        return [_strip_null_fields(item) for item in value]
    return value
