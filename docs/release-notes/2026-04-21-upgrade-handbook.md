# 2026-04-21 Alethicode 升级说明书

面向读者：下一个接手工程师、SRE、运维、架构 review。上线前必读。升级内容很多，本文件是唯一的总览；细节参考对应 ADR / CHANGELOG。

## 一、升级概要

本次升级包含 5 批互相叠加的改动，全部针对**中国大陆生产部署 + 国际标准对齐**：

1. **AI Runtime 集成收口**：实施 `docs/todos/todo-ai-runtime-integration-handoff.md` 的 15 项 P0/P1，让 LangGraph tutor_graph + Spring AI Gateway 双线进入 production-ready 状态。
2. **首轮 Code Review 修复**：6 项（1 严重 + 5 中等），包括上线会 100% 破坏的认证契约 bug。
3. **安全与承载压力审计修复**：10 项（1 严重 P1 性能索引 + 4 中等安全/性能 + 5 优化）。
4. **V2 命名清理**：V1 已删除后，`AITutorWorkflowV2Controller` 去掉 V2 后缀。
5. **架构成熟度升级**（合规 + 国际标准）：OpenTelemetry、结构化日志、Resilience4j 限流、AIGC/PIPL 合规服务、CI/SBOM、ADR、威胁模型、SLO/告警。

涉及 **3 个 DB 迁移**（V57 / V58 / V59）、**4 套新依赖**（OTel / Resilience4j / Logstash / Cachetools）、**2 个新 Python 包组**（OTel instrumentation）、**1 个新服务**（Jaeger）。

---

## 二、按模块的改动映射

### 2.1 Tutor Workflow（AI 导学）

| 改动 | 代码位置 | 目的 |
|------|---------|------|
| Controller 重命名 | `AITutorWorkflowV2Controller` 改名 `TutorWorkflowController` | 统一命名，删除过渡期 V2 后缀 |
| fail-fast 收口 | `TutorWorkflowAuthorizer` + 6 个独立异常 | 集中 problem / language / submission / AC 校验 |
| language 解析 | `TutorWorkflowController.resolveLanguage` | 优先级 session projection > eventData > request |
| Body size 限制 | `enforceRequestBodyLimit` (256 KiB) | 防 OOM |
| 错误响应脱敏 | `fail503Redacted(action, e)` | 下游异常只进日志 |
| Rate limit | `@RateLimiter(tutorWorkflow)` 20 req/s + 429 Retry-After | 防刷 |
| 认证契约 | `extractUserId` 优先读 `getDetails()` | 对齐 `SessionAuthenticationFilter` |
| WebSocket 安全 | Origin 白名单 + HandshakeInterceptor + ownership | 三道闸 |
| Poller 重复订阅保护 | `runPollers` map + 旧 thread interrupt | 防 interrupt/resume 重复事件 |
| Poller 提前退出 | `sessionMap.containsKey` + `Thread.interrupted()` | session 关闭立即释放 |
| MAX_RUN_DURATION | 60min 降到 10min | 避免资源久占 |
| deleteSession 级 interrupt | `webSocketHandler.interruptPoller(runId)` | 确定性释放 |

### 2.2 Internal Tutor Tool API

- `getLearnerState` 真实化 → 接 `LearnerProfileProjector`
- `getCoursewareHits` 真实化 → 接 `CoursewareRetrievalService`
- `getSimilarErrors` 真实化 → 接 `SimilarErrorRetrievalService`
- ERROR_FEEDBACK 一次查询 → `loadLatestErrorContext` 合并 2 次 SQL
- `validateServiceKey` null / 空 header 检查
- 通用 runtime 异常脱敏

### 2.3 tutor_graph（Python）

- 依赖补齐：`langchain-openai`, `cachetools`, 5 个 OTel 包
- Checkpointer fail-fast：移除静默降级 MemorySaver
- `TUTOR_GRAPH_CHECKPOINTER` 枚举：postgres（默认）/ memory（测试）
- Dockerfile 单 worker + `pip install .`
- dict 改 `TTLCache(maxsize=10000, ttl=3h)` 防 OOM
- OTel 接入 `app/observability.py`

### 2.4 数据库迁移

| 迁移 | 目的 |
|------|------|
| V57__ai_tutor_workflow_session_language.sql | 加 `language` 列 + 回填 + 2 索引 |
| V58__ai_tutor_workflow_event_client_event_index.sql | 修 V57 索引错位 |
| V59__compliance_audit_and_sensitive_log.sql | 3 张合规表 |

### 2.5 前端

- 移除所有 `Python3` fallback → `_resolveProblemLanguage()`
- Watchdog 重试 90 → 30（与后端 10min 对齐）

### 2.6 可观测性

- OTel + tracing bridge：Java + Python 双端接入 W3C traceparent
- 结构化日志：`logback-spring.xml` prod profile JSON
- Health 分层：`/actuator/health/liveness` + `/readiness`
- Jaeger 服务：compose profile `observability`

### 2.7 合规

- `AigcComplianceService`：标识 + 审计 + Micrometer counter
- `PiplDataSubjectService` + `PrivacyController`：`/api/privacy/*`
- `InternalServiceKeyValidator`：prod profile 强校验

### 2.8 韧性与防护

- Resilience4j RateLimiter：tutorWorkflow (20/s) + adminWrite (10/s)
- `GlobalRestExceptionHandler`：统一 422 / 413 / 429 / 400
- `TutorGraphClient` JdkClientHttpConnector + 5s connect

### 2.9 DevOps / 供应链（国内镜像）

- `.github/workflows/ci.yml` 三语言 + Trivy + SBOM
- `.github/dependabot.yml` 北京时间凌晨扫描
- `.pre-commit-config.yaml`
- `scripts/ops/generate_sbom.sh`

### 2.10 架构文档

- `docs/adr/` 6 个 ADR
- `docs/security/threat-model.md` STRIDE
- `docs/sre/slo.md` 4 服务 SLO
- `deploy/observability/prometheus/alerts.yml` 13 条告警
- `deploy/nginx/tutor-workflow-sticky.conf.example`

---

## 三、部署前检查清单

上线前必须逐项打勾，缺失任意一项都可能是 P0 事故起点。

### 3.1 环境变量（Java 后端）

- [ ] `INTERNAL_SERVICE_KEY` 长度 >= 24 字符，非 `dev-internal-key`
- [ ] `SPRING_PROFILES_ACTIVE=prod`（或 production / release）
- [ ] `OPENAI_API_KEY` / `LLM_BASE_URL=https://api.deepseek.com/v1` / `LLM_MODEL=deepseek-chat`（默认 DeepSeek，其他 provider 见 ADR-0004）
- [ ] `EMBEDDING_API_KEY` / `EMBEDDING_BASE_URL` / `EMBEDDING_MODEL` 同上
- [ ] `TUTOR_GRAPH_BASE_URL=http://tutor-graph:8100`
- [ ] `ALETHICODE_WEBSOCKET_ALLOWED_ORIGINS`（可选）
- [ ] `OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4318/v1/traces` 或阿里云 ARMS
- [ ] `OTEL_SAMPLING_PROBABILITY=0.01` 生产建议低采样
- [ ] `DB_PASSWORD` / `REDIS_PASSWORD` 从密钥管理系统注入

### 3.2 环境变量（tutor_graph）

- [ ] `TUTOR_GRAPH_CHECKPOINTER=postgres`（严禁 memory 上线）
- [ ] `TUTOR_GRAPH_DATABASE_URI` 指向专用 LangGraph schema
- [ ] `TUTOR_GRAPH_JAVA_TOOL_BASE_URL=http://backend:8080`
- [ ] `TUTOR_GRAPH_INTERNAL_SERVICE_KEY` 与 Java 侧一致
- [ ] `TUTOR_GRAPH_LLM_API_KEY` / `TUTOR_GRAPH_LLM_BASE_URL` / `TUTOR_GRAPH_LLM_MODEL`
- [ ] `TUTOR_GRAPH_REACT_ENABLED=false`
- [ ] 启动必须 `--workers 1`

### 3.3 数据库

- [ ] Flyway 自动应用 V57 / V58 / V59
- [ ] `problem.is_public` / `problem.visible` / `problem.languages` 列存在
- [ ] `submission.user_id` / `problem_id` / `result` 存在
- [ ] `user` 表有 id / username / email 列
- [ ] LangGraph Postgres 与 Alethicode 主库隔离（可同集群不同 schema）

### 3.4 部署拓扑

- [ ] Java 副本数 > 1 时 Nginx/Ingress 配 sticky session
- [ ] tutor_graph 严格 1 副本（ADR-0005 落地前）
- [ ] Jaeger / ARMS / SLS Trace 任选一个作为 OTLP 接收端
- [ ] Prometheus scrape backend `/actuator/prometheus`
- [ ] 加载 `deploy/observability/prometheus/alerts.yml`
- [ ] Alertmanager 配企业微信 / 钉钉 / 飞书机器人

### 3.5 合规

- [ ] 《生成式 AI 管理办法》算法备案 / 模型供应商清单已交网信办
- [ ] PIPL 隐私政策说明 `/api/privacy/*` 入口
- [ ] 等保 2.0 定级 / 备案启动
- [ ] AIGC 敏感词扫描接入内容安全供应商
- [ ] 学生代码留存策略明确

### 3.6 CI 供应链

- [ ] Maven settings.xml 配阿里云 mirror
- [ ] Docker 镜像走阿里云 ACR / 腾讯 TCR
- [ ] SBOM 在 release 流程生成并归档 90+ 天
- [ ] Trivy fs 扫描零 CRITICAL / HIGH

---

## 四、新增 REST / WebSocket 端点

| 方法 | 路径 | 用途 | 鉴权 | Rate Limit |
|-----|------|------|------|-----------|
| POST | /api/ai/tutor-workflow-sessions | 创建导学 session | 登录 | tutorWorkflow |
| GET | /api/ai/tutor-workflow-sessions/{id} | 查询 session | 登录+owner | - |
| DELETE | /api/ai/tutor-workflow-sessions/{id} | 终止 session | 登录+owner | - |
| POST | /api/ai/tutor-workflow-sessions/{id}/runs | 发起 run | 登录+owner | tutorWorkflow |
| GET | /api/ai/tutor-workflow-sessions/{id}/checkpoints | 列 checkpoint | 登录+owner | - |
| POST | /api/ai/tutor-workflow-sessions/{id}/checkpoint-restorations | 恢复 | 登录+owner | tutorWorkflow |
| POST | /api/ai/tutor-workflow-sessions/{id}/interrupt-responses | 响应 interrupt | 登录+owner | tutorWorkflow |
| WS | /ws/tutor-workflow-sessions/{id} | 实时 runtime_event | session cookie + owner | - |
| POST | /api/privacy/data-exports | PIPL 个人数据导出 | 登录 | adminWrite |
| DELETE | /api/privacy/personal-data | PIPL 删除请求 | 登录 | adminWrite |
| GET | /actuator/health/liveness | k8s liveness probe | 无需 | - |
| GET | /actuator/health/readiness | k8s readiness probe | 无需 | - |

所有 POST / DELETE 受 CSRF 保护，需 X-CSRFToken header。

---

## 五、内部 API（tutor_graph -> Java）

所有需 X-Internal-Service-Key header：

- GET /internal/ai-tutor/problems/{id}/workflow-context
- GET /internal/ai-tutor/submissions/{id}/diagnosis-evidence
- GET /internal/ai-tutor/learners/{id}/state
- GET /internal/ai-tutor/problems/{id}/courseware-hits
- GET /internal/ai-tutor/learners/{id}/similar-errors
- POST /internal/ai-tutor/transfer-problems
- POST /internal/ai-tutor/workflow-events

---

## 六、新增依赖清单

### Java (pom.xml)

- micrometer-tracing-bridge-otel
- opentelemetry-exporter-otlp
- net.logstash.logback:logstash-logback-encoder:8.0
- io.github.resilience4j:resilience4j-spring-boot3:2.2.0
- io.github.resilience4j:resilience4j-reactor:2.2.0

### Python (pyproject.toml)

- langchain-openai >= 0.3
- cachetools >= 5.3
- opentelemetry-api >= 1.27
- opentelemetry-sdk >= 1.27
- opentelemetry-exporter-otlp-proto-http >= 1.27
- opentelemetry-instrumentation-fastapi >= 0.48b0
- opentelemetry-instrumentation-httpx >= 0.48b0
- opentelemetry-instrumentation-logging >= 0.48b0

---

## 七、回滚指引

按风险从低到高：

1. 回滚 CI / Dependabot / pre-commit / ADR / SBOM / Nginx 示例：删文件即可
2. 回滚 OpenTelemetry：清空 OTEL_EXPORTER_OTLP_ENDPOINT
3. 回滚 Rate Limit：改 limit-for-period=200 或删 @RateLimiter 注解
4. 回滚 PIPL / AIGC API：删 PrivacyController + AigcComplianceService
5. 回滚 V59 migration：DROP 3 张表（但合规审计断裂）
6. 回滚 V58 索引：DROP INDEX idx_atwf_event_session_client_event
7. 回滚 V57 language 列：不推荐，Controller 已依赖
8. 完全回滚 TutorWorkflowController 重命名：git revert

---

## 八、测试矩阵

```
cd backend && mvn -q -DskipTests -T 1C compile
mvn -q -Dtest='!*IntegrationTest,!*ContractTest,!AITutorWorkflowAdminServiceImplTest,!BetaFeatureRegistryTest,!ErrorReviewPackageServiceTest,!AdminProblemTeacherPermissionTest' test
mvn -q -Dtest='InternalServiceKeyValidatorTest,TutorWorkflow*,*TutorWorkflow*,Ai*Test,SpringAi*Test' test
cd ../tutor_graph && python -m pytest -q
cd ../frontend && npm test && npm run build
```

已知 pre-existing 失败（与本次改动无关，不是 blocker）：

- BetaFeatureRegistryTest
- ErrorReviewPackageServiceTest.createPackageShouldReturnBeforeAiSpecializedProblemsFinish
- AdminProblemTeacherPermissionTest.teacherShouldListProblemsWithoutLanguagePackScopeRestriction
- *IntegrationTest（需本地 Postgres + 正确密码）
- AITutorWorkflowAdminServiceImplTest（method signature mismatch）

---

## 九、监控告警 Runbook 要点

| 告警 | 含义 | 处置 |
|------|------|------|
| BackendErrorRateHigh | 5xx > 1% 10 分钟 | 查 Jaeger trace + 最近部署 |
| BackendLatencyP95High | p95 > 400ms 15 分钟 | 查 DB slow query + LLM call 延迟 |
| TutorWorkflowActiveRunsSpike | 活跃 run > 500 | 检查 poller 泄露 / tutor_graph OOM |
| TutorGraphPollFailureRate | Java-tutor_graph 轮询失败 > 5% | 检查 tutor_graph 健康 + 网络 |
| AiCircuitBreakerOpen | LLM 连续 5 次失败 | 切换备用 provider |
| AigcAuditWriteFailing | AIGC 审计写失败 | 合规警报，立即修 DB 或暂停 AI 生成 |
| PiiAccessLogWriteFailing | PII 审计写失败 | 合规警报，立即修 DB 或暂停相关 API |

---

## 十、后续待做（独立 PR）

按优先级：

1. Redis 共享状态（ADR-0005）：Java 多副本必需
2. SSE 替换 HTTP 轮询：减少 tutor_graph 压力
3. Testcontainers：让 *IntegrationTest 在 CI 里可跑
4. Langfuse 自建 / LLMOps：prompt 版本化 + 自动 eval
5. 内容安全供应商接入：AigcComplianceService.scanForSensitiveContent
6. 等保 2.0 三级备案
7. AIGC 算法备案（网信办）
8. mTLS Java-tutor_graph

---

## 十一、参考文档索引

- CHANGELOG.md 按时间倒序的完整日志
- docs/adr/* 架构决策
- docs/security/threat-model.md STRIDE 威胁模型
- docs/sre/slo.md SLO / SLI
- docs/todos/todo-ai-runtime-integration-handoff.md 本次源头 TODO
- deploy/docker-compose.yml 本地 / 小规模部署
- deploy/observability/prometheus/alerts.yml 告警规则
- deploy/nginx/tutor-workflow-sticky.conf.example 多副本反代示例

---

下一位接手的工程师：先读 docs/adr/0003 和本文件，然后看 todo_ai_runtime_integration_handoff.md 的已完成 vs 未做对照；剩余未做部分的独立 PR 路线见第十节。
