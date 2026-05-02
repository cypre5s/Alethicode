-- V57: Persist language / last_checkpoint_id on ai_tutor_workflow_session projection.
--
-- Rationale:
--   - TutorWorkflowController.createRun must derive language from the session projection so a
--     subsequent request that only carries event_data can still run without
--     falling back to a hard-coded default. This honors the fail-fast contract.
--   - Admin observability queries join on event.server_event + session.last_checkpoint_id
--     to resolve the latest pause point, so last_checkpoint_id must carry a real value.

ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS language VARCHAR(32) NOT NULL DEFAULT '';

-- Backfill language for existing rows when a run has already recorded it in its event payload.
UPDATE ai_tutor_workflow_session s
SET language = COALESCE(NULLIF(s.language, ''), lang.resolved)
FROM (
    SELECT
        e.session_id,
        (e.event_data #>> '{event_data,language}') AS resolved
    FROM ai_tutor_workflow_event e
    WHERE (e.event_data #>> '{event_data,language}') IS NOT NULL
      AND (e.event_data #>> '{event_data,language}') <> ''
) AS lang
WHERE s.session_id = lang.session_id
  AND (s.language IS NULL OR s.language = '');

-- last_checkpoint_id needs an index for admin dashboards that scan restoration-capable sessions.
CREATE INDEX IF NOT EXISTS idx_atwf_session_last_checkpoint
    ON ai_tutor_workflow_session (last_checkpoint_id)
    WHERE last_checkpoint_id IS NOT NULL;

-- event projection query shape used by admin observability filters.
CREATE INDEX IF NOT EXISTS idx_atwf_event_session_event_type
    ON ai_tutor_workflow_event (session_id, event_type, created_at DESC);
