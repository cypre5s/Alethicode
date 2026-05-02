ALTER TABLE "user"
    ADD COLUMN IF NOT EXISTS admin_type VARCHAR(32) NOT NULL DEFAULT 'Regular User';

CREATE TABLE IF NOT EXISTS user_profile (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES "user"(id) ON DELETE CASCADE,
    acm_problems_status JSONB NOT NULL DEFAULT '{}'::jsonb,
    real_name TEXT
);

ALTER TABLE problem
    ADD COLUMN IF NOT EXISTS _id TEXT,
    ADD COLUMN IF NOT EXISTS is_public BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS title TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS description TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS input_description TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS output_description TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS samples JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS test_case_score JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS hint TEXT,
    ADD COLUMN IF NOT EXISTS languages JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS template JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS last_update_time TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by_id BIGINT REFERENCES "user"(id),
    ADD COLUMN IF NOT EXISTS time_limit INTEGER NOT NULL DEFAULT 1000,
    ADD COLUMN IF NOT EXISTS memory_limit INTEGER NOT NULL DEFAULT 256,
    ADD COLUMN IF NOT EXISTS reference_solution_language TEXT,
    ADD COLUMN IF NOT EXISTS reference_solution_code TEXT,
    ADD COLUMN IF NOT EXISTS visible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS difficulty TEXT NOT NULL DEFAULT 'Low',
    ADD COLUMN IF NOT EXISTS source TEXT,
    ADD COLUMN IF NOT EXISTS submission_number BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS accepted_number BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS statistic_info JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS is_ai_generated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ai_source_classroom_id BIGINT,
    ADD COLUMN IF NOT EXISTS ai_source_problem_id BIGINT REFERENCES problem(id),
    ADD COLUMN IF NOT EXISTS ai_variant_index INTEGER,
    ADD COLUMN IF NOT EXISTS visibility_status VARCHAR(20) NOT NULL DEFAULT 'class_private';

CREATE INDEX IF NOT EXISTS idx_problem_display_id ON problem(_id);
CREATE INDEX IF NOT EXISTS idx_problem_visible_create_time ON problem(visible, create_time DESC);

CREATE TABLE IF NOT EXISTS problem_tag (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS problem_problem_tags (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE,
    problemtag_id BIGINT NOT NULL REFERENCES problem_tag(id) ON DELETE CASCADE,
    UNIQUE (problem_id, problemtag_id)
);

CREATE INDEX IF NOT EXISTS idx_problem_problem_tags_problem_id ON problem_problem_tags(problem_id);
CREATE INDEX IF NOT EXISTS idx_problem_problem_tags_tag_id ON problem_problem_tags(problemtag_id);
