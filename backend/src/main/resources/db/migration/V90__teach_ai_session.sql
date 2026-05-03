-- V90__teach_ai_session.sql
-- L99 Sprint 13: 学生教 AI 会话表（HypoCompass 范式）

CREATE TABLE IF NOT EXISTS teach_ai_session (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    problem_id          BIGINT,
    target_kc_id        BIGINT       NOT NULL,
    misconception_text  TEXT         NOT NULL,
    student_explanation TEXT,
    grader_score        INTEGER,
    grader_feedback     TEXT,
    grader_metadata     JSONB,
    round_count         INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_tas_user
  ON teach_ai_session(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tas_kc
  ON teach_ai_session(target_kc_id);
