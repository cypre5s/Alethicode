ALTER TABLE ai_learner_memory
    ADD COLUMN IF NOT EXISTS memory_type VARCHAR(64) NOT NULL DEFAULT 'generic',
    ADD COLUMN IF NOT EXISTS memory_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(64) NOT NULL DEFAULT 'legacy',
    ADD COLUMN IF NOT EXISTS last_recalled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS memory_embedding VECTOR(16);

ALTER TABLE ai_learner_notebook
    ADD COLUMN IF NOT EXISTS notebook_summary TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS notebook_embedding VECTOR(16);

CREATE UNIQUE INDEX IF NOT EXISTS idx_ai_learner_memory_user_key
    ON ai_learner_memory(user_id, memory_key);

CREATE INDEX IF NOT EXISTS idx_ai_learner_memory_user_type_active
    ON ai_learner_memory(user_id, memory_type, enabled, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_learner_notebook_user_update
    ON ai_learner_notebook(user_id, update_time DESC);
