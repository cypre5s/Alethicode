ALTER TABLE language_pack_problem_generation_log
    ADD COLUMN IF NOT EXISTS materialized_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_lp_problem_gen_log_materialized
    ON language_pack_problem_generation_log(init_task_id, materialized_at);

COMMENT ON COLUMN language_pack_problem_generation_log.materialized_at
    IS 'judge 跑过 reference_solution + 物化 outputs 的时间戳；NULL 表示未物化';
