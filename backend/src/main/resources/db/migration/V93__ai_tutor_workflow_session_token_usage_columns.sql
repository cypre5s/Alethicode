-- V93：补齐 ai_tutor_workflow_session 的 token / 上下文用量列。
--
-- 历史 V87 schema slot 早期被 learning_health_summary_view 占用，导致
-- 后续把同一版本号改为 ai_tutor_session_token_usage 时 Flyway 已经记入
-- success=true 但实际表里没有 tokens_used / tokens_limit / model_name。
-- 结果：GET /api/ai/tutor-workflow-sessions/{id}/usage 在
-- InternalAITutorToolServiceImpl#getSessionUsage 直接抛 SQLException → 500。
--
-- 修复必须满足：
--   * 不修改 V87 已落地的 checksum
--   * 在所有缺列环境（生产 / 本地 / CI）幂等补齐
--   * 与 V87 字段定义、默认值完全一致，避免再次出现表列漂移

ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS tokens_used  BIGINT       NOT NULL DEFAULT 0;

ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS tokens_limit BIGINT       NOT NULL DEFAULT 0;

ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS model_name   VARCHAR(120) NOT NULL DEFAULT '';
