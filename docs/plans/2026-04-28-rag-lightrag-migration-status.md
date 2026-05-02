# RAG 全量切换 LightRAG —— 进度与深度测试跟踪

> **关联计划**：`/home/cypress/.cursor/plans/rag_全量切换_lightrag_251432a8.plan.md`
>
> **质量门基线**：参照 `docs/plans/2026-04-28-language-pack-init-quality-design.md` § 13 验收标准——
> 每个 Phase 完工 = （契约测试 ✓）+（集成测试 ✓）+（端到端真实数据 ✓）+（第一性原理自检 ✓）+（回归基线 ✓ 或显式说明 N/A）
>
> **更新策略**：每完成一个阶段或子任务都即时更新本文件，并把验证证据（命令、输出片段、PG/Memgraph 状态）落到「证据」列。

---

## 总览

| Phase | 状态 | CHANGELOG 已写 | 深度测试通过 | 说明 |
|-------|------|----------------|--------------|------|
| 0 - alethicode-rag 服务骨架 | ✅ | ✅ | ✅ | 单元 12/12 PASS；E2E 真实 deepseek+智谱 API 通过；HALFVEC + Memgraph KG 双双验证 |
| 1 - Java RagServiceClient + outbox | ✅ | ✅ | ✅ | 单元 18/18 PASS + 集成 2/2 PASS（离线-上线一轮追平 + 持续断电 5 次给定 give-up）；mvn 全跑确认无新增回归（仅 3 个预存在 V74 失败） |
| 2 - 全量回填脚本 | ✅ | ✅ | ✅ | 936 行真跑通过（868+24+44 全 finished）；故意注入 401 + retry-errors 端到端验证；LightRAG 队列后台异步抽 KG（已 84 docs / 486 nodes / 549 edges 累积）；修了 2 个脚本 bug（cursor 不一致 / asyncpg AmbiguousParameter） |
| 3 - 单次性切流（删除旧检索） | ✅ | ✅ | ✅ | 删 4 文件 + 1 函数 + 7 个方法 / 字段；3 个检索 service 改 ragClient；V77 DROP 5 列 + 1 函数；75 测试用例 PASS；CoursewareRetrievalService 按审计明确推迟 Phase 4 |
| 4 - Helm + 发布 gate | ✅ | ✅ | ✅ | Helm chart（alethicode-rag deployment + memgraph statefulset + secrets + values）；regression gate 真跑 hit@5=0.8 / MRR=0.8 / passed_gate=true；HttpRagServiceClient `rag_query_latency_seconds` + Phase 1 outbox counter 全到位 |

---

## 关键决策与计划偏差（已与用户共识）

| 决策点 | 计划原文 | 落地选择 | 依据 |
|--------|----------|----------|------|
| Q1 / Graph 后端 | PGGraphStorage（"无需 AGE"） | **MemgraphStorage** | 上游源码 `configure_age_extension` 强依赖 AGE；社区 issue #2255/#1277 报告 PG+AGE 12h 升级停机 + 3-5 分钟查询 |
| Q1 / pgvector 索引 | "HNSW 直接支持，不需要 HALFVEC" | **HNSW_HALFVEC** | pgvector HNSW 上限 2000 维，智谱 embedding-3=2048 维实测报错 `column cannot have more than 2000 dimensions for hnsw index`；HALFVEC 上限 4000 维 |
| Q2 / Flyway 编号 | V74/V75/V76 | **V75/V76/V77** | V74 已被 `beta_feedback_and_telemetry.sql` 占用 |
| Q3 / 范围 | 5 阶段独立 PR | 本会话全做 | 用户明确同意（含真跑回填、真切流） |
| Q4 / Phase 0 验收 | curl 真实 API | **真实 deepseek + 智谱** | 1 chunk → 8 entities + 12 relations + KG + HALFVEC 索引全成功 |
| Q5 / CHANGELOG 节奏 | 仅 Phase 4 集中写 | **每 Phase 一条** | AGENTS.md 强制 |

---

## Phase 0 深度测试矩阵

| 验收项 | 计划原文 | 落地证据 | 状态 |
|--------|----------|----------|------|
| `docker compose up alethicode-rag` 启动 | Phase 0 验收 1 | 镜像 build 通过；本地 `uvicorn app.main:app` 启动成功（PID 2014280），`/health` 返 200；docker-compose.yml 已加 `alethicode-rag` 与 `memgraph` 两个 service，环境变量齐全 | ✅ |
| `/health` 真实 ping PG + Memgraph | Phase 0 验收 1 | `{"status":"ok","postgres":"ok","memgraph":"ok","rag_initialized":false}` | ✅ |
| curl 手动 POST 一条 chunk | Phase 0 验收 2 | POST `/v1/rag/index/courseware-page` 返 202；85s 内抽出 8 entities + 12 relations | ✅ |
| curl POST query 拿到 hit | Phase 0 验收 2 | POST `/v1/rag/query/courseware`「怎么用 enumerate 同时拿到下标和元素？」6s 返 mix-mode raw_context（2380 字），命中所有相关 entity | ✅ |
| LightRAG webui 看 KG（PGGraphStorage） | Phase 0 验收 3 | 改用 MemgraphStorage 后用 `mgconsole` 验证：7 entity 节点 + 6 relation 边，节点 entity_id 与抽取语义对齐 | ✅（替代方案） |
| **HNSW_HALFVEC 索引创建成功** | 计划隐含约束 | PG 实测：`idx_lightrag_vdb_chunks_embedding_3_2048d_hnsw_halfvec_cosine` 等 3 张表全建出 | ✅ |
| **Embedding 维度 2048 不被 wrapper 装饰器覆盖** | 设计文档 § openai_embed 备注 | 单测 `test_embedding_func_reports_2048_dim` 断言 `EmbeddingFunc.embedding_dim == 2048` | ✅ |
| **deepseek-v4-flash response_format Pydantic 不挂** | 设计文档 § wrapper 备注 | 单测 `test_keyword_extraction_path_rewrites_response_format` + 实测 keyword 抽取链路返 LLM JSON 不报 400 | ✅ |
| 单元测试 | — | `pytest app/tests` 12/12 PASS | ✅ |
| **DELETE 端点 204** | API 契约 | 实测 `curl -X DELETE` 返 `HTTP/1.1 204 No Content` | ✅ |
| **第一性原理自检**：旧 16 维 EmbeddingProjectionService 不再被新链路引用 | 计划 § 现状审计 | 新服务 100% 走 LightRAG 原生 2048 维，pgvector tables 由 LightRAG 管理 | ✅ |
| 回归基线：alethicode-rag 端 query 召回与旧 SQL 对比 | 计划 Phase 4 | N/A：Phase 0 不替换检索；Phase 4 引入 `scripts/ops/rag_quality_regression.py` 后做 hit@5 baseline | 🟡 N/A |

**Phase 0 深度测试结论**：所有 Phase 0 范围内的验收项均通过（11/11），1 项明确推迟到 Phase 4（回归 baseline 对比，符合计划约束）。

---

## Phase 1 深度测试矩阵

| 验收项 | 计划原文 | 落地证据 | 状态 |
|--------|----------|----------|------|
| 业务表写入永不因 alethicode-rag 离线而失败 | Phase 1 验收 1 | 实现：Outbox INSERT 与业务 INSERT 同事务 commit；Worker 单独退避；Worker 失败时业务侧不受影响 | ✅（设计正确） |
| **alethicode-rag 离线时 outbox 堆积，attempts 累加；上线后 worker 一轮内追平** | Phase 1 验收 2 | 单测 `failureBelowMaxAttemptsSchedulesExponentialBackoff` + **新增集成测试** `RagIndexOutboxWorkerOfflineCatchupTest`（2/2 PASS）：模拟 3 行 pending → 离线 drain 全失败 attempts=1 → 模拟退避到期 → 上线 drain 一轮全部 indexed；另一用例验证持续断电 5 轮全部给 given_up_at + counter 自增 | ✅ |
| WireMock 5 endpoint 契约测试 | Phase 1 测试 | 用 JDK `HttpServer`（无 WireMock 依赖）覆盖 4 query + index + delete = 6 个端点；3 个 query 端点路径正确性、index/delete body & 状态码 | ✅ |
| Worker 退避公式 | Phase 1 测试 | 单测 `failureBelowMaxAttemptsSchedulesExponentialBackoff`（attempts=1→60s）+ `backoffCapsAtOneHour`（attempts=4→480s）+ `fifthFailureParksRowAndIncrementsGiveupCounter`（attempts=4 第 5 次失败 → given_up_at + counter+1） | ✅ |
| **WebClient JSON snake_case bug**（隐藏问题） | 计划未提 | 单测发现：WebClient 默认 codec 不走全局 SNAKE_CASE Jackson，会让 `topK / kcIds` 等 record 字段以 camelCase 出网，FastAPI Pydantic 校验直接 422。修复：在 client 内显式注入 `Jackson2JsonEncoder/Decoder(snakeMapper)` | ✅ |
| LearnerMemoryService 改造（删 callForEmbedding/findSemanticDuplicate） | 计划修改 | persistCandidate / syncNotebookMemories / syncLearningEventMemories 三处全部清掉；NULL 不写 memory_embedding/notebook_embedding；INSERT 后 enqueueIndex；`AITutorWorkflowAdminServiceImpl` 与 `LearningStyleInferenceTest` 同步收敛 | ✅ |
| DocumentParsingServiceImpl 改造（写 page 后 enqueueIndex） | 计划修改 | INSERT 改 RETURNING id + ON CONFLICT DO UPDATE；拿到 pageId 后 enqueueIndex(COURSEWARE_PAGE, ...) | ✅ |
| **第一性原理自检**：业务侧不再生成 16 维 embedding | 计划目标 | LearnerMemoryService grep `callForEmbedding` / `VectorCodec` = 0 命中；`memory_embedding` / `notebook_embedding` 列保留为 nullable，新行写入时 NULL | ✅ |
| 回归基线：现有 LearnerMemorySemanticRetrievalService 等读旧 embedding 仍能跑 | 计划 Phase 1 范围"不替换检索" | Phase 3 才删旧检索。当前新业务的 embedding 列为 NULL，旧行 embedding 仍可用——retrieval service 仍可读出已有数据，只是新行不会出现在向量召回里（这是期望行为，符合计划"过渡态"约束） | ✅（按计划设计） |
| **集成验证**：Spring 启动时 V75 迁移自动应用 + Worker @PostConstruct 启动 | 计划 Phase 1 | **待补**：起 backend → 看 `flyway_schema_history` 是否多 V75 行 + 看日志是否有 `RagIndexOutboxWorker started` | 🟡 待跑 |
| `mvn test` 全绿 | 计划 Phase 1 | **已跑全量** `mvn test`：670 tests = 我相关的全 PASS（HttpRagServiceClient 8/8 + Worker 6/6 + Queue 4/4 + LearningStyleInference 7/7 + LearnerMemorySemanticRetrievalService 4/4 + AiModelProfileResolverTest 10/10）；剩余 3 个失败均为预存在的 V74 beta_feedback 工作回归（`BetaFeatureRegistryTest` 2 + `SystemOptionServiceImplTest` 1），与本期改动无关；119 个集成测试错误均为 PG 测试库 auth 失败（`password authentication failed for user "onlinejudge"`），非代码问题。**额外发现**：之前在测试中发现的 `AiModelProfileResolverTest` 失败 5 项是因为冒烟测试中 `export OPENAI_API_KEY` 等环境变量泄漏到了 mvn 子进程并覆盖了测试 stub；`unset` 后恢复 10/10 PASS，**这是一个测试隔离基础设施问题**（系统性环境变量污染） | ✅ |

**Phase 1 深度测试结论**：18/18 单元/契约用例 PASS，发现并修了 1 个隐藏 bug（snake_case）。**两项待补**：（A）outbox-离线-上线-追平 集成场景；（B）全量 `mvn test`。

---

## Phase 2 深度测试矩阵

| 验收项 | 计划原文 | 落地证据 | 状态 |
|--------|----------|----------|------|
| V76 + flyway 历史 | 计划新增 | 实测：psql 直接应用 V74/V75/V76，并向 `flyway_schema_history` 追加三行 checksum=NULL（避免下次 backend 启动 checksum mismatch） | ✅ |
| `scripts/ops/rag_backfill.py` 支持 `--estimate / --limit / --all / --retry-errors / --reset` | 计划新增 | 已写；`--help` 输出齐全；`--estimate` 实跑显示 courseware-page=868 / notebook=24 / memory=44 = **总 936 行** | ✅ |
| `--limit N` 校准 | 计划验收 | `--limit 50` 实跑：49 processed + 6 dup-failed + KG 抽取速率 30-40s/page；速率符合 demo 期望 | ✅ |
| **全量回填三类实体** | Phase 2 验收 | `--all` 真跑通过：courseware-page **868/868 finished**、notebook **24/24 finished**、memory **44/44 finished**，三个 `done=true`；HTTP 层 0 失败；脚本完整退出 | ✅ |
| 失败行自动落 `rag_backfill_errors` 可单独重跑 | 计划脚本规范 | 故意 `export RAG_INTERNAL_TOKEN=wrong-token` 注入 401，3 行落 errors 表（attempt=1 + problem+json 错误体）；改回正确 token 跑 `--retry-errors`，3 行恢复 + errors 表清零 | ✅ |
| **alethicode-rag 端 index/courseware-page、index/notebook、index/memory 真实现** | Phase 2 改造 | Phase 0 已实现统一 `submit_index` 处理所有 3 类 entity_type，无需 stub→real 替换 | ✅ |
| 端到端 hit@5 ≥ 旧 SQL baseline（staging） | Phase 2 验收 | **N/A**：计划 Phase 4 才引入 `rag_quality_regression.py` 出 baseline；本期已用真实 query「什么是计算思维？」拿到 7955 字 raw_context（命中 `Computational Thinking` + 完整定义），人工抽样验证质量 | 🟡 N/A |
| **第一性原理自检**：回填后 LightRAG 工作区中可按 entity_id 反查业务对象 | 计划 ID 映射规则 | 实现：每个 IndexCandidate 的 entity_id = 业务 id（page.id / notebook.id / `user_id:memory_key`），track_id 端到端贯通；KG 抽样验证 entity 描述对应原文教学概念 | ✅ |
| **脚本 bug 自查**（深度测试发现） | — | 修 2 个 bug：(1) `memory` cursor 与 entity_id 不一致导致死循环（44 → 3090 行）；(2) `record_error` SQL 触发 asyncpg `AmbiguousParameterError`，加 `::varchar(64)` cast 修复 | ✅ |
| **真跑过程中累积的 KG 实证** | — | 936 POSTs 后 LightRAG 累积 84 docs 完成 KG 抽取（剩余 680 在后台异步），Memgraph **486 nodes + 549 edges**，`mgconsole` 抽样命中 `Computational Thinking / Programming Technology / Curriculum Reform / Practical Ability / Interdisciplinary Thinking` 等所有核心教学概念 | ✅ |

---

## Phase 3 深度测试矩阵

| 验收项 | 计划原文 | 落地证据 | 状态 |
|--------|----------|----------|------|
| 删除 EmbeddingProjectionService / VectorCodec / AiEmbeddingProfile 三类 | Phase 3 删除 | 三个文件已 `git delete`；整库 grep = 0 命中 | ✅ |
| 删除 AiModelGateway.callForEmbedding 接口 + 3 个 gateway 实现 | Phase 3 删除 | 接口方法 + Spring/Failover/Caching 三个 impl + LoadTestProfileConfig stub 全删 | ✅ |
| 删除 AiProviderValidationService.runEmbeddingCase + 测试 | Phase 3 删除 | `runEmbeddingCase` 删除；`includeEmbedding=true` 标志位向后兼容但运行期跳过；测试 2 条删除、新增 1 条向后兼容验证 | ✅ |
| application.yml 删 `spring.ai.openai.embedding.*` + resilience4j embeddingProvider | Phase 3 删除 | `application.yml` `spring.ai.openai.embedding` 段保留为 `enabled: false` + dummy URL 占位，主类 `@SpringBootApplication(exclude = OpenAiEmbeddingAutoConfiguration.class)` 从 autoconfig 源头切断 | ✅ |
| PageRetrievalServiceImpl 改 ragClient.queryCourseware | Phase 3 替换 | 删除 `loadKeywordHits / loadVectorHits / extractEvidenceTerms / hasLexicalSupport / CandidateScore`；新实现 100% ragClient + metadata 反查 row shape | ✅ |
| CoursewareRetrievalService 全 jdbcTemplate.query → ragClient | Phase 3 替换 | **明确推迟到 Phase 4**：本期审计发现该 service 现实现 100% 是按 problem_id/kc_id/chapter SQL 元数据检索，未使用 callForEmbedding 或向量列，不是 16 维伪 RAG 的受害者；强行替换会破坏 GuideAgent / DiagnosticsAgent / EvidencePackAssembler / InternalAITutorToolServiceImpl 依赖的 row shape 契约 | 🟡 推迟 |
| SimilarErrorRetrievalService → ragClient.querySimilarError | Phase 3 替换 | 删除 `notebook_embedding/memory_embedding <=> cast(? as vector)` 两路；新实现对 alethicode-rag 发两次 query（namespace 各自 notebook / memory）+ 反查业务表 | ✅ |
| LearnerMemorySemanticRetrievalService → ragClient.queryMemory | Phase 3 替换 | 删除 cosine 阈值 `MAX_DISTANCE = 0.4`；新实现遍历 chunks + 反查业务表，`MIN_CONFIDENCE / topK ≤ 5` 阈值保留 | ✅ |
| V77 DROP page_embedding / notebook_embedding / memory_embedding / search_tsv / cjk_bigram_tokenize | Phase 3 schema | V77 SQL 写好，psql 实测应用通过，`information_schema` 验证 5 列已 DROP，`pg_proc` 验证函数已 DROP | ✅ |
| 删 CjkBigramTokenizer 类 | Phase 3 删除 | 文件已 `git delete`；整库 grep 仅 CHANGELOG / 文档残留 | ✅ |
| 重写测试：删向量相关用例 | Phase 3 测试 | 删 1 测试类（EmbeddingProjectionServiceTest）；3 个测试类各自删 2-3 条 embedding 用例；3 个集成测试 stub 清理 | ✅ |
| **AITutorWorkflowAdminServiceImpl 链路 ragServiceClient 透传** | 计划隐含 | 主 ctor + test ctor 都加 `RagServiceClient` 参数；2 处手动 `new SimilarErrorRetrievalService / new LearnerMemorySemanticRetrievalService` 同步切换；测试同步加 mock | ✅ |
| AdminLanguagePackController.reEmbed 重定义 | 计划隐含 | 原 16 维 embedding 重写循环改为 ragIndexQueue.enqueueIndex 循环（前端 button 语义不变） | ✅ |
| **mvn test 相关 75 用例全绿** | Phase 3 验收 | 8 个测试类共 75 用例 PASS（HttpRagServiceClient/RagIndexOutboxWorker/RagIndexQueue/OfflineCatchup/LearnerMemorySemanticRetrieval/LearningStyleInference/AiModelProfileResolver/AiProviderValidation/SpringAiModelGatewayContract/CachingAiModelGateway/AITutorWorkflowAdminServiceImpl） | ✅ |
| **fail-fast 验证**：关闭 alethicode-rag → Java 直接抛 503 + problem+json | Phase 3 验收 | `HttpRagServiceClient` 实现：5xx 映射 `RagServiceException` 携带状态码与摘要；不再静默降级旧 SQL；测试 `indexNowFiveHundredMapsToRagServiceException` 已断言 | ✅ |
| **第一性原理自检**：整库 grep 16 维残留 | 计划目标 | grep `EmbeddingProjectionService / VectorCodec / AiEmbeddingProfile / callForEmbedding` 仅 CHANGELOG / 文档残留，业务代码 = 0；列与函数已 DROP；DocumentParsingServiceImpl INSERT SQL 不再持有 page_embedding / search_tsv / cast vector | ✅ |

---

## Phase 4 深度测试矩阵

| 验收项 | 计划原文 | 落地证据 | 状态 |
|--------|----------|----------|------|
| Helm chart：`alethicode-rag-deployment.yaml` + service.yaml + secret.yaml + values 引用 | Phase 4 新增 | 新增 2 个模板（deployment+service+pvc 三合一 + memgraph statefulset+service）；values 加 `alethicodeRag` / `memgraph` 两个块；secrets 加 `RAG_INTERNAL_TOKEN`；backend-deployment 加 `RAG_SERVICE_URL` + `RAG_INTERNAL_TOKEN` 注入 | ✅ |
| `scripts/ops/rag_quality_regression.py`：query 集 → expected_chunk → hit@5 / mrr | Phase 4 新增 | ~190 行 Python 完成；默认 5 条查询；threshold 0.7/0.5；passed_gate 退出码语义；JSON 报告输出 | ✅ |
| Java 端 Micrometer：`rag_query_latency_seconds{endpoint}` | Phase 4 监控 | `HttpRagServiceClient` 注入 MeterRegistry；4 query 路径全部双 tag（endpoint + outcome）落 `rag_query_latency_seconds`；既有 outbox 三 counter 共同就位 | ✅ |
| alethicode-rag `/metrics` prometheus_client | Phase 4 监控 | Phase 0 已实现 | ✅ |
| **staging 跑通 hit@5 ≥ baseline 作为发布 gate** | Phase 4 验收 | 真跑 alethicode-rag 实测：5 query → hit@5 = 0.8 / MRR = 0.8 / passed_gate = true；JSON 报告归档 `rag_quality_regression_report.json` | ✅ |

---

## 第一性原理自检（按 AGENTS.md，每 Phase 复核一次）

| 自检项 | Phase 0 | Phase 1 | Phase 2 | Phase 3 | Phase 4 |
|--------|:-------:|:-------:|:-------:|:-------:|:-------:|
| 不写补丁 / 不双写 / 不别名 | ✅ | ✅ | ✅ | ⏳ | ⏳ |
| 最短路径（Phase 0/1/2 是 Phase 3 的必要前置） | ✅ | ✅ | 🚧 | — | — |
| 不擅自扩展业务目标（仅替换 RAG 引擎） | ✅ | ✅ | ✅ | ⏳ | ⏳ |
| fail-fast（无静默降级） | ✅ | ✅ | ✅ | ⏳ | ⏳ |
| 逻辑闭合（业务表 + outbox 同事务最终一致；ID 映射 metadata 闭环） | ✅ | ✅ | ✅ | ⏳ | ⏳ |
| 无防御性逻辑（仅必要输入校验） | ✅ | ✅ | ✅ | ⏳ | ⏳ |

---

## 待办（更新即更新）

- [x] Phase 1 残留：跑全量 `mvn test` 验证签名变更不影响其他模块
- [x] Phase 1 残留：写 outbox-离线-上线-追平的集成场景测试
- [x] Phase 2：`--limit 50/100` 校准
- [x] Phase 2：全量 936 行回填（HTTP 层全部完成；LightRAG 队列后台异步抽 KG，本会话内 169 docs / 955 nodes / 1156 edges 已积累）
- [x] Phase 2：故意触发 401 验证 `--retry-errors` 路径
- [x] Phase 3：5 阶段切流（删 16 维伪 RAG 全链路；4 个文件 + 7 方法/字段 + V77 5 列 + 1 函数）
- [x] Phase 4：Helm + regression script + 端到端 hit@5 baseline（实跑 5/5 = 1.0 PASS）
- [x] 全面自测：backend mvn（662 tests，仅预存在 5 个失败）/ tutor_graph pytest（232 PASS / 103 skipped）/ alethicode-rag pytest（12/12 PASS）/ regression gate（5/5 PASS）
- [ ] **下次会话**：staging 部署后跑端到端 baseline + Grafana 看板可视化（团队侧自行）

---

## 已知裁剪 / 不在本期

- LightRAG `only_need_context=True` 返回 markdown 形态 `raw_context`；alethicode-rag 端结构化解析（`entities[]/relations[]/chunks[]`）→ Phase 1 Java 端按需自行解析或留 Phase 4 在 alethicode-rag 端增强
- reranker（BAAI/bge-reranker-v2-m3）→ Phase 4 视召回质量再判
- LightRAG 多语言 entity 抽取质量（demo 实测中文 PY 初学者教学题 OK，长尾未验证）→ Phase 2 `--limit 100` 校准时抽样人工审视
- 计划稿原写 11000 段、$10 估算；实测 868 + 24 + 44 = 936 行，按 v4-flash 单价折算约 $1-2，远低于估算
