# 课件内容包初始化 → RAG 链路硬化设计

> **日期**：2026-04-29  
> **作者**：cypress + Cursor  
> **状态**：DESIGN (待审批)  
> **关联前置**：
> - `docs/plans/2026-04-28-rag-lightrag-migration-status.md`（sprint 12 RAG 切流落地状态）
> - `docs/plans/2026-04-28-language-pack-init-quality-design.md`（课件包入库质量校验）
> - `docs/plans/2026-04-25-persistent-memory-layer-design.md`（V75 outbox + worker 设计）
> - 对应 commit 链：`f5e538bb / ff9f626e / 2cd8091d / b71bf93f / 250ef26a / 4ceff571`

---

## 一、动机

4/29 sprint 12 RAG 切流落地后做端到端验证时，发现 alethicode-rag KG 完全空（`memgraph nodes=0`），但 vector chunks 有 868 行（来自 outbox worker 实时推送）。深挖后定位为 **`OPENAI_API_KEY` 在 `deploy/.env` 是空字符串，被 docker compose `env_file` 加载后覆盖了 `environment` 段已经从 shell env（含 `backend/.env` 真实 key）替换好的真值**，导致 alethicode-rag 启动后调 LLM 全部 `Authentication Fails (governor)`，LightRAG 的 entity extraction 阶段 100% 失败但 vector embedding（用 zhipu embedding API，与 LLM 不同 key）依然成功。

这次问题的本质 **不是 RAG 设计问题，是初始化阶段的"配置链路 + 失败可见性 + fail-fast"全部缺失**：

1. 课件持续入库时，业务侧已经全部 commit + outbox 已写，但 RAG 端 `lightrag_doc_status` 累积了 928 个 `failed`，没有任何机制主动告警；
2. `start.sh` 启动 alethicode-rag 时不校验 LLM key 是否真的能用，只校验 `/health` 端点（health 用本地 ping 不需要 LLM）；
3. 课件激活/发布前没有"RAG 链路就绪"的预检，所以一旦 RAG 不可用，错误形态是"业务能写但用户用不到"，最难诊断；
4. 管理员后台对 alethicode-rag 内部 `lightrag_doc_status` 完全不可见，只能登容器读 PG。

本设计目标：**让"课件包初始化 → 学生能看到 RAG 检索结果"这条用户旅程在任何环境异常下都 fail-fast、可见、可重试**。不引入新的微服务，不重写 RAG 设计。

---

## 二、现状（What is）

### 2.1 现有路径

```
管理员上传课件 PDF
  └─> LanguagePackInitService.startInit / Temporal pipeline
       └─> DocumentNormalizationService (normalize)
            └─> DocumentParsingServiceImpl.persistPage(...) [transactional]
                 ├─> INSERT INTO language_pack_page (业务表)
                 └─> ragIndexQueue.enqueueIndex(COURSEWARE_PAGE, pageId, content, metadata)
                      └─> INSERT INTO rag_index_outbox(action='INDEX')
RagIndexOutboxWorker (Spring @Scheduled fixed-delay 30s)
  └─> SELECT pending FROM rag_index_outbox
  └─> POST alethicode-rag /v1/rag/index/courseware-page
       └─> LightRAG: chunk + embedding + entity extract + KG write
            ├─> 成功 → lightrag_doc_status='processed'
            └─> 失败 → lightrag_doc_status='failed' (LLM auth / network / etc.)
```

### 2.2 现有保护层

| 保护 | 在哪 | 强度 |
| --- | --- | --- |
| 业务写入 ↔ outbox 同事务 | `RagIndexQueueService.@Transactional` | ✅ 强 |
| 幂等 | `(entity_type, entity_id, action)` 唯一约束 | ✅ 强 |
| outbox 自动重试 | `RagIndexOutboxWorker.attempts < 5` | ⚠️ 中 |
| 学生侧"包就绪"判断 | `listQaPacks` SQL 检查 `preview_asset_path` 全有 | ⚠️ 弱（不看 RAG） |
| 容器 `/health` | `app/routes/health.py` 检查 PG + memgraph | ⚠️ 弱（不查 LLM） |
| init quality report | `LanguagePackInitQualityReportService` | ⚠️ 弱（不查 RAG） |

### 2.3 现有薄弱点（来自本次故障复盘）

1. **配置链路脆弱**：`backend/.env`（含真实 key）+ `deploy/.env`（含空 key）+ docker compose `environment:` 段 + `env_file:` 段，叠加先后顺序复杂，任一改动都可能让"看似可用"变成"运行时 fail"。
2. **失败不可见**：`lightrag_doc_status='failed'` 的累积没有指标推送、没有告警、没有管理员后台可视化。
3. **批量延迟**：outbox worker fixed-delay=30s，500 页课件等待 KG 全量抽取要走至少 17 轮（即 8.5 分钟仅排队，加上 LLM 调用本身耗时，总时长以小时计）。
4. **就绪判定不准**：学生侧"包可问答"只看 `preview_asset_path`，可能 KG 完全空也通过；管理员侧"包发布"完全不查 RAG 状态。

---

## 三、设计（What to do）

按"是否必改 / 改造侵入度"分三层。每层独立可验收，可分阶段交付。

### 3.1 **Tier 1：当下必改（防回归 + 最小代价）**

#### T1.1 启动期 LLM key fail-fast

**位置**：`start.sh` 的 `start_alethicode_rag` 函数，在 `docker compose up -d ... alethicode-rag` 之前。

**逻辑**：

```bash
start_alethicode_rag() {
  : "${OPENAI_API_KEY:?OPENAI_API_KEY is empty; check backend/.env}"
  : "${EMBEDDING_API_KEY:?EMBEDDING_API_KEY is empty; check backend/.env}"
  if [[ -z "${DB_PASSWORD}" ]]; then
    echo "[ERROR] DB_PASSWORD not resolved; resolve_postgres_credentials must run first" >&2
    exit 1
  fi
  # 已有 docker rm -f + docker compose up
}
```

也可在 alethicode-rag `app/main.py` 的 startup hook 加自检：发一个最小 chat completion，失败则 `sys.exit(1)`，让容器明确不健康，比 health 200 但 LLM 不通要早暴露问题。

**收益**：把"配置错误但容器假装健康"的窗口从分钟级降到秒级。

#### T1.2 课件包激活前的 RAG 链路预检

**新增**：`AdminLanguagePackController.publishLanguagePack` 在置 `status='published'` 之前，调一次：

```java
ragHealthCheckService.assertReadyForPublish();
//  - alethicode-rag /health == ok（postgres+memgraph）
//  - 一次最小 query/courseware（mode=naive）必须 ≤2s 返回 200
//  - rag_index_outbox 当前 worker 心跳 < 60s（通过 sys_options 或专门表）
```

任一失败 → `BadRequestException("RAG 链路未就绪，无法发布")`，UI 弹 toast 让管理员先修。

**收益**：杜绝"已发布但学生问答全空"的状态。

#### T1.3 容器配置稳定性沉淀

**已经做**（4/29 commit `250ef26a`）：alethicode-rag docker-compose 段去掉 `env_file: .env`，不再让 deploy/.env 空 key 覆盖 environment 段。

**还要做**：
- `deploy/.env.example` 把 `OPENAI_API_KEY=` / `EMBEDDING_API_KEY=` 这种空值占位**删掉**（保留注释说明），鼓励放在 `backend/.env` 或 secret manager
- `start.sh` 在 source `backend/.env` 之后立即 export `OPENAI_API_KEY EMBEDDING_API_KEY`，明确这两个值的源头

#### T1.4 大批量入库后主动唤醒 LightRAG

**位置**：`DocumentParsingServiceImpl.parseAndPersistDocument` 的最末（事务 commit 之后）。

**逻辑**：当本批次的 page 数量 ≥ 阈值（默认 50）时，主动调一次：

```java
ragServiceClient.wakeUpPipeline();  // POST /v1/rag/index/courseware-page 一个 noop 触发器
```

LightRAG 内部 `aprocess_enqueue_documents` 一旦被触发会 drain 整个 pending 队列，等于把 outbox worker 30s 心跳压成"几乎实时"。

**收益**：500 页课件批量入库的 KG 抽取从"几小时排队"压到"几小时纯 LLM 计算"，去掉无效等待。

### 3.2 **Tier 2：短期改进（DX + 运维可见性）**

#### T2.1 管理员后台 RAG 状态 API

**新增**：`GET /api/admin/language-pack/{id}/rag-status`

返回结构：

```json
{
  "language_pack_id": 43,
  "total_pages": 561,
  "outbox": { "pending": 12, "in_flight": 3, "succeeded": 540, "given_up": 6 },
  "rag_pipeline": { "processed": 540, "processing": 3, "pending": 12, "failed": 6 },
  "kg": { "entity_count": 1623, "relation_count": 2008, "last_extraction_at": "..." },
  "vector": { "chunk_count": 1024, "last_indexed_at": "..." },
  "ready_for_publish": false,
  "blockers": ["6 failed docs need rebuild"]
}
```

数据来源：`language_pack_page` JOIN `rag_index_outbox` JOIN `lightrag_doc_status` JOIN memgraph cypher count。

**UI**：在课件管理详情页新增"RAG 索引状态"卡片，红/黄/绿三色 + 一键重建按钮。

#### T2.2 失败 doc 重建入口

**新增**：`POST /api/admin/language-pack/{id}/rag-rebuild`

**逻辑**：
1. 找出该 pack 在 `lightrag_doc_status` 中 `status='failed'` 或 `processing` 但 stale > 1h 的 doc_id
2. 在 `rag_index_outbox` 重新 `enqueueIndex` 这些 page（attempts 重置）
3. 同时 POST 一个 wake-up 触发 LightRAG drain

**预期**：管理员一键修复"为什么我的 pack 学生用不到"，无需进容器。

#### T2.3 学生侧 listQaPacks 就绪语义扩展

**位置**：`LanguagePackQaServiceImpl.listQaPacks`

**改动**：从仅看 `preview_asset_path` 扩展到看 `language_pack_init_active_execution_state.rag_ingestion_status='ready'`（V48 已有 init_active_execution_state，需要补 rag_ingestion_status 字段或读 rag-status API 的聚合）。

**收益**：学生侧不再看到"已发布但 KG 还在抽"的伪就绪 pack。

### 3.3 **Tier 3：长期（容量 + 可演进）**

#### T3.1 KG 增长容量与去重

**问题**：单实例 alethicode-rag KG 当前一个 Python 包就 ~2000 nodes / ~2500 edges。10 个 pack 并发 ingestion 时可能：
- LLM 调用排队，TPM/RPM 触限
- memgraph node/edge 数线性增长，单实例内存压力
- entity 抽取的同义词没合并（"列表"/"list"/"List" 各自一个 entity）

**短期**：
- LightRAG `entity_merge_threshold` 调高（让相似 entity 合并）
- LLM 调用分 pool（高优低优）

**长期**：
- alethicode-rag 横向扩展（多实例 + memgraph cluster）
- 单 pack 独立工作区（每个 `language_pack_id` 一个 LightRAG `workspace`）

#### T3.2 课件初始化全链路 e2e 冒烟

**新增**：`backend/src/test/java/.../LanguagePackRagInitIntegrationTest.java` 或独立 e2e 脚本。

**用例**：
1. 启动全栈 docker compose
2. 上传一个最小 1-page PPT 课件包
3. 等待 outbox worker drain（最多 60s）
4. 调 `/api/admin/language-pack/{id}/rag-status`，断言 `kg.entity_count > 0`
5. 调 `/api/language-pack-qa` query，断言响应非空且引用了正确 chunk

**纳入 CI**：sprint 12 之后凡 RAG 链路改动都要这条 e2e 通过才能合并。

---

## 四、实施分期

| 阶段 | 工时 | 验收 |
| --- | --- | --- |
| Phase A: T1.1 + T1.3 + T1.4 | 半天 | start.sh 启动期 fail-fast；docker compose 不再被 env_file 覆盖；500 页课件入库后 60 秒内开始 KG 抽取 |
| Phase B: T1.2 | 半天 | 课件激活按钮在 RAG 不可用时直接报错，UI 显示具体 blocker |
| Phase C: T2.1 + T2.2 + T2.3 | 1-2 天 | 管理员能看 RAG 状态、能一键重建；学生侧 listQaPacks 反映真实 KG 就绪 |
| Phase D: T3.1 + T3.2 | 1-3 天 | 多 pack 并发 ingestion 不爆内存；新增 e2e 测试 CI 跑通 |

**建议**：Phase A 与 B 在下个迭代立刻做，C 与 D 进入 sprint 13 backlog。

---

## 五、风险与未做项

### 5.1 不在本设计范围

- LightRAG 1.4.15 本身的 KG 抽取 prompt 工程优化（"为什么 entity 名字是英文 Key-Value Pair 而不是中文"等）—— 是 alethicode-rag 内部 LLM prompt 的 follow-up，建议另起 `2026-XX-XX-rag-prompt-quality.md`
- entity 同义词合并的 deepseek-v4-flash 适配性 —— 需 A/B 实验，独立设计
- WSL2 mirrored networking 在生产 Linux 服务器的迁移规范 —— 已在 `2026-04-29-backend-service-relocation.md` 提及，部署阶段单独写

### 5.2 已识别但本期不解决

- `failed=928` 历史死信清理：先观察 sprint 12 切流后是否还增长，若稳定则下个迭代写一次性清理脚本
- 课件包"软删除"语义：`is_active = false` 时 outbox 是否要级联 DELETE → 单独 design

### 5.3 主要风险

- **LLM rate limit 与 KG 抽取吞吐**：本设计假设 deepseek-v4-flash 速率宽松，若上游限速实质达不到 ~6 doc/min，T1.4 主动唤醒不能解决问题，需要 T3.1 横向扩展先做。
- **wake-up 端点的设计**：POST 一个 noop 触发器是 hack；正规做法是 alethicode-rag 暴露 `POST /v1/rag/pipeline/drain`。在 T1.4 实现时改造。
- **预检与发布原子性**：T1.2 的健康检查与 publish 之间存在窗口（健康通过后 publish 之前 RAG 挂掉）。本期接受窗口，长期可加 retry-publish。

---

## 六、验收清单（汇总）

- [ ] T1.1：`start.sh` 启动 alethicode-rag 前 OPENAI_API_KEY/EMBEDDING_API_KEY/DB_PASSWORD 任一空时立即 exit 1
- [ ] T1.2：管理员发布课件时若 alethicode-rag 不可用，按钮报 422 + 弹 toast 提示具体 blocker
- [ ] T1.3：`deploy/.env.example` 不再有空 key 占位；`backend/.env` 注释明确这是 LLM key 的唯一源头
- [ ] T1.4：500 页课件批量入库后 60 秒内 LightRAG 开始 drain（看 `lightrag_doc_status.processing > 0`）
- [ ] T2.1：`GET /api/admin/language-pack/{id}/rag-status` 返回结构如上；UI 课件管理页有该卡片
- [ ] T2.2：`POST /api/admin/language-pack/{id}/rag-rebuild` 能让 failed doc 重新走 outbox
- [ ] T2.3：`listQaPacks` 不再返回 KG 完全空的 pack（除非显式开启降级开关）
- [ ] T3.1：单 alethicode-rag 实例支撑 10 pack × 600 page × 6 并发不爆内存
- [ ] T3.2：CI 中 `LanguagePackRagInitIntegrationTest` 跑通

---

