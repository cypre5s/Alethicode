-- ai_error_review_problem: 新增 is_ai_generated 区分错题和AI特化题
ALTER TABLE ai_error_review_problem
    ADD COLUMN IF NOT EXISTS is_ai_generated BOOLEAN NOT NULL DEFAULT FALSE;

-- ai_error_review_package: 新增 all_ac 标记6题是否全部AC
ALTER TABLE ai_error_review_package
    ADD COLUMN IF NOT EXISTS all_ac BOOLEAN NOT NULL DEFAULT FALSE;

-- problem: 新增 ai_source_review_package_id 关联AI特化题到复习包
ALTER TABLE problem
    ADD COLUMN IF NOT EXISTS ai_source_review_package_id VARCHAR(64);
