-- V94__twin_weekly_digest.sql
-- L99: 补全 twin_weekly_digest 表（周度摘要）

CREATE TABLE IF NOT EXISTS twin_weekly_digest (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    week_start      DATE         NOT NULL,
    digest_text     TEXT         NOT NULL,
    metrics         JSONB        NOT NULL,
    evidence_event_ids JSONB     NOT NULL DEFAULT '[]'::jsonb,
    email_sent_at   TIMESTAMPTZ,
    email_opened_at TIMESTAMPTZ,
    web_viewed_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, week_start)
);
