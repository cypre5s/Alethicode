-- V71: Faded Parsons + 错题本结构扩展 (ALETH-PLAN-2026-0427-FP01 Phase 1)

ALTER TABLE ai_learner_notebook
    ADD COLUMN IF NOT EXISTS entry_type                 VARCHAR(32) NOT NULL DEFAULT 'error',
    ADD COLUMN IF NOT EXISTS breakthrough_insight       TEXT,
    ADD COLUMN IF NOT EXISTS kc_ids                     JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS misconception_distribution JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_ai_notebook_entry_type
    ON ai_learner_notebook(user_id, entry_type, update_time DESC);

CREATE INDEX IF NOT EXISTS idx_ai_notebook_kc_ids
    ON ai_learner_notebook USING GIN (kc_ids);

CREATE TABLE IF NOT EXISTS ai_parsons_session (
    id                       VARCHAR(64)  PRIMARY KEY,
    user_id                  BIGINT       NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    problem_id               BIGINT       NOT NULL,
    workflow_session_id      VARCHAR(64),
    source_card_id           VARCHAR(64),
    previous_session_id      VARCHAR(64),
    fsrs_origin              VARCHAR(64),
    language                 VARCHAR(32)  NOT NULL DEFAULT 'Python3',
    fading_level             INTEGER      NOT NULL,
    mastery_snapshot         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    blocks                   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    distractors              JSONB        NOT NULL DEFAULT '[]'::jsonb,
    submitted_order          JSONB,
    submission_count         INTEGER      NOT NULL DEFAULT 0,
    judge_status             VARCHAR(16),
    walkthrough_text         TEXT,
    walkthrough_score        DOUBLE PRECISION,
    walkthrough_attempts     INTEGER      NOT NULL DEFAULT 0,
    breakthrough_notebook_id VARCHAR(64),
    create_time              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    finalized_at             TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_aps_user_problem
    ON ai_parsons_session(user_id, problem_id, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_aps_workflow_session
    ON ai_parsons_session(workflow_session_id);

CREATE INDEX IF NOT EXISTS idx_aps_fsrs_origin
    ON ai_parsons_session(fsrs_origin)
    WHERE fsrs_origin IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_aps_previous_session
    ON ai_parsons_session(previous_session_id)
    WHERE previous_session_id IS NOT NULL;
