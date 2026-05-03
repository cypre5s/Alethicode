-- V93__twin_customization_phase_e.sql
-- L99 Phase E: 世界观 + 主题皮肤 + 年度报告 + 分享卡片

CREATE TABLE IF NOT EXISTS twin_world_setting (
    user_id             BIGINT       PRIMARY KEY,
    world_name          VARCHAR(120) NOT NULL DEFAULT '编程学院',
    world_narrative     TEXT,
    theme_id            VARCHAR(32)  NOT NULL DEFAULT 'academy',
    custom_palette      JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS twin_annual_report (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    year            INTEGER      NOT NULL,
    report_data     JSONB        NOT NULL,
    highlight_text  TEXT,
    share_card_url  VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, year)
);

CREATE INDEX IF NOT EXISTS idx_tar_user
  ON twin_annual_report(user_id, year DESC);

CREATE TABLE IF NOT EXISTS twin_share_card (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    card_type       VARCHAR(32)  NOT NULL,
    card_data       JSONB        NOT NULL,
    image_url       VARCHAR(500),
    share_platform  VARCHAR(32),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tsc_user
  ON twin_share_card(user_id, created_at DESC);
