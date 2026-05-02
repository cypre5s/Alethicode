CREATE INDEX IF NOT EXISTS idx_student_monitoring_snapshot_classroom_status_time
    ON student_monitoring_snapshot (classroom_id, status, snapshot_time DESC);

CREATE INDEX IF NOT EXISTS idx_student_monitoring_snapshot_classroom_problem_user_time
    ON student_monitoring_snapshot (classroom_id, classroom_problem_id, user_id, snapshot_time DESC);

CREATE INDEX IF NOT EXISTS idx_submission_problem_user_time
    ON submission (problem_id, user_id, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_submission_problem_result_time
    ON submission (problem_id, result, create_time DESC);
