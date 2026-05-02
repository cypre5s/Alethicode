-- V20: Judge Server Monitoring Tables
-- Adds metric snapshot, minute rollup, task rollup, event, and alert tables
-- for the judge server monitoring dashboard.

-- Extend judge_server with latest snapshot fields
ALTER TABLE judge_server
    ADD COLUMN IF NOT EXISTS agent_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS status_reason VARCHAR(128),
    ADD COLUMN IF NOT EXISTS heartbeat_lag_seconds DOUBLE PRECISION DEFAULT 0,
    ADD COLUMN IF NOT EXISTS available_slots INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS running_tasks INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS queued_tasks INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS filesystem_usage_ratio DOUBLE PRECISION DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cgroup_cpu_throttled_ratio DOUBLE PRECISION DEFAULT 0,
    ADD COLUMN IF NOT EXISTS queue_wait_p95_seconds DOUBLE PRECISION DEFAULT 0,
    ADD COLUMN IF NOT EXISTS end_to_end_p95_seconds DOUBLE PRECISION DEFAULT 0,
    ADD COLUMN IF NOT EXISTS security_incident_total_1h INTEGER DEFAULT 0;

-- Raw metric snapshot (10s granularity, retained 48h)
CREATE TABLE IF NOT EXISTS judge_server_metric_snapshot (
    id BIGSERIAL PRIMARY KEY,
    judge_server_id BIGINT NOT NULL REFERENCES judge_server(id) ON DELETE CASCADE,
    captured_at TIMESTAMPTZ NOT NULL,
    payload_version INTEGER NOT NULL DEFAULT 2,

    cpu_usage_ratio DOUBLE PRECISION,
    cpu_load_1 DOUBLE PRECISION,
    cpu_load_5 DOUBLE PRECISION,
    cpu_load_15 DOUBLE PRECISION,
    cpu_iowait_ratio DOUBLE PRECISION,
    memory_total_bytes BIGINT,
    memory_available_bytes BIGINT,
    memory_usage_ratio DOUBLE PRECISION,
    swap_total_bytes BIGINT,
    swap_used_bytes BIGINT,
    swap_usage_ratio DOUBLE PRECISION,
    filesystem_total_bytes BIGINT,
    filesystem_available_bytes BIGINT,
    filesystem_usage_ratio DOUBLE PRECISION,
    filesystem_inode_usage_ratio DOUBLE PRECISION,
    disk_read_bytes_per_second DOUBLE PRECISION,
    disk_write_bytes_per_second DOUBLE PRECISION,
    disk_read_iops DOUBLE PRECISION,
    disk_write_iops DOUBLE PRECISION,
    disk_await_seconds DOUBLE PRECISION,
    network_receive_bytes_per_second DOUBLE PRECISION,
    network_transmit_bytes_per_second DOUBLE PRECISION,
    network_receive_drop_per_second DOUBLE PRECISION,
    network_transmit_drop_per_second DOUBLE PRECISION,
    pressure_cpu_waiting_ratio DOUBLE PRECISION,
    pressure_memory_waiting_ratio DOUBLE PRECISION,
    pressure_io_waiting_ratio DOUBLE PRECISION,

    running_tasks INTEGER,
    queued_tasks INTEGER,
    available_slots INTEGER,
    compile_in_progress INTEGER,
    run_in_progress INTEGER,
    spj_in_progress INTEGER,
    cleanup_in_progress INTEGER,

    cgroup_cpu_usage_ratio DOUBLE PRECISION,
    cgroup_cpu_throttled_ratio DOUBLE PRECISION,
    cgroup_memory_working_set_bytes BIGINT,
    cgroup_memory_rss_bytes BIGINT,
    cgroup_memory_cache_bytes BIGINT,
    cgroup_pids_current INTEGER,
    cgroup_pids_limit INTEGER,
    cgroup_oom_total BIGINT,
    cgroup_fs_reads_bytes_per_second DOUBLE PRECISION,
    cgroup_fs_writes_bytes_per_second DOUBLE PRECISION
);

CREATE INDEX IF NOT EXISTS idx_metric_snapshot_server_time
    ON judge_server_metric_snapshot(judge_server_id, captured_at DESC);
CREATE INDEX IF NOT EXISTS idx_metric_snapshot_captured_at
    ON judge_server_metric_snapshot(captured_at DESC);

-- Minute rollup (1m granularity, retained 30d)
CREATE TABLE IF NOT EXISTS judge_server_metric_rollup_minute (
    id BIGSERIAL PRIMARY KEY,
    judge_server_id BIGINT NOT NULL REFERENCES judge_server(id) ON DELETE CASCADE,
    window_start TIMESTAMPTZ NOT NULL,

    cpu_usage_ratio_avg DOUBLE PRECISION,
    cpu_usage_ratio_max DOUBLE PRECISION,
    memory_usage_ratio_avg DOUBLE PRECISION,
    memory_usage_ratio_max DOUBLE PRECISION,
    filesystem_usage_ratio_avg DOUBLE PRECISION,
    disk_await_seconds_avg DOUBLE PRECISION,
    disk_await_seconds_max DOUBLE PRECISION,
    network_receive_bytes_per_second_avg DOUBLE PRECISION,
    network_transmit_bytes_per_second_avg DOUBLE PRECISION,

    queue_wait_p50_seconds DOUBLE PRECISION,
    queue_wait_p95_seconds DOUBLE PRECISION,
    queue_wait_p99_seconds DOUBLE PRECISION,
    compile_p50_seconds DOUBLE PRECISION,
    compile_p95_seconds DOUBLE PRECISION,
    run_p50_seconds DOUBLE PRECISION,
    run_p95_seconds DOUBLE PRECISION,
    end_to_end_p50_seconds DOUBLE PRECISION,
    end_to_end_p95_seconds DOUBLE PRECISION,
    end_to_end_p99_seconds DOUBLE PRECISION,

    throughput INTEGER DEFAULT 0,
    error_ratio DOUBLE PRECISION DEFAULT 0,

    running_tasks_avg DOUBLE PRECISION,
    queued_tasks_avg DOUBLE PRECISION,
    available_slots_avg DOUBLE PRECISION,

    cgroup_cpu_throttled_ratio_avg DOUBLE PRECISION,
    cgroup_cpu_throttled_ratio_max DOUBLE PRECISION,

    UNIQUE(judge_server_id, window_start)
);

CREATE INDEX IF NOT EXISTS idx_rollup_minute_server_window
    ON judge_server_metric_rollup_minute(judge_server_id, window_start DESC);

-- Task distribution rollup per minute
CREATE TABLE IF NOT EXISTS judge_server_task_rollup_minute (
    id BIGSERIAL PRIMARY KEY,
    judge_server_id BIGINT NOT NULL REFERENCES judge_server(id) ON DELETE CASCADE,
    window_start TIMESTAMPTZ NOT NULL,
    language VARCHAR(32),
    result VARCHAR(32),
    task_count INTEGER NOT NULL DEFAULT 0,
    queue_wait_p95_seconds DOUBLE PRECISION,
    compile_p95_seconds DOUBLE PRECISION,
    run_p95_seconds DOUBLE PRECISION,
    memory_peak_p95_bytes BIGINT
);

CREATE INDEX IF NOT EXISTS idx_task_rollup_server_window
    ON judge_server_task_rollup_minute(judge_server_id, window_start DESC);

-- Event log (retained 180d)
CREATE TABLE IF NOT EXISTS judge_server_event (
    id BIGSERIAL PRIMARY KEY,
    judge_server_id BIGINT NOT NULL REFERENCES judge_server(id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    message TEXT,
    details_json JSONB,
    dedup_key VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_event_server_occurred
    ON judge_server_event(judge_server_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_event_severity_occurred
    ON judge_server_event(severity, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_event_type_occurred
    ON judge_server_event(event_type, occurred_at DESC);

-- Alert state
CREATE TABLE IF NOT EXISTS judge_server_alert_state (
    id BIGSERIAL PRIMARY KEY,
    judge_server_id BIGINT NOT NULL REFERENCES judge_server(id) ON DELETE CASCADE,
    alert_key VARCHAR(128) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    last_value DOUBLE PRECISION,
    UNIQUE(judge_server_id, alert_key, status)
);

CREATE INDEX IF NOT EXISTS idx_alert_state_server_status
    ON judge_server_alert_state(judge_server_id, status);
CREATE INDEX IF NOT EXISTS idx_alert_state_severity_status
    ON judge_server_alert_state(severity, status);
