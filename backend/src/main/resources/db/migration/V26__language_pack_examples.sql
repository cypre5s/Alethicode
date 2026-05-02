-- V26: language_pack examples — extracted example code/exercises and KC binding

CREATE TABLE IF NOT EXISTS language_pack_example (
    id                  BIGSERIAL    PRIMARY KEY,
    language_pack_id    BIGINT       NOT NULL REFERENCES language_pack(id) ON DELETE CASCADE,
    init_task_id        BIGINT       NOT NULL REFERENCES language_pack_init_task(id) ON DELETE CASCADE,
    raw_text            TEXT         NOT NULL,
    normalized_body     TEXT         NOT NULL DEFAULT '',
    input_description   TEXT         NOT NULL DEFAULT '',
    output_description  TEXT         NOT NULL DEFAULT '',
    evidence_excerpt    TEXT         NOT NULL DEFAULT '',
    page_range_start    INTEGER,
    page_range_end      INTEGER,
    create_time         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lp_example_pack ON language_pack_example(language_pack_id);
CREATE INDEX IF NOT EXISTS idx_lp_example_task ON language_pack_example(init_task_id);

CREATE TABLE IF NOT EXISTS language_pack_example_kc_mapping (
    id          BIGSERIAL PRIMARY KEY,
    example_id  BIGINT    NOT NULL REFERENCES language_pack_example(id) ON DELETE CASCADE,
    kc_id       BIGINT    NOT NULL REFERENCES language_pack_kc(id) ON DELETE CASCADE,
    UNIQUE (example_id, kc_id)
);

CREATE INDEX IF NOT EXISTS idx_lp_example_kc_example ON language_pack_example_kc_mapping(example_id);
CREATE INDEX IF NOT EXISTS idx_lp_example_kc_kc ON language_pack_example_kc_mapping(kc_id);
