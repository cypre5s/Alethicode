-- V73: 语言包初始化质量报告
--
-- 背景：
--   Language Pack init 流水线引入 Reference Solution 自验证闸门后，
--   需要把每次 init 任务的「lint 违规 / self-validation 失败 / 重试 / escalation」
--   汇总到一张表，供 Grafana 看板与回归 backlog 消费。
--   同设计稿 § 6.1.5 与 § 13 P0 验收 #6。

CREATE TABLE IF NOT EXISTS language_pack_init_quality_report (
    id                       BIGSERIAL PRIMARY KEY,
    init_task_id             BIGINT       NOT NULL REFERENCES language_pack_init_task(id) ON DELETE CASCADE,
    language_pack_id         BIGINT       NOT NULL,
    total_packages           INTEGER      NOT NULL,
    self_validated_count     INTEGER      NOT NULL,
    failed_count             INTEGER      NOT NULL,
    retried_count            INTEGER      NOT NULL,
    escalated_count          INTEGER      NOT NULL,
    failure_breakdown        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- 例：{"R1_self_validation": 5, "R2_set_order": 3, "R3_algo_bug": 2, "R7_float_precision": 1}
    lint_summary             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- 例：{"hard_violations": {"REF001": 2}, "soft_violations": {"REF005": 7}}
    escalated_packages       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    -- 例：[{"display_id":"PPT4-3","reason":"self_validation_after_3_retries", ...}]
    duration_ms              BIGINT       NOT NULL,
    create_time              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lpiqr_pack ON language_pack_init_quality_report(language_pack_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_lpiqr_task ON language_pack_init_quality_report(init_task_id);

COMMENT ON TABLE language_pack_init_quality_report IS
    '初始化质量门聚合报告：每次 init 任务一行，记录 self-validation 通过率、根因分布与 escalation 列表';
COMMENT ON COLUMN language_pack_init_quality_report.failure_breakdown IS
    'JSONB 根因计数；key 与 docs/plans/2026-04-28-language-pack-init-quality-design.md § 1.2 R1-R8 对齐';
COMMENT ON COLUMN language_pack_init_quality_report.lint_summary IS
    'JSONB lint 违规统计：{"hard_violations": {"REF001": n}, "soft_violations": {"REF005": n}}';
COMMENT ON COLUMN language_pack_init_quality_report.escalated_packages IS
    'JSONB 升级到人工 EscalationReviewAgent 的题包详情列表';
