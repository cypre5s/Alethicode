-- V31: student-facing language pack QA session domain

CREATE TABLE IF NOT EXISTS language_pack_chat_session (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    language_pack_id  BIGINT      NOT NULL REFERENCES language_pack(id) ON DELETE CASCADE,
    status            VARCHAR(32) NOT NULL DEFAULT 'active',
    create_time       TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_language_pack_chat_session_status CHECK (status IN ('active', 'archived'))
);

CREATE INDEX IF NOT EXISTS idx_lp_chat_session_user_pack
    ON language_pack_chat_session(user_id, language_pack_id, update_time DESC);

CREATE TABLE IF NOT EXISTS language_pack_chat_message (
    id           BIGSERIAL PRIMARY KEY,
    session_id   BIGINT      NOT NULL REFERENCES language_pack_chat_session(id) ON DELETE CASCADE,
    role         VARCHAR(16) NOT NULL,
    content      TEXT        NOT NULL DEFAULT '',
    answer_json  JSONB,
    create_time  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_language_pack_chat_message_role CHECK (role IN ('user', 'assistant'))
);

CREATE INDEX IF NOT EXISTS idx_lp_chat_message_session
    ON language_pack_chat_message(session_id, id);

CREATE TABLE IF NOT EXISTS language_pack_chat_retrieval_log (
    id             BIGSERIAL PRIMARY KEY,
    session_id     BIGINT      NOT NULL REFERENCES language_pack_chat_session(id) ON DELETE CASCADE,
    query_text     TEXT        NOT NULL DEFAULT '',
    page_hit_json  JSONB       NOT NULL DEFAULT '[]'::jsonb,
    create_time    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lp_chat_retrieval_session
    ON language_pack_chat_retrieval_log(session_id, create_time DESC);

CREATE TABLE IF NOT EXISTS language_pack_chat_feedback (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT      NOT NULL REFERENCES language_pack_chat_session(id) ON DELETE CASCADE,
    message_id      BIGINT      NOT NULL REFERENCES language_pack_chat_message(id) ON DELETE CASCADE,
    feedback_label  VARCHAR(32) NOT NULL,
    comment         TEXT        NOT NULL DEFAULT '',
    create_time     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (message_id),
    CONSTRAINT chk_language_pack_chat_feedback_label CHECK (
        feedback_label IN ('helpful', 'unhelpful', 'citation_incorrect')
    )
);

CREATE INDEX IF NOT EXISTS idx_lp_chat_feedback_session
    ON language_pack_chat_feedback(session_id, create_time DESC);
