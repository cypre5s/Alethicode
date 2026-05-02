-- V35: add resumable init batch runs for KC/example/problem stages

CREATE TABLE IF NOT EXISTS language_pack_init_batch_run (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES language_pack_init_task(id) ON DELETE CASCADE,
    stage_name VARCHAR(64) NOT NULL,
    document_id BIGINT NULL REFERENCES language_pack_document(id) ON DELETE CASCADE,
    chapter_index INTEGER NOT NULL DEFAULT 0,
    batch_start_page INTEGER NOT NULL DEFAULT 0,
    batch_end_page INTEGER NOT NULL DEFAULT 0,
    requested_window_size INTEGER NOT NULL DEFAULT 0,
    effective_window_size INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'running',
    attempt_no INTEGER NOT NULL DEFAULT 1,
    input_hash VARCHAR(64) NOT NULL DEFAULT '',
    output_hash VARCHAR(64) NOT NULL DEFAULT '',
    failure_reason TEXT NOT NULL DEFAULT '',
    output_json TEXT NOT NULL DEFAULT '{}',
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_lp_init_batch_run_status CHECK (
        status IN ('running', 'completed', 'failed', 'reused', 'split')
    )
);

CREATE INDEX IF NOT EXISTS idx_lp_init_batch_run_task_stage
    ON language_pack_init_batch_run(task_id, stage_name);

CREATE INDEX IF NOT EXISTS idx_lp_init_batch_run_scope
    ON language_pack_init_batch_run(task_id, stage_name, document_id, chapter_index, batch_start_page, batch_end_page);
