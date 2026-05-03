-- V89__ai_learner_mastery_override.sql
-- L99 Sprint 09: 学生手动标注 KC 掌握度覆写

CREATE TABLE IF NOT EXISTS ai_learner_mastery_override (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    kc_id           BIGINT       NOT NULL,
    original_mastery NUMERIC(5,4),
    overridden_mastery NUMERIC(5,4) NOT NULL,
    reason          VARCHAR(280),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, kc_id)
);

CREATE INDEX IF NOT EXISTS idx_almo_user
  ON ai_learner_mastery_override(user_id);
