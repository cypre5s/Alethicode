# Alethicode 详细设计说明书

> 文档编号：ALETHICODE-DDD-COURSE-2026  
> 版本：v1.0  
> 参考标准：IEEE 1016、ISO/IEC 25010  
> 说明：本文以当前项目代码结构和配置为基础，描述主要模块的实现级设计。

## 1. 详细设计范围

本文覆盖 Alethicode 的前端页面与组件、后端控制器与服务、数据库迁移、AI Tutor、RAG、课堂、管理端、安全、缓存、异常、部署和可观测性等实现细节。由于课程文档不等同于完整源码级注释，本文聚焦核心流程和关键设计决策。

## 2. 后端包级设计

后端源码位于 `backend/src/main/java/com/alethicode`，主要包职责如下：

| 包 | 职责 |
|---|---|
| `config` | Spring、安全、缓存、AI、WebSocket、内部服务 Key、健康检查和配置属性。 |
| `controller` | REST API 入口，包括账号、题目、提交、AI、语言包、课堂、管理和隐私。 |
| `controller.internal` | 内部服务接口，供 Tutor Graph、语言包质量服务等调用。 |
| `dto` | 请求和响应对象，隔离 API 契约与内部模型。 |
| `middleware` | 过滤器和请求中间件，例如内部 API Key、限流等。 |
| `service` | 核心业务服务，包含账号、题目、提交、AI Tutor、语言包、课堂、RAG、合规和系统配置。 |
| `websocket` | 课堂协作和 Tutor 工作流实时通道。 |
| `mcp` | AI 工具提供和 MCP 相关集成。 |
| `util` | 通用工具，例如并发控制、TOTP 等。 |

## 3. 账号与权限详细设计

### 3.1 控制器

`AccountController` 提供登录、退出、注册、个人资料、密码重置、邮箱变更、会话管理和验证码等接口。`ProfileController` 提供 AI Tutor 学习者画像相关资料接口。管理端还有 `AdminAccountController` 负责用户管理。

### 3.2 会话设计

1. 用户登录后由后端创建服务端会话。
2. 会话数据通过 Spring Session Data Redis 保存。
3. 前端通过 Cookie 携带会话标识。
4. 管理端路由守卫会在进入页面前请求 `profile`，根据 `admin_type` 判断访问权限。

### 3.3 权限设计

| 权限点 | 设计 |
|---|---|
| 学生端 | 公开页面可访问，个人中心、课堂、课件问答等需要登录。 |
| 教师端 | 允许访问教学相关页面，限制用户管理、系统配置和可观测性等管理员页面。 |
| 管理员 | 可访问管理端完整功能。 |
| 内部服务 | 使用内部服务 Key 匹配器和校验器验证。 |

## 4. 在线评测详细设计

### 4.1 题目流程

```text
学生进入题目列表
    │
    ▼
查询题目分页、标签、难度和状态
    │
    ▼
进入题目详情
    │
    ▼
前端 CodeMirror 编辑代码
    │
    ▼
提交代码到后端
    │
    ▼
后端创建提交记录并调度 Judge Server
    │
    ▼
保存判题结果并返回给前端
```

### 4.2 主要职责

| 组件 | 职责 |
|---|---|
| `ProblemController` | 学生端题目查询和详情接口。 |
| `AdminProblemController` | 管理端题目新增、编辑、导入导出和批量操作。 |
| `SubmissionController` | 学生提交与提交记录查询。 |
| `AdminSubmissionController` | 管理端提交记录查看和管理。 |
| `SubmissionJudgeExecutor` | 执行提交判题逻辑，与 Judge Server 交互。 |
| `SubmissionDataCollector` | 采集提交数据，为 AI、统计和画像提供学习信号。 |

### 4.3 判题数据

提交记录至少需要关联用户、题目、语言、代码、提交时间、判题状态、运行时间、内存、错误信息和测试点结果。判题结果既用于前端展示，也用于 AI Tutor 的错误诊断和学习者画像。

## 5. AI Tutor 详细设计

### 5.1 会话与运行

`TutorWorkflowController` 负责 AI Tutor 工作流会话，主要接口包括：

| 能力 | 说明 |
|---|---|
| 创建会话 | 基于题目、用户和学习上下文创建 Tutor 会话。 |
| 查询会话 | 获取会话详情、状态、模式和投影信息。 |
| 发起运行 | 提交用户输入或事件，驱动 Tutor 工作流。 |
| 检查点 | 查询和恢复工作流检查点。 |
| 对话记录 | 获取会话中的消息和卡片。 |
| 模式切换 | 在不同对话模式或辅导模式之间切换。 |
| 压缩和分叉 | 控制长对话 token 成本，保留学习路径。 |
| 中断响应 | 支持用户打断长响应或后台运行。 |

### 5.2 上下文构造

AI Tutor 的上下文来源包括：

1. 当前题目描述、输入输出、样例和标签。
2. 学生代码和最近提交结果。
3. Judge Server 错误信息。
4. 语言包课件、页面、知识点和引用。
5. 学习者画像、掌握度、历史错误和记忆。
6. 当前会话历史消息、卡片和模式。
7. 教师或系统配置的 Prompt 策略。

### 5.3 EvidencePack 设计

EvidencePack 的目标是把“AI 可使用的事实”从“生成文本”中分离。生成前先组织证据，模型只能围绕证据和教学目标回答。

| 证据类型 | 作用 |
|---|---|
| Problem Evidence | 题目事实，避免误读题。 |
| Submission Evidence | 学生真实代码和错误，避免泛泛而谈。 |
| Courseware Evidence | 课件引用，保证回答贴合课堂。 |
| Learner Evidence | 学习者水平、误区和掌握度。 |
| Workflow Evidence | 当前阶段、历史卡片、上一步行动。 |

### 5.4 Reflection 设计

Reflection 是 AI 回答后的自检层，主要检查：

1. 是否直接给出完整答案。
2. 是否缺少题目或课件依据。
3. 是否误导初学者。
4. 是否包含不安全或越权内容。
5. 是否符合当前阶段的教学目标。
6. 是否需要补充引用或拒答。

### 5.5 工作流状态

AI Tutor 工作流的主要阶段包括 READING、IDEATING、SCAFFOLDING、CODING、ERROR_FEEDBACK、AC_REVIEW 和 TRANSFER。Java 侧维护前端恢复所需投影，`tutor-graph` 维护 LangGraph 运行状态。

## 6. 课件 RAG 与语言包详细设计

### 6.1 语言包处理流程

```text
教师/管理员发起语言包初始化
    │
    ├── 导入课件文档
    ├── 解析文档和页面
    ├── 抽取知识点和题目候选
    ├── 生成或校验参考解法
    ├── 建立 RAG 索引
    └── 生成质量报告
```

### 6.2 课件问答流程

1. 学生选择语言包。
2. 后端创建课件问答会话。
3. 学生发送消息，可包含 `@courseware`、`@page`、`@kc` 等引用。
4. 后端解析引用并调用 RAG 服务检索。
5. LLM 基于检索结果生成回答。
6. 前端展示回答、引用、页面预览和反馈入口。

### 6.3 关键服务

| 服务 | 职责 |
|---|---|
| `LanguagePackQaService` | 课件问答业务编排。 |
| `ConversationContextService` | 问答上下文组织。 |
| `ReferenceResolver` | 引用解析。 |
| `HttpRagServiceClient` | 调用 RAG 微服务。 |
| `RagIndexQueueService` | 维护 RAG 索引队列。 |
| `RagIndexOutboxWorker` | 处理索引出站任务和离线补偿。 |
| `ReferenceSolutionLinter` | 参考解法检查。 |
| `ReferenceSolutionSelfValidator` | 参考解法自验证。 |

## 7. 课堂协作详细设计

### 7.1 功能结构

| 功能 | 说明 |
|---|---|
| 班级管理 | 教师创建班级，学生加入班级。 |
| 成员管理 | 查看学生列表、角色和加入状态。 |
| 课堂管理 | 创建课堂、组织课堂练习。 |
| 作业管理 | 发布作业、查看提交、评分与反馈。 |
| AI 出题 | 根据课堂目标和知识点生成题目候选。 |
| 学情分析 | 汇总提交、掌握度、误区和风险。 |
| 实时协作 | WebSocket 支持课堂协作和状态同步。 |

### 7.2 课堂监控

课堂监控服务根据学生提交、错误类型、耗时、连续失败和参与情况识别风险。教师端可以查看学生状态，并对需要帮助的学生进行干预。

## 8. 前端详细设计

### 8.1 学生端路由

| 路由 | 功能 |
|---|---|
| `/` | 首页。 |
| `/login`、`/register` | 登录和注册。 |
| `/problem` | 题目列表。 |
| `/problem/:problemID` | 题目详情、代码编辑、提交和 AI Tutor。 |
| `/status`、`/status/:id/` | 提交列表和提交详情。 |
| `/learner-notebook` | 学习者笔记本。 |
| `/classroom`、`/classroom/join`、`/classroom/detail` | 课堂相关页面。 |
| `/language-pack-qa` | 课件问答。 |
| `/language-pack-qa/viewer` | 课件页面预览。 |
| `/review-package` | 错题复习包。 |

### 8.2 管理端路由

| 路由 | 功能 |
|---|---|
| `/admin/problems` | 题目列表。 |
| `/admin/problem/create`、`/admin/problem/edit/:problemId` | 题目创建和编辑。 |
| `/admin/user` | 用户管理。 |
| `/admin/judge-server` | 判题服务器管理。 |
| `/admin/language-pack-init` | 语言包初始化。 |
| `/admin/kc-management` | 知识点管理。 |
| `/admin/secrets/ai` | AI 配置。 |
| `/admin/secrets/observability` | 可观测性配置。 |
| `/admin/beta-feedback` | Beta 反馈。 |
| `/admin/usage-stats` | 使用统计。 |

### 8.3 前端状态与接口

前端使用 Vue Router 管理路由，Vuex 管理全局用户资料和权限状态，TanStack Query 辅助服务端状态管理，Axios 封装 HTTP 请求。CodeMirror 提供代码编辑体验，Element Plus 提供基础 UI 组件。

## 9. 数据库详细设计

### 9.1 迁移策略

数据库迁移文件位于 `backend/src/main/resources/db/migration`。迁移采用 Flyway 命名规则，按版本号顺序执行。课程提交时不应手工修改数据库，而应通过迁移文件表达 schema 变更。

### 9.2 索引策略

1. 用户、题目、提交、课堂和语言包的高频查询字段建立索引。
2. 搜索场景使用全文搜索、CJK bigram 或向量索引。
3. AI 工作流事件、会话和 token 用量按查询维度建立索引。
4. RAG 索引任务通过 outbox 和补偿机制提高一致性。

### 9.3 数据一致性

| 场景 | 一致性策略 |
|---|---|
| 用户资料修改 | 后端事务保护，必要时刷新前端 profile。 |
| 提交判题 | 先创建提交记录，再异步或同步更新结果。 |
| AI 会话运行 | 会话状态、事件和投影分离，支持检查点恢复。 |
| RAG 索引 | 主业务写入后进入索引队列，worker 处理并可离线补偿。 |
| 语言包初始化 | 长任务拆分阶段，保存进度和质量报告。 |

## 10. 缓存与限流设计

| 机制 | 说明 |
|---|---|
| Caffeine L1 缓存 | JVM 内快速缓存热点配置和查询结果。 |
| Redis L2 缓存 | 跨实例共享缓存、Session 和限流。 |
| Resilience4j | 对外部 AI/RAG/Judge 调用进行限流、重试、熔断或隔离。 |
| AI 响应缓存 | 对可复用的模型响应进行缓存，降低延迟和成本。 |

## 11. 异常处理设计

1. Controller 通过统一异常处理器返回结构化错误响应。
2. 业务服务使用明确异常表达权限不足、资源不存在、输入非法和外部服务失败。
3. 外部服务失败应记录上下文日志和 trace id。
4. 前端根据错误类型展示可理解提示，避免把堆栈或敏感配置暴露给用户。

## 12. 安全详细设计

| 风险 | 设计控制 |
|---|---|
| 未登录访问 | 路由守卫、后端认证和服务层校验。 |
| 越权访问 | 角色检查、教师管理端限制、资源归属校验。 |
| CSRF | Spring Security CSRF 或等效机制。 |
| 内部接口暴露 | 内部服务 Key 校验、路径匹配和请求过滤。 |
| Prompt 注入 | Prompt Safety Filter、上下文边界、引用解析和拒答策略。 |
| 敏感日志 | 合规服务、敏感日志过滤和审计表。 |
| 个人数据 | 数据导出、个人数据删除和隐私接口。 |

## 13. 可观测性详细设计

| 观测对象 | 指标或日志 |
|---|---|
| HTTP API | 请求量、延迟、状态码、异常。 |
| AI 调用 | 模型、token、耗时、失败率、质量报告、trace。 |
| RAG 调用 | 查询耗时、命中数、索引状态、异常。 |
| 判题 | 队列、结果、错误、服务器健康。 |
| 课堂 | 活跃课堂、提交、风险学生和作业状态。 |
| 数据库 | 慢查询、连接池、迁移版本和索引。 |

## 14. 部署详细设计

### 14.1 本地开发

后端使用 Maven 启动，前端使用 Vite 启动，微服务按各自 `pyproject.toml` 安装依赖。开发环境需要 PostgreSQL、Redis 和 Judge Server，AI/RAG 相关演示还需要 LLM 与 Embedding Key。

### 14.2 Docker Compose

部署目录包含 PostgreSQL、Redis、PgBouncer、NATS、Temporal、Memgraph、后端、前端、RAG、Tutor Graph 和监控相关服务。前端 Nginx 应通过 Docker DNS 解析后端服务，避免长期持有旧容器 IP。

## 15. 关键设计决策

| 决策 | 理由 |
|---|---|
| 使用 Spring Boot 作为主后端 | 便于统一账号、权限、业务事务、数据库访问和管理端接口。 |
| 使用 Vue 3 + Vite | 前端开发效率高，适合学生端和管理端 SPA。 |
| 使用 Flyway | 数据库结构版本可追踪，可在部署时自动迁移。 |
| 使用 Redis Session | 支持服务端会话管理，便于扩展和统一权限。 |
| 使用 tutor-graph 管理 AI 工作流 | LangGraph 更适合复杂 AI 节点和状态机运行。 |
| 使用 RAG 微服务 | 将课件检索、向量、图谱和 LLM 检索逻辑与主业务解耦。 |
| 使用 EvidencePack + Reflection | 提高 AI 回答的教学可信度和安全性。 |
