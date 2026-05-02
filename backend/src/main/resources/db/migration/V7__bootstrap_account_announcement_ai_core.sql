ALTER TABLE "user"
    ADD COLUMN IF NOT EXISTS password_hash TEXT,
    ADD COLUMN IF NOT EXISTS email TEXT,
    ADD COLUMN IF NOT EXISTS reset_password_token TEXT,
    ADD COLUMN IF NOT EXISTS reset_password_token_expire_time TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS auth_token TEXT,
    ADD COLUMN IF NOT EXISTS two_factor_auth BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS tfa_token TEXT,
    ADD COLUMN IF NOT EXISTS session_keys JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS open_api BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS open_api_appkey TEXT;

CREATE INDEX IF NOT EXISTS idx_user_email ON "user" (lower(email));
CREATE INDEX IF NOT EXISTS idx_user_open_api_appkey ON "user" (open_api_appkey);

ALTER TABLE user_profile
    ADD COLUMN IF NOT EXISTS oi_problems_status JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS avatar TEXT NOT NULL DEFAULT '/public/avatar/default.png',
    ADD COLUMN IF NOT EXISTS blog TEXT,
    ADD COLUMN IF NOT EXISTS mood TEXT,
    ADD COLUMN IF NOT EXISTS github TEXT,
    ADD COLUMN IF NOT EXISTS school TEXT,
    ADD COLUMN IF NOT EXISTS major TEXT,
    ADD COLUMN IF NOT EXISTS language TEXT,
    ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'Student',
    ADD COLUMN IF NOT EXISTS accepted_number INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_score BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS submission_number INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS announcement (
    id BIGSERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    last_update_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    visible BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_announcement_visible_create_time
    ON announcement(visible, create_time DESC);

CREATE TABLE IF NOT EXISTS ai_dialogue_session (
    session_id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    problem_id BIGINT,
    current_stage TEXT NOT NULL DEFAULT 'reading',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    context_window JSONB NOT NULL DEFAULT '{}'::jsonb,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_dialogue_user_active
    ON ai_dialogue_session(user_id, is_active, update_time DESC);

CREATE TABLE IF NOT EXISTS ai_inference_task (
    task_id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    submission_id VARCHAR(64),
    session_id VARCHAR(64) REFERENCES ai_dialogue_session(session_id) ON DELETE SET NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    request_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    rag_hit BOOLEAN NOT NULL DEFAULT FALSE,
    rag_score DOUBLE PRECISION,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_task_user_create_time
    ON ai_inference_task(user_id, create_time DESC);
