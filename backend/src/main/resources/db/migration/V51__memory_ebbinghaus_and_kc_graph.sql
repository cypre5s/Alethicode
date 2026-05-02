ALTER TABLE ai_learner_memory
    ADD COLUMN IF NOT EXISTS recall_count INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS ai_kc_relation (
    id BIGSERIAL PRIMARY KEY,
    from_kc_id BIGINT NOT NULL REFERENCES ai_knowledge_component(id) ON DELETE CASCADE,
    to_kc_id BIGINT NOT NULL REFERENCES ai_knowledge_component(id) ON DELETE CASCADE,
    relation_type VARCHAR(32) NOT NULL,
    weight DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    UNIQUE(from_kc_id, to_kc_id, relation_type)
);

CREATE INDEX IF NOT EXISTS idx_ai_kc_relation_to
    ON ai_kc_relation(to_kc_id, relation_type);

CREATE INDEX IF NOT EXISTS idx_ai_kc_relation_from
    ON ai_kc_relation(from_kc_id, relation_type);
