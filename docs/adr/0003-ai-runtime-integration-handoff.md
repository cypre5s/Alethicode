# ADR-0003: AI Runtime 集成收口 — fail-fast、ownership、真实检索、生产级合规

- **Status**: Accepted
- **Date**: 2026-04-21

## 背景

`docs/todos/todo-ai-runtime-integration-handoff.md` 汇总了 LangGraph + Spring AI
两条主线已落地后仍残留的 P0/P1 集成缺口。本 ADR 记录一次性收口的最终决策。

## 决策摘要

### 1. Java 侧 fail-fast 边界

- `TutorWorkflowAuthorizer` 集中 problem 可访问性、language 白名单、submission 归属 /
  AC 校验；每条校验使用独立异常，Controller 映射到 401 / 403 / 404 / 409 / 422。
- `TutorWorkflowController` 所有 POST 端点（createSession / createRun /
  checkpoint-restorations / interrupt-responses）有 256 KiB body 上限。
- `createRun` 语言解析优先级：`session projection → event_data → request.language`，
  不允许任何 `Python3` 默认兜底；不在 allowed languages 列表直接 422。

### 2. WebSocket 安全三道闸

- `WebSocketOriginConfigurer` 收口 Origin 白名单（`website.base-url` + 开发 localhost）
- `ClassroomHandshakeInterceptor` 强制登录；`ATTR_USER_ID` 仅信任正数
- `TutorWorkflowWebSocketHandler.afterConnectionEstablished` 校验 session ownership；
  同 session 第二次连接替换并关闭旧连接

### 3. Python `tutor_graph` 严格 fail-fast

- 删除 Postgres checkpointer 失败后回落 `MemorySaver` 的静默降级
- 显式 `TUTOR_GRAPH_CHECKPOINTER=postgres|memory` 枚举；非法值拒绝启动
- 单 worker 强制（Dockerfile `--workers 1`），模块级 `_run_events` / `_active_runs` 不跨进程

### 4. Internal 工具 API 接真实业务域

- `getLearnerState` 接 `LearnerProfileProjector`（mastery / weak KC / memory / action bias）
- `getCoursewareHits` 接 `CoursewareRetrievalService`（KC + 章节 + language pack）
- `getSimilarErrors` 接 `SimilarErrorRetrievalService`（pgvector 相似度排序）
- `createTransferProblem` 真实写入 `problem` 表 + `ai_tutor_side_effect_log` 幂等 / hash 冲突 409

### 5. 生产级合规（中国大陆）

- PIPL：`PiplDataSubjectService` + `/api/privacy/data-exports|/personal-data`；审计写
  `pii_access_log`（5 年保留）
- AIGC：`AigcComplianceService.labelAiGeneratedContent` + `auditGeneration`；输入输出
  留 6 个月（`aigc_audit_log.retention_expires_at`）
- 等保 2.0 / DSL：SBOM（`scripts/ops/generate_sbom.sh`）+ CI Trivy 扫描 + 阿里云镜像

### 6. 可观测性

- Java / Python 双侧接入 OpenTelemetry；W3C traceparent 跨进程
- 结构化日志（Logback JSON + Python OTel LoggingInstrumentor），Loki / 阿里云 SLS 可索引
- `management.endpoint.health.probes.enabled=true` 拆分 liveness / readiness
- Resilience4j RateLimiter 针对 tutor workflow / admin write，429 + Retry-After

### 7. 删除 V2 后缀

- V1 `AITutorWorkflowController` 已删除后，`V2` 成为冗余命名；重命名为 `TutorWorkflowController`
- Javadoc / SQL 注释 / CHANGELOG 本次新增条目同步更新；历史条目保留 snapshot

## 后果

**正面**
- 所有合规需要的落地点都已在代码 + DB 层面就位
- OTel / 结构化日志 / 健康检查三件套让线上排障从"看日志猜"变成"trace 定位 + SLO 告警"
- 明年 LLMOps（Langfuse 自建）只需在 `AiModelGateway` 接 hook

**负面 / 绑定**
- 多引入 Resilience4j + Micrometer Tracing + Logstash Encoder + OTel 四类依赖
- tutor_graph 单 worker 的硬约束直到 ADR-0005 Redis 改造前不会放松
- 生产必须显式设置 `INTERNAL_SERVICE_KEY`（非 dev 默认），`InternalServiceKeyValidator` 保证这一点

## 后续

- 下次迭代：Redis 共享状态（ADR-0005）+ SSE 替换 HTTP 轮询
- Langfuse 接入点已预留在 `AiModelGateway`
- `AITutorWorkflowAdminServiceImpl` 的手动 new + Spring @Service 双实例仍需清理（独立 PR）
