CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

CREATE INDEX IF NOT EXISTS idx_submission_user_time
    ON submission (user_id, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_classroom_member_user_classroom_role
    ON classroom_member (user_id, classroom_id, role);

CREATE INDEX IF NOT EXISTS idx_classroom_assignment_submission_assignment_time
    ON classroom_assignment_submission (assignment_id, submit_time DESC);

CREATE INDEX IF NOT EXISTS idx_classroom_assignment_problem_submission_submission_status
    ON classroom_assignment_problem_submission (submission_id, judge_status);

CREATE INDEX IF NOT EXISTS idx_ai_learning_event_user_time
    ON ai_learning_event (user_id, created_at DESC);
