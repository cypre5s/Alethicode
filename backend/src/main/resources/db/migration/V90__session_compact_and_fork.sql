-- V90: compact / fork 支撑列
--
-- compact_count: 记录每个 session 被压缩的次数（统计 + 防滥用）
-- parent_session_id / fork_from_message_id: fork 会话分叉的来源追溯

ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS compact_count         INT         NOT NULL DEFAULT 0;

ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS parent_session_id     VARCHAR(64);

ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS fork_from_message_id  BIGINT;

ALTER TABLE language_pack_chat_session
    ADD COLUMN IF NOT EXISTS compact_count         INT         NOT NULL DEFAULT 0;

ALTER TABLE language_pack_chat_session
    ADD COLUMN IF NOT EXISTS parent_session_id     BIGINT;

ALTER TABLE language_pack_chat_session
    ADD COLUMN IF NOT EXISTS fork_from_message_id  BIGINT;
