-- V85__ai_learner_narrative_feedback.sql
-- L99 Sprint 03: 学生对孪生摘要的反馈日志

CREATE TABLE IF NOT EXISTS ai_learner_narrative_feedback (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    summary_version INTEGER NOT NULL,
    is_accurate     BOOLEAN NOT NULL,
    reason          VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_alnf_user_time
  ON ai_learner_narrative_feedback(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_alnf_version_accurate
  ON ai_learner_narrative_feedback(summary_version, is_accurate);
