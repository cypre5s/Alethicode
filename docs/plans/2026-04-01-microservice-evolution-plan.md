# Alethicode Microservice Evolution Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在不引入结构性错误的前提下，按最短正确路径把当前单体系统演进为“核心主站 + 独立热点能力服务”的架构，优先解决模块耦合、扩容受限和高并发热点问题。

**Architecture:** 先做单体内边界收口，再外置判题执行能力和 AI 异步执行能力，最后视收益决定是否继续拆分课堂实时协作。主站保留用户、权限、题目、课堂编排和统一 API 入口，独立服务只承接计算密集或连接密集型热点能力。

**Tech Stack:** Spring Boot 3.4, Java 21, PostgreSQL, Redis, WebSocket, Flyway, Maven

---

### Task 1: 冻结目标边界并形成拆分基线

**Files:**
- Modify: `docs/plans/2026-04-01-microservice-evolution-plan.md`
- Check: `backend/src/main/java/com/alethicode/service/impl/SubmissionServiceImpl.java`
- Check: `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
- Check: `backend/src/main/java/com/alethicode/service/impl/AITutorServiceImpl.java`
- Check: `backend/src/main/java/com/alethicode/service/impl/ClassroomServiceImpl.java`
- Check: `backend/src/main/java/com/alethicode/config/SecurityConfig.java`

**Step 1: 明确本次演进只解决三个目标**

写入并确认：
- 目标一：降低 `SubmissionServiceImpl`、`AITutorWorkflowAdminServiceImpl`、`AITutorServiceImpl`、`ClassroomServiceImpl` 的跨域耦合。
- 目标二：提升判题和 AI 工作流的并发能力与扩容能力。
- 目标三：不在第一阶段拆课堂主业务，不引入分布式事务，不改业务语义。

**Step 2: 明确本次不做的事情**

写入并确认：
- 不做全量微服务化。
- 不做课堂域整域拆分。
- 不做兼容旧新双写长期并存。
- 不做复杂服务治理平台先行。

**Step 3: 记录当前架构基线**

写入并确认：
- 当前是单体 Spring Boot。
- 认证依赖 Session + CSRF。
- 核心业务大量直接使用 `JdbcTemplate`。
- 多个热点能力位于超大 Service 中。
- AI 工作流和课堂协作包含实时连接与异步执行逻辑。

**Step 4: 形成架构判定结论**

写入并确认：
- 该项目适合“渐进式微服务演进”。
- 不适合立即拆成多个业务型微服务。

**Step 5: 提交文档变更**

Run:
```bash
git add docs/plans/2026-04-01-microservice-evolution-plan.md
git commit -m "docs: add microservice evolution plan"
```

Expected: 新增计划文档提交成功。

### Task 2: 选择演进路径并确定推荐方案

**Files:**
- Modify: `docs/plans/2026-04-01-microservice-evolution-plan.md`

**Step 1: 记录方案 A**

写入：
- 方案 A：保持单体，只做模块化重构。
- 优点：风险最低、迁移最快。
- 缺点：无法从部署层面提升判题与 AI 的独立扩容能力。

**Step 2: 记录方案 B**

写入：
- 方案 B：保留业务主站，拆出“判题服务 + AI 执行服务”。
- 优点：直接命中并发热点，拆分收益最大，数据边界相对可控。
- 缺点：需要补齐任务投递、状态回写和统一认证边界。

**Step 3: 记录方案 C**

写入：
- 方案 C：按业务域拆出 account、submission、classroom、ai 四大微服务。
- 优点：理论上边界最彻底。
- 缺点：当前共享表、共享 session、跨域查询太多，落地成本最高，短期风险不可接受。

**Step 4: 形成推荐结论**

写入：
- 推荐方案：选择方案 B。
- 推荐理由：判题和 AI 是高并发热点，具备相对天然的异步边界；课堂和账户仍保留在主站内，避免过早引入网络耦合。

**Step 5: 提交文档变更**

Run:
```bash
git add docs/plans/2026-04-01-microservice-evolution-plan.md
git commit -m "docs: compare microservice evolution options"
```

Expected: 方案对比与推荐结论写入完成。

### Task 3: 定义目标架构与服务边界

**Files:**
- Modify: `docs/plans/2026-04-01-microservice-evolution-plan.md`
- Check: `backend/src/main/java/com/alethicode/controller/SubmissionController.java`
- Check: `backend/src/main/java/com/alethicode/controller/AITutorWorkflowController.java`
- Check: `backend/src/main/java/com/alethicode/controller/classroom/ClassroomCoreController.java`

**Step 1: 定义主站职责**

写入主站保留范围：
- 用户、登录、Session、CSRF、权限。
- 题目、比赛、后台管理。
- 课堂、作业、课件、成员、课堂会话编排。
- 提交入口、AI 工作流入口、统一 API 输出。

**Step 2: 定义判题服务职责**

写入判题服务职责：
- 接收判题任务。
- 拉取测试用例。
- 连接判题机或沙箱执行。
- 回传判题结果、性能指标、错误信息。

**Step 3: 定义 AI 执行服务职责**

写入 AI 执行服务职责：
- 承接 AI workflow 的异步生成、检索、trace、评估任务。
- 持有短生命周期任务执行上下文。
- 把最终结果和中间状态回写主站可查询存储。

**Step 4: 定义暂不拆分的课堂实时能力**

写入：
- 第一阶段保留课堂 WebSocket 在主站。
- 只有当连接规模成为真实瓶颈时，才拆出 realtime gateway。

**Step 5: 定义调用关系**

写入：
- 用户 -> 主站 API。
- 主站 -> 队列/任务表 -> 判题服务。
- 主站 -> 队列/任务表 -> AI 执行服务。
- 判题服务/AI 执行服务 -> 回写主站持久层或通过受控内部接口回传。

### Task 4: 定义数据库归属与数据边界

**Files:**
- Modify: `docs/plans/2026-04-01-microservice-evolution-plan.md`
- Check: `backend/src/main/resources/db/migration`

**Step 1: 定义主站数据库归属**

写入主站持有表：
- `user` 及登录会话相关表。
- `problem`、`contest`、`announcement`、`system_config` 相关表。
- `classroom*` 全量表。
- `submission` 作为提交事实主表。
- `ai_workflow_session`、`ai_workflow_event`、`ai_workflow_checkpoint`。
- `ai_learning_event`、`ai_learner_notebook`、`ai_code_snapshot`。

**Step 2: 定义判题服务数据库策略**

写入：
- 第一阶段不独占业务库。
- 判题服务只通过任务表或内部接口获取待判题任务。
- 判题结果只回写 `submission` 所需字段，不直接读写课堂和账户域数据。

**Step 3: 定义 AI 执行服务数据库策略**

写入：
- 第一阶段不单独建业务主库。
- AI 服务仅读 AI workflow 所需上下文与题目信息。
- AI 服务只回写 AI 结果和运行日志，不直接变更课堂主业务状态。

**Step 4: 明确禁止项**

写入：
- 禁止新服务直接跨域查询 `classroom` 与 `submission` 以外无关表。
- 禁止多个服务共同维护同一业务状态机。
- 禁止引入分布式事务。

**Step 5: 形成最终原则**

写入：
- 数据主权仍在主站。
- 外部服务只负责执行，不拥有业务编排权。

### Task 5: 设计同步改异步的任务流

**Files:**
- Modify: `docs/plans/2026-04-01-microservice-evolution-plan.md`
- Check: `backend/src/main/java/com/alethicode/service/impl/SubmissionServiceImpl.java`
- Check: `backend/src/main/java/com/alethicode/websocket/WorkflowRealtimeSupport.java`

**Step 1: 设计判题任务流**

写入：
1. 主站创建 `submission`，状态设为“等待执行”。
2. 主站向 `judge_task` 队列或任务表投递任务。
3. 判题服务消费任务并执行。
4. 判题服务回写结果。
5. 主站按原接口返回查询结果。

**Step 2: 设计 AI workflow 异步任务流**

写入：
1. 主站写入 `ai_workflow_session` / `ai_workflow_event`。
2. 主站投递 `ai_workflow_task`。
3. AI 执行服务消费任务并调用检索、LLM、trace、评估组件。
4. AI 执行服务回写 `node_outputs`、`event`、checkpoint、trace 日志。
5. 主站继续通过 WebSocket 或轮询返回状态。

**Step 3: 设计失败策略**

写入：
- failfast，不做静默降级。
- 任务失败必须显式写入失败状态和错误原因。
- 主站查询接口必须返回明确的 `running / failed / completed` 状态。

**Step 4: 设计幂等要求**

写入：
- 任务消费必须按 `submission_id` 或 `session_id + event_id` 幂等。
- 重试不得重复写出多个终态。

**Step 5: 形成迁移约束**

写入：
- 先保留旧接口，不保留旧执行路径。
- 一旦切换完成，删除主站内旧线程池执行逻辑。

### Task 6: 第一阶段代码重构计划

**Files:**
- Modify: `docs/plans/2026-04-01-microservice-evolution-plan.md`
- Check: `backend/src/main/java/com/alethicode/service/impl/SubmissionServiceImpl.java`
- Check: `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
- Check: `backend/src/main/java/com/alethicode/service/impl/AITutorServiceImpl.java`
- Check: `backend/src/main/java/com/alethicode/service/impl/ClassroomServiceImpl.java`

**Step 1: 先收口 Submission**

写入子任务：
- 把提交校验、提交落库、判题任务投递、结果回写拆成独立 application service。
- 删除 `SubmissionServiceImpl` 内固定线程池执行职责。

**Step 2: 再收口 AI Workflow**

写入子任务：
- 把 session 编排、event 持久化、异步任务投递、执行结果回写拆成独立组件。
- 主站内保留 workflow 状态机与权限校验。
- 抽离 LLM 执行、检索、trace、scaffolding 为 worker 可复用 handler。

**Step 3: 暂缓 Classroom 拆分**

写入子任务：
- 只做类内拆分，不做服务部署拆分。
- 先把课堂会话、作业、课件、AI 出题从 `ClassroomServiceImpl` 切成多个 domain service。

**Step 4: 收敛访问模式**

写入子任务：
- 新增 repository/query gateway，禁止 application service 直接拼接跨域 SQL。

**Step 5: 确定退出条件**

写入：
- 当主站不再直接执行判题和 AI 重任务时，第一阶段完成。

### Task 7: 第二阶段基础设施引入计划

**Files:**
- Modify: `docs/plans/2026-04-01-microservice-evolution-plan.md`
- Check: `backend/pom.xml`
- Check: `deploy/README.md`
- Check: `load_tests/ai_workflow/README.md`

**Step 1: 选择最小基础设施**

写入：
- 队列优先选择 Redis Stream 或 RabbitMQ 二选一。
- 只引入一个消息中间件。
- 不引入注册中心、配置中心、熔断框架作为第一阶段前置条件。

**Step 2: 设计部署拓扑**

写入：
- `gateway/main-app`
- `judge-worker`
- `ai-worker`
- `postgres`
- `redis`

**Step 3: 设计配置拆分**

写入：
- 把 `application.yml` 中判题、AI、主站配置拆成独立命名空间。
- 每个服务只读取自己需要的配置。

**Step 4: 设计可观测性**

写入：
- 保留现有 actuator/prometheus。
- 新增任务队列积压、任务执行时长、失败率指标。
- 新增 `submission` 和 `ai_workflow` 的关联追踪 ID。

**Step 5: 明确验收条件**

写入：
- 能独立扩容 `judge-worker` 与 `ai-worker`。
- 主站实例数变化不影响任务执行正确性。

### Task 8: 第三阶段接口与认证改造计划

**Files:**
- Modify: `docs/plans/2026-04-01-microservice-evolution-plan.md`
- Check: `backend/src/main/java/com/alethicode/config/SecurityConfig.java`
- Check: `backend/src/main/java/com/alethicode/middleware/SessionAuthenticationFilter.java`

**Step 1: 定义第一阶段认证策略**

写入：
- 用户仍只登录主站。
- worker 不接受浏览器直接访问。
- 主站与 worker 之间使用内部 token 或 mTLS 二选一。

**Step 2: 定义接口边界**

写入：
- 浏览器只调用主站 API。
- worker 只暴露内部健康检查、消费确认、结果回传接口。

**Step 3: 定义状态查询边界**

写入：
- 所有任务状态统一从主站查询。
- 不允许前端绕过主站直连 worker。

**Step 4: 定义切换原则**

写入：
- 不改前端 API 路径。
- 后端内部执行路径替换为异步任务流。

**Step 5: 明确未来演进**

写入：
- 只有在未来必须拆统一网关时，才考虑从 Session 迁移到 token 化统一认证。

### Task 9: 验证、压测与回归计划

**Files:**
- Modify: `docs/plans/2026-04-01-microservice-evolution-plan.md`
- Check: `load_tests/ai_workflow/README.md`
- Check: `backend/README.md`

**Step 1: 定义判题回归**

写入：
- 提交创建、排队、执行、结果回写、重复消费幂等。
- 无可用 worker 时明确失败。

**Step 2: 定义 AI workflow 回归**

写入：
- `session create`
- `workflow event`
- `interrupt`
- `checkpoint restore`
- `websocket/轮询状态同步`

**Step 3: 定义压测目标**

写入：
- 比较改造前后的 `error_rate`、`http_req_duration p95`、`workflow_latency_ms p95`、`stuck_session_count`。
- 单独观察 worker 横向扩容后吞吐是否线性提升。

**Step 4: 定义发布策略**

写入：
- 先在本地 docker compose 跑通。
- 再灰度切换 AI workflow。
- 再灰度切换判题链路。

**Step 5: 定义回退原则**

写入：
- 若异步链路失败率超阈值，立即回退到上一稳定版本。
- 回退是版本回退，不保留双路径长期并行。

### Task 10: 里程碑与执行顺序

**Files:**
- Modify: `docs/plans/2026-04-01-microservice-evolution-plan.md`

**Step 1: 写入 M1**

写入：
- M1：单体内解耦。
- 完成 `Submission`、`AITutorWorkflow`、`Classroom` 的领域收口。

**Step 2: 写入 M2**

写入：
- M2：判题 worker 外置。
- 主站只投递任务，不直接执行判题。

**Step 3: 写入 M3**

写入：
- M3：AI worker 外置。
- 主站只编排 workflow，不直接执行重型 AI 计算。

**Step 4: 写入 M4**

写入：
- M4：补充监控、压测、扩容预案。

**Step 5: 写入最终交付标准**

写入：
- 主站职责清晰。
- 热点能力可独立扩容。
- 前端接口不变。
- 无分布式事务。
- 无长期双写双跑。

