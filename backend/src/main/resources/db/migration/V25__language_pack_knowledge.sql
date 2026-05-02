-- V25: language_pack knowledge domain — chapters, KCs, and KC-to-page mappings

CREATE TABLE IF NOT EXISTS language_pack_chapter (
    id                BIGSERIAL    PRIMARY KEY,
    language_pack_id  BIGINT       NOT NULL REFERENCES language_pack(id) ON DELETE CASCADE,
    init_task_id      BIGINT       NOT NULL REFERENCES language_pack_init_task(id) ON DELETE CASCADE,
    chapter_index     INTEGER      NOT NULL,
    title             VARCHAR(512) NOT NULL,
    description       TEXT         NOT NULL DEFAULT '',
    page_range_start  INTEGER,
    page_range_end    INTEGER,
    create_time       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (language_pack_id, chapter_index)
);

CREATE INDEX IF NOT EXISTS idx_lp_chapter_pack ON language_pack_chapter(language_pack_id);

CREATE TABLE IF NOT EXISTS language_pack_kc (
    id                BIGSERIAL    PRIMARY KEY,
    language_pack_id  BIGINT       NOT NULL REFERENCES language_pack(id) ON DELETE CASCADE,
    init_task_id      BIGINT       NOT NULL REFERENCES language_pack_init_task(id) ON DELETE CASCADE,
    chapter_id        BIGINT       REFERENCES language_pack_chapter(id) ON DELETE SET NULL,
    name              VARCHAR(256) NOT NULL,
    name_normalized   VARCHAR(256) NOT NULL,
    name_en           VARCHAR(256) NOT NULL DEFAULT '',
    description       TEXT         NOT NULL DEFAULT '',
    synced_ai_kc_id   BIGINT,
    create_time       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (language_pack_id, name_normalized)
);

CREATE INDEX IF NOT EXISTS idx_lp_kc_pack ON language_pack_kc(language_pack_id);
CREATE INDEX IF NOT EXISTS idx_lp_kc_chapter ON language_pack_kc(chapter_id);

CREATE TABLE IF NOT EXISTS language_pack_kc_page_mapping (
    id       BIGSERIAL PRIMARY KEY,
    kc_id    BIGINT    NOT NULL REFERENCES language_pack_kc(id) ON DELETE CASCADE,
    page_id  BIGINT    NOT NULL REFERENCES language_pack_page(id) ON DELETE CASCADE,
    UNIQUE (kc_id, page_id)
);

CREATE INDEX IF NOT EXISTS idx_lp_kc_page_kc ON language_pack_kc_page_mapping(kc_id);
CREATE INDEX IF NOT EXISTS idx_lp_kc_page_page ON language_pack_kc_page_mapping(page_id);
