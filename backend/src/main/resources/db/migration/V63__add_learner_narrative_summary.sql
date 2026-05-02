-- V63: 学情画像层（DeepTutor P1）—— 自然语言长期学习摘要表 + 历史 snapshot 加列
-- 设计文档：docs/plans/2026-04-25-persistent-memory-layer-design.md

CREATE TABLE IF NOT EXISTS ai_learner_narrative_summary (
    user_id            BIGINT       NOT NULL,
    summary_version    INTEGER      NOT NULL DEFAULT 1,
    summary_text       TEXT         NOT NULL,
    summary_payload    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    learning_style_key VARCHAR(32)  NOT NULL DEFAULT 'step_by_step',
    last_event_id      BIGINT,
    last_session_id    VARCHAR(64),
    is_user_overridden BOOLEAN      NOT NULL DEFAULT FALSE,
    user_disabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id)
);

CREATE INDEX IF NOT EXISTS idx_aln_user_updated
    ON ai_learner_narrative_summary(user_id, updated_at DESC);

ALTER TABLE ai_learner_profile_snapshot
    ADD COLUMN IF NOT EXISTS narrative_summary_version INTEGER,
    ADD COLUMN IF NOT EXISTS narrative_summary_text    TEXT;
