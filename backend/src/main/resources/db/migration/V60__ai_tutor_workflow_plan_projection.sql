ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS plan JSONB NOT NULL DEFAULT '{}'::JSONB;

ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS recommendation_reason TEXT NOT NULL DEFAULT '';
