-- ============================================================
-- V37: Unify error taxonomy across monitoring, notebook, events
-- ============================================================

-- 1. student_monitoring_snapshot: split single "status" column into
--    activity_status (real-time activity) + error_taxonomy (teaching classification)

ALTER TABLE student_monitoring_snapshot
    ADD COLUMN IF NOT EXISTS activity_status VARCHAR(20) NOT NULL DEFAULT 'offline',
    ADD COLUMN IF NOT EXISTS error_taxonomy  VARCHAR(64);

UPDATE student_monitoring_snapshot SET
    activity_status = CASE status
        WHEN 'compile_error'  THEN 'abnormal'
        WHEN 'runtime_error'  THEN 'abnormal'
        WHEN 'infinite_loop'  THEN 'abnormal'
        WHEN 'typing'         THEN 'typing'
        WHEN 'running'        THEN 'running'
        WHEN 'idle'           THEN 'idle'
        WHEN 'submitted'      THEN 'submitted'
        WHEN 'completed'      THEN 'completed'
        WHEN 'coding'         THEN 'typing'
        WHEN 'active'         THEN 'typing'
        WHEN 'online'         THEN 'idle'
        ELSE 'offline'
    END,
    error_taxonomy = CASE status
        WHEN 'compile_error'  THEN 'syntax_error'
        WHEN 'runtime_error'  THEN 'runtime_error'
        WHEN 'infinite_loop'  THEN 'performance'
        ELSE NULL
    END;

ALTER TABLE student_monitoring_snapshot DROP COLUMN IF EXISTS status;

CREATE INDEX IF NOT EXISTS idx_sms_activity_status
    ON student_monitoring_snapshot(classroom_id, activity_status);

CREATE INDEX IF NOT EXISTS idx_sms_error_taxonomy
    ON student_monitoring_snapshot(classroom_id, error_taxonomy)
    WHERE error_taxonomy IS NOT NULL;

-- 2. ai_learner_notebook: rename error_category -> error_taxonomy, backfill canonical values

ALTER TABLE ai_learner_notebook RENAME COLUMN error_category TO error_taxonomy;

UPDATE ai_learner_notebook SET error_taxonomy = CASE error_taxonomy
    WHEN 'compile_error'  THEN 'syntax_error'
    WHEN 'compile'        THEN 'syntax_error'
    WHEN 'syntax'         THEN 'syntax_error'
    WHEN 'invalid_syntax' THEN 'syntax_error'
    WHEN 'wrong_answer'   THEN 'logic_error'
    WHEN 'logic'          THEN 'logic_error'
    WHEN 'runtime'        THEN 'runtime_error'
    WHEN 'time_limit'     THEN 'performance'
    WHEN 'memory_limit'   THEN 'performance'
    WHEN 'system_error'   THEN 'unknown'
    WHEN 'unknown_error'  THEN 'unknown'
    WHEN 'type_error'     THEN 'name_or_type_error'
    WHEN 'name_error'     THEN 'name_or_type_error'
    WHEN 'value_error'    THEN 'name_or_type_error'
    WHEN 'boundary'       THEN 'boundary_condition'
    WHEN 'boundary_error' THEN 'boundary_condition'
    WHEN 'index_error'    THEN 'boundary_condition'
    WHEN 'timeout'        THEN 'performance'
    WHEN 'memory_error'   THEN 'performance'
    ELSE COALESCE(NULLIF(error_taxonomy, ''), 'unknown')
END;

-- 3. ai_learning_event: promote first-class columns from extra_data

ALTER TABLE ai_learning_event
    ADD COLUMN IF NOT EXISTS error_taxonomy VARCHAR(64),
    ADD COLUMN IF NOT EXISTS root_cause     TEXT,
    ADD COLUMN IF NOT EXISTS detector_name  VARCHAR(128);

UPDATE ai_learning_event SET
    error_taxonomy = extra_data ->> 'error_category',
    root_cause     = extra_data ->> 'root_cause',
    detector_name  = extra_data ->> 'detector_name'
WHERE extra_data ->> 'error_category' IS NOT NULL
   OR extra_data ->> 'root_cause'     IS NOT NULL
   OR extra_data ->> 'detector_name'  IS NOT NULL;

UPDATE ai_learning_event SET error_taxonomy = CASE error_taxonomy
    WHEN 'compile_error'  THEN 'syntax_error'
    WHEN 'compile'        THEN 'syntax_error'
    WHEN 'syntax'         THEN 'syntax_error'
    WHEN 'wrong_answer'   THEN 'logic_error'
    WHEN 'logic'          THEN 'logic_error'
    WHEN 'runtime'        THEN 'runtime_error'
    WHEN 'time_limit'     THEN 'performance'
    WHEN 'memory_limit'   THEN 'performance'
    WHEN 'system_error'   THEN 'unknown'
    WHEN 'unknown_error'  THEN 'unknown'
    WHEN 'type_error'     THEN 'name_or_type_error'
    WHEN 'name_error'     THEN 'name_or_type_error'
    ELSE error_taxonomy
END
WHERE error_taxonomy IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ai_learning_event_error_taxonomy
    ON ai_learning_event(user_id, error_taxonomy)
    WHERE error_taxonomy IS NOT NULL;

-- 4. New tables for error review packages

CREATE TABLE IF NOT EXISTS ai_error_review_package (
    id              VARCHAR(64) PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    error_taxonomy  VARCHAR(64) NOT NULL,
    evidence_summary JSONB      NOT NULL DEFAULT '{}'::jsonb,
    problem_count   INTEGER     NOT NULL DEFAULT 0,
    completed_count INTEGER     NOT NULL DEFAULT 0,
    mastery_reached BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_error_review_package_user
    ON ai_error_review_package(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_error_review_package_taxonomy
    ON ai_error_review_package(user_id, error_taxonomy);

CREATE TABLE IF NOT EXISTS ai_error_review_problem (
    id          VARCHAR(64) PRIMARY KEY,
    package_id  VARCHAR(64) NOT NULL REFERENCES ai_error_review_package(id) ON DELETE CASCADE,
    problem_id  BIGINT      NOT NULL REFERENCES problem(id) ON DELETE CASCADE,
    sequence    INTEGER     NOT NULL DEFAULT 0,
    submitted   BOOLEAN     NOT NULL DEFAULT FALSE,
    is_correct  BOOLEAN,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_error_review_problem_package
    ON ai_error_review_problem(package_id, sequence);
