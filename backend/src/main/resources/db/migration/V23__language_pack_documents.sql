-- V23: language_pack_document — stores original and canonical document records per init task

CREATE TABLE IF NOT EXISTS language_pack_document (
    id                  BIGSERIAL    PRIMARY KEY,
    init_task_id        BIGINT       NOT NULL REFERENCES language_pack_init_task(id) ON DELETE CASCADE,
    language_pack_id    BIGINT       NOT NULL REFERENCES language_pack(id) ON DELETE CASCADE,
    original_filename   VARCHAR(512) NOT NULL,
    original_path       TEXT         NOT NULL,
    canonical_path      TEXT,
    preview_pdf_path    TEXT,
    file_hash           VARCHAR(128) NOT NULL,
    file_size_bytes     BIGINT       NOT NULL DEFAULT 0,
    page_count          INTEGER      NOT NULL DEFAULT 0,
    status              VARCHAR(32)  NOT NULL DEFAULT 'pending',
    failure_reason      TEXT,
    create_time         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_time         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_document_status CHECK (
        status IN ('pending', 'normalizing', 'normalized', 'failed')
    ),
    UNIQUE (init_task_id, file_hash)
);

CREATE INDEX IF NOT EXISTS idx_lp_document_task ON language_pack_document(init_task_id);
CREATE INDEX IF NOT EXISTS idx_lp_document_pack ON language_pack_document(language_pack_id);
