-- Phase 3.2 (post-0423): per-problem rating for AI error-review packages.
-- 学生在 review-package 上对单题点「我会了 / 再练一题」时写入 user_rating，
-- 包级 FSRS 推进沿用现有 ai_error_review_package.fsrs_* 字段。

ALTER TABLE ai_error_review_problem
    ADD COLUMN IF NOT EXISTS user_rating VARCHAR(16),
    ADD COLUMN IF NOT EXISTS rated_at    TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_aerp_user_rating
    ON ai_error_review_problem(package_id, user_rating);

COMMENT ON COLUMN ai_error_review_problem.user_rating IS
    'student-driven rating for FSRS per-problem mastery: again | good | null';
COMMENT ON COLUMN ai_error_review_problem.rated_at IS
    'when student last set user_rating';
