-- V74: 公测反馈、附件、遥测事件表 + beta feedback 配置占位
--
-- 背景：小范围公测期间需要采集学生 Bug 上报（带截图）、产品体验事件、Web Vitals
-- 性能数据。复用现有 ai_learning_event / ai_tutor_workflow_event 承接学习行为事件，
-- 不在本表内重复。

-- ============================================================
-- 1. beta_feedback_report：学生提交的 Bug / 建议主表
-- ============================================================
CREATE TABLE IF NOT EXISTS beta_feedback_report (
    id                    BIGSERIAL PRIMARY KEY,
    reporter_user_id      BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    type                  VARCHAR(64)  NOT NULL,
        -- enum: cant_open / button_dead / page_confusing /
        --       wrong_problem_or_answer / ai_unclear / submit_wrong / other
    severity              VARCHAR(16)  NOT NULL,
        -- enum: blocker / high / medium / low
    description           TEXT NOT NULL DEFAULT '',
    route                 TEXT NOT NULL DEFAULT '',
    problem_id            BIGINT,
    submission_id         BIGINT,
    workflow_session_id   VARCHAR(64),
    status                VARCHAR(16)  NOT NULL DEFAULT 'pending',
        -- enum: pending / triaging / fixing / resolved / wontfix
    wjx_followup_opened   BOOLEAN NOT NULL DEFAULT FALSE,
    browser_meta          JSONB NOT NULL DEFAULT '{}'::jsonb,
        -- {ua, viewport, lang, dpr, online, network}
    recent_actions        JSONB NOT NULL DEFAULT '[]'::jsonb,
        -- last 20 telemetry events on the same page
    mail_status           VARCHAR(16) NOT NULL DEFAULT 'pending',
        -- enum: pending / sent / failed / disabled
    mail_error            TEXT NOT NULL DEFAULT '',
    privacy_notice_version VARCHAR(32) NOT NULL DEFAULT '',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at           TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_beta_feedback_user_time
    ON beta_feedback_report(reporter_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_beta_feedback_status_time
    ON beta_feedback_report(status, created_at DESC);

-- ============================================================
-- 2. beta_feedback_attachment：截图等附件
-- ============================================================
CREATE TABLE IF NOT EXISTS beta_feedback_attachment (
    id            BIGSERIAL PRIMARY KEY,
    report_id     BIGINT NOT NULL REFERENCES beta_feedback_report(id) ON DELETE CASCADE,
    file_name     VARCHAR(256) NOT NULL,
    content_type  VARCHAR(128) NOT NULL,
    size_bytes    INTEGER NOT NULL,
    storage_path  TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_beta_feedback_att_report
    ON beta_feedback_attachment(report_id);

-- ============================================================
-- 3. beta_telemetry_event：前端体验事件流
-- ============================================================
CREATE TABLE IF NOT EXISTS beta_telemetry_event (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES "user"(id) ON DELETE SET NULL,
    event_type  VARCHAR(64) NOT NULL,
        -- enum: page_view / feature_click / frontend_error /
        --       api_error / web_vital / feedback_opened / feedback_submitted
    route       TEXT NOT NULL DEFAULT '',
    problem_id  BIGINT,
    session_id  VARCHAR(64),
    payload     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_beta_telemetry_user_time
    ON beta_telemetry_event(user_id, created_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_beta_telemetry_type_time
    ON beta_telemetry_event(event_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_beta_telemetry_route_time
    ON beta_telemetry_event(route, created_at DESC);

-- ============================================================
-- 4. sys_options 默认配置（管理员可在后台改）
-- ============================================================
INSERT INTO sys_options(key, value)
VALUES (
    'beta_feedback_config',
    jsonb_build_object(
        'enabled', true,
        'notify_email', '1822250281@qq.com',
        'wjx_url', 'https://v.wjx.cn/vm/mvsfyTf.aspx',
        'privacy_notice_version', '2026-04-28-v1',
        'screenshot_max_bytes', 5242880,
        'screenshot_allowed_types', '["image/png","image/jpeg","image/webp"]',
        'telemetry_event_types', '["page_view","feature_click","frontend_error","api_error","web_vital","feedback_opened","feedback_submitted"]'
    )
)
ON CONFLICT (key) DO NOTHING;
