CREATE TABLE IF NOT EXISTS ai_generation_task (
    id VARCHAR(64) PRIMARY KEY,
    classroom_id VARCHAR(64) NOT NULL REFERENCES classroom(id) ON DELETE CASCADE,
    lesson_id VARCHAR(64) REFERENCES classroom_lesson(id) ON DELETE SET NULL,
    created_by_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'queued',
    question_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    counts JSONB NOT NULL DEFAULT '{}'::jsonb,
    total_requested INTEGER NOT NULL DEFAULT 0,
    generated_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT NOT NULL DEFAULT '',
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_generation_task_classroom_time
    ON ai_generation_task(classroom_id, create_time DESC);

CREATE TABLE IF NOT EXISTS ai_generated_problem (
    id VARCHAR(64) PRIMARY KEY,
    classroom_id VARCHAR(64) NOT NULL REFERENCES classroom(id) ON DELETE CASCADE,
    lesson_id VARCHAR(64) REFERENCES classroom_lesson(id) ON DELETE SET NULL,
    source_type VARCHAR(20) NOT NULL,
    source_pages JSONB NOT NULL DEFAULT '[]'::jsonb,
    question_type VARCHAR(20) NOT NULL DEFAULT 'coding',
    extracted_concepts JSONB NOT NULL DEFAULT '[]'::jsonb,
    difficulty_estimation VARCHAR(20),
    generated_problem_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    test_data_generator_code TEXT NOT NULL DEFAULT '',
    reference_solution_code TEXT NOT NULL DEFAULT '',
    validation_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    validation_log TEXT,
    test_cases_count INTEGER NOT NULL DEFAULT 0,
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    published_problem_id VARCHAR(64) REFERENCES classroom_problem(id) ON DELETE SET NULL,
    created_by_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_generated_problem_classroom_status
    ON ai_generated_problem(classroom_id, validation_status, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_ai_generated_problem_lesson
    ON ai_generated_problem(lesson_id);
