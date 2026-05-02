-- V27: language_pack problem generation, validation, and publish tracking

CREATE TABLE IF NOT EXISTS language_pack_problem_generation_log (
    id                  BIGSERIAL    PRIMARY KEY,
    init_task_id        BIGINT       NOT NULL REFERENCES language_pack_init_task(id) ON DELETE CASCADE,
    language_pack_id    BIGINT       NOT NULL REFERENCES language_pack(id) ON DELETE CASCADE,
    kc_id               BIGINT       REFERENCES language_pack_kc(id) ON DELETE SET NULL,
    example_id          BIGINT       REFERENCES language_pack_example(id) ON DELETE SET NULL,
    candidate_title     TEXT         NOT NULL DEFAULT '',
    candidate_body      TEXT         NOT NULL DEFAULT '',
    candidate_input_description  TEXT NOT NULL DEFAULT '',
    candidate_output_description TEXT NOT NULL DEFAULT '',
    candidate_samples_json       TEXT NOT NULL DEFAULT '[]',
    reference_solution  TEXT         NOT NULL DEFAULT '',
    test_cases_json     TEXT         NOT NULL DEFAULT '[]',
    validation_status   VARCHAR(32)  NOT NULL DEFAULT 'pending',
    validation_message  TEXT,
    create_time         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_gen_log_status CHECK (
        validation_status IN ('pending', 'validating', 'passed', 'failed')
    )
);

CREATE INDEX IF NOT EXISTS idx_lp_gen_log_task ON language_pack_problem_generation_log(init_task_id);
CREATE INDEX IF NOT EXISTS idx_lp_gen_log_status ON language_pack_problem_generation_log(validation_status);

CREATE TABLE IF NOT EXISTS language_pack_problem_mapping (
    id                  BIGSERIAL    PRIMARY KEY,
    language_pack_id    BIGINT       NOT NULL REFERENCES language_pack(id) ON DELETE CASCADE,
    problem_id          BIGINT       NOT NULL,
    generation_log_id   BIGINT       REFERENCES language_pack_problem_generation_log(id) ON DELETE SET NULL,
    create_time         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (language_pack_id, problem_id)
);

CREATE INDEX IF NOT EXISTS idx_lp_problem_map_pack ON language_pack_problem_mapping(language_pack_id);
CREATE INDEX IF NOT EXISTS idx_lp_problem_map_problem ON language_pack_problem_mapping(problem_id);
