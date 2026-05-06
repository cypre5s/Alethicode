-- Career Bridging V84：里程碑 + 报告表（plan 2.2 节）
--   1. career_bridging_milestone：触发节点登记表（enrollment / kc_cluster_graduated /
--      chapter_entered / project_completed / path_node_unlocked 5 类）
--   2. career_bridging_report：LLM 生成的 Why 报告，挂在 milestone 上
--   注意：UNIQUE 含 NULL 列在 PostgreSQL 默认行为下视为不同行——本表
--   milestone_ref 允许 NULL（如 enrollment 没有 ref），重复 milestone 由
--   应用层校验避免插入

CREATE TABLE IF NOT EXISTS career_bridging_milestone (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    milestone_type VARCHAR(64) NOT NULL,
    milestone_ref VARCHAR(128),
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    consumed_at TIMESTAMPTZ,
    UNIQUE (user_id, milestone_type, milestone_ref)
);

CREATE INDEX IF NOT EXISTS idx_career_milestone_user_unconsumed
    ON career_bridging_milestone(user_id, triggered_at DESC)
    WHERE consumed_at IS NULL;

CREATE TABLE IF NOT EXISTS career_bridging_report (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    milestone_id BIGINT REFERENCES career_bridging_milestone(id) ON DELETE SET NULL,
    major_code VARCHAR(64) NOT NULL,
    report_kind VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content_md TEXT NOT NULL,
    citations JSONB NOT NULL DEFAULT '[]'::jsonb,
    rollout_mode VARCHAR(16) NOT NULL DEFAULT 'baseline',
    reflection_passed BOOLEAN NOT NULL DEFAULT TRUE,
    trace_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_career_report_user_created
    ON career_bridging_report(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_career_report_milestone
    ON career_bridging_report(milestone_id) WHERE milestone_id IS NOT NULL;
