-- Phase B: classroom 作业智能组卷 + 提交学情回写
--   1. classroom_assignment 增 compose_strategy / target_kc_ids（智能组卷需要的元数据）
--   2. classroom_assignment_problem_submission 增 error_taxonomy / review_package_id
--      （事件订阅者写入：AC 不动；WA + taxonomy 命中时写复习包 ID）
--   3. 加索引便于评分页的学情面板按 user_id 查询提交聚合

ALTER TABLE classroom_assignment
    ADD COLUMN IF NOT EXISTS compose_strategy VARCHAR(20) NOT NULL DEFAULT 'manual';

ALTER TABLE classroom_assignment
    DROP CONSTRAINT IF EXISTS chk_classroom_assignment_compose_strategy;
ALTER TABLE classroom_assignment
    ADD CONSTRAINT chk_classroom_assignment_compose_strategy
    CHECK (compose_strategy IN ('manual', 'smart_kc'));

ALTER TABLE classroom_assignment
    ADD COLUMN IF NOT EXISTS target_kc_ids JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_classroom_assignment_target_kcs
    ON classroom_assignment USING gin (target_kc_ids);

ALTER TABLE classroom_assignment_problem_submission
    ADD COLUMN IF NOT EXISTS error_taxonomy VARCHAR(64);

ALTER TABLE classroom_assignment_problem_submission
    ADD COLUMN IF NOT EXISTS review_package_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_classroom_assignment_problem_submission_review_pkg
    ON classroom_assignment_problem_submission(review_package_id);

CREATE INDEX IF NOT EXISTS idx_classroom_assignment_problem_submission_taxonomy
    ON classroom_assignment_problem_submission(error_taxonomy);
