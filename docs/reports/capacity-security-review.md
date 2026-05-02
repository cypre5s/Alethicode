# Alethicode 承载能力与安全性 Review

> 生成日期: 2026-04-17
> 审阅范围: 承载能力（Scalability / Performance）+ 安全性（Security）专项
> 状态标记: `TODO` 未修 / `DOING` 修复中 / `FIXED` 已修复 / `VERIFIED` 已自测
>
> 和 `BUG_REVIEW.md` 的正确性缺陷清单互为补充；本文件聚焦架构级承载瓶颈与安全加固。

---

## 统计

| 大类 | 数量 | 已修 | 剩余 |
|------|------|------|------|
| Critical 🔴（P0 上线必修） | 13 | 0 | 13 |
| High 🟠（P1 上量前修） | 22 | 0 | 22 |
| Medium 🟡（P2 持续改进） | 20 | 0 | 20 |
| **总计** | **55** | **0** | **55** |

## 快速索引

- [A. 承载能力（Scalability / Performance）](#a-承载能力-scalability--performance)
  - [A.1 数据库与连接池](#a1-数据库与连接池) — SC-01 ~ SC-05
  - [A.2 缓存体系](#a2-缓存体系) — SC-06 ~ SC-08
  - [A.3 异步 / 线程池](#a3-异步--线程池) — SC-09 ~ SC-12
  - [A.4 水平扩展](#a4-水平扩展单实例--多实例) — SC-13 ~ SC-16
  - [A.5 文件存储与上传](#a5-文件存储与上传) — SC-17 ~ SC-19
  - [A.6 LLM 调用 / AI Tutor](#a6-llm-调用与-ai-tutor) — SC-20 ~ SC-22
  - [A.7 可观测性](#a7-可观测性) — SC-23 ~ SC-25
- [B. 安全性（Security）](#b-安全性security)
  - [B.1 认证与会话](#b1-认证与会话) — SEC-01 ~ SEC-05
  - [B.2 授权 / 权限](#b2-授权--权限) — SEC-06 ~ SEC-08
  - [B.3 CSRF / XSS / 安全头](#b3-csrf--xss--安全头) — SEC-09 ~ SEC-11
  - [B.4 输入校验](#b4-输入校验) — SEC-12 ~ SEC-14
  - [B.5 SQL 注入 / 反序列化](#b5-sql-注入--反序列化) — SEC-15 ~ SEC-17
  - [B.6 敏感信息 / 密钥](#b6-敏感信息--密钥) — SEC-18 ~ SEC-20
  - [B.7 速率限制](#b7-速率限制多维度) — SEC-21 ~ SEC-22
  - [B.8 WebSocket / 实时通道](#b8-websocket--实时通道) — SEC-23 ~ SEC-24
  - [B.9 依赖与扫描](#b9-依赖与扫描) — SEC-25 ~ SEC-26
  - [B.10 数据保护](#b10-数据保护) — SEC-27 ~ SEC-28

---

## A. 承载能力 (Scalability / Performance)

### A.1 数据库与连接池

#### SC-01 HikariCP 连接池未显式配置 [TODO] 🔴
- **文件**: `backend/src/main/resources/application.yml` L10-13
- **问题**: 没有 `spring.datasource.hikari.*` 任何配置，沿用默认 `maximum-pool-size=10`。项目开了虚拟线程（`spring.threads.virtual.enabled=true`），高并发下成百上千的 vthread 争抢 10 条连接，会看到请求排队等连接超时。
- **修复**:
  ```yaml
  spring:
    datasource:
      hikari:
        maximum-pool-size: 50              # CPU 核数 × 4-8
        minimum-idle: 10
        connection-timeout: 3000
        validation-timeout: 2000
        leak-detection-threshold: 20000    # 20s 未归还告警
        max-lifetime: 1800000              # 30 分钟
        idle-timeout: 600000               # 10 分钟
  ```
- **风险**: 高。默认 10 在中等规模（>50 并发）就会瓶颈。

#### SC-02 `lower(username)` 函数索引缺失 [TODO] 🔴
- **文件**:
  - `middleware/SessionAuthenticationFilter.java` L62-84
  - `controller/AutonomyController.java`（已通过 #1 修复到 AuthUserResolver，但仍会 fallback 到 filter 里的 lower() 查询）
  - `service/impl/AccountServiceImpl.java` 多处
- **问题**: `SELECT ... FROM "user" WHERE lower(username) = ?` 未命中任何索引 → 全表扫描。用户量增长后登录/每请求解析将成为最热的慢查询。
- **修复**: 新增 Flyway 迁移
  ```sql
  -- V_XX__user_lower_username_index.sql
  CREATE INDEX IF NOT EXISTS idx_user_lower_username
      ON "user" ((lower(username)));
  ```
- **风险**: 高。用户数 >10K 时必修。

#### SC-03 JSONB 列缺 GIN 索引 [TODO] 🟠
- **文件**: Flyway 迁移 V17/V18 中 `ai_learner_memory.memory_payload`、`submission.info`、`submission.statistic_info` 等
- **问题**: 业务查询会走 JSONB `@>` / `->` 操作，无 GIN 索引时只能顺序扫描
- **修复**:
  ```sql
  CREATE INDEX IF NOT EXISTS idx_ai_learner_memory_payload_gin
      ON ai_learner_memory USING GIN (memory_payload);
  CREATE INDEX IF NOT EXISTS idx_submission_info_gin
      ON submission USING GIN (info);
  ```
  仅对实际热点字段建，避免写入放大。

#### SC-04 `AITutorServiceImpl` 存在慢 SQL 候选 [TODO] 🟠
- **文件**: `service/impl/AITutorServiceImpl.java` L265-340 (skillHeatmap)、L700-900 (notebook)、L1642-1700 (knowledge graph)
- **问题**: 多个 `GROUP BY to_char(create_time at time zone 'UTC', 'YYYY-MM-DD')` 的天级聚合；部分 ORDER BY submission_number/accepted_number 可能不走索引
- **修复**:
  1. 对 `submission(user_id, create_time DESC)` 加复合索引
  2. 对 `problem(visible, submission_number DESC, id DESC)` 加复合索引
  3. 用 `EXPLAIN ANALYZE` 审最慢的 3 个查询，决定是否做物化视图
- **风险**: 中高，随提交量增长线性恶化。

#### SC-05 重复 user.id 查询（已部分修复） [TODO] 🟠
- **文件**: `service/impl/AccountServiceImpl.findUserBy*`、`AITutorServiceImpl.resolveUser`
- **问题**: 本次 review 已修 Controller 层（AuthUserResolver），但 Service 内仍有重复查询（`login`、`notebook*`、`strategyFeedback` 等链路每次都查 user row）
- **修复**: 加 `@EnableCaching` + 对 `findUserByUsername`/`findUserById` 加 `@Cacheable(key="#username", value="userByUsername")`，写操作时 `@CacheEvict`

### A.2 缓存体系

#### SC-06 无统一缓存抽象 [TODO] 🔴
- **文件**: 全局
- **问题**: 没有 `@EnableCaching`；两处手写内存缓存（`SystemOptionServiceImpl.optionCache`、`MasteryService.prereqMasteryCache`）各做各的 TTL。重启失效、多实例不共享、逻辑重复
- **修复**:
  1. 加 `@EnableCaching` 到 `AlethicodeJavaApplication`
  2. 开发/测试用 Caffeine；生产用 Redis（项目已有 spring-session-data-redis 依赖，可复用连接）
  3. 把现有手写缓存改为 `@Cacheable` + `cacheManager` 统一管理
- **关联**: SC-07 / SC-08

#### SC-07 元数据表无缓存 [TODO] 🟠
- **文件**: `AITutorServiceImpl`、`LearningTwinService`、`BeginnerSupplementPlannerService` 等
- **问题**: `language_pack_kc` / `ai_knowledge_component` / `language_pack_chapter` 属于"读多写极少"元数据，但每次诊断、推荐、审题都重新查，典型读放大
- **修复**: SC-06 铺路后加 `@Cacheable("kcByLanguagePack")`；管理员写入时 `@CacheEvict(allEntries=true)`

#### SC-08 LLM 响应无缓存 [TODO] 🟡
- **文件**: `service/LlmClient.java`、各 Agent
- **问题**: 同一道题同一事件的相邻请求（学生反复点击"审题"）每次都打 LLM。课堂场景下 30 人同时触发同题 `READING` 会产生 30 个 LLM 调用
- **修复**: 对 `(problem_id, event, language, normalized_content_hash)` 做 5-30 分钟短期缓存；注意用户个性化（memory_refs）不适合共享缓存，需分离 prompt 结构
- **风险**: 成本/延迟双向获利，值得做

### A.3 异步 / 线程池

#### SC-09 judge/codeQuality 线程池硬编码 [TODO] 🔴
- **文件**: `service/impl/SubmissionServiceImpl.java` L75-110（本次 review 改过生命周期，但容量仍硬编码 4 + 2）
- **问题**: 判题调度 4 线程、代码质量评估 2 线程，峰值高于这个数就只能排队；且不可通过配置调整
- **修复**:
  ```java
  // AlethicodeProperties
  public static class Executor {
      private int judgePoolSize = Runtime.getRuntime().availableProcessors();
      private int codeQualityPoolSize = 2;
      // getter/setter...
  }
  ```
  在 `SubmissionServiceImpl` 构造函数注入 properties 并取 pool size

#### SC-10 散落的 `Executors.newFixedThreadPool` [TODO] 🟠
- **文件**: `AITutorWorkflowAdminServiceImpl`、`LanguagePack*Service` 等多处
- **问题**: 多个服务各自 `Executors.newFixedThreadPool`/`newSingleThreadExecutor`，Spring 不管理生命周期，也没统一的线程命名和监控
- **修复**: 新建 `config/AsyncConfig.java`，声明 2-3 个命名 `TaskExecutor` Bean（`aiTutorExec`、`languagePackExec`、`submissionExec`），所有服务通过 `@Autowired(@Qualifier)` 注入

#### SC-11 Redis Stream 参数硬编码 [TODO] 🟠
- **文件**: `config/RedisStreamConfig.java` L38-41
- **问题**: `pollTimeout=2s`、`batchSize=10` 硬编码，大流量时吞吐不足
- **修复**: 抽到 `alethicode.stream.judge-dispatch.{poll-timeout-ms, batch-size, parallelism}` 配置项

#### SC-12 虚拟线程 pinning 风险 [TODO] 🟠
- **文件**: 全局 `@Transactional` 方法、所有 `synchronized` 块
- **问题**: Java 21 虚拟线程在 `synchronized`/native 调用/长事务中会 pin 到 carrier thread。`SubmissionServiceImpl` 内的判题调度含 HTTP 同步调用 + DB 事务，容易 pin
- **修复**:
  1. 启动参数加 `-Djdk.tracePinnedThreads=short` 暴露 pin 点
  2. 把 `synchronized` 改为 `ReentrantLock`（Java 21 vthread 已支持 ReentrantLock）
  3. 长 DB 事务保持在 platform thread 执行（通过自定义 `TaskExecutor` `blockingExecutor`）

### A.4 水平扩展（单实例 → 多实例）

#### SC-13 SubmissionThrottleService 用 JVM 内存 token bucket [TODO] 🔴
- **文件**: `service/submission/SubmissionThrottleService.java` L15
- **问题**: `private static final ConcurrentMap<String, TokenBucketState> THROTTLE_BUCKETS = new ConcurrentHashMap<>();` 是进程内状态。N 个实例 → 实际限流倍数 = N，直接把上限放大
- **修复**: 改为 Redis Lua 脚本做原子 token bucket（参考 `bucket4j-redis` / `resilience4j-reactor-ratelimiter`）。现有接口不变，只替换 `TOKEN_BUCKETS` 实现
- **关联**: SEC-21 / SEC-22 同款修复

#### SC-14 WebSocket 内存 Map 状态 [TODO] 🔴
- **文件**:
  - `websocket/ClassroomCollabWebSocketHandler.java` L32-33
  - `websocket/ClassroomMonitorWebSocketHandler.java`
  - `websocket/WorkflowWebSocketHandler.java`
  - `websocket/QaWebSocketHandler.java`
- **问题**: `roomSessions` / `roomPresence` 都是 JVM `ConcurrentHashMap`。若多实例部署，A 节点的学生收不到 B 节点其他学生的协作更新 → 课堂协同直接坏掉
- **修复（两选一）**:
  1. **Redis Pub/Sub 广播**：Handler 推送消息同时 `PUBLISH channel:classroom:<id>`，所有实例 `SUBSCRIBE` 转发给本地订阅者。工作量中等。
  2. **Nginx sticky session by classroom_id**：在 nginx upstream 层按 URL hash `/ws/classroom/collab/<id>` 粘性路由到同一节点。工作量小，但限制单班级容量到单机。
- **选择建议**: MVP 用方案 2，正式上量用方案 1

#### SC-15 BetaFeatureRegistry 缓存不跨实例 [TODO] 🟠
- **文件**: `config/BetaFeatureRegistry.java` L63
- **问题**: `ConcurrentHashMap<String, Boolean> cache` 只在单实例生效；管理员实例 A 打开 `REACT_ENABLED`，实例 B 下的用户命中的仍是旧值
- **修复**: 改用 Spring Cache + `@CacheEvict` + Redis Pub/Sub 通知所有实例 evict；或直接每次读 DB（JSON 一行，500 μs 级别可接受）

#### SC-16 Spring Session 未显式启用 Redis [TODO] 🟠
- **文件**: `application.yml`
- **问题**: `spring-session-data-redis` 依赖在 pom 里，但 yml 未写 `spring.session.store-type=redis`。Spring Boot 3.4 的 auto-config 要求明确配置；否则可能回退到 in-memory HttpSession
- **修复**:
  ```yaml
  spring:
    session:
      store-type: redis
      timeout: 30m
      redis:
        namespace: alethicode:session
        flush-mode: immediate
  ```

### A.5 文件存储与上传

#### SC-17 课件/头像存本地文件，不支持水平扩展 [TODO] 🟠
- **文件**: `service/impl/AdminUploadServiceImpl.saveFile`、`ClassroomLessonService`、课件预览 preview-dir
- **问题**: 文件散落在 `UPLOAD_DIR` / `CLASSROOM_LESSON_DIR` / `LANGUAGE_PACK_PREVIEW_DIR` 等本地目录，多实例部署需共享 NFS 或把这些挂载同一卷
- **修复**: 抽 `AssetStorage` 接口
  ```java
  interface AssetStorage {
      String put(String prefix, String filename, InputStream body, String contentType);
      byte[] get(String key);
      boolean delete(String key);
  }
  ```
  实现 `LocalAssetStorage` + `S3AssetStorage`（MinIO 兼容），按 `alethicode.system.storage.type` 切换

#### SC-18 全局 multipart 上限对普通接口过大 [TODO] 🟠
- **文件**: `application.yml` L21-24
- **问题**: `max-file-size=128MB`、`max-request-size=256MB` 全局统一。头像上传接口（`AccountController.uploadAvatar`）继承了这个上限，攻击者可发 200MB 请求拖死内存
- **修复**:
  1. 降全局到 10MB
  2. 课件/题目数据集走专用 `@RequestPart` + 自定义 `MultipartResolver`，或直接走 Nginx/S3 presigned URL 绕开 Spring
  3. Nginx `client_max_body_size` 分 path 差异化

#### SC-19 头像从 DB base64 读 [TODO] 🟡
- **文件**: `controller/PublicAssetController.avatar` L70-93
- **问题**: 头像不在本地文件时，从 `sys_options` 读 base64 反序列化 → 每次请求都解一次 base64 到字节流
- **修复**: 头像统一走 S3/CDN；或至少在 DB 取一次后写入本地缓存文件

### A.6 LLM 调用与 AI Tutor

#### SC-20 LLM 调用无 per-user/per-classroom 限流 [TODO] 🔴
- **文件**: `service/LlmClient.java`、各 Agent
- **问题**: 恶意用户/学生恶作剧可无限触发 ReAct（最多 3 迭代 × 多 Agent × 每次 3-5 秒 × Token 消耗），瞬间耗尽 API key 额度；多班级共用一个 key 时影响大
- **修复**:
  1. 复用 SubmissionThrottleService 模板给 LLM 调用加限流：每用户 20 次/小时、每班级 200 次/小时
  2. 配 `alethicode.llm.rate-limit.*`
- **关联**: SC-13（多实例限流需同步走 Redis）

#### SC-21 AI Tutor inference 同步阻塞 Tomcat 线程 [TODO] 🟠
- **文件**: `service/impl/AITutorServiceImpl.inference` 等
- **问题**: 当前 `inference` 接口在 HTTP 请求线程里同步调 LLM + DB 写，5 秒起步。虚拟线程能稍缓解但仍消耗连接
- **修复**: 部分接口已实现 task_id + 轮询模式（`ai/tutor/task`），需全面化。新接口一律返回 task_id，前端 polling `GET /api/ai/tutor/task?task_id=xxx` 或走 WebSocket 通知
- **配套**: 前端 UnifiedAgentPanel 需支持任务进度流

#### SC-22 NFK ONNX 推理无 session pool [TODO] 🟠
- **文件**: `service/NfkInferenceService.java` L62-68
- **问题**: 单例 `OrtSession` + `setIntraOpNumThreads(2)`，所有并发请求争抢；尽管 ONNX session 本身线程安全，但 pin 到 2 op threads 会瓶颈
- **修复**: 建 N 个 OrtSession 放进 `ArrayBlockingQueue`，`predict` 时 `poll` 一个 session + 计算 + `offer` 回队列；或用 `OrtThreadPoolParams` 定制

### A.7 可观测性

#### SC-23 业务指标缺失 [TODO] 🟠
- **文件**: 全局
- **问题**: Micrometer + Prometheus 已暴露系统指标，但没有业务指标（提交 tps、LLM latency P95、judge queue length、login success/failure rate、throttle rejected count）
- **修复**: 在关键路径注入 `MeterRegistry`，埋点 `Counter`/`Timer`/`Gauge`。建议先埋 5 个：
  - `alethicode.submission.created_total`
  - `alethicode.submission.judge_latency_seconds`
  - `alethicode.llm.call_total` + `alethicode.llm.latency_seconds`
  - `alethicode.auth.login_attempt_total{outcome=success|failed|throttled}`
  - `alethicode.nfk.infer_latency_seconds`

#### SC-24 慢 SQL 日志未启用 [TODO] 🟠
- **文件**: `application.yml` / PG 配置
- **问题**: HikariCP leak detection 未开（SC-01 一并修）；PG 侧也未开 `log_min_duration_statement`
- **修复**:
  - Spring 侧 SC-01 已含 `leak-detection-threshold: 20000`
  - PG 侧 docker-compose 启动参数加 `-c log_min_duration_statement=500`（>500ms 日志）

#### SC-25 全链路追踪缺失 [TODO] 🟡
- **文件**: 全局
- **问题**: 多服务调用链（Controller → DomainService → Service → DB/LLM）没有 traceId 贯穿，出问题定位难
- **修复**: 加 `micrometer-tracing-bridge-otel` + OpenTelemetry exporter → Tempo/Jaeger

---

## B. 安全性 (Security)

### B.1 认证与会话

#### SEC-01 登录无失败次数限流 [TODO] 🔴
- **文件**: `service/impl/AccountServiceImpl.login` L88-123
- **问题**: 仅 captcha（注册有），登录本身**只要知道 captcha 解答+密码就能无限次重试**；结合密码字典可遍历常见弱密码
- **修复**: 复用 `SubmissionThrottleService` 模式给 login 加 token bucket
  ```java
  // AccountServiceImpl.login 方法开头
  String throttleKey = "login:" + username + ":" + clientIp;
  if (!loginThrottleService.allowLogin(throttleKey)) {
      throw BusinessExceptions.fromLegacy("error",
          "登录过于频繁，请 15 分钟后再试");
  }
  // 登录失败时 recordFailure(throttleKey)
  // 登录成功时 clear(throttleKey)
  ```
  建议 5 次失败锁 15 分钟（IP + username 双维度），同时可触发 email 告警

#### SEC-02 密码重置接口无限流 [TODO] 🟠
- **文件**: `AccountServiceImpl.applyResetPassword` L395-423
- **问题**: 每次请求生成 32 字符 token，有效期 20 分钟，DB 更新；但**无 rate limit**。同一 email 可被反复轰炸，让用户收到无数重置邮件
- **修复**: 按 `email` + `ip` 限流；相同 email 5 分钟内只能发一次，每天最多 5 次

#### SEC-03 Session 超时未显式配置 [TODO] 🟠
- **文件**: `application.yml`
- **问题**: Spring Session Redis 默认 30 分钟，对 admin 偏长
- **修复**:
  ```yaml
  spring.session.timeout: 30m   # 普通用户
  ```
  admin 用户考虑登录时单独标记、下次请求时若身份是 admin 把 `session.setMaxInactiveInterval(900)` 改成 15 分钟

#### SEC-04 auth_token timing-safe 比较（已在 DB 侧，OK） [VERIFIED]
- **文件**: `AccountServiceImpl.findUserByAuthToken`
- **说明**: `WHERE auth_token = ?` 在 DB 里比较，timing attack 影响在网络和 DB 层面均被平滑化；此项不是 bug，仅需关注若未来改为内存比较要用 `MessageDigest.isEqual`

#### SEC-05 auth_token / open_api_appkey 永久有效 [TODO] 🟠
- **文件**: `AccountServiceImpl.issueSsoToken` L570-583、`refreshOpenApiAppkey` L562-572
- **问题**: 写入 `user.auth_token` 后无过期列；token 泄露后永久可用
- **修复**: 加 `auth_token_expire_time` 列，默认 7 天；读取时校验；定时任务清理过期 token

### B.2 授权 / 权限

#### SEC-06 Controller 缺少 @PreAuthorize 统一兜底 [TODO] 🔴
- **文件**: 几乎所有非 admin 的 `*Controller`
- **问题**: 项目目前靠 `Authentication` 参数 + Service 内手工判空实现授权。**漏写一处就是水平越权（本次 review 的 #1 就是例子）**。依赖"每个新 controller 作者都记得在 service 里判空"是不现实的。
- **修复**: 在所有非 `@PreAuthorize` 注解的 `@RestController` 类上加：
  ```java
  @PreAuthorize("isAuthenticated()")
  ```
  对不需要登录的端点（login、register、captcha、public/**），显式 `@PreAuthorize("permitAll()")`。同时 SecurityConfig 里已有 `.anyRequest().authenticated()` 做第二道保险。

#### SEC-07 Classroom 学生档案越权风险 [TODO] 🔴
- **文件**: `controller/classroom/ClassroomAnalyticsController` L67-77
- **问题**: `riskStudentAdvice(classroomId, userId)`、`studentProfile(classroomId, userId)` 都接受 `@PathVariable Long userId`。若 Service 内部未校验"当前登录者是该 classroom 的 teacher"，任意教师可查看非自己班级学生档案
- **修复**:
  1. 确认 Service 层有 `classroomAccessHelper.requireTeacher(classroomId, currentUserId)` 检查
  2. 方法级加 `@PreAuthorize("@classroomGuard.canAccessStudent(#classroomId, #userId)")`
  3. 新建 `ClassroomGuard` Bean 暴露该方法

#### SEC-08 超级管理员操作无二次验证 + 审计 [TODO] 🟠
- **文件**: `controller/AdminConfigController` 所有 `/api/admin/super/*`
- **问题**: admin 账号被钓鱼后可直接改 LLM key / Infra secrets 到恶意 endpoint，无审计日志、无二次确认
- **修复**:
  1. 新建 `admin_audit_log` 表记录 who/when/what
  2. 超级敏感操作（AI provider key、infra secrets）要求重新输入密码 / TOTP 二次确认
  3. 变更时发通知到预置的 SOC email

### B.3 CSRF / XSS / 安全头

#### SEC-09 CSP 形同虚设 [TODO] 🔴
- **文件**: `config/SecurityConfig.java` L71-72
- **问题**: CSP 里同时开了 `script-src 'unsafe-inline' 'unsafe-eval'` 和 `style-src 'unsafe-inline'`——实际就是"没 CSP"。加了 DOMPurify 后，剩下的主要风险是第三方脚本被篡改或内联注入
- **修复**（分步）:
  1. 先去 `'unsafe-eval'`（marked/katex 都有无 eval 模式；CodeMirror 6 也不需要 eval）
  2. 用 nonce/sha256 替代 `'unsafe-inline'`：Thymeleaf/Vue SSR 时生成 nonce 注入模板
  3. 验收：浏览器控制台无 CSP violation

#### SEC-10 WebSocket Origin（已修 #38）[VERIFIED]
- **说明**: 本次 review 已把 `setAllowedOrigins("*")` 改为基于 `alethicode.website.base-url` + localhost 白名单

#### SEC-11 DOMPurify 协议白名单验证 [TODO] 🟠
- **文件**: `frontend/src/utils/sanitize.js`
- **问题**: `ALLOWED_ATTR` 含 `href`、`src`。DOMPurify 默认已拒绝 `javascript:` / `data:text/html` 等危险协议，但需要自测验证
- **修复**: 添加单元测试
  ```js
  describe('sanitize', () => {
    it('strips javascript: links', () => {
      const html = sanitize('<a href="javascript:alert(1)">x</a>')
      expect(html).not.toContain('javascript:')
    })
    it('strips data: html', () => {
      const html = sanitize('<a href="data:text/html,<script>alert(1)</script>">x</a>')
      expect(html).not.toContain('data:text/html')
    })
  })
  ```

### B.4 输入校验

#### SEC-12 60+ 处 @RequestBody Map 不走 Jakarta Validation [TODO] 🔴
- **文件**: `AITutorController`、`AITutorWorkflowController`、`ClassroomCoreController`、`ClassroomAssignmentController` 等
- **问题**: 大量接口签名 `@RequestBody Map<String, Object> request`，只能靠 service 层逐个字段判空。漏判就是 NPE / 越权 / 类型错乱（已在本次 review 的 #12/#14 体现）
- **修复**: 逐步替换为明确 DTO
  1. 先把所有新增接口必须走 DTO 的约束写进 AGENTS.md
  2. 按优先级把现存 60+ 处接口改造，每次提交 3-5 个
  3. 加静态检查：如果 Controller 参数 `Map<String, Object>`，CI 报 warning

#### SEC-13 @RequestParam Map<String, String> 同类问题 [TODO] 🟠
- **文件**: 同上，以及 `AITutorController.skillRadar/skillHeatmap/recommendProblems`
- **问题**: 类型转换散落在 service 内的 `parseLong/parseInt`，违反 failfast
- **修复**: 改为明确命名参数 `@RequestParam Long languagePackId`，Spring 自动做类型校验；校验失败由 `GlobalExceptionHandler` 转 400

#### SEC-14 listSubmissions 可按用户名枚举他人记录 [TODO] 🟠
- **文件**: `controller/SubmissionController.listSubmissions` L62-76
- **问题**: 接受 `username` 参数，返回他人提交列表；依赖 `alethicode.website.submission-list-show-all` 开关，默认 **true**。实际场景下教师只应看自己班级的、学生只应看自己的
- **修复**:
  1. 默认值改 false
  2. 非 admin/teacher 拒绝 `username` 参数
  3. teacher 只能查看自己班级学生

### B.5 SQL 注入 / 反序列化

#### SEC-15 SQL 注入 CI 静态检查 [TODO] 🟠
- **文件**: `pom.xml` / CI
- **问题**: 本次 review 没发现活跃的 SQL 注入，但依赖"开发者正确使用 PreparedStatement"。新代码可能误用字符串拼接
- **修复**:
  ```xml
  <plugin>
      <groupId>com.github.spotbugs</groupId>
      <artifactId>spotbugs-maven-plugin</artifactId>
      <configuration>
          <plugins>
              <plugin>
                  <groupId>com.h3xstream.findsecbugs</groupId>
                  <artifactId>findsecbugs-plugin</artifactId>
                  <version>1.13.0</version>
              </plugin>
          </plugins>
      </configuration>
  </plugin>
  ```
  把 `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE` 设为 ERROR

#### SEC-16 Jackson polymorphic 反序列化注意 [TODO] 🟡
- **文件**: 无显式使用
- **问题**: 将来若引入 `@JsonTypeInfo`/`@JsonSubTypes`，默认 typing 是危险的
- **修复**: `ObjectMapper.deactivateDefaultTyping()`（Jackson 2.10+ 默认已关闭）；显式子类 whitelist

#### SEC-17 NFK ONNX model-path 受控 [TODO] 🟡
- **文件**: `service/NfkInferenceService.init`
- **问题**: `alethicode.nfk.model-path` 若暴露给 admin 可动态替换，恶意 ONNX 可触发 OnnxRuntime 内核 DoS 或潜在 CVE
- **修复**: model-path 只允许指向 `classpath:` 或受控目录；禁止从上传接口动态替换

### B.6 敏感信息 / 密钥

#### SEC-18 关键环境变量空值未 fail-fast [TODO] 🔴
- **文件**: `config/AlethicodeProperties.java` L19-25（已有 judge-server.token 校验）；`application.yml` 其他敏感项未校验
- **问题**: `DB_PASSWORD`、`OPENAI_API_KEY` 空字符串时 Spring 正常启动，直到首次使用才 500 错，错误信息可能泄露内部结构
- **修复**: 扩展 `AlethicodeProperties.validate`
  ```java
  @PostConstruct
  void validate() {
      if (judgeServer.getToken().isBlank()) { throw ... }
      // 新增：AI 开启时必须有 key
      if (isAiEnabled() && openaiApiKey.isBlank()) {
          throw new IllegalStateException(
              "spring.ai.openai.enabled=true 但 OPENAI_API_KEY 未配置");
      }
  }
  ```
  `application.yml` 里 `${DB_PASSWORD}` 没有 fallback 默认值，Spring Boot 启动时就会 fail-fast，这部分已 OK。

#### SEC-19 配置响应未对密钥打码 [TODO] 🟠
- **文件**: `service/impl/SystemOptionServiceImpl` 的 `getInfraSecrets` / `getAiProviderConfig`
- **问题**: 管理员打开 UI 查看时，API 返回完整 `sk-xxxxx-xxxx` 字符串（即使 HTTPS，浏览器 DevTools 也能看到）
- **修复**: 统一 helper：
  ```java
  private static String mask(String raw) {
      if (raw == null || raw.length() <= 8) return "****";
      return raw.substring(0, 3) + "***" + raw.substring(raw.length() - 4);
  }
  ```
  所有 secret 字段出库前走 mask；只有 `PUT` 写入时接受完整值

#### SEC-20 日志中 PII 脱敏 [TODO] 🟠
- **文件**: `GlobalExceptionHandler`、`LlmClient`、`AccountServiceImpl` 等
- **问题**: `log.error("Unhandled exception", exception)` 的 exception.getMessage() 可能带 SQL 参数、用户名、email
- **修复**: Logback 层加 `MaskingPatternLayout` 对 `password=xxx`、`email=xxx@` 模式替换；或者业务日志统一过 `SensitiveFieldSanitizer`

### B.7 速率限制（多维度）

#### SEC-21 全局 API 限流缺失 [TODO] 🔴
- **文件**: 全局
- **问题**: 除 submission/debug 外，所有 API 都无限流：注册、发邮件、AI Tutor、Learning Twin、KC 图都能被 DoS
- **修复**:
  1. Gateway 层：Nginx `limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;` + `/api/ai/*` 单独一组更严
  2. 应用层：通用 `RateLimitingFilter`，按 IP + userId 限流；特殊接口（LLM、upload）单独更严
  3. 配置化，用 AlethicodeProperties.RateLimit

#### SEC-22 strategyFeedback 无限流 [TODO] 🟠
- **文件**: `service/aitutor/profile/StrategyFeedbackService`（本次 review 新建）
- **问题**: 已加白名单但未限流；登录用户可 1 秒发 1000 次 `(strategy_type, rating)` 让 UPSERT 撑爆表
- **修复**: 复用 `SubmissionThrottleService` 加 `strategy_feedback:<user_id>` 维度，例如 60 次/分钟

### B.8 WebSocket / 实时通道

#### SEC-23 WebSocket 消息 size 未限制 [TODO] 🟠
- **文件**: `config/ClassroomWebSocketConfig`、`config/WorkflowWebSocketConfig`
- **问题**: Spring WebSocket 默认 message size 8KB，但各 Handler 内用 `TextMessage`，未显式限制；`@Valid` 也不适用
- **修复**: `registry.setMessageSizeLimit(65_536)`（协作代码需要稍大）并在 Handler 内 `message.getPayloadLength() > LIMIT` 时关闭连接

#### SEC-24 QaWebSocketHandler 每条消息查 DB [TODO] 🟠
- **文件**: `websocket/QaWebSocketHandler.qaSessionOwnedByUser` L74-82
- **问题**: 每次建连都查 `language_pack_chat_session` 验证 ownership——一个恶意客户端可高频建连耗 DB
- **修复**: ownership 验证缓存到 WS session attributes；同时给 `/ws/qa/*` 加连接速率限制（IP 级）

### B.9 依赖与扫描

#### SEC-25 OWASP Dependency-Check 未启用 [TODO] 🟠
- **文件**: `pom.xml`
- **问题**: 没有 CVE 扫描 CI gate，Spring Boot 3.4.4 / Jsoup 1.18.3 未来出 CVE 无法自动发现
- **修复**:
  ```xml
  <plugin>
      <groupId>org.owasp</groupId>
      <artifactId>dependency-check-maven</artifactId>
      <version>10.0.4</version>
      <configuration>
          <failBuildOnCVSS>7</failBuildOnCVSS>
      </configuration>
  </plugin>
  ```
  CI 中执行 `mvn dependency-check:check`

#### SEC-26 前端 npm audit 未启用 [TODO] 🟠
- **文件**: `frontend/package.json`
- **问题**: 同上，无前端依赖 CVE 扫描
- **修复**: CI 加 `npm audit --production --audit-level=high`（每日 cron 跑）

### B.10 数据保护

#### SEC-27 敏感列未加密 [TODO] 🟡
- **文件**: DB schema 中 `ai_learner_memory.memory_value`、`ai_learner_notebook.root_cause/fix_outcome/student_reflection`、`submission.code` 等
- **问题**: 这些字段含学生编程过程中的错误描述、代码、反思文本，可能含 PII；DB 若被直接导出会泄露
- **修复**:
  1. 用 PostgreSQL `pgcrypto` 的 `pgp_sym_encrypt/decrypt`（密钥在 env）
  2. 或应用层 AES-GCM（`javax.crypto.Cipher`），字段级加密

#### SEC-28 GDPR-like 数据删除 [TODO] 🟡
- **文件**: 用户相关全链表
- **问题**: 用户注销/老师离职后学习记录未规定保留期和删除机制
- **修复**:
  1. `user.deleted_at` 列 + 定时任务 30 天后物理删除（保留审计所需的 submission 统计）
  2. 提供"数据导出"API（让用户能拿走自己所有记录）

---

## C. 修复梯队

### 第一梯队（上线必修，13 项）

> 这些问题在生产流量下会直接暴雷，或是严重安全漏洞。

| 编号 | 标题 | 类型 | 工作量估算 |
|------|------|------|------|
| SC-01 | HikariCP 连接池配置 | 性能 | < 1 小时 |
| SC-02 | lower(username) 函数索引 | 性能 | < 1 小时 |
| SC-06 | 启用统一缓存抽象 | 性能 | 半天 |
| SC-09 | judge 线程池可配置 | 性能 | < 1 小时 |
| SC-13 | SubmissionThrottle 多实例 | 扩展 | 1-2 天 |
| SC-14 | WebSocket 多实例 | 扩展 | 2-3 天 |
| SC-20 | LLM 限流 | 安全/性能 | 半天 |
| SEC-01 | 登录失败限流 | 安全 | 半天 |
| SEC-06 | @PreAuthorize 兜底 | 安全 | 1 天 |
| SEC-07 | 学生档案越权检查 | 安全 | 半天 |
| SEC-09 | 移除 CSP unsafe-eval | 安全 | 1 天（适配测试） |
| SEC-12 | 替换 Map<String, Object> DTO | 安全 | 3-5 天（分批） |
| SEC-18 | 环境变量 fail-fast | 安全 | < 1 小时 |
| SEC-21 | 全局 API 限流 | 安全 | 2-3 天 |

### 第二梯队（小流量稳定后修，22 项）

> SC-03/04/05/07/08/10/11/12/15/16/17/18 + SEC-02/05/08/11/13/14/19/20/22/23/24/25/26

### 第三梯队（持续改进，20 项）

> 剩余 P2 + 架构级（S3 迁移、OpenTelemetry、RBAC 重构、数据加密、GDPR 支持）

---

## D. Quick Wins（半天内可完成）

按 ROI 排序的"立即能做"清单：

1. **SC-01** HikariCP 池大小（5 行 yml）
2. **SC-02** 函数索引 Flyway 迁移（10 行 SQL）
3. **SEC-09** CSP 去 `unsafe-eval`（1 行改动 + 浏览器回归测试）
4. **SEC-18** OPENAI_API_KEY 空值 fail-fast（10 行 Java）
5. **SEC-19** secret 字段 mask（30 行 Java）
6. **SEC-01** login 失败限流（复用 SubmissionThrottleService 模板，约 50 行）
7. **SEC-23** WebSocket 消息 size 限制（3 行配置）
8. **SC-24** PG 慢 SQL 日志（一行 docker 启动参数）

> 8 项全做完大约半天到 1 天，能吃掉一半最严重的威胁。

---

## E. 后续扩展建议

- 长期架构演进：单节点 → Nginx + 多 Spring 实例 + Redis（session/cache/ratelimit）+ MinIO（对象存储）
- 监控建议：Prometheus + Grafana 先上三张板子（请求/数据库/LLM）
- 安全建议：季度做一次 OWASP Top 10 自测 + SpotBugs + npm audit + penetration test
- 数据建议：按 user_id / classroom_id 分区，便于后续分库分表

---

## 备注

- 本文件由 code review 产生，和 `BUG_REVIEW.md` 的 42 个正确性缺陷互不重叠
- 所有 `TODO` 项目修复后应更新状态标记并填写"修复"段落（可参考 `BUG_REVIEW.md` 的格式）
- 建议配合 `CHANGELOG.md` 同步记录每项修复的上线时间
