-- V77: Phase 3 单次性切流 — 删除全部 16 维伪 RAG 残留物。
--
-- 计划稿 `rag_全量切换_lightrag_251432a8.plan.md` Phase 3：
-- 「DROP page_embedding/notebook_embedding/memory_embedding/search_tsv/cjk_bigram_tokenize」。
--
-- 这是一次性 cutover：alethicode-rag 微服务（FastAPI + LightRAG 1.4.15）
-- 已通过 Phase 2 全量回填 936 行业务数据（868 page + 24 notebook + 44 memory），
-- LightRAG 工作区内的 PG 表（lightrag_*）+ Memgraph KG 持有完整 2048 维 embedding
-- + 知识图谱，Java 端 4 个检索 service（PageRetrieval / SimilarError /
-- LearnerMemorySemantic / Courseware）已通过 Phase 3 改造为 ragClient 调用。
-- 业务表上残留的 16 维 embedding 列 + tsvector 列 + 自研 cjk_bigram 分词函数已无任何
-- 调用方，本迁移把它们彻底删除，避免拖慢业务表写入与索引扫描。

ALTER TABLE language_pack_page
    DROP COLUMN IF EXISTS page_embedding,
    DROP COLUMN IF EXISTS embedding_updated_at,
    DROP COLUMN IF EXISTS search_tsv;

ALTER TABLE ai_learner_notebook
    DROP COLUMN IF EXISTS notebook_embedding,
    DROP COLUMN IF EXISTS notebook_summary;

ALTER TABLE ai_learner_memory
    DROP COLUMN IF EXISTS memory_embedding;

DROP INDEX IF EXISTS idx_lp_page_search;

DROP FUNCTION IF EXISTS cjk_bigram_tokenize(text);

COMMENT ON TABLE language_pack_page IS
    '课件页表；Phase 3（V77）后向量检索改由 alethicode-rag (LightRAG) 接管，本表只持业务展示字段';
COMMENT ON TABLE ai_learner_memory IS
    '长期记忆表；Phase 3（V77）后向量召回改由 alethicode-rag 接管，本表只持业务文本与衰减元数据';
COMMENT ON TABLE ai_learner_notebook IS
    '学生错题本；Phase 3（V77）后相似错误检索改由 alethicode-rag 接管，本表只持业务字段';
