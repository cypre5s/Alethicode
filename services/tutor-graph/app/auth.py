"""校验 Java 后端访问 tutor-graph 的内部服务密钥。"""

from __future__ import annotations

import hmac
import importlib

from fastapi import Header, HTTPException, status


def _matches(candidate: str, expected: str) -> bool:
    return bool(expected) and hmac.compare_digest(candidate, expected)


def is_internal_service_key_valid(candidate: str | None) -> bool:
    if not candidate:
        return False
    config = importlib.import_module("app.config")

    return _matches(candidate, config.INTERNAL_SERVICE_KEY) or _matches(
        candidate,
        config.INTERNAL_SERVICE_PREVIOUS_KEY,
    )


async def require_internal_service_key(
    x_internal_service_key: str | None = Header(default=None, alias="X-Internal-Service-Key"),
) -> None:
    if not is_internal_service_key_valid(x_internal_service_key):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={
                "type": "https://alethicode.dev/errors/unauthorized",
                "title": "Unauthorized",
                "status": 401,
                "detail": "X-Internal-Service-Key header missing or invalid",
            },
        )
