-- V22: language_pack domain core tables
-- language_pack: versioned language teaching pack metadata
-- language_pack_init_task: initialization task state machine
-- language_pack_init_stage_log: per-stage execution log

CREATE TABLE IF NOT EXISTS language_pack (
    id              BIGSERIAL PRIMARY KEY,
    slug            VARCHAR(128) NOT NULL,
    version         INTEGER      NOT NULL DEFAULT 1,
    name            VARCHAR(256) NOT NULL,
    primary_language VARCHAR(64) NOT NULL,
    description     TEXT         NOT NULL DEFAULT '',
    status          VARCHAR(32)  NOT NULL DEFAULT 'draft',
    document_count  INTEGER      NOT NULL DEFAULT 0,
    page_count      INTEGER      NOT NULL DEFAULT 0,
    chapter_count   INTEGER      NOT NULL DEFAULT 0,
    kc_count        INTEGER      NOT NULL DEFAULT 0,
    example_count   INTEGER      NOT NULL DEFAULT 0,
    problem_count   INTEGER      NOT NULL DEFAULT 0,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (slug, version)
);

CREATE TABLE IF NOT EXISTS language_pack_init_task (
    id                BIGSERIAL    PRIMARY KEY,
    language_pack_id  BIGINT       NOT NULL REFERENCES language_pack(id) ON DELETE CASCADE,
    stage             VARCHAR(32)  NOT NULL DEFAULT 'created',
    target_problem_count INTEGER   NOT NULL DEFAULT 10,
    enable_objective_questions BOOLEAN NOT NULL DEFAULT false,
    failure_reason    TEXT,
    create_time       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_time       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_init_task_stage CHECK (
        stage IN ('created', 'normalizing', 'parsing', 'kc_ready',
                  'examples_ready', 'problems_validated', 'published', 'failed')
    )
);

CREATE INDEX IF NOT EXISTS idx_init_task_language_pack ON language_pack_init_task(language_pack_id);
CREATE INDEX IF NOT EXISTS idx_init_task_stage ON language_pack_init_task(stage);

CREATE TABLE IF NOT EXISTS language_pack_init_stage_log (
    id           BIGSERIAL    PRIMARY KEY,
    task_id      BIGINT       NOT NULL REFERENCES language_pack_init_task(id) ON DELETE CASCADE,
    from_stage   VARCHAR(32)  NOT NULL,
    to_stage     VARCHAR(32)  NOT NULL,
    message      TEXT         NOT NULL DEFAULT '',
    create_time  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_stage_log_task ON language_pack_init_stage_log(task_id);
