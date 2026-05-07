# Alethicode 概要设计说明书

> 文档编号：ALETHICODE-HLD-COURSE-2026  
> 版本：v1.0  
> 参考标准：IEEE 1016 软件设计描述  
> 设计范围：Alethicode 前端、后端、微服务、数据层、部署与运行支撑。

## 1. 设计目标

Alethicode 的总体设计目标是将传统在线评测、AI 导学、课件 RAG 和课堂教学管理整合为一个可运行、可维护、可观测的教育平台。概要设计强调模块边界、核心数据流、关键接口、部署拓扑和质量属性，不深入到具体类和方法实现。

## 2. 设计原则

1. **真实判题优先**：代码运行和评测结果由 Judge Server 生成，AI 不替代判题。
2. **教学上下文优先**：AI 回答必须尽量基于题目、提交、课件、学习者画像和历史对话。
3. **主业务后端集中**：Java Spring Boot 后端是账号、题目、提交、课堂、语言包和管理 API 的主入口。
4. **AI 能力可替换**：LLM、Embedding、RAG、Tutor Graph 通过配置和内部服务接口接入。
5. **数据迁移可追踪**：数据库结构通过 Flyway 版本化迁移管理。
6. **可观测性内建**：日志、指标、链路追踪和错误上报作为系统设计的一部分。

## 3. 总体架构

```text
┌─────────────────────────────────────────────────────────────┐
│                         用户层                               │
│ 学生端 OJ │ 教师端课堂 │ 管理端 │ 课件问答 │ AI Tutor 面板       │
│                 Vue 3 + Vite + Element Plus                  │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTP / WebSocket
┌──────────────────────────────▼──────────────────────────────┐
│                    Spring Boot 后端                           │
│ Controller │ Service │ DTO │ Repository/JdbcTemplate │ Config │
│ Account / Problem / Submission / AI Tutor / Classroom / Admin │
└─────────────┬──────────────┬──────────────┬─────────────────┘
              │              │              │
              │              │              │ Internal HTTP
              │              │              ▼
              │              │   ┌────────────────────────────┐
              │              │   │ tutor-graph / alethicode-rag│
              │              │   │ FastAPI + LangGraph/LightRAG│
              │              │   └────────────────────────────┘
              │              │
              ▼              ▼
┌─────────────────┐ ┌─────────────────┐
│ Judge Server    │ │ External LLM/API │
│ sandbox judging │ │ Chat/Embedding   │
└─────────────────┘ └─────────────────┘
              │
┌─────────────▼───────────────────────────────────────────────┐
│                         数据层                               │
│ PostgreSQL + pgvector │ Redis │ Memgraph │ NATS │ Temporal │ FS │
└─────────────────────────────────────────────────────────────┘
```

## 4. 前端概要设计

### 4.1 前端应用划分

| 应用区域 | 主要路径 | 说明 |
|---|---|---|
| 学生端 OJ | `/`、`/problem`、`/problem/:problemID`、`/status` | 首页、题库、题目详情、提交列表和详情。 |
| 学习辅助 | `/learner-notebook`、`/review-package`、AI Tutor 面板 | 学习者笔记本、错题复习、题目页 AI 导学。 |
| 课堂功能 | `/classroom`、`/classroom/join`、`/classroom/detail` | 班级列表、加入班级、班级详情、课堂和作业入口。 |
| 课件问答 | `/language-pack-qa`、`/language-pack-qa/viewer` | 课件问答会话和 PDF/页面预览。 |
| 管理端 | `/admin/` 下的 Vue Router | 题目、用户、判题服务器、语言包、AI 配置、可观测性等。 |

### 4.2 前端关键组件

| 组件类别 | 说明 |
|---|---|
| 代码编辑器 | 基于 CodeMirror，支持多语言编辑、选区安全和提交。 |
| AI 卡片 | 题目引导、构思分析、错误诊断、知识复盘、迁移练习、Parsons 题等。 |
| Chat Composer | `@` 引用、`/` 命令、上下文用量条、历史召回、压缩和分叉入口。 |
| 课堂视图 | 班级列表、作业、课堂详情、协作编程、课堂分析。 |
| 管理组件 | 题目编辑、测试用例、AI 配置、语言包初始化、可观测性面板。 |

## 5. 后端概要设计

### 5.1 分层结构

| 层 | 职责 |
|---|---|
| Controller | 接收 HTTP 请求，执行参数校验，调用服务层，返回统一响应。 |
| Service | 承载业务规则、事务、权限判断、跨模块编排和外部服务调用。 |
| Repository/JdbcTemplate | 访问 PostgreSQL 数据库，执行查询、更新和复杂 SQL。 |
| DTO/Request/Response | 定义请求和响应契约，隔离内部实体和外部接口。 |
| Config/Middleware | 安全、缓存、AI 网关、WebSocket、内部 API Key、健康检查和跨域配置。 |
| Exception Handler | 统一异常转换和错误响应。 |

### 5.2 后端模块划分

| 模块 | 职责 |
|---|---|
| Account | 登录、注册、资料、密码、会话和权限信息。 |
| Problem | 题目查询、管理、导入导出、测试用例和难度校准。 |
| Submission | 提交记录、判题结果、数据采集和判题执行。 |
| AI Tutor | 学习者画像、工作流、上下文、Prompt、工具调用、观察和配额。 |
| Language Pack | 课件、页面、知识点、问答、质量报告、视频任务和题目发布。 |
| Classroom | 班级、成员、课堂、作业、AI 出题、分析和监控。 |
| RAG Client | RAG 索引、队列、查询、回补和异常处理。 |
| Compliance | 隐私、审计、敏感日志和合规数据处理。 |
| Admin/System | 配置、监控、公告、反馈、使用统计和基础设施状态。 |

## 6. AI 子系统概要设计

### 6.1 AI Tutor 工作流

AI Tutor 采用阶段化导学思想，围绕学生做题流程形成七个主要阶段：

1. READING：理解题意。
2. IDEATING：分析思路。
3. SCAFFOLDING：搭建代码骨架。
4. CODING：辅助编码。
5. ERROR_FEEDBACK：解释错误和调试。
6. AC_REVIEW：通过后复盘。
7. TRANSFER：迁移练习和举一反三。

Java 后端负责业务入口、权限、上下文、投影和 UI 恢复；`tutor-graph` 负责 LangGraph 工作流状态和节点执行；二者通过内部 HTTP 和事件投影协作。

### 6.2 RAG 课件问答

课件 RAG 的概要流程如下：

```text
课件上传/初始化
    │
    ▼
文档解析 → 页面切分 → 知识点抽取 → 索引入库
    │
    ▼
学生提问 → 引用解析 → RAG 检索 → LLM 生成 → 返回答案和引用
```

`alethicode-rag` 使用 FastAPI、LightRAG、PostgreSQL pgvector 和 Memgraph 提供检索服务。Java 后端负责语言包业务、问答会话、消息、反馈、页面预览和视频任务。

## 7. 数据架构

### 7.1 数据库设计概览

系统使用 PostgreSQL 作为主数据库，并通过 Flyway 管理迁移。当前迁移文件覆盖账号、题库、提交、AI 核心、课堂、语言包、RAG、学习者画像、错题复习、可观测性和配置等数据域。

| 数据域 | 主要内容 |
|---|---|
| 账号域 | 用户、权限、会话、资料、重置密码、隐私请求。 |
| 题库域 | 题目、标签、测试用例、提交、判题结果和难度。 |
| AI 域 | 工作流会话、事件、检查点、对话、token 用量、学习者画像、记忆。 |
| 语言包域 | 语言包、文档、页面、知识点、QA 会话、质量报告和视频任务。 |
| 课堂域 | 班级、成员、课堂、作业、分析和风险检测。 |
| 运维域 | 系统配置、反馈、审计、可观测性和使用统计。 |

### 7.2 缓存与消息

| 组件 | 用途 |
|---|---|
| Redis | Session、缓存、限流和部分状态存储。 |
| Caffeine | JVM 内 L1 缓存，与 Redis 组成多级缓存。 |
| NATS | 异步事件和判题/索引相关消息。 |
| Temporal | 语言包流水线和长任务工作流。 |
| Memgraph | LightRAG 图谱存储。 |

## 8. 部署架构

```text
Nginx / Frontend Container
          │
          ▼
Backend Spring Boot Container
          │
          ├── PostgreSQL / PgBouncer
          ├── Redis
          ├── Judge Server
          ├── tutor-graph
          ├── alethicode-rag
          ├── Memgraph
          ├── NATS
          └── Temporal
```

部署目录使用 Docker Compose 编排，包含数据库、缓存、消息、图数据库、后端、前端、RAG、Tutor Graph、监控和日志等服务。生产部署需要配置 `.env` 中的数据库密码、Redis 密码、Judge Token、LLM Key、Embedding Key 和观测系统地址。

## 9. 接口设计概要

| 接口类别 | 典型路径 |
|---|---|
| 账号 | `/api/login`、`/api/register`、`/api/profile`、`/api/logout` |
| 题库 | `/api/problems`、`/api/admin/problems` |
| 提交 | `/api/submission`、`/api/admin/submissions` |
| AI Tutor | `/api/ai/tutor/*`、`/api/ai/tutor-workflow-sessions/*` |
| 课件问答 | `/api/language-pack-qa/*` |
| 课堂 | `/api/classroom/*`、相关课堂 Controller 路径 |
| 管理 | `/api/admin/*` |
| 内部服务 | `/internal/ai-tutor/*`、`/internal/language-pack/quality/*` |
| 隐私合规 | `/api/privacy/*` |

## 10. 安全架构

| 安全点 | 设计 |
|---|---|
| 身份认证 | 基于账号登录和服务端 Session。 |
| 会话存储 | Spring Session Data Redis。 |
| 权限控制 | 前端路由守卫 + 后端服务层权限检查。 |
| CSRF/CORS | Spring Security 配置保护浏览器请求。 |
| 内部接口 | 内部服务 Key 校验和请求匹配器。 |
| 输入校验 | Jakarta Bean Validation、DTO 校验、JSON Schema、业务规则校验。 |
| 敏感数据 | 合规审计、敏感日志控制、隐私导出和删除。 |
| AI 安全 | Prompt Safety Filter、EvidencePack、Reflection、拒答边界和引用约束。 |

## 11. 可观测性设计

| 能力 | 技术 |
|---|---|
| 健康检查 | Spring Boot Actuator、Docker healthcheck、微服务 health API。 |
| 指标 | Micrometer、Prometheus、Grafana。 |
| 链路追踪 | OpenTelemetry、Micrometer Tracing、Jaeger。 |
| 错误上报 | Sentry/GlitchTip。 |
| 业务观测 | AI trace、质量报告、工作流时间线、token 用量和反馈统计。 |

## 12. 设计约束

1. Java 后端是主业务入口，不直接把业务状态分散到多个微服务。
2. `tutor-graph` 是 LangGraph 工作流状态事实来源，Java 侧维护 UI 恢复和管理所需投影。
3. RAG 的 embedding 模型、索引表和查询配置必须一致，否则检索结果可能为空。
4. 外部服务密钥必须通过环境变量配置，不应写入代码和文档。
5. 对课程演示而言，优先展示稳定闭环，不应临场依赖长耗时初始化任务。
