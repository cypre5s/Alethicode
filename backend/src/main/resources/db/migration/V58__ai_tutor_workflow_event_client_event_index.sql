-- V58: Align index with the tutor workflow hot query.
--
-- Why this migration exists:
--   `InternalAITutorToolServiceImpl.loadLatestErrorContext` runs:
--       SELECT event_data FROM ai_tutor_workflow_event
--       WHERE session_id = :sid AND client_event = 'ERROR_FEEDBACK'
--       ORDER BY created_at DESC LIMIT 1
--   every time tutor_graph requests `/internal/ai-tutor/learners/{id}/similar-errors`,
--   which fires on every WA submission.
--
--   V57 added `idx_atwf_event_session_event_type (session_id, event_type, created_at DESC)`
--   thinking the filtered column was `event_type`. But `services/tutor-graph/app/nodes/projection.py`
--   writes the same value into BOTH `event_type` and `client_event` columns, and the Java
--   query filters on `client_event`. Postgres won't pick the V57 index for that predicate,
--   falling back to the generic `(session_id, created_at DESC)` index and a filter, which
--   scans every event of an active session.
--
--   We add a dedicated partial index on `(session_id, client_event, created_at DESC)` so
--   the hot path is a single index scan. The V57 index is kept as-is for admin-side
--   aggregations that may filter on `event_type` later.

CREATE INDEX IF NOT EXISTS idx_atwf_event_session_client_event
    ON ai_tutor_workflow_event (session_id, client_event, created_at DESC)
    WHERE client_event IS NOT NULL;
