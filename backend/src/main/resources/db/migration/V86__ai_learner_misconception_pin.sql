-- V86__ai_learner_misconception_pin.sql
-- L99 Sprint 04: 错误模式个人馆（学生钉选展品）

CREATE TABLE IF NOT EXISTS ai_learner_misconception_pin (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    memory_id     BIGINT       NOT NULL,
    annotation    VARCHAR(280),
    pin_order     INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, memory_id)
);

CREATE INDEX IF NOT EXISTS idx_almp_user_order
  ON ai_learner_misconception_pin(user_id, pin_order ASC);
