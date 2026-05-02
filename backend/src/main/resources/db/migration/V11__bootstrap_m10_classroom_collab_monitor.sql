CREATE TABLE IF NOT EXISTS classroom_session (
    id VARCHAR(64) PRIMARY KEY,
    classroom_id VARCHAR(64) NOT NULL REFERENCES classroom(id) ON DELETE CASCADE,
    classroom_problem_id VARCHAR(64) REFERENCES classroom_problem(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    mode VARCHAR(20) NOT NULL,
    yjs_doc_name VARCHAR(255) NOT NULL UNIQUE,
    relay_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    scaffolding_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    host_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    participant_count INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_classroom_session_classroom_active
    ON classroom_session(classroom_id, is_active, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_classroom_session_host
    ON classroom_session(host_id, create_time DESC);

CREATE TABLE IF NOT EXISTS student_monitoring_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    classroom_id VARCHAR(64) NOT NULL REFERENCES classroom(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    classroom_problem_id VARCHAR(64) REFERENCES classroom_problem(id) ON DELETE SET NULL,
    snapshot_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(20) NOT NULL,
    code_snapshot TEXT NOT NULL DEFAULT '',
    code_hash VARCHAR(64) NOT NULL DEFAULT '',
    edit_distance INTEGER NOT NULL DEFAULT 0,
    submission_count INTEGER NOT NULL DEFAULT 0,
    ac_count INTEGER NOT NULL DEFAULT 0,
    elapsed_time_seconds INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_student_monitoring_snapshot_classroom_time
    ON student_monitoring_snapshot(classroom_id, snapshot_time DESC);

CREATE INDEX IF NOT EXISTS idx_student_monitoring_snapshot_user_problem_time
    ON student_monitoring_snapshot(user_id, classroom_problem_id, snapshot_time DESC);
