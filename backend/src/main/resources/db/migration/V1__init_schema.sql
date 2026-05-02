CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS sys_options (
    key VARCHAR(128) PRIMARY KEY,
    value JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS judge_server (
    id BIGSERIAL PRIMARY KEY,
    hostname VARCHAR(255) NOT NULL UNIQUE,
    judger_version VARCHAR(64) NOT NULL,
    cpu_core INTEGER NOT NULL,
    memory_usage NUMERIC(10,2) NOT NULL,
    cpu_usage NUMERIC(10,2) NOT NULL,
    service_url VARCHAR(512),
    ip VARCHAR(64),
    is_disabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_heartbeat TIMESTAMPTZ NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    task_number INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_judge_server_last_heartbeat ON judge_server(last_heartbeat DESC);
