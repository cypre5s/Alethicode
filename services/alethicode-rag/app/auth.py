"""Internal-only auth for alethicode-rag.

Every request must carry `X-Internal-Token` matching `RAG_INTERNAL_TOKEN`.
This service is only callable by the Java backend / scripts inside the
trust boundary; there is no end-user authentication path.
"""

from __future__ import annotations

import hmac

from fastapi import Header, HTTPException, status

from .config import get_settings


async def require_internal_token(
    x_internal_token: str | None = Header(default=None, alias="X-Internal-Token"),
) -> None:
    settings = get_settings()
    if not _valid_token(x_internal_token, settings.internal_token, settings.internal_previous_token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={
                "type": "https://alethicode.dev/errors/unauthorized",
                "title": "Unauthorized",
                "status": 401,
                "detail": "X-Internal-Token header missing or invalid",
            },
        )


def _valid_token(candidate: str | None, current: str, previous: str) -> bool:
    if not candidate:
        return False
    if current and hmac.compare_digest(candidate, current):
        return True
    return bool(previous) and hmac.compare_digest(candidate, previous)
