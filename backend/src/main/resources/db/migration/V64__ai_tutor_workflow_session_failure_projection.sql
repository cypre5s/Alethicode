ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS failure_bucket VARCHAR(64);

ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS last_error TEXT NOT NULL DEFAULT '';
