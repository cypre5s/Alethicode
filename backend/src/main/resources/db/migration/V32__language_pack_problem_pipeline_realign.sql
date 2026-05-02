-- V32: realign language-pack initialization around courseware units and problem packages

ALTER TABLE language_pack_init_task
    ADD COLUMN IF NOT EXISTS coverage_report_json TEXT NOT NULL DEFAULT '{}';

UPDATE language_pack_init_task t
SET stage = CASE
    WHEN t.stage = 'examples_ready'
        AND EXISTS (
            SELECT 1
            FROM language_pack_problem_generation_log g
            WHERE g.init_task_id = t.id
        ) THEN 'problem_packages_ready'
    WHEN t.stage = 'examples_ready' THEN 'units_ready'
    ELSE t.stage
END
WHERE t.stage = 'examples_ready';

ALTER TABLE language_pack_init_task
    DROP CONSTRAINT IF EXISTS chk_init_task_stage;

ALTER TABLE language_pack_init_task
    ADD CONSTRAINT chk_init_task_stage CHECK (
        stage IN ('created', 'normalizing', 'parsing', 'kc_ready',
                  'units_ready', 'problem_packages_ready',
                  'problems_validated', 'published', 'failed')
    );

ALTER TABLE language_pack_example
    ADD COLUMN IF NOT EXISTS document_id BIGINT REFERENCES language_pack_document(id) ON DELETE SET NULL;

ALTER TABLE language_pack_example
    ADD COLUMN IF NOT EXISTS unit_type VARCHAR(32) NOT NULL DEFAULT 'demo';

ALTER TABLE language_pack_example
    ADD COLUMN IF NOT EXISTS source_title TEXT NOT NULL DEFAULT '';

ALTER TABLE language_pack_example
    ADD COLUMN IF NOT EXISTS oj_convertible BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE language_pack_example
    ADD COLUMN IF NOT EXISTS oj_block_reason TEXT NOT NULL DEFAULT '';

ALTER TABLE language_pack_example
    ADD COLUMN IF NOT EXISTS source_signature TEXT NOT NULL DEFAULT '';

ALTER TABLE language_pack_problem_generation_log
    ADD COLUMN IF NOT EXISTS problem_package_json TEXT NOT NULL DEFAULT '{}';

ALTER TABLE language_pack_problem_generation_log
    ADD COLUMN IF NOT EXISTS source_example_ids_json TEXT NOT NULL DEFAULT '[]';

ALTER TABLE language_pack_problem_generation_log
    ADD COLUMN IF NOT EXISTS source_signature TEXT NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS language_pack_init_artifact (
    id            BIGSERIAL PRIMARY KEY,
    task_id        BIGINT      NOT NULL REFERENCES language_pack_init_task(id) ON DELETE CASCADE,
    artifact_type  VARCHAR(64) NOT NULL,
    source_stage   VARCHAR(32) NOT NULL,
    content_json   TEXT        NOT NULL DEFAULT '{}',
    content_markdown TEXT      NOT NULL DEFAULT '',
    content_hash   VARCHAR(128) NOT NULL DEFAULT '',
    create_time    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lp_init_artifact_task ON language_pack_init_artifact(task_id);
CREATE INDEX IF NOT EXISTS idx_lp_init_artifact_type ON language_pack_init_artifact(artifact_type);

CREATE TABLE IF NOT EXISTS language_pack_init_agent_run (
    id                 BIGSERIAL PRIMARY KEY,
    task_id            BIGINT      NOT NULL REFERENCES language_pack_init_task(id) ON DELETE CASCADE,
    agent_name         VARCHAR(128) NOT NULL,
    source_stage       VARCHAR(32) NOT NULL,
    model_name         VARCHAR(128) NOT NULL DEFAULT '',
    prompt_version     VARCHAR(128) NOT NULL DEFAULT '',
    input_artifact_hash VARCHAR(128) NOT NULL DEFAULT '',
    output_artifact_hash VARCHAR(128) NOT NULL DEFAULT '',
    status             VARCHAR(32) NOT NULL DEFAULT 'running',
    failure_reason     TEXT,
    create_time        TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_lp_init_agent_run_status CHECK (status IN ('running', 'completed', 'failed'))
);

CREATE INDEX IF NOT EXISTS idx_lp_init_agent_run_task ON language_pack_init_agent_run(task_id);
CREATE INDEX IF NOT EXISTS idx_lp_init_agent_run_agent ON language_pack_init_agent_run(agent_name);

UPDATE language_pack_example
SET source_title = CASE
    WHEN source_title <> '' THEN source_title
    ELSE COALESCE(NULLIF(split_part(raw_text, E'\n', 1), ''), raw_text)
END
WHERE source_title = '';

UPDATE language_pack_example
SET unit_type = CASE
    WHEN raw_text LIKE '%上机作业%' THEN 'assignment'
    WHEN raw_text LIKE '%练习%' THEN 'exercise'
    WHEN raw_text LIKE '%示例%' OR raw_text LIKE '%举例%' THEN 'worked_example'
    WHEN raw_text LIKE '%程序设计实例%' THEN 'worked_example'
    ELSE 'demo'
END
WHERE unit_type = 'demo';

UPDATE language_pack_example
SET oj_convertible = CASE
    WHEN raw_text LIKE '%上机作业%' THEN true
    WHEN raw_text LIKE '%练习%' THEN true
    WHEN raw_text LIKE '%示例%' THEN true
    WHEN raw_text LIKE '%举例%' THEN true
    WHEN raw_text LIKE '%程序设计实例%' THEN true
    ELSE oj_convertible
END;

UPDATE language_pack_example
SET oj_block_reason = CASE
    WHEN oj_convertible THEN ''
    ELSE 'not_oj_convertible_from_legacy_data'
END
WHERE oj_block_reason = '';

UPDATE language_pack_example
SET source_signature = CONCAT(
    COALESCE(source_title, ''),
    '#',
    COALESCE(page_range_start::text, ''),
    '-',
    COALESCE(page_range_end::text, ''),
    '#',
    COALESCE(unit_type, '')
)
WHERE source_signature = '';

UPDATE language_pack_problem_generation_log
SET source_example_ids_json = CASE
    WHEN example_id IS NULL THEN '[]'
    ELSE CONCAT('[', example_id::text, ']')
END
WHERE source_example_ids_json = '[]';

UPDATE language_pack_problem_generation_log
SET source_signature = CASE
    WHEN source_signature <> '' THEN source_signature
    WHEN example_id IS NULL THEN CONCAT('generation-log#', id::text)
    ELSE CONCAT('example#', example_id::text)
END
WHERE source_signature = '';
