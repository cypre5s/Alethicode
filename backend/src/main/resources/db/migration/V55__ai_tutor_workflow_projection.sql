-- V55: Projection tables for LangGraph tutor workflow
-- These tables are NOT the runtime source of truth (LangGraph checkpointer is).
-- They serve API queries, frontend recovery, admin observability, and audit.

CREATE TABLE IF NOT EXISTS ai_tutor_workflow_session (
    session_id      VARCHAR(64)     PRIMARY KEY,
    thread_id       VARCHAR(128)    NOT NULL,
    user_id         BIGINT          NOT NULL,
    problem_id      BIGINT          NOT NULL,
    phase           VARCHAR(64)     NOT NULL DEFAULT 'READING',
    runtime_state   VARCHAR(64)     NOT NULL DEFAULT 'COMPLETED',
    pending_human_action TEXT       NOT NULL DEFAULT '',
    node_outputs    JSONB           NOT NULL DEFAULT '{}'::JSONB,
    behavior_metrics JSONB          NOT NULL DEFAULT '{}'::JSONB,
    available_actions JSONB         NOT NULL DEFAULT '[]'::JSONB,
    last_checkpoint_id VARCHAR(128),
    last_run_id     VARCHAR(128),
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_atwf_session_user_problem
    ON ai_tutor_workflow_session (user_id, problem_id)
    WHERE is_active = TRUE;

CREATE TABLE IF NOT EXISTS ai_tutor_workflow_event (
    id              BIGSERIAL       PRIMARY KEY,
    session_id      VARCHAR(64)     NOT NULL,
    run_id          VARCHAR(128)    NOT NULL,
    thread_id       VARCHAR(128)    NOT NULL,
    event_type      VARCHAR(64)     NOT NULL,
    runtime_state   VARCHAR(64),
    server_event    VARCHAR(64),
    client_event    VARCHAR(64),
    failure_bucket  VARCHAR(64),
    trace_id        VARCHAR(128),
    event_data      JSONB           NOT NULL DEFAULT '{}'::JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_atwf_event_session
    ON ai_tutor_workflow_event (session_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_atwf_event_trace
    ON ai_tutor_workflow_event (trace_id)
    WHERE trace_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS ai_tutor_side_effect_log (
    idempotency_key VARCHAR(256)    PRIMARY KEY,
    session_id      VARCHAR(64)     NOT NULL,
    run_id          VARCHAR(128)    NOT NULL,
    effect_type     VARCHAR(64)     NOT NULL,
    request_hash    VARCHAR(128)    NOT NULL,
    result_json     JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
