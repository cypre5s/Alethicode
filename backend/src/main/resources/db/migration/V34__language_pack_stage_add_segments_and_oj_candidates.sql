-- V34: extend language-pack init task stages for segmentation and OJ candidate review

ALTER TABLE language_pack_init_task
    DROP CONSTRAINT IF EXISTS chk_init_task_stage;

ALTER TABLE language_pack_init_task
    ADD CONSTRAINT chk_init_task_stage CHECK (
        stage IN ('created', 'normalizing', 'parsing', 'kc_ready',
                  'segments_ready', 'units_ready', 'oj_candidates_ready',
                  'problem_packages_ready', 'problems_validated', 'published', 'failed')
    );
