CREATE TABLE IF NOT EXISTS ai_learner_notebook (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    problem_id BIGINT,
    language VARCHAR(32) NOT NULL DEFAULT 'Python3',
    error_category VARCHAR(64) NOT NULL DEFAULT 'unknown',
    root_cause TEXT,
    fix_outcome TEXT,
    student_reflection TEXT,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_ptr JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_notebook_user_create_time
    ON ai_learner_notebook(user_id, create_time DESC);

CREATE TABLE IF NOT EXISTS ai_learning_event (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    problem_id BIGINT,
    event_type VARCHAR(64) NOT NULL,
    extra_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_correct BOOLEAN,
    vulnerability_tag VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_learning_event_user_created
    ON ai_learning_event(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_code_snapshot (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    problem_id BIGINT NOT NULL,
    code TEXT NOT NULL,
    trigger VARCHAR(32) NOT NULL DEFAULT 'interval',
    char_count INTEGER NOT NULL DEFAULT 0,
    line_count INTEGER NOT NULL DEFAULT 0,
    diff_chars_added INTEGER NOT NULL DEFAULT 0,
    diff_chars_deleted INTEGER NOT NULL DEFAULT 0,
    session_id VARCHAR(64),
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_code_snapshot_user_problem_time
    ON ai_code_snapshot(user_id, problem_id, create_time DESC);

CREATE TABLE IF NOT EXISTS ai_calibration_state (
    user_id BIGINT PRIMARY KEY REFERENCES "user"(id) ON DELETE CASCADE,
    calibrated BOOLEAN NOT NULL DEFAULT FALSE,
    current_index INTEGER NOT NULL DEFAULT 0,
    accumulated JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS generated_user_file (
    file_id VARCHAR(64) PRIMARY KEY,
    content BYTEA NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expire_time TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_generated_user_file_expire_time
    ON generated_user_file(expire_time);
