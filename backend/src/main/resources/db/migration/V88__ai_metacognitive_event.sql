-- V88__ai_metacognitive_event.sql
-- L99 Sprint 07: 元认知预测事件表

CREATE TABLE IF NOT EXISTS ai_metacognitive_event (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT       NOT NULL,
    problem_id              BIGINT       NOT NULL,
    session_id              VARCHAR(64),
    predicted_output        TEXT,
    predicted_reason        TEXT,
    code_snapshot           TEXT,
    actual_output           TEXT,
    diff_kind               VARCHAR(32),
    related_submission_id   VARCHAR(36),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    verified_at             TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_amce_user_time
  ON ai_metacognitive_event(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_amce_user_diff
  ON ai_metacognitive_event(user_id, diff_kind) WHERE diff_kind IS NOT NULL;
