-- V75: rag_index_outbox 表，承担"业务表写入永不依赖 alethicode-rag"的解耦层。
--
-- 背景：Phase 1（计划 rag_全量切换_lightrag_251432a8.plan.md）要把
-- LearnerMemoryService.persistCandidate 与 DocumentParsingServiceImpl 写完业务表后
-- 多写一行 outbox，由 RagIndexOutboxWorker 异步重放到 alethicode-rag。
-- 业务侧永远不会因为 RAG 服务挂掉而失败；RAG 服务上线后 worker 一轮就能追平。

CREATE TABLE IF NOT EXISTS rag_index_outbox (
    id              BIGSERIAL PRIMARY KEY,
    entity_type     VARCHAR(64) NOT NULL,
        -- enum: courseware-page / notebook / memory
        -- 与 services/alethicode-rag 端 EntityType 严格对齐
    entity_id       VARCHAR(255) NOT NULL,
        -- 业务 id：page_id（数字字符串）/ notebook_id（uuid）/ memory_key（字符串）
    action          VARCHAR(16) NOT NULL DEFAULT 'INDEX',
        -- enum: INDEX / DELETE
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
        -- 给 alethicode-rag 的 IndexRequest body：{content, metadata: {...}}
    attempts        INTEGER NOT NULL DEFAULT 0,
    last_error      TEXT,
    next_retry_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    indexed_at      TIMESTAMP WITHOUT TIME ZONE,
        -- 写入 alethicode-rag 成功后落值；同时表示该行不再被 worker 拾起
    given_up_at     TIMESTAMP WITHOUT TIME ZONE,
        -- attempts >= 5 后写入；"软死信"（保留行用于排查），不再重试
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT rag_index_outbox_action_check CHECK (action IN ('INDEX','DELETE')),
    CONSTRAINT rag_index_outbox_entity_type_check
        CHECK (entity_type IN ('courseware-page','notebook','memory'))
);

-- 幂等键：同一业务对象的同一动作只允许一行 pending；后续业务侧再写入时直接 UPDATE 覆盖
-- payload + 重置 attempts/next_retry_at/last_error/indexed_at/given_up_at 等字段。
CREATE UNIQUE INDEX IF NOT EXISTS uq_rag_outbox_entity_action
    ON rag_index_outbox (entity_type, entity_id, action);

-- worker 扫表：只看 pending 行（未索引也未 give_up），按 next_retry_at 升序拣 100 条。
-- 部分索引保证扫表成本仅与 pending 队列长度相关，不会被历史 indexed 行拖慢。
CREATE INDEX IF NOT EXISTS idx_rag_outbox_pending_next_retry
    ON rag_index_outbox (next_retry_at)
    WHERE indexed_at IS NULL AND given_up_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_rag_outbox_given_up
    ON rag_index_outbox (given_up_at)
    WHERE given_up_at IS NOT NULL;

COMMENT ON TABLE rag_index_outbox IS
    'Phase 1 / V75: 业务表写入到 alethicode-rag 异步索引的解耦表。';
COMMENT ON COLUMN rag_index_outbox.entity_type IS
    'courseware-page | notebook | memory，与 alethicode-rag EntityType 严格对齐';
COMMENT ON COLUMN rag_index_outbox.action IS
    'INDEX：POST /v1/rag/index/{type} ；DELETE：DELETE /v1/rag/index/{type}/{id}';
COMMENT ON COLUMN rag_index_outbox.attempts IS
    '已尝试次数；失败时按 60s × 2^attempts 退避，封顶 1h；attempts >= 5 写 given_up_at';
COMMENT ON COLUMN rag_index_outbox.indexed_at IS
    '成功标记；非空行不再被 worker 拾起';
COMMENT ON COLUMN rag_index_outbox.given_up_at IS
    '软死信标记；attempts >= 5 后落值，alert 看 rag_outbox_giveup_total counter';
