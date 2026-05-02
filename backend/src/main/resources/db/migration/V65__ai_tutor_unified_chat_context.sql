-- V65: Unified Chat shared context (DeepTutor P3)
-- Design: docs/plans/2026-04-25-unified-chat-context-design.md §7.2
--
-- Adds:
--   * ai_tutor_workflow_session.active_mode + last_mode_switched_at  – per-session active Mode (user view)
--   * ai_tutor_workflow_event.card_id / card_type                    – stable card anchors for @card: references
--   * ai_tutor_workflow_event.mode_when_produced                     – which user Mode produced the card
--   * ai_tutor_workflow_event.referenced_card_ids                    – jsonb array of card_ids the LLM referenced

ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS active_mode VARCHAR(32) NOT NULL DEFAULT 'reading',
    ADD COLUMN IF NOT EXISTS last_mode_switched_at TIMESTAMPTZ;

ALTER TABLE ai_tutor_workflow_event
    ADD COLUMN IF NOT EXISTS card_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS card_type VARCHAR(48),
    ADD COLUMN IF NOT EXISTS mode_when_produced VARCHAR(32),
    ADD COLUMN IF NOT EXISTS referenced_card_ids JSONB NOT NULL DEFAULT '[]'::jsonb;

-- @last_error / @last_visualize lookups must hit an index even on long sessions.
CREATE INDEX IF NOT EXISTS idx_atwf_event_card_id
    ON ai_tutor_workflow_event (session_id, card_type, created_at DESC)
    WHERE card_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_atwf_event_card_id_unique
    ON ai_tutor_workflow_event (card_id)
    WHERE card_id IS NOT NULL;
