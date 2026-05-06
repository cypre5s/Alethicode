-- V87: AI Tutor session token / context usage projection
--
-- Phase 1 sprint 引入 frontend ContextUsageBar 组件，需要后端在 ai_tutor_workflow_session
-- 上记录每个会话的累计 token 使用量、上限以及最近一次推理使用的模型名。
--
-- 这一次只做 schema 预留：列以 IDEMPOTENT ADD COLUMN IF NOT EXISTS 形式追加，缺省值
-- 0/NULL 让组件以「数据未接入」自动隐藏，等 Phase 2 sprint 把 LangGraph 实际推理 token
-- 计量回写到这三列后，前端会自动显示彩条。
--
-- 字段说明：
--   tokens_used   累计已消耗 token（包含 prompt + completion）；非负整数
--   tokens_limit  当前模型上下文窗口大小，前端用作彩条的分母；0 表示未接入
--   model_name    最近一次推理使用的模型短名，例如 'deepseek-chat' / 'gpt-4o-mini'
--
-- 与 V65 unified chat context、V64 failure projection 同表，由 service 读 3 列拼成 usage
-- DTO 给前端 / 给 tutor-graph 内部 retrieval 链路作为提示。

ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS tokens_used  BIGINT       NOT NULL DEFAULT 0;

ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS tokens_limit BIGINT       NOT NULL DEFAULT 0;

ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS model_name   VARCHAR(120) NOT NULL DEFAULT '';
