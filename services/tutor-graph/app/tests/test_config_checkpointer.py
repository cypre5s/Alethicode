"""Guard rails around ``TUTOR_GRAPH_CHECKPOINTER`` resolution.

Production must fail fast when required env vars are missing or the checkpointer
value is bogus; test environments opt into the in-memory mode explicitly via
``TUTOR_GRAPH_CHECKPOINTER=memory``.
"""

from __future__ import annotations

import importlib
import os
import sys

import pytest


REQUIRED_ENV = {
    "TUTOR_GRAPH_DATABASE_URI": "postgresql://u:p@h:5432/db",
    "TUTOR_GRAPH_JAVA_TOOL_BASE_URL": "http://java:8080",
    "TUTOR_GRAPH_INTERNAL_SERVICE_KEY": "k",
}


def _reload_config(env: dict[str, str]):
    for key in list(os.environ.keys()):
        if key.startswith("TUTOR_GRAPH_"):
            del os.environ[key]
    for k, v in env.items():
        os.environ[k] = v
    sys.modules.pop("app.config", None)
    return importlib.import_module("app.config")


def test_defaults_to_postgres_mode():
    config = _reload_config(REQUIRED_ENV)
    assert config.CHECKPOINTER_MODE == "postgres"
    assert config.DATABASE_URI == "postgresql://u:p@h:5432/db"


def test_memory_mode_skips_database_uri_requirement():
    env = {
        "TUTOR_GRAPH_CHECKPOINTER": "memory",
        "TUTOR_GRAPH_JAVA_TOOL_BASE_URL": "http://java:8080",
        "TUTOR_GRAPH_INTERNAL_SERVICE_KEY": "k",
    }
    config = _reload_config(env)
    assert config.CHECKPOINTER_MODE == "memory"
    assert config.DATABASE_URI == ""


def test_previous_internal_service_key_defaults_to_blank():
    config = _reload_config(REQUIRED_ENV)
    assert config.INTERNAL_SERVICE_PREVIOUS_KEY == ""


def test_previous_internal_service_key_can_be_set_for_rotation():
    config = _reload_config({
        **REQUIRED_ENV,
        "TUTOR_GRAPH_INTERNAL_SERVICE_PREVIOUS_KEY": "previous-key-for-rolling-window",
    })
    assert config.INTERNAL_SERVICE_PREVIOUS_KEY == "previous-key-for-rolling-window"


def test_postgres_mode_without_database_uri_fails_fast():
    env = {
        "TUTOR_GRAPH_CHECKPOINTER": "postgres",
        "TUTOR_GRAPH_JAVA_TOOL_BASE_URL": "http://java:8080",
        "TUTOR_GRAPH_INTERNAL_SERVICE_KEY": "k",
    }
    for key in list(os.environ.keys()):
        if key.startswith("TUTOR_GRAPH_"):
            del os.environ[key]
    for k, v in env.items():
        os.environ[k] = v
    sys.modules.pop("app.config", None)
    with pytest.raises(RuntimeError, match="TUTOR_GRAPH_DATABASE_URI"):
        importlib.import_module("app.config")


def test_invalid_mode_fails_fast():
    env = {
        **REQUIRED_ENV,
        "TUTOR_GRAPH_CHECKPOINTER": "redis",
    }
    for key in list(os.environ.keys()):
        if key.startswith("TUTOR_GRAPH_"):
            del os.environ[key]
    for k, v in env.items():
        os.environ[k] = v
    sys.modules.pop("app.config", None)
    with pytest.raises(RuntimeError, match="Invalid TUTOR_GRAPH_CHECKPOINTER"):
        importlib.import_module("app.config")
