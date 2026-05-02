ALTER TABLE language_pack_init_task
    ADD COLUMN IF NOT EXISTS active_step_key VARCHAR(32),
    ADD COLUMN IF NOT EXISTS active_status VARCHAR(16) NOT NULL DEFAULT 'idle',
    ADD COLUMN IF NOT EXISTS active_message TEXT,
    ADD COLUMN IF NOT EXISTS progress_current INTEGER,
    ADD COLUMN IF NOT EXISTS progress_total INTEGER,
    ADD COLUMN IF NOT EXISTS active_started_at TIMESTAMPTZ;

UPDATE language_pack_init_task
SET active_status = 'idle'
WHERE active_status IS NULL OR active_status = '';

UPDATE language_pack_init_task
SET stage = 'problem_packages_ready'
WHERE stage = 'problem_gen';

ALTER TABLE language_pack_init_task
    DROP CONSTRAINT IF EXISTS chk_init_task_stage;

ALTER TABLE language_pack_init_task
    ADD CONSTRAINT chk_init_task_stage CHECK (
        stage IN ('created', 'normalizing', 'parsing', 'kc_ready',
                  'segments_ready', 'units_ready', 'oj_candidates_ready',
                  'problem_packages_ready', 'problems_validated', 'published', 'failed')
    );

ALTER TABLE language_pack_init_task
    DROP CONSTRAINT IF EXISTS chk_init_task_active_status;

ALTER TABLE language_pack_init_task
    ADD CONSTRAINT chk_init_task_active_status CHECK (
        active_status IN ('idle', 'running')
    );

