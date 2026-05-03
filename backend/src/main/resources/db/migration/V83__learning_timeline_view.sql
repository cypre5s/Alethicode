-- V83__learning_timeline_view.sql
-- L99 Sprint 01: 聚合 4 张事件表为统一的学习时间轴视图。
-- 不新建表；用 view 提供统一查询接口，业务查询走原表索引。

CREATE OR REPLACE VIEW v_learning_timeline AS
  SELECT
    user_id,
    'submission' AS event_kind,
    id           AS event_id,
    create_time  AS event_at,
    problem_id,
    result       AS payload_int,
    NULL::TEXT    AS payload_text,
    NULL::JSONB   AS payload_json
  FROM submission
  UNION ALL
  SELECT
    user_id,
    'memory'    AS event_kind,
    id::TEXT,
    created_at,
    source_problem_id,
    (confidence * 100)::INTEGER,
    memory_type,
    memory_payload
  FROM ai_learner_memory
  WHERE enabled = TRUE
  UNION ALL
  SELECT
    user_id,
    'ai_event' AS event_kind,
    id::TEXT,
    created_at,
    problem_id,
    NULL,
    event_type,
    NULL
  FROM ai_learning_event
  UNION ALL
  SELECT
    user_id,
    'notebook' AS event_kind,
    id,
    create_time,
    problem_id,
    NULL,
    entry_type,
    NULL
  FROM ai_learner_notebook
  WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_submission_user_time
  ON submission(user_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_learning_event_user_time
  ON ai_learning_event(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_learner_memory_user_time
  ON ai_learner_memory(user_id, created_at DESC) WHERE enabled = TRUE;
CREATE INDEX IF NOT EXISTS idx_ai_notebook_user_time
  ON ai_learner_notebook(user_id, create_time DESC) WHERE is_deleted = FALSE;
