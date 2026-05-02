CREATE INDEX IF NOT EXISTS idx_ai_learning_event_detector_event_time
    ON ai_learning_event ((extra_data ->> 'detector_name'), event_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_learner_notebook_user_problem_category_active
    ON ai_learner_notebook (user_id, problem_id, error_category, update_time DESC)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_submission_user_problem_result_time
    ON submission (user_id, problem_id, result, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_student_monitoring_snapshot_classroom_user_time
    ON student_monitoring_snapshot (classroom_id, user_id, snapshot_time DESC);

CREATE INDEX IF NOT EXISTS idx_problem_ai_generated_visible_time
    ON problem (is_ai_generated, visible, create_time DESC);
