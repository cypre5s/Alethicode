from __future__ import annotations

import importlib
import os
import sys

import pytest
from fastapi import HTTPException


def _reload_config(env: dict[str, str]):
    for key in list(os.environ.keys()):
        if key.startswith("TUTOR_GRAPH_"):
            del os.environ[key]
    os.environ.update(env)
    sys.modules.pop("app.config", None)
    sys.modules.pop("app.auth", None)
    return importlib.import_module("app.auth")


def _env(**overrides: str) -> dict[str, str]:
    return {
        "TUTOR_GRAPH_CHECKPOINTER": "memory",
        "TUTOR_GRAPH_JAVA_TOOL_BASE_URL": "http://backend:8080",
        "TUTOR_GRAPH_INTERNAL_SERVICE_KEY": "current-service-key",
        **overrides,
    }


def test_current_internal_service_key_is_accepted():
    auth = _reload_config(_env())

    assert auth.is_internal_service_key_valid("current-service-key") is True


def test_previous_internal_service_key_is_accepted_during_rotation():
    auth = _reload_config(_env(TUTOR_GRAPH_INTERNAL_SERVICE_PREVIOUS_KEY="previous-service-key"))

    assert auth.is_internal_service_key_valid("previous-service-key") is True


def test_blank_and_wrong_internal_service_keys_are_rejected():
    auth = _reload_config(_env(TUTOR_GRAPH_INTERNAL_SERVICE_PREVIOUS_KEY="previous-service-key"))

    assert auth.is_internal_service_key_valid("") is False
    assert auth.is_internal_service_key_valid(None) is False
    assert auth.is_internal_service_key_valid("wrong") is False


@pytest.mark.asyncio
async def test_dependency_raises_401_for_invalid_key():
    auth = _reload_config(_env())

    with pytest.raises(HTTPException) as exc:
        await auth.require_internal_service_key("wrong")

    assert exc.value.status_code == 401
    assert exc.value.detail["detail"] == "X-Internal-Service-Key header missing or invalid"
