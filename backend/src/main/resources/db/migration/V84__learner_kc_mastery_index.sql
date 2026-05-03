-- V84__learner_kc_mastery_index.sql
-- L99 Sprint 02: KC 星系图性能索引

CREATE INDEX IF NOT EXISTS idx_learner_kc_mastery_user
  ON learner_kc_mastery(user_id, mastery DESC);
