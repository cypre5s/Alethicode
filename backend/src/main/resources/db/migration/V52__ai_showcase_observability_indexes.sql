-- AI 展示 & 可观测性查询索引
-- 为 Agent 概览、Trace 时间线、质量评测等 Admin API 提速

CREATE INDEX IF NOT EXISTS idx_ai_workflow_event_type_time
    ON ai_workflow_event (event_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_workflow_event_agent_name
    ON ai_workflow_event ((event_data->>'agent_name'))
    WHERE event_data->>'agent_name' IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ai_workflow_event_tool_name
    ON ai_workflow_event ((event_data->>'tool_name'))
    WHERE event_data->>'tool_name' IS NOT NULL;
