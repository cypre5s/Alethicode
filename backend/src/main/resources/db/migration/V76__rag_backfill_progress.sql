-- V76: rag_backfill_progress + rag_backfill_errors，给 scripts/ops/rag_backfill.py 落进度。
--
-- 背景：Phase 2 把历史 language_pack_page + ai_learner_notebook + ai_learner_memory
-- 全量灌进 LightRAG。脚本必须可中断、可重跑、可定位失败行。

CREATE TABLE IF NOT EXISTS rag_backfill_progress (
    id              BIGSERIAL PRIMARY KEY,
    entity_type     VARCHAR(64) NOT NULL,
        -- enum: courseware-page / notebook / memory
    last_id         VARCHAR(255),
        -- 最近成功处理的业务 id（语义随 entity_type 变化）；
        -- 重跑脚本时按 (entity_type) 行的 last_id 做断点续传起点
    total           INTEGER NOT NULL DEFAULT 0,
    finished        INTEGER NOT NULL DEFAULT 0,
    failed          INTEGER NOT NULL DEFAULT 0,
    started_at      TIMESTAMP WITHOUT TIME ZONE,
    finished_at     TIMESTAMP WITHOUT TIME ZONE,
    notes           TEXT,
        -- 自由文本：本次跑的命令行参数 / 估算 / 截止时间等
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT rag_backfill_progress_entity_type_check
        CHECK (entity_type IN ('courseware-page','notebook','memory'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rag_backfill_progress_entity
    ON rag_backfill_progress (entity_type);

CREATE TABLE IF NOT EXISTS rag_backfill_errors (
    id              BIGSERIAL PRIMARY KEY,
    entity_type     VARCHAR(64) NOT NULL,
    entity_id       VARCHAR(255) NOT NULL,
    attempt         INTEGER NOT NULL DEFAULT 1,
    error_text      TEXT NOT NULL,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT rag_backfill_errors_entity_type_check
        CHECK (entity_type IN ('courseware-page','notebook','memory'))
);

CREATE INDEX IF NOT EXISTS idx_rag_backfill_errors_entity
    ON rag_backfill_errors (entity_type, entity_id);

COMMENT ON TABLE rag_backfill_progress IS
    'Phase 2 / V76: 全量回填脚本的断点 / 总览（per entity_type 一行）';
COMMENT ON TABLE rag_backfill_errors IS
    'Phase 2 / V76: 单行失败的明细，按 (entity_type, entity_id) 可单独重跑';
