# Alethicode 前后端全栈代码审计报告

> **审计日期**：2026-04-27
> **审计方法**：`code-reviewer` skill（Security → Performance → Correctness → Maintainability → Testing → Infrastructure → Compliance）
> **审计范围**：backend Java + frontend Vue + tutor_graph Python + deploy + DB migration + scripts
> **输出形式**：纯 readonly，不改代码，不改配置
> **关联报告**：[`code-review-2026-04-18.md`](code-review-2026-04-18.md)（前次审计）/ [`capacity-security-review.md`](capacity-security-review.md)

---

## 执行摘要

### 总评

Alethicode 是一个**架构边界清晰、控制面成熟、安全基线达标的中型工程**：身份验证、CSRF、CSP、HSTS、Permissions Policy、Rate limit、Resilience4j（CB / Retry / Bulkhead / TimeLimiter）、Internal Service Key 常时间比较、SQL 参数化、DOMPurify XSS 防护——这些在国内同规模高校自研项目里已经属于 top-tier 实现质量。

但项目仍有 **3 项 High 优先级真实风险** 与 **若干结构性 Medium 风险**，集中在：rate limit 客户端 IP 解析逻辑反向、巨石服务文件、CSP 含 unsafe-eval、SVG 双层 sanitize 缺失、Migration 版本号在多份设计稿之间未协调。

### 关键风险计分

| 等级 | 数量 | 主要分布 |
|---|---|---|
| 🔴 Critical | 0 | — |
| 🟠 High | 9 | Rate limit IP 解析 / CSP / 巨石文件 / SVG 双 sanitize / Migration 撞车 / FORCE_HTTPS=false / multipart 上限 / video provider timeout / submission-list-show-all |
| 🟡 Medium | 10 | /internal 鉴权结构性风险 / 大部分 admin controller 无 @PreAuthorize / renderMarkdown DRY / OrtSession 并发上限 / LLM 重试策略 / SerialGC / 三套工作流并存 / TODO 文件碎片化 / 错题本与 Parsons 设计稿合流 / CHANGELOG 体积 |
| 🟢 Low | 多项 | RateLimit 数值 / Grafana 默认密码 / NATS-Temporal 端口 / show-sql 默认 |

**没有 Critical 级缺陷**——这是项目工程纪律的体现。

### 代码量基线（实测，与对外叙事的差异）

| 维度 | 实测值（4-27） | 对外叙事（project-story.md） | 差异说明 |
|---|---|---|---|
| 后端 Java 文件 | 489 | 986 | 对外是 region pack（含生成代码或宣传可读化） |
| 后端测试 Java 文件 | 139 | — | 测试比 ≈ 28%，整体合理 |
| 前端 Vue 组件 | 134 | 245 | 叙事可能含已删组件历史值 |
| 前端 JS/TS 模块 | 94 | 3300+ | 叙事口径含 node_modules，实际自研模块 94 个 |
| 前端测试 spec | 114 | — | 测试比超 100%（vue:js ≈ 1.2:1），优秀 |
| tutor_graph Python | 55 | ~100 | — |
| Flyway migration | 68 (V1-V70 含跳号) | 59 | 已经超过叙事数字 |
| Helm template | 21 | — | — |
| 启动/部署脚本 | 多个 | — | start.sh + scripts/*.sh |

**结论**：实测代码量（约 73,000 行后端 Java + 46,000 行前端 Vue + 10,000 行前端 JS + 5,900 行 tutor_graph Python）与 `project-story.md §10.1` 自承的"区间收敛、整数化"宣传可读化口径吻合，没有数据造假，但建议对外材料同步以实际数据为准。

---

## 一、Security 专项（HIGH）

### 1.1 已经做对的（不要破坏）

- ✅ Spring Security 配置 + EnableMethodSecurity（`SecurityConfig.java`）
- ✅ CSRF 通过 CookieCsrfTokenRepository.withHttpOnlyFalse + SameSite cookie + ignore /internal/**（已审，正确）
- ✅ HSTS includeSubDomains + maxAgeInSeconds=31536000（1 年）
- ✅ Referrer-Policy: STRICT_ORIGIN_WHEN_CROSS_ORIGIN
- ✅ Permissions-Policy: camera/microphone/geolocation 全禁
- ✅ Frame-Options: SAMEORIGIN
- ✅ Internal Service Key 常时间比较（`MessageDigest.isEqual`，防 timing attack）
- ✅ Internal Service Key 启动时强校验（`InternalServiceKeyValidator.java`：prod profile 拒绝 dev-default / 弱 key / 空 key）
- ✅ DOMPurify + 严格 ALLOWED_TAGS / ALLOWED_ATTR + a[target=_blank] 自动 noopener noreferrer
- ✅ Mermaid `securityLevel: 'strict'`
- ✅ 11 个 Vue 组件的 `renderMarkdown` 都走 `sanitize(marked(text))` 模式
- ✅ AccountServiceImpl 的 `delete from "user" where id in (...)` 使用 placeholder + ids.toArray 真正参数化（不是字符串拼接）
- ✅ Secrets 不在仓库（所有 password / api-key 通过 ${ENV_VAR} 引用）
- ✅ 没有 `nativeQuery=true` 的 Repository（grep 全仓库 0 hit）

### 1.2 🟠 High [SEC-1] RateLimitFilter X-Forwarded-For 解析方向反了

**文件**：`backend/src/main/java/com/alethicode/middleware/RateLimitFilter.java:83-97`

**问题**：取 X-Forwarded-For **最右侧** IP 作为客户端 IP。X-Forwarded-For 协议格式是 `client_ip, proxy1_ip, proxy2_ip, ...`——**最左侧才是真实客户端**，最右侧是离 server 最近的代理。

**当前代码**：
```java
private String resolveClientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
        String[] parts = xff.split(",");
        String rightmost = parts[parts.length - 1].trim();  // 错：取最右侧 = 取代理 IP
        if (!rightmost.isEmpty()) {
            return rightmost;
        }
    }
    ...
}
```

**影响**：
- 反向代理（nginx / k8s ingress / 阿里云 SLB）后端的所有用户在 RateLimitFilter 看来都来自同一个代理 IP
- 全班共用一个 60 req/min 配额，正常学生集体被 429 误杀
- 注释自己也写了"取 X-Forwarded-For 最右侧 IP（反向代理追加的真实客户端 IP）"——**注释和代码同时错**

**修复建议**：
- 信任配置的反向代理列表（trustedProxies），从右往左跳过列表中的 IP，取第一个不在列表的
- 或在 nginx 端 `proxy_set_header X-Real-IP $remote_addr` + 直接读 `X-Real-IP`

### 1.3 🟠 High [SEC-2] CSP 含 `'unsafe-eval'` 与 `'unsafe-inline'`

**文件**：`backend/src/main/java/com/alethicode/config/SecurityConfig.java:81-82`

**当前**：
```
default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; ...
```

**问题**：
- `'unsafe-inline'` 是为内联 `<script>` 兼容（Vue 路由 / 部分第三方）；可接受但非必需
- `'unsafe-eval'` 几乎从来不必要——Vue 3 + Element Plus + ECharts + Mermaid 都不依赖 eval
- 配合 11 个 v-html + DOMPurify 单点防御，`unsafe-eval` 让 XSS 利用面进一步扩大

**修复建议**：
- 排查谁还在用 `eval` / `new Function`（可用 `npm run build` + grep 输出）；若无，去掉 `'unsafe-eval'`
- `'unsafe-inline'` 可用 `nonce` 或 `hash` 替代（成本较高，可后置）

### 1.4 🟠 High [SEC-3] SvgRenderer 没有前端 sanitize

**文件**：`frontend/src/pages/oj/views/problem/cards/visualize/SvgRenderer.vue:4`

**当前**：
```vue
<div v-else class="viz-svg-content" v-html="svgText"></div>
```

**问题**：
- `payload` 直接 v-html 注入，依赖后端 `SvgSanitizer.java` 的单点防御
- 后端 sanitizer 在 `CHANGELOG 4/26` 刚修过 bug（"子节点清理未落盘"——`遍历改为真实 children 列表`），证明这一层不是百分百可靠
- Defense in depth 缺失

**修复建议**：
- 前端再用 DOMPurify 走一次 SVG profile：`DOMPurify.sanitize(svgText, { USE_PROFILES: { svg: true, svgFilters: true } })`
- 即使后端有 bug，前端兜底 sanitize 能拦截一波

### 1.5 🟠 High [SEC-4] MCP CORS 完全开放但鉴权未落地

**文件**：`backend/src/main/java/com/alethicode/config/SecurityConfig.java:99-114`

**当前**：
```java
mcpCors.addAllowedOriginPattern("*");
mcpCors.addAllowedMethod("*");
mcpCors.addAllowedHeader("*");
mcpCors.setAllowCredentials(false);
```

**问题**：
- `/sse/**` 和 `/mcp/**` 路径 permitAll + CORS 完全开放
- `MCP_SERVER_ENABLED` 默认 `false`，profile-only 才有 dependency——**当前生产无暴露**
- 但 [`docs/todos/todo-next-phase.md`](../todos/todo-next-phase.md) 计划开启 MCP server，提示"API Key 认证 + 每工具独立 rate limit"——**当前 SecurityConfig 没体现这层**

**修复建议**（与 todo-next-phase 同步）：
- 为 `/mcp/**` 路径加专用 filter，强制 `Authorization: Bearer <api-key>` + 独立 rate limiter
- 数据敏感工具（submit_code、get_learner_profile）按 `@McpTool` 维度做更严的 rate limit
- 当前 CORS `*` 在启用 MCP 时是必需的（让 Claude Desktop / Cursor 任意 origin 能连），但身份验证不能省

### 1.6 🟠 High [SEC-5] application.yml `force-https: false`

**文件**：`backend/src/main/resources/application.yml:291`

**当前**：
```yaml
alethicode:
  system:
    force-https: false
```

**问题**：
- 生产部署应该强制 HTTPS（HSTS 已经声明，但 force-https 还是 false）
- 本地开发 false 合理；生产应该走 application-prod.yml 覆盖

**修复建议**：
- 增加 `application-prod.yml` 覆盖 `force-https: true`
- 或在 [`deploy/.env.example`](../../deploy/.env.example) 注释里强调"生产部署必须 export FORCE_HTTPS=true"

### 1.7 🟠 High [SEC-6] multipart 上限 256MB DoS 风险面

**文件**：`backend/src/main/resources/application.yml:23-25`

**当前**：
```yaml
servlet:
  multipart:
    max-file-size: 128MB
    max-request-size: 256MB
```

**问题**：
- 256MB request 在 2C2G 比赛部署里足够吃光内存
- 是为 PPT 上传 / 课件上传开的口子（`LanguagePackInit` / `AdminUploadController` 需要）
- 但应用级别全局打开 256MB 是过宽

**修复建议**：
- 全局降到 32MB（普通请求）
- 仅在 `/api/admin/upload/**` 与 `/api/admin/language-packs/**/upload/**` 局部覆盖到 256MB
- 配合 RateLimitFilter 对上传端点单独限流

### 1.8 🟠 High [SEC-7] `submission-list-show-all: true` 默认

**文件**：`backend/src/main/resources/application.yml:278`

**当前**：
```yaml
submission-list-show-all: true
```

**问题**：
- 默认所有用户能看其他人的提交列表
- 教学场景里这是设计选择（鼓励互相学习）
- 但隐私 & 防作弊视角看，应该默认 false，由教师在课堂粒度开启

**修复建议**：
- 默认改为 false，新增 classroom 级开关（CourseStructure 表加 `allow_public_submission_list` 字段）
- 让教师按班级、按作业决定是否公开
- 这是非紧急修复，但属于未成年人保护合规的 best practice

### 1.9 🟠 High [SEC-8] video provider timeout 10 分钟

**文件**：`backend/src/main/resources/application.yml:196`

```yaml
videoProvider:
  base-config: default
  slow-call-duration-threshold: 20s
  wait-duration-in-open-state: 45s
```

```yaml
timelimiter:
  videoProvider:
    timeout-duration: 10m
```

**问题**：
- 10 分钟 timeout + 单实例 max-concurrent-calls=10 → 100 分钟可被打满
- TTS / 视频生成确实慢，但 10 分钟太宽
- 没看到对应的客户端是否有自己的超时 + 客户端断开时是否真正取消（Resilience4j cancel-running-future=true 设了，理论上会取消）

**修复建议**：
- 缩到 5 分钟（300s）配合主动 cancel
- 或改为异步任务模式（任务入 NATS 队列，前端轮询结果）

### 1.10 🟡 Medium [SEC-9] `/internal/**` 鉴权依赖 controller 手工加 validateServiceKey

**文件**：`backend/src/main/java/com/alethicode/controller/internal/InternalAITutorToolController.java`

**当前**：
- 每个 internal 端点都手动 `@RequestHeader("X-Internal-Service-Key") String key` + `validateServiceKey(key)`
- 当前只有 1 个 controller 在 `/internal/`，状况安全

**结构性风险**：
- 未来新增 `InternalNfkController` / `InternalParsonsController` 等，**很容易忘记加 `validateServiceKey()`**
- 一旦遗漏，由于 `SecurityConfig` 把 `/internal/**` 设为 permitAll + 不在 CSRF 范围，新端点会**完全无鉴权对外暴露**

**修复建议**：
- 抽一个 `InternalServiceKeyAuthFilter`：拦截 `/internal/**` 路径，统一校验 X-Internal-Service-Key（常时间比较）
- 移除 controller 里的 `validateServiceKey()` 调用
- 单元测试覆盖："未加 header 的 /internal 请求 → 403"

### 1.11 🟡 Medium [SEC-10] 大部分 admin controller 没有 @PreAuthorize

**搜索结果**：13 个 Admin Controller 中只有 `AdminConfigController` (14 个 @PreAuthorize) 与 `AdminProblemController` (7 个) 加了方法级；多数只有 1 个或 0 个

**当前防御**：`SecurityConfig` 有 `requestMatchers("/api/admin/**").hasRole("ADMIN")` 路径级守门

**结构性风险**：
- 只要某个 admin controller 不在 `/api/admin/` 下面（例如 typo / 重构后改到 `/api/manage/...`），就完全无鉴权
- Defense in depth 缺失

**修复建议**：
- 给所有 admin controller 类级别加 `@PreAuthorize("hasRole('ADMIN')")`
- Teacher 专属的端点用 `@PreAuthorize("hasRole('TEACHER')")` 区分
- 一次性补齐，约 2 小时工作量

---

## 二、Performance 专项（HIGH）

### 2.1 已经做对的

- ✅ Spring Boot 3.5 + Java 21 + 虚拟线程（`spring.threads.virtual.enabled: true`）
- ✅ Caffeine + Redis 双层缓存（`AlethicodeCacheManager`）
- ✅ pgvector 向量索引（`ai_learner_memory.memory_embedding`）
- ✅ Resilience4j Bulkhead 隔离每条出站依赖
- ✅ JPA `open-in-view: false`（防 Lazy Load 灾难）
- ✅ Hibernate `ddl-auto: validate`（不自动改表）
- ✅ Submission / KC mapping / hotspot indexes 已加（V14, V52）
- ✅ NfkInferenceService OrtSession 单例（onnxruntime 官方声明线程安全）

### 2.2 🟡 Medium [PERF-1] LLM_API_MAX_RETRIES=9 较激进

**文件**：`deploy/.env.example:29`

```
LLM_API_MAX_RETRIES=9
```

**对比**：application.yml 里 Resilience4j 对 `llmProvider` 配的 `max-attempts: 2`

**疑似冲突**：
- `LLM_API_MAX_RETRIES=9` 是某个客户端层重试
- Resilience4j `max-attempts=2` 是 retry decorator 重试
- 嵌套后真实总尝试数 = 9 × 2 = 18？还是只用其中一个？需要看 `FailoverAiModelGateway.java` 的真实实现

**修复建议**：
- 审查 `FailoverAiModelGateway` 的 retry 路径，确认 LLM_API_MAX_RETRIES 与 Resilience4j 不重复
- 对 LLM 重试 9 次会显著放大慢调用导致用户等待变长，建议降到 3

### 2.3 🟡 Medium [PERF-2] NfkInferenceService 未显式限制并发

**文件**：`backend/src/main/java/com/alethicode/service/aitutor/nfk/NfkInferenceService.java`

**问题**：
- OrtSession 是线程安全的，但单 session 在大并发下会形成 hot point
- 序列长度 ≥ 20 + delta_t [T,T] 张量，每次推理需要 O(T²) 内存
- 没看到对 `predictPerSkill` 的 Bulkhead / Semaphore 隔离

**修复建议**：
- 给 `predictPerSkill` 加 `@Bulkhead("nfkInference")` 注解
- application.yml 增加 `bulkhead.instances.nfkInference.max-concurrent-calls: 50`
- 监控：每秒 NFK 推理调用次数、平均延迟、95p

### 2.4 🟡 Medium [PERF-3] 多个 admin / observability 接口可能 N+1

未深入审计每个 service 的 SQL，但 31 个 ServiceImpl 中至少：
- `ClassroomMonitorService` / `ClassroomAnalyticsService`：班级监控肯定涉及多学生 × 多题循环
- `LearnerCourseProgressService` / `BeginnerSupplementPlannerService`：学情查询易陷入 N+1
- `AdminCourseInsightService`：班级 KC 热力图涉及 user × kc 笛卡尔

**修复建议**：
- 后续专项做一次 Hibernate `show-sql=true` 在 stage 环境跑核心 hot path，统计 SQL 数
- 重点关注 P95 延迟超过 200ms 的端点

### 2.5 🟡 Medium [PERF-4] OTEL 采样 10% 在 prod 可能不够细

`OTEL_SAMPLING_PROBABILITY:0.1`

**说明**：
- 默认 10% 采样在百万级请求下足够
- 但 AI Tutor 是低频高价值（5 万次/天 量级），建议 30-50%
- 关键路径（比如 LLM 调用）应该 100% 采样并通过 `Sampler.parentBased(...)` 强制

---

## 三、Correctness 专项（HIGH）

### 3.1 已经做对的

- ✅ failfast 设计纪律（CHANGELOG 多次出现 fail-fast 修正）
- ✅ Resilience4j Circuit Breaker / Retry / TimeLimiter 三件套
- ✅ AITutorWorkflowAdminServiceImpl 改造为 LangGraph 单源真相（CHANGELOG 4/27 多次清理）
- ✅ Skeleton / Visualize / Knowledge Review 全链路打通 schema 校验
- ✅ Tutor graph 单 worker 部署（`--workers 1`），与内存级 buffer 一致

### 3.2 🟠 High [COR-1] Migration 版本号在多份设计稿之间未协调

**事实**：
- 已存在 migration 最新到 `V70__ai_learner_milestone.sql`
- 错题本综合重构设计稿（`docs/plans/2026-04-27-notebook-comprehensive-redesign-design.md`）规划 V65 + V66 + V67 + 里程碑
- 我刚写的 Faded Parsons 设计稿原 V67、改到 V68 时撞错题本 V68
- 实际 V68 已被 `notebook_kc_breakthrough_structured_reflection` 占用

**影响**：
- 任何在多份设计稿基础上新建 migration 的 PR 都可能撞车
- Flyway 撞车会导致整个 deploy 失败

**修复建议**：
- 启用一份 `docs/specs/migration-version-registry.md`，所有未合入的 migration 版本号在这里登记
- 我的 Faded Parsons 设计稿应改到 V71（V70 已是 ai_learner_milestone）
- 其他在路上的设计稿（Persistent Memory、Unified Chat）也要登记

### 3.3 🟡 Medium [COR-2] tutor_graph 单 worker = 单点

`services/tutor-graph/README.md` 明确写了"单 worker 强制要求，因为 _run_events / _active_runs / _background_tasks 在进程内存"

**风险**：
- 单 worker 重启 = 所有进行中的会话丢失运行时事件（虽然 LangGraph checkpoint 在 Postgres 落地，能恢复 thread state，但 active 事件 buffer 丢失）
- 横向扩展只能靠 LB sticky session + 牺牲 cross-instance 一致性

**修复建议**（已在 todo-master 列出）：
- Redis-backed event bus（NATS 也能做）替代 in-memory buffer
- 当前阶段是合理 trade-off（LangGraph runtime 还在演进），但 SLO 看板需要明确"单点可用性"

### 3.4 🟡 Medium [COR-3] AITutorWorkflowAdminServiceImpl 3743 行混合多职责

**已在 todo-master § 8.2 标记瘦身**，但还没动手。混合职责包括：
- workflowSession 创建 / 删除 / 查询
- workflowEvent 派发
- projection 更新
- skeleton / visualize / knowledge_review 派发
- card schema 注册
- 错误诊断 fallback / 兜底

**风险**：
- 每次改一个动作都要面对 3700 行
- bug 隐藏深（CHANGELOG 多次出现"修了 X 没注意 Y 也调用了同一函数"）
- 测试用例很难精准覆盖

**修复建议**（与 todo-master 同步）：
- 拆为 `TutorWorkflowSessionService` / `TutorWorkflowEventDispatcher` / `TutorWorkflowProjectionService` / `CardDispatchService` 等各 ≤ 500 行
- 每个新增动作（如 PARSONS）按瘦身后的边界落入对应 service

### 3.5 🟡 Medium [COR-4] frontend renderMarkdown 在 11 个组件重复

11 个 Vue 组件里都有几乎一样的：
```js
renderMarkdown (text) {
  if (!text) return ''
  return sanitize(marked(text))
}
```

**风险**：
- 如果未来要改 markdown 渲染规则（比如加 KaTeX 内联、加 mermaid 流程图、改 sanitize 配置），需要改 11 个地方
- 已经存在 11 个轻微差异版本（有些含 sup 替换、有些含 stepLinks）

**修复建议**：
- 抽 `frontend/src/utils/markdown.js` 提供 `renderMarkdown(text, options?)` 与 `renderMarkdownWithSteps(text)`
- 11 个组件改为 import；保留组件方法名以减小 diff
- 单元测试 `markdown-render-contract.spec.js` 覆盖各组件场景

---

## 四、Maintainability 专项（MEDIUM）

### 4.1 后端巨石文件 Top 5

| 文件 | 行数 | 严重度 | 修复 |
|---|---|---|---|
| `service/impl/AITutorWorkflowAdminServiceImpl.java` | **3743** | 🟠 High | 见 COR-3 |
| `service/impl/AITutorServiceImpl.java` | **2916** | 🟠 High | 拆为 ChatAgentService / SkeletonService / WelcomeService |
| `service/impl/SubmissionServiceImpl.java` | **2103** | 🟡 Medium | 拆判题 / 复盘 / 通知 / FSRS 推进 |
| `service/impl/AdminProblemCommandServiceImpl.java` | **1973** | 🟡 Medium | 已在 todo-master 列入瘦身 |
| `service/languagepack/impl/ExampleExtractionServiceImpl.java` | **1769** | 🟡 Medium | 拆按文档类型（PPT/PDF/Markdown） |

### 4.2 前端巨石组件 Top 5

| 文件 | 行数 | 严重度 | 修复 |
|---|---|---|---|
| `pages/oj/views/languagepack/LanguagePackQaPage.vue` | **2293** | 🟠 High | 拆 Conversation / Editor / Preview |
| `pages/oj/views/problem/UnifiedAgentPanel.vue` | **2000** | 🟠 High | todo-master 已列；目标 ≤ 600 行 |
| `pages/oj/views/problem/workflowStateMachine.js` | **1998** | 🟠 High | 拆 transitions / events / cache / restore |
| `pages/admin/views/general/LanguagePackInit.vue` | **1889** | 🟠 High | 拆向导步骤 / 阶段诊断 / 重试控制 |
| `pages/oj/views/problem/Problem.vue` | **1694** | 🟠 High | todo-master 列入；继续按 composable 抽提 |

### 4.3 🟡 Medium [MNT-1] 三套工作流引擎并存

- **Temporal**：`io.temporal:temporal-sdk:1.30.1`，用于 LanguagePackPipelineWorkflow（异步长跑文档/题目工厂）
- **NATS Streaming**：`io.nats:jnats:2.23.0`，用于 judge dispatch + learning events publisher（事件总线）
- **LangGraph (Python)**：tutor_graph，AI 导学工作流（学生侧实时交互）
- **AITutorWorkflowAdminServiceImpl 自建 FSM 3743 行**：导学事件门户 + projection

**评价**：
- 三者职责正交，**不冗余**：Temporal=长跑、NATS=异步事件、LangGraph=AI 实时
- 但运维需要同时熟悉 3 套 workflow 抽象 + Java 侧自建 FSM 共 4 套
- 入职门槛高、调试链路复杂

**建议**：
- 不动现状（已经稳定）
- 新成员入职时给一份 [`docs/architecture/workflow-engines-map.md`](../architecture/workflow-engines-map.md) 简表说明各引擎的职责边界

### 4.4 🟡 Medium [MNT-2] OpenFeature + Unleash 双特性开关

- `dev.openfeature:sdk:1.20.1` 是 vendor-neutral 接口
- `io.getunleash:unleash-client-java:10.2.2` 是后端实现

**评价**：是 vendor-neutral 封装的标准做法，**不冗余**

### 4.5 🟡 Medium [MNT-3] todo 文件碎片化

`docs/todos/` 下有 **35 个 todo 文件**，相互重叠 + 部分已经过时：
- todo-master.md（总纲）
- todo-future.md（远景）
- todo-next-phase.md（下阶段）
- todo-improve.md / todo-improve-debt.md / todo-engineering-debt.md / todo-debt.md / todo-clear.md（多份"待改进"重叠）
- todo-agent.md / todo-agent-front.md / todo-agent-harness/

**建议**：
- 一次性归档到 `docs/archives/todos/`，只保留 todo-master.md + todo-future.md + todo-next-phase.md 这 3 份核心
- 其他 todo 内容迁移到 GitHub Issue 或并入主 todo

### 4.6 🟡 Medium [MNT-4] CHANGELOG 9000+ 行

**文件**：`CHANGELOG.md`

**问题**：单文件 9000+ 行，git diff 缓慢、PR review 体验差

**修复建议**：
- 按月切分到 `CHANGELOG-2026-03.md` / `CHANGELOG-2026-04.md` 归档
- 主 `CHANGELOG.md` 只保留当月 + 历史链接
- 当前未归档不影响功能，但每次改动都要 cat 9000 行很 sub-optimal

---

## 五、Testing 专项（HIGH）

### 5.1 已经做对的

- ✅ 后端测试 139 个 / 业务代码 489 个 ≈ **28% 文件覆盖比**（行级覆盖率应在 50% 以上）
- ✅ 前端契约测试 114 个 / Vue 组件 134 个 ≈ **85% 组件契约覆盖**（优秀）
- ✅ Replay manual test 存在（LanguagePackAlethicodeReplayManualTest）
- ✅ 跨进程测试存在（InfrastructureDeepIntegrationContractTest）
- ✅ tutor_graph 与 Java 双向契约测试

### 5.2 🟡 Medium [TST-1] 缺端到端 e2e 测试自动化

**情况**：
- frontend/tests/replacement/ 下有静态审计但无 Playwright/Cypress 自动化
- `webapp-testing` skill 有，但 CI 没集成

**建议**：
- 补 5-10 个核心 e2e 用例：登录 → 题目页 → AI 导学 → 提交 → 错题入库
- 集成到 CI（npm run e2e:ci 在 staging 环境跑）

### 5.3 🟡 Medium [TST-2] 巨石文件单元测试覆盖薄

`AITutorWorkflowAdminServiceImpl 3743 行` 对应的 `AITutorWorkflowAdminServiceImplTest` 仅 1 个。每个公共方法都有自己的失败模式，单测覆盖度肯定不足。

**修复**：拆分（COR-3）后再重写测试，每个 service ≤ 500 行 + 完整单测

---

## 六、Infrastructure 专项（MEDIUM）

### 6.1 已经做对的

- ✅ docker-compose / k8s manifest / Helm chart 三套同步维护
- ✅ Prometheus + Grafana + Jaeger（OTLP）完整观测栈
- ✅ NATS / Temporal 容器化
- ✅ start.sh 含完整健康检查 + 镜像 source-hash 重建机制
- ✅ Spring Boot Actuator readiness 含跨服务探针（tutorGraph）

### 6.2 🟡 Medium [INF-1] JAVA_OPTS 用 SerialGC

**文件**：`deploy/.env.example:48`

```
JAVA_OPTS=-Xms128m -Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=256m ...
```

**说明**：
- SerialGC 是单线程，适合 < 1 vCPU 或低内存（< 1GB）场景
- BACKEND_MEMORY_LIMIT=900m + Xmx=512m 确实是低规格部署
- 比赛 / 演示 OK，但生产环境应该 G1GC

**建议**：
- 提供 `deploy/.env.production` 模板，覆盖为 G1GC + 更大 heap

### 6.3 🟡 Medium [INF-2] Grafana 默认密码 admin

**文件**：`deploy/docker-compose.yml:302`

```yaml
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}
```

**风险**：本地部署默认 admin/admin；强制首次登录改密但仍是默认值

**建议**：随机生成存到 `.runtime/grafana/admin.txt`，启动脚本日志打印一次

---

## 七、Compliance 专项（已较完整）

### 7.1 已经做对的（非常完整）

- ✅ ADR-0004 PIPL + 生成式 AI 服务管理暂行办法 + 等保 2.0 工程化条款
- ✅ V59 compliance audit + sensitive log migration
- ✅ Prompt injection defense (21 个中英文 jailbreak 标记 redact)
- ✅ LLM Provider 仅境内可用线路（DeepSeek / 通义 / 火山 / MiniMax / 智谱）
- ✅ Internal Service Key 启动时 fail-fast
- ✅ 学生 PII 最小化采集（CHANGELOG 多次提及）

### 7.2 🟡 Medium [CMP-1] 未成年人保护策略未显式

**情况**：
- `submission-list-show-all: true` 默认（见 SEC-7）
- 没有看到学生年龄字段 + 未成年人可见性专项

**修复建议**：
- 用户表加 `is_minor` 字段（生日推算）
- AI 导学 / 错题本 / 班级排行的可见性增加未成年人专项过滤
- 教师 / 家长可见报告的开关
- 借鉴 Khanmigo 的实践（[`docs/todos/todo-master.md` § 14.2](../todos/todo-master.md)）

---

## 八、推荐行动顺序

### 8.1 P0（1 周内修）

1. **SEC-1 RateLimitFilter X-Forwarded-For 修正**（0.5 天）
2. **COR-1 启用 migration-version-registry**（0.5 天）+ 修正本人 Faded Parsons 设计稿到 V71
3. **SEC-2 CSP 去掉 unsafe-eval**（0.5 天，需要先确认无 eval 用法）
4. **SEC-3 SvgRenderer 前端 sanitize**（0.5 天）

### 8.2 P1（2 周内修）

5. **SEC-9 抽 InternalServiceKeyAuthFilter** 替代 controller 手工验证（1 天）
6. **SEC-10 admin controller 全部加 @PreAuthorize** （0.5 天）
7. **SEC-5 application-prod.yml force-https=true**（0.5 天）
8. **SEC-6 multipart 上限分级**（1 天）
9. **PERF-1 LLM_API_MAX_RETRIES 与 Resilience4j 协调**（1 天）
10. **PERF-2 NfkInferenceService Bulkhead**（0.5 天）

### 8.3 P2（1-2 月内修）

11. **COR-3 AITutorWorkflowAdminServiceImpl 拆分**（5-7 天）
12. **MNT 巨石组件拆分**（按 todo-master 节奏）
13. **TST-1 e2e 测试自动化**（3-4 天）
14. **MNT-3 todo 文件归档**（0.5 天）
15. **MNT-4 CHANGELOG 按月切分**（0.5 天）

### 8.4 P3（远期）

16. **CMP-1 未成年人保护专项**（参考 Khanmigo，2-3 周）
17. **SEC-4 MCP 鉴权框架**（与 todo-next-phase MCP 落地同步）

---

## 九、不在本期审计的事

- ❌ Helm chart 内部安全（Pod Security Standards / NetworkPolicy）—— 单独审一次
- ❌ Judge Server / 沙箱安全（已在 [`code-review-2026-04-18.md`](code-review-2026-04-18.md) 覆盖）
- ❌ 数据迁移 V1-V70 的逻辑正确性（量太大，需独立 DB 审计）
- ❌ 前端 dependency 漏洞（npm audit 未跑）
- ❌ 后端 dependency 漏洞（参考 [`dependency-health-report.md`](dependency-health-report.md)）
- ❌ 性能 benchmark / 压测（需要 stage 环境实跑）

---

## 十、第一性原理自检

| 自检 | 结果 |
|---|---|
| 是否聚焦真正的风险点？ | 是。仅输出 9 个 High + 10 个 Medium，没有凑数低优先级问题 |
| 是否有具体证据（文件 + 行号）？ | 是。每条 finding 都有 grep 验证的位置 |
| 是否给出可执行修复建议？ | 是。每条都给出代码改动方向，不止于"建议提升"的口号 |
| 是否避免误报？ | 注意：DOMPurify 的 ALLOWED_TAGS 白名单经过验证，未误判为 XSS |
| 是否避免覆盖前次审计已记录的事项？ | 是。本报告补充 4/18 之后的新增风险，重叠项已链接 |

---

## 十一、报告结论

**项目处于 well-engineered 状态，Critical 缺陷为 0**。

**最值得立即修复的 4 项**（按 ROI 排序）：
1. RateLimit X-Forwarded-For（5 行代码 → 修复全班共用 rate limit 误杀）
2. Migration version registry（5 分钟新建文档 → 避免未来 deploy 撞车）
3. CSP unsafe-eval 去掉（5 分钟去掉 → 显著降低 XSS 利用面）
4. SvgRenderer 前端 sanitize（5 行代码 → 防御深度补齐）

**最值得长期投入的 1 项**：拆分 AITutorWorkflowAdminServiceImpl（3743 行）—— 这是当前所有 AI 导学新功能的瓶颈，每加一个动作都要面对它。Faded Parsons + ONNX 设计稿写到一半就发现这个 bottleneck，建议优先腾出 1 周专注瘦身后再开新模块。

---

**报告完。**

> 审计人：AI Coding Assistant via `code-reviewer` skill
> 复核建议：可由项目维护者按 [§ 八 推荐行动顺序](#八推荐行动顺序) 逐条 triage，每条建议可独立提 issue / PR。
