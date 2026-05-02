# Alethicode 工程债务清单 — 对标顶尖实践

> 2026-04-18 深度对标调研：IntelliCode (EACL 2026 multi-agent tutoring)、CogEvo-Edu、Spring Boot 67项生产清单、2026年多Agent框架评估
> 项目规模：384 Java 文件 / 34K LOC + 173 前端文件 / 2核4GB 云主机部署

---

## 1. 架构缺陷

### 1.1 无 Agent 状态一致性保证 [HIGH]

**现状**：OrchestratorAgent 派发到 GuideAgent/DiagnosticsAgent 等子 Agent 时，learner state 的读取和更新没有并发控制。

**对标**：IntelliCode (EACL 2026) 采用 single-writer policy + StateGraph Orchestrator，只有 Orchestrator 有权写入 learner state，所有 Agent 都是 pure function（只读 state → 返回 proposed update）。

**风险**：两个并发请求同时触发 MasteryService.updateMastery，后写覆盖前写。

**修复方案**：在 OrchestratorAgent 或 AITutorWorkflowDomainServiceImpl 层加 per-user lock（`synchronized(userId)` 或 Redis 分布式锁）。

### 1.2 无 Agent 执行 Checkpoint/Resume [HIGH]

**现状**：Agent 执行是一次性的。如果 LLM 调用超时、服务重启，整个 workflow 丢失。

**对标**：LangGraph 提供 checkpoint/resume/time-travel debugging，IntelliCode 的 StateGraph 支持 atomic state snapshot。

**风险**：用户正在做题，Agent 返回诊断结果途中服务重启，所有上下文丢失。

**修复方案**：在 `ai_workflow_event` 表中持久化每一步 Agent 输出（已部分实现），增加 resume-from-last-event 能力。

### 1.3 单体同步架构 [MEDIUM]

**现状**：所有 Agent 在 Tomcat 线程池内同步执行。LLM 调用耗时 5-30 秒，占用整个线程。

**对标**：生产级系统用 WebFlux/Virtual Threads + 消息队列（Redis Stream/Kafka）解耦 Agent 执行。

**风险**：50 并发 AI 请求 × 30 秒 LLM 响应 = Tomcat 线程池耗尽，非 AI 请求也被阻塞。

**修复方案**：启用 Virtual Threads（已配置 `spring.threads.virtual.enabled=true`），但需验证 LlmClient 的 HttpClient 是否兼容。

### 1.4 无事件溯源 [MEDIUM]

**现状**：`ai_workflow_event` 表记录了 phase 事件，但不是严格的 event-sourcing。无法从事件流重建 learner state。

**对标**：CogEvo-Edu 的 Meta-Control Layer 每个 state transition 都是 auditable event，支持时间旅行调试。

**修复方案**：长期项，不急。当前 trace 回放已覆盖基本调试需求。

---

## 2. 性能缺陷

### 2.1 LLM 调用阻塞 Tomcat 线程 [CRITICAL]

**现状**：LlmClient.callForJson 是同步 HTTP 调用，LLM 响应 5-30 秒，整个期间占用 Tomcat 线程。

**影响**：Tomcat max-threads=50 时，理论最大并发 AI 请求约 50。一旦 LLM 变慢，非 AI 接口（提交代码、查看题目）也会受影响。

**修复方案**：
- 短期：确认 Virtual Threads 生效（Spring Boot 3.5 + Tomcat 支持），每个虚拟线程占用极少内存
- 中期：LlmClient 改用 java.net.http.HttpClient 的异步 API + CompletableFuture
- 长期：Agent 执行移到独立线程池，通过 SSE 推送结果（已部分实现）

### 2.2 无 LLM 响应缓存 [HIGH]

**现状**：相同题目的 GuideAgent（审题引导）每次请求都重新调用 LLM，成本和延迟都浪费。

**影响**：同一道题 100 个学生点击"审题引导"，产生 100 次 LLM 调用。

**修复方案**：对 `(problem_id, phase, event)` 三元组的 Agent 输出做 Redis 缓存，TTL 10 分钟。个性化内容（含 learner state）不缓存。

### 2.3 无 JVM 诊断配置 [HIGH]

**现状**：`JAVA_OPTS` 没有 GC 日志、没有 OOM heap dump。

**修复方案**：立即添加到 docker-compose.yml 的 JAVA_OPTS：
```
-Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags:filecount=3,filesize=10m
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/heapdump.hprof
```

### 2.4 无 HikariCP 连接泄露检测 [HIGH]

**现状**：`application-prod.yml` 没有配置 `leak-detection-threshold`。

**修复方案**：
```yaml
spring.datasource.hikari.leak-detection-threshold: 30000
```

### 2.5 N+1 查询风险 [MEDIUM]

**文件**：`DifficultyCalibrationService.calibrateByLanguagePack` 对每个 problem 单独查询 submission 统计。

**修复方案**：改用 batch 查询，一次取所有 problem 的统计数据。

---

## 3. 安全缺陷

### 3.1 CVE-2026-22731 Actuator 认证绕过 [CRITICAL]

**状态**：已修复。Spring Boot 3.5.11 → 3.5.12。

### 3.2 MCP SSE 端点无 CORS 限制 [HIGH]

**现状**：`/sse` 端点通过 SecurityConfig permitAll 且无 CORS 配置，任何域名的 JavaScript 都可连接。

**修复方案**：SecurityConfig 中为 `/sse/**` 配置 CORS origin 白名单。

### 3.3 无 LLM 调用独立配额 [HIGH]

**现状**：RateLimitFilter 按 IP 限流（60 次/分钟），但 Agent 调用链内部没有 LLM 调用配额。恶意用户可通过反复触发 Agent 耗尽 LLM API 额度。

**修复方案**：在 LlmClient 层加 per-user 的 LLM 调用令牌桶（例如每用户每小时 100 次 LLM 调用）。

### 3.4 MCP API Key 每次读环境变量 [MEDIUM]

**现状**：`McpApiKeyFilter.doFilterInternal` 每次请求都调用 `System.getenv("MCP_API_KEY")`。

**修复方案**：在 Filter 初始化时缓存一次。

### 3.5 生产未强制 HTTPS [MEDIUM]

**现状**：`alethicode.system.force-https=false`。

**修复方案**：`application-prod.yml` 中设为 `true`，或在 SecurityConfig 中加 `http.redirectToHttps()`。

---

## 4. 可靠性/压力缺陷

### 4.1 无 Circuit Breaker [CRITICAL]

**现状**：LLM API 不可用时，所有 Agent 请求会 hang 到 150 秒超时才失败。50 个并发请求 × 150 秒 = 所有线程被占满，整个系统不可用。

**修复方案**：
- 在 LlmClient 层加 Resilience4j CircuitBreaker
- 配置：5 次连续失败 → 开路 60 秒 → 半开 → 探测 1 次 → 恢复
- 开路期间直接返回降级响应："AI 助教暂时离线，请稍后重试"

### 4.2 无 LLM Fallback 降级策略 [HIGH]

**现状**：LLM 调用失败直接抛 `IllegalStateException`，前端显示错误弹窗。

**修复方案**：每个 Agent 实现 fallback 方法，返回静态降级内容：
- GuideAgent fallback：返回题面结构化解析（不需要 LLM）
- DiagnosticsAgent fallback：返回编译错误原文 + "请检查以下行"

### 4.3 无负载测试 [HIGH]

**现状**：没有 k6/Gatling/JMeter 脚本。

**修复方案**：创建 `loadtest/` 目录，包含 k6 脚本覆盖：
- 基线：50 并发用户，持续 5 分钟
- 峰值：200 并发用户，持续 2 分钟
- Agent 压测：20 并发 AI 请求，LLM mock 延迟 5 秒

### 4.4 SSE 连接无 Idle Timeout [MEDIUM]

**现状**：Agent workflow 的 SSE 流没有检测客户端断连的机制。

**修复方案**：SSE 端点加 heartbeat（每 30 秒发送空 comment），客户端 60 秒无活动则服务端关闭连接。

### 4.5 Flyway 大表加列风险 [MEDIUM]

**现状**：V54 migration 给 `problem` 表加 `difficulty_score` 和 `auto_generated` 列。PostgreSQL `ADD COLUMN ... DEFAULT` 在 PG11+ 是非阻塞的（默认值写入元数据而不是行），但 `IF NOT EXISTS` 行为需确认。

**状态**：V54 使用的是 `ADD COLUMN IF NOT EXISTS`，PG16 下是安全的。

---

## 5. 可观测性缺陷

### 5.1 无 OpenTelemetry 分布式追踪 [HIGH]

**现状**：Spring Boot 3.5 原生支持 OpenTelemetry，但 Alethicode 未启用。

**影响**：无法追踪一次 Agent 调用经历了哪些 service → DB → LLM 调用，无法定位性能瓶颈。

**修复方案**：
```yaml
management.tracing:
  sampling.probability: 0.1
  propagation.type: w3c
```
配合 Jaeger 或 Tempo 收集端。

### 5.2 无 LLM 调用 Micrometer 指标 [HIGH]

**现状**：不知道 LLM 调用延迟 P95/P99、成功率、token 消耗。

**修复方案**：在 LlmClient 中用 Micrometer Timer 和 Counter 记录每次调用：
- `llm.call.duration` (timer, tags: agent, model)
- `llm.call.errors` (counter, tags: error_type)
- `llm.call.tokens` (counter, tags: direction=input/output)

### 5.3 生产关闭了 Prometheus [MEDIUM]

**现状**：`application-prod.yml` 禁用了 prometheus endpoint。

**修复方案**：保留 prometheus endpoint，但仅允许内网访问（通过 nginx 或 management port 隔离）。

### 5.4 无告警规则 [MEDIUM]

**现状**：没有 Prometheus alerting rules。

**修复方案**：添加关键告警：
- JVM heap usage > 85%
- HTTP P95 latency > 2s
- Error rate > 5%
- HikariCP active connections > 80% pool size
- LLM call error rate > 20%

---

## 6. 前端缺陷

### 6.1 UnifiedAgentPanel.vue 超大单文件 [HIGH]

**现状**：2000+ 行，包含 20+ 种卡片渲染、消息管理、WebSocket 通信、反馈收集。

**修复方案**：拆分为 `AgentMessageRenderer.vue`（卡片渲染）、`AgentChatInput.vue`（输入框）、`AgentRuntimeStatus.vue`（运行时状态）。

### 6.2 无前端错误边界 [MEDIUM]

**现状**：Agent 卡片数据异常时（如 `reasoning_chain` 格式错误），整个 UnifiedAgentPanel 会崩溃。

**修复方案**：每个卡片组件用 `<ErrorBoundary>` 包裹，或在组件内加 `errorCaptured` hook。

### 6.3 ECharts 未按需加载 [MEDIUM]

**现状**：`ObservabilityDashboard.vue` 全量引入 `echarts`（约 800KB gzipped）。

**修复方案**：改用 `echarts/core` + 按需导入 heatmap/bar/line 组件。

---

## 修复优先级

| 序号 | 缺陷 | 维度 | 严重度 | 工期 |
|------|------|------|--------|------|
| 1 | JVM OOM dump + GC 日志 | 性能 | HIGH | 5 分钟 |
| 2 | HikariCP leak-detection | 性能 | HIGH | 5 分钟 |
| 3 | LLM Circuit Breaker | 可靠性 | CRITICAL | 2 小时 |
| 4 | LLM 响应缓存 | 性能 | HIGH | 3 小时 |
| 5 | MCP CORS 配置 | 安全 | HIGH | 30 分钟 |
| 6 | LLM Micrometer 指标 | 可观测性 | HIGH | 2 小时 |
| 7 | MCP API Key 缓存 | 安全 | MEDIUM | 15 分钟 |
| 8 | OpenTelemetry 启用 | 可观测性 | HIGH | 1 小时 |
| 9 | Agent per-user lock | 架构 | HIGH | 2 小时 |
| 10 | N+1 查询修复 | 性能 | MEDIUM | 1 小时 |
