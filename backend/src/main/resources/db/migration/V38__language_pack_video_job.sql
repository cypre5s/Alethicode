CREATE TABLE language_pack_video_job (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT NOT NULL REFERENCES language_pack_chat_session(id),
    message_id      BIGINT NOT NULL UNIQUE REFERENCES language_pack_chat_message(id),
    user_id         BIGINT NOT NULL,
    language_pack_id BIGINT NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'queued',
    question_text   TEXT NOT NULL DEFAULT '',
    answer_markdown TEXT NOT NULL DEFAULT '',
    source_citations_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    storyboard_json JSONB,
    subtitle_json   JSONB,
    provider_name   VARCHAR(64),
    provider_job_id VARCHAR(256),
    progress_percent INT NOT NULL DEFAULT 0,
    error_message   TEXT,
    video_path      TEXT,
    poster_path     TEXT,
    duration_seconds INT,
    create_time     TIMESTAMP NOT NULL DEFAULT now(),
    update_time     TIMESTAMP NOT NULL DEFAULT now(),
    completed_time  TIMESTAMP
);

CREATE INDEX idx_video_job_session ON language_pack_video_job(session_id);
CREATE INDEX idx_video_job_user ON language_pack_video_job(user_id);
CREATE INDEX idx_video_job_status ON language_pack_video_job(status);
