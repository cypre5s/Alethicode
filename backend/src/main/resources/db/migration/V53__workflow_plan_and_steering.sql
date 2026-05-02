-- 学生端 AI 导学 Plan 持久化
-- 每次 Planner 生成新的 plan 就写入一条记录，Steering REDIRECT 会更新 plan_steps 和 replan_count
CREATE TABLE IF NOT EXISTS ai_workflow_plan (
    plan_id UUID PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL REFERENCES ai_workflow_session(session_id) ON DELETE CASCADE,
    trigger_event VARCHAR(64) NOT NULL,
    autonomy_level VARCHAR(32) NOT NULL,
    plan_steps JSONB NOT NULL DEFAULT '[]'::jsonb,
    coordination_reasoning TEXT NOT NULL DEFAULT '',
    replan_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_workflow_plan_session_time
    ON ai_workflow_plan(session_id, created_at DESC);

-- 学生下发的 Steering 信号（PAUSE / SKIP_CURRENT / REDIRECT / TAKE_OVER）
-- executor 每步开始前通过 UPDATE ... RETURNING 原子消费最新一条未处理信号
CREATE TABLE IF NOT EXISTS ai_workflow_steering_signal (
    signal_id UUID PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    signal_type VARCHAR(32) NOT NULL,
    redirect_instruction TEXT,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_workflow_steering_session_unconsumed
    ON ai_workflow_steering_signal(session_id, created_at DESC)
    WHERE consumed = false;

-- 清理旧的 Beta 特性键名，改为 WORKFLOW_COORDINATOR_ENABLED
-- 如果之前 admin 开启了 AUTONOMY_LAB_COORDINATOR_ENABLED，这里无缝迁移到新键名
UPDATE sys_options
SET value = (
    CASE
        WHEN value ? 'AUTONOMY_LAB_COORDINATOR_ENABLED'
            THEN (value - 'AUTONOMY_LAB_COORDINATOR_ENABLED')
                 || jsonb_build_object('WORKFLOW_COORDINATOR_ENABLED', value->'AUTONOMY_LAB_COORDINATOR_ENABLED')
        ELSE value
    END
)
WHERE key = 'beta_features';
