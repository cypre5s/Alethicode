-- Coding Lens V85：题面专业化重写的缓存表
--   问题 × 专业 → 经过 critic 校验的专业语境题面变体
--   plan 2.3 节强约束：判题不动；不引入 test_case_id；不允许修改 problem 表 IO schema
--   domain_metaphor 仅供前端展示从原变量名到专业语义的字典映射

CREATE TABLE IF NOT EXISTS problem_domain_variant (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE,
    major_code VARCHAR(64) NOT NULL,
    title TEXT NOT NULL,
    description_md TEXT NOT NULL,
    sample_input_text TEXT,
    sample_output_text TEXT,
    domain_metaphor JSONB NOT NULL DEFAULT '{}'::jsonb,
    semantic_drift_score DOUBLE PRECISION,
    reflection_passed BOOLEAN NOT NULL DEFAULT TRUE,
    locked_for_exam BOOLEAN NOT NULL DEFAULT FALSE,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    validated_by BIGINT REFERENCES "user"(id) ON DELETE SET NULL,
    UNIQUE (problem_id, major_code)
);

CREATE INDEX IF NOT EXISTS idx_problem_domain_variant_problem
    ON problem_domain_variant(problem_id);

CREATE INDEX IF NOT EXISTS idx_problem_domain_variant_major
    ON problem_domain_variant(major_code) WHERE reflection_passed = TRUE;
