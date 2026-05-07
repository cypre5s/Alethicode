-- V58：让索引对齐 tutor workflow 热点查询。
--
-- 迁移原因：
--   `InternalAITutorToolServiceImpl.loadLatestErrorContext` runs:
--       SELECT event_data FROM ai_tutor_workflow_event
--       WHERE session_id = :sid AND client_event = 'ERROR_FEEDBACK'
--       ORDER BY created_at DESC LIMIT 1
--   每次 tutor_graph 请求 `/internal/ai-tutor/learners/{id}/similar-errors` 时都会执行，
--   该请求会在每次 WA 提交后触发。
--
--   V57 added `idx_atwf_event_session_event_type (session_id, event_type, created_at DESC)`
--   当时误以为过滤列是 `event_type`。但 `services/tutor-graph/app/nodes/projection.py`
--   会把同一值同时写入 `event_type` 和 `client_event` 两列，而 Java
--   查询过滤的是 `client_event`。Postgres 不会为该谓词选择 V57 索引，
--   会回退到通用 `(session_id, created_at DESC)` 索引再过滤，
--   从而扫描活跃会话的所有事件。
--
--   因此新增 `(session_id, client_event, created_at DESC)` 专用部分索引，
--   让热点路径退化为单次索引扫描。V57 索引保留给管理员侧
--   后续可能按 `event_type` 过滤的聚合查询。

CREATE INDEX IF NOT EXISTS idx_atwf_event_session_client_event
    ON ai_tutor_workflow_event (session_id, client_event, created_at DESC)
    WHERE client_event IS NOT NULL;
