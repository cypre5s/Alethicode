"""HTTP client for Java internal tool API — all calls carry X-Internal-Service-Key."""

from __future__ import annotations

import os

import httpx


class JavaToolsClient:
    def __init__(self, base_url: str, service_key: str) -> None:
        self._base = base_url.rstrip("/")
        self._headers = {"X-Internal-Service-Key": service_key}
        self._visualize_timeout = float(os.environ.get("TUTOR_GRAPH_VISUALIZE_TIMEOUT_SECONDS", "150"))
        self._client = httpx.AsyncClient(
            base_url=self._base,
            headers=self._headers,
            timeout=30.0,
        )

    async def get_workflow_context(
        self, problem_id: int, *, user_id: int, session_id: str, language: str,
    ) -> dict:
        r = await self._client.get(
            f"/internal/ai-tutor/problems/{problem_id}/workflow-context",
            params={"user_id": user_id, "session_id": session_id, "language": language},
        )
        r.raise_for_status()
        return r.json()

    async def get_diagnosis_evidence(
        self, submission_id: str, *, user_id: int, problem_id: int, session_id: str,
    ) -> dict:
        r = await self._client.get(
            f"/internal/ai-tutor/submissions/{submission_id}/diagnosis-evidence",
            params={"user_id": user_id, "problem_id": problem_id, "session_id": session_id},
        )
        r.raise_for_status()
        return r.json()

    async def get_learner_state(
        self, user_id: int, *, problem_id: int, session_id: str, language: str,
        context_signals: dict | None = None,
    ) -> dict:
        body: dict = {
            "problem_id": problem_id,
            "session_id": session_id,
            "language": language,
        }
        if context_signals:
            body["context_signals"] = context_signals
        r = await self._client.post(
            f"/internal/ai-tutor/learners/{user_id}/state-with-context",
            json=body,
        )
        r.raise_for_status()
        return r.json()

    async def get_courseware_hits(
        self, problem_id: int, *, user_id: int, session_id: str, **kwargs,
    ) -> dict:
        params = {"user_id": user_id, "session_id": session_id, **kwargs}
        r = await self._client.get(
            f"/internal/ai-tutor/problems/{problem_id}/courseware-hits",
            params=params,
        )
        r.raise_for_status()
        return r.json()

    async def get_similar_errors(
        self, user_id: int, *, problem_id: int, session_id: str, language: str, **kwargs,
    ) -> dict:
        params = {"problem_id": problem_id, "session_id": session_id, "language": language, **kwargs}
        r = await self._client.get(
            f"/internal/ai-tutor/learners/{user_id}/similar-errors",
            params=params,
        )
        r.raise_for_status()
        return r.json()

    async def create_transfer_problem(self, payload: dict) -> dict:
        r = await self._client.post(
            "/internal/ai-tutor/transfer-problems",
            json=payload,
        )
        r.raise_for_status()
        return r.json()

    async def post_workflow_event(self, payload: dict) -> dict:
        r = await self._client.post(
            "/internal/ai-tutor/workflow-events",
            json=payload,
        )
        r.raise_for_status()
        return r.json()

    async def dispatch_parsons(self, payload: dict) -> dict:
        r = await self._client.post(
            "/internal/ai-tutor/parsons/dispatch",
            json=payload,
            timeout=self._visualize_timeout,
        )
        if r.status_code == 422:
            detail = ""
            try:
                body = r.json()
                if isinstance(body, dict):
                    detail = str(body.get("error") or body)
                else:
                    detail = str(body)
            except Exception:
                detail = r.text or ""
            raise httpx.HTTPStatusError(
                f"422 parsons dispatch failed: {detail}",
                request=r.request,
                response=r,
            )
        r.raise_for_status()
        return r.json()

    async def grade_parsons(self, payload: dict) -> dict:
        r = await self._client.post(
            "/internal/ai-tutor/parsons/grade",
            json=payload,
            timeout=30.0,
        )
        r.raise_for_status()
        return r.json()

    async def dispatch_visualize(self, payload: dict) -> dict:
        r = await self._client.post(
            "/internal/ai-tutor/visualize/dispatch",
            json=payload,
            timeout=self._visualize_timeout,
        )
        if r.status_code == 422:
            # Propagate validator detail (mermaid syntax / chart schema / svg sanitize)
            # so projection layer can record the precise reason; default httpx
            # `raise_for_status` truncates to "Client error '422 ' for url ..." which
            # makes ops blind to which validator failed.
            detail = ""
            try:
                body = r.json()
                if isinstance(body, dict):
                    detail = str(body.get("error") or body)
                else:
                    detail = str(body)
            except Exception:
                detail = r.text or ""
            raise httpx.HTTPStatusError(
                f"422 visualize validation failed: {detail}",
                request=r.request,
                response=r,
            )
        r.raise_for_status()
        return r.json()

    async def get_last_cards(self, session_id: str, *, limit: int = 5) -> list[dict]:
        """Unified Chat P3: list the last N cards in the session for chat evidence."""
        if not session_id:
            return []
        r = await self._client.get(
            f"/internal/ai-tutor/sessions/{session_id}/last-cards",
            params={"limit": limit},
        )
        r.raise_for_status()
        body = r.json() or {}
        cards = body.get("cards", [])
        return cards if isinstance(cards, list) else []

    async def resolve_references(
        self,
        session_id: str,
        references: list[str],
        current_query: str | None = None,
    ) -> dict:
        """Unified Chat P3: resolve refs to a dict with `cards` (CardSummary) and `coursewares`
        (CoursewareSummary, requires `current_query`).

        Backwards compatibility: callers that pass no `current_query` still get cards as before;
        coursewares will be an empty list because the Java side skips RAG retrieval without a query.

        Returns:
            {"cards": [...], "coursewares": [...]} — both lists default to [] when missing.
        """
        if not session_id or not references:
            return {"cards": [], "coursewares": []}
        payload: dict = {"references": references}
        if current_query and current_query.strip():
            payload["current_query"] = current_query.strip()
        r = await self._client.post(
            f"/internal/ai-tutor/sessions/{session_id}/references/resolve",
            json=payload,
        )
        r.raise_for_status()
        body = r.json() or {}
        cards = body.get("cards", []) or []
        coursewares = body.get("coursewares", []) or []
        return {
            "cards": cards if isinstance(cards, list) else [],
            "coursewares": coursewares if isinstance(coursewares, list) else [],
        }

    async def close(self) -> None:
        await self._client.aclose()
