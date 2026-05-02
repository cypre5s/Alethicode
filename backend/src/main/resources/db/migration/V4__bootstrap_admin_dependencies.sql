CREATE TABLE IF NOT EXISTS "user" (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS problem (
    id BIGSERIAL PRIMARY KEY,
    test_case_id VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS submission (
    id VARCHAR(64) PRIMARY KEY,
    problem_id BIGINT,
    user_id BIGINT NOT NULL DEFAULT 0,
    username VARCHAR(255) NOT NULL DEFAULT '',
    code TEXT NOT NULL DEFAULT '',
    result INTEGER NOT NULL DEFAULT 6,
    info JSONB NOT NULL DEFAULT '{}'::jsonb,
    language VARCHAR(64) NOT NULL DEFAULT '',
    shared BOOLEAN NOT NULL DEFAULT FALSE,
    statistic_info JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip VARCHAR(64),
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_submission_create_time ON submission(create_time DESC);
