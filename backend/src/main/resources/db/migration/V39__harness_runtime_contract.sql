-- Phase 4 基础：Harness Runtime Contract 字段扩展
-- 为 ai_workflow_event 和 language_pack_chat_message 补充统一运行时 contract 字段

ALTER TABLE ai_workflow_event
    ADD COLUMN IF NOT EXISTS runtime_state   VARCHAR(32),
    ADD COLUMN IF NOT EXISTS trace_id        VARCHAR(64),
    ADD COLUMN IF NOT EXISTS failure_bucket  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS recovery_reason VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_ai_workflow_event_runtime_state
    ON ai_workflow_event (runtime_state)
    WHERE runtime_state IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ai_workflow_event_trace_id
    ON ai_workflow_event (trace_id)
    WHERE trace_id IS NOT NULL;

ALTER TABLE language_pack_chat_message
    ADD COLUMN IF NOT EXISTS trace_id        VARCHAR(64),
    ADD COLUMN IF NOT EXISTS runtime_state   VARCHAR(32),
    ADD COLUMN IF NOT EXISTS failure_bucket  VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_lp_chat_message_trace_id
    ON language_pack_chat_message (trace_id)
    WHERE trace_id IS NOT NULL;
