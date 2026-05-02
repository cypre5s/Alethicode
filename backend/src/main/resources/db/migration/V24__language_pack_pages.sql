-- V24: language_pack_page — page-level parsed content from normalized documents

CREATE TABLE IF NOT EXISTS language_pack_page (
    id                  BIGSERIAL    PRIMARY KEY,
    document_id         BIGINT       NOT NULL REFERENCES language_pack_document(id) ON DELETE CASCADE,
    language_pack_id    BIGINT       NOT NULL REFERENCES language_pack(id) ON DELETE CASCADE,
    page_no             INTEGER      NOT NULL,
    chunk_index         INTEGER      NOT NULL DEFAULT 0,
    page_title          VARCHAR(512) NOT NULL DEFAULT '',
    page_text           TEXT         NOT NULL DEFAULT '',
    text_hash           VARCHAR(128) NOT NULL DEFAULT '',
    preview_asset_path  TEXT         NOT NULL DEFAULT '',
    excerpt             TEXT         NOT NULL DEFAULT '',
    search_tsv          TSVECTOR,
    page_embedding      VECTOR(16),
    embedding_updated_at TIMESTAMPTZ,
    create_time         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (document_id, page_no, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_lp_page_document ON language_pack_page(document_id);
CREATE INDEX IF NOT EXISTS idx_lp_page_pack ON language_pack_page(language_pack_id);
CREATE INDEX IF NOT EXISTS idx_lp_page_search ON language_pack_page USING GIN(search_tsv);
CREATE INDEX IF NOT EXISTS idx_lp_page_text_hash ON language_pack_page(document_id, text_hash);
