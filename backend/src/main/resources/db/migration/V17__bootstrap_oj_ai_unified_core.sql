CREATE TABLE IF NOT EXISTS ai_feedback_label (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    problem_id BIGINT,
    session_id VARCHAR(64) NOT NULL DEFAULT '',
    workflow_event_id VARCHAR(128) NOT NULL DEFAULT '',
    card_type VARCHAR(64) NOT NULL DEFAULT '',
    feedback_label VARCHAR(32) NOT NULL DEFAULT '',
    agent_id INTEGER,
    source_event_type VARCHAR(64) NOT NULL DEFAULT '',
    extra_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_feedback_label_user_problem_time
    ON ai_feedback_label(user_id, problem_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_tutor_trace (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    phase VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    trace_status VARCHAR(32) NOT NULL DEFAULT 'ok',
    evidence_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    learner_state JSONB NOT NULL DEFAULT '{}'::jsonb,
    decision JSONB NOT NULL DEFAULT '{}'::jsonb,
    guardrail JSONB NOT NULL DEFAULT '{}'::jsonb,
    schema_validation JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_tutor_trace_session_time
    ON ai_tutor_trace(session_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_tutor_generation_log (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    phase VARCHAR(64) NOT NULL,
    card_type VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL DEFAULT '',
    prompt_hash VARCHAR(64) NOT NULL DEFAULT '',
    evidence_hash VARCHAR(64) NOT NULL DEFAULT '',
    request_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    schema_pass BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_tutor_generation_log_session_time
    ON ai_tutor_generation_log(session_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_retrieval_log (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    problem_id BIGINT,
    phase VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL DEFAULT '',
    source_id VARCHAR(64) NOT NULL DEFAULT '',
    score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    chunk_excerpt TEXT NOT NULL DEFAULT '',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_retrieval_log_session_time
    ON ai_retrieval_log(session_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_learner_profile_snapshot (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    problem_id BIGINT,
    session_id VARCHAR(64) NOT NULL DEFAULT '',
    mastery_by_kc JSONB NOT NULL DEFAULT '{}'::jsonb,
    weak_kcs JSONB NOT NULL DEFAULT '[]'::jsonb,
    misconception_distribution JSONB NOT NULL DEFAULT '{}'::jsonb,
    recent_behavior JSONB NOT NULL DEFAULT '{}'::jsonb,
    frustration_level VARCHAR(16) NOT NULL DEFAULT 'low',
    confidence_proxy VARCHAR(16) NOT NULL DEFAULT 'low',
    recommended_action_bias JSONB NOT NULL DEFAULT '{}'::jsonb,
    memory_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_learner_profile_snapshot_user_time
    ON ai_learner_profile_snapshot(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_courseware_chunk (
    id BIGSERIAL PRIMARY KEY,
    classroom_id BIGINT,
    lesson_id BIGINT,
    chapter VARCHAR(64) NOT NULL DEFAULT '',
    kc_id BIGINT REFERENCES ai_knowledge_component(id) ON DELETE SET NULL,
    problem_id BIGINT REFERENCES problem(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL DEFAULT '',
    content TEXT NOT NULL DEFAULT '',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_courseware_chunk_lookup
    ON ai_courseware_chunk(problem_id, kc_id, chapter, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_eval_dataset (
    id BIGSERIAL PRIMARY KEY,
    dataset_name VARCHAR(128) NOT NULL,
    phase VARCHAR(64) NOT NULL DEFAULT '',
    card_type VARCHAR(64) NOT NULL DEFAULT '',
    input_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    expectation JSONB NOT NULL DEFAULT '{}'::jsonb,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_eval_dataset_name_time
    ON ai_eval_dataset(dataset_name, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_eval_run (
    id BIGSERIAL PRIMARY KEY,
    dataset_name VARCHAR(128) NOT NULL,
    model_name VARCHAR(128) NOT NULL DEFAULT '',
    variant_name VARCHAR(128) NOT NULL DEFAULT '',
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_eval_run_dataset_time
    ON ai_eval_run(dataset_name, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_rollout_decision (
    id BIGSERIAL PRIMARY KEY,
    scope_type VARCHAR(64) NOT NULL,
    scope_key VARCHAR(128) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_rollout_decision_scope_time
    ON ai_rollout_decision(scope_key, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_learner_memory (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    memory_key VARCHAR(128) NOT NULL,
    memory_value TEXT NOT NULL DEFAULT '',
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    source_problem_id BIGINT,
    expires_at TIMESTAMPTZ,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_learner_memory_user_active
    ON ai_learner_memory(user_id, enabled, expires_at, updated_at DESC);
