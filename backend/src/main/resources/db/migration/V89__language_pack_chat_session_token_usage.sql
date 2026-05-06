-- V89: language_pack_chat_session token / context usage projection
--
-- Phase 1 sprint chat composer 把共享的 ContextUsageBar 同时挂在 AI 导学
-- 助手与课件问答页。AI 导学侧由 V87 在 ai_tutor_workflow_session 上加了
-- tokens_used / tokens_limit / model_name 三列；本迁移给课件问答的
-- language_pack_chat_session 表幂等加同样三列，让前端 / 后端 service 在两侧
-- 走同一份 SessionUsage DTO 与同一组 endpoint 模式。

ALTER TABLE language_pack_chat_session
    ADD COLUMN IF NOT EXISTS tokens_used  BIGINT       NOT NULL DEFAULT 0;

ALTER TABLE language_pack_chat_session
    ADD COLUMN IF NOT EXISTS tokens_limit BIGINT       NOT NULL DEFAULT 0;

ALTER TABLE language_pack_chat_session
    ADD COLUMN IF NOT EXISTS model_name   VARCHAR(120) NOT NULL DEFAULT '';
