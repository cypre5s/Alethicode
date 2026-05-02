ALTER TABLE judge_server_metric_snapshot
    ADD COLUMN IF NOT EXISTS tasks_completed_total BIGINT,
    ADD COLUMN IF NOT EXISTS tasks_completed_per_minute DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS queue_wait_p50_seconds DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS queue_wait_p99_seconds DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS compile_p50_seconds DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS compile_p95_seconds DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS run_p50_seconds DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS run_p95_seconds DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS end_to_end_p50_seconds DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS end_to_end_p95_seconds DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS end_to_end_p99_seconds DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS system_error_ratio DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS timeout_ratio DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS memory_peak_p95_bytes BIGINT,
    ADD COLUMN IF NOT EXISTS heartbeat_reject_total BIGINT,
    ADD COLUMN IF NOT EXISTS auth_failure_total BIGINT,
    ADD COLUMN IF NOT EXISTS restart_total BIGINT,
    ADD COLUMN IF NOT EXISTS seccomp_violation_total BIGINT,
    ADD COLUMN IF NOT EXISTS output_limit_exceeded_total BIGINT,
    ADD COLUMN IF NOT EXISTS cleanup_failure_total BIGINT,
    ADD COLUMN IF NOT EXISTS workspace_leak_count INTEGER,
    ADD COLUMN IF NOT EXISTS workspace_usage_bytes BIGINT;

ALTER TABLE judge_server_metric_rollup_minute
    ADD COLUMN IF NOT EXISTS cpu_load_1_avg DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS cpu_load_5_avg DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS cpu_load_15_avg DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS swap_usage_ratio_avg DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS disk_read_iops_avg DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS disk_write_iops_avg DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS network_receive_drop_per_second_avg DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS network_transmit_drop_per_second_avg DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS pressure_cpu_waiting_ratio_avg DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS pressure_memory_waiting_ratio_avg DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS pressure_io_waiting_ratio_avg DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS cgroup_memory_working_set_bytes_avg DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS cgroup_pids_current_avg DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS auth_failure_total_max BIGINT,
    ADD COLUMN IF NOT EXISTS heartbeat_reject_total_max BIGINT,
    ADD COLUMN IF NOT EXISTS seccomp_violation_total_max BIGINT,
    ADD COLUMN IF NOT EXISTS cgroup_oom_total_max BIGINT,
    ADD COLUMN IF NOT EXISTS cleanup_failure_total_max BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_task_rollup_server_window_dimension
    ON judge_server_task_rollup_minute(
        judge_server_id,
        window_start,
        COALESCE(language, ''),
        COALESCE(result, '')
    );
