-- Phase A: ai_generated_problem 增加真实 KC 链接字段；放宽 ai_problem_kc_mapping.kc_id 取值
--   1. ai_generated_problem.target_kc_ids: classroom 出题时教师选定的 language_pack_kc.id 列表
--   2. ai_generated_problem.source_strategy: 题目来源 lesson_llm / lp_kc_pick / hybrid
--   3. ai_problem_kc_mapping.kc_id: 现状外键引用 ai_knowledge_component(id)，但生产侧 BeginnerSupplementPlannerService
--      等服务一律 join language_pack_kc.id = m.kc_id；本次统一约定 kc_id 即 language_pack_kc.id，删除旧 FK 约束。

ALTER TABLE ai_generated_problem
    ADD COLUMN IF NOT EXISTS target_kc_ids JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE ai_generated_problem
    ADD COLUMN IF NOT EXISTS source_strategy VARCHAR(20) NOT NULL DEFAULT 'lesson_llm';

ALTER TABLE ai_generated_problem
    DROP CONSTRAINT IF EXISTS chk_ai_generated_problem_source_strategy;
ALTER TABLE ai_generated_problem
    ADD CONSTRAINT chk_ai_generated_problem_source_strategy
    CHECK (source_strategy IN ('lesson_llm', 'lp_kc_pick', 'hybrid'));

CREATE INDEX IF NOT EXISTS idx_ai_generated_problem_target_kcs
    ON ai_generated_problem USING gin (target_kc_ids);

CREATE INDEX IF NOT EXISTS idx_ai_generated_problem_source_strategy
    ON ai_generated_problem(source_strategy);

ALTER TABLE ai_problem_kc_mapping
    DROP CONSTRAINT IF EXISTS ai_problem_kc_mapping_kc_id_fkey;
