CREATE TABLE IF NOT EXISTS ai_workflow_session (
    session_id VARCHAR(64) PRIMARY KEY,
    thread_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE,
    phase VARCHAR(64) NOT NULL DEFAULT 'READING',
    node_outputs JSONB NOT NULL DEFAULT '{}'::jsonb,
    behavior_metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    pending_human_action TEXT NOT NULL DEFAULT '',
    last_safe_response TEXT,
    submission_id VARCHAR(64) NOT NULL DEFAULT '',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_workflow_session_user_problem_active
    ON ai_workflow_session(user_id, problem_id, is_active, updated_at DESC);

CREATE TABLE IF NOT EXISTS ai_workflow_event (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL REFERENCES ai_workflow_session(session_id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    event_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_workflow_event_session_time
    ON ai_workflow_event(session_id, created_at ASC);

CREATE TABLE IF NOT EXISTS ai_workflow_checkpoint (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL REFERENCES ai_workflow_session(session_id) ON DELETE CASCADE,
    checkpoint_id VARCHAR(64) NOT NULL,
    channel_values JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_workflow_checkpoint_session_time
    ON ai_workflow_checkpoint(session_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS idx_ai_workflow_checkpoint_unique
    ON ai_workflow_checkpoint(session_id, checkpoint_id);

CREATE TABLE IF NOT EXISTS ai_knowledge_component (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    name_en VARCHAR(255) NOT NULL DEFAULT '',
    chapter VARCHAR(64) NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    p_init DOUBLE PRECISION NOT NULL DEFAULT 0.3,
    p_transit DOUBLE PRECISION NOT NULL DEFAULT 0.2,
    p_slip DOUBLE PRECISION NOT NULL DEFAULT 0.1,
    p_guess DOUBLE PRECISION NOT NULL DEFAULT 0.2
);

CREATE INDEX IF NOT EXISTS idx_ai_kc_chapter_name
    ON ai_knowledge_component(chapter, name);

CREATE TABLE IF NOT EXISTS ai_problem_kc_mapping (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE,
    kc_id BIGINT NOT NULL REFERENCES ai_knowledge_component(id) ON DELETE CASCADE,
    weight DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    UNIQUE (problem_id, kc_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_problem_kc_mapping_kc
    ON ai_problem_kc_mapping(kc_id);

CREATE TABLE IF NOT EXISTS ai_misconception (
    id VARCHAR(64) PRIMARY KEY,
    kc_id BIGINT REFERENCES ai_knowledge_component(id) ON DELETE SET NULL,
    source VARCHAR(64) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    name VARCHAR(255) NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    correction_hint TEXT NOT NULL DEFAULT '',
    evidence_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_misconception_source_status_time
    ON ai_misconception(source, status, created_at DESC);
