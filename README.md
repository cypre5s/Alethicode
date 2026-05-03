<div align="center">

# Alethicode

**面向非计算机专业 Python 初学者的 AI 智能在线评测平台**

*An AI-powered Online Judge for non-CS Python beginners.*

[![Version](https://img.shields.io/badge/version-v1.0.0-blue.svg)](./CHANGELOG.md)
[![CI](https://github.com/cypre5s/Alethicode/actions/workflows/ci.yml/badge.svg)](https://github.com/cypre5s/Alethicode/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](#技术栈)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6db33f.svg)](#技术栈)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883.svg)](#技术栈)
[![Node](https://img.shields.io/badge/Node-%3E%3D20.19-339933.svg)](#技术栈)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+pgvector-336791.svg)](#技术栈)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](#license)
[![Conventional Commits](https://img.shields.io/badge/commits-conventional-fe5196.svg)](https://www.conventionalcommits.org/)

[简介](#项目简介) ·
[特性](#核心特性) ·
[架构](#系统架构) ·
[快速开始](#快速开始) ·
[文档](#文档索引) ·
[路线图](#路线图)

</div>

---

## 目录

- [项目简介](#项目简介)
- [核心特性](#核心特性)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [本地开发](#本地开发)
- [项目结构](#项目结构)
- [配置参考](#配置参考)
- [测试](#测试)
- [部署](#部署)
- [安全](#安全)
- [可观测性](#可观测性)
- [文档索引](#文档索引)
- [路线图](#路线图)
- [代码规范](#代码规范)
- [更新日志](#更新日志)
- [License](#license)
- [致谢](#致谢)

---

## 项目简介

**Alethicode** 是一个为 **非计算机专业 Python 初学者** 设计的智能在线评测系统（OJ），将传统 OJ 的自动判题能力与 AI 驱动的个性化教学辅导相融合。

我们解决三个核心问题：

1. **传统 OJ 对初学者太冷**：只给 AC/WA，不解释「为什么错」「怎么改」「下一步该学什么」。
2. **市面 AI 教学产品离课堂太远**：脱离教师真实教学节奏、无法对接已有讲义/课件/学情。
3. **教师无法低成本扩展教学规模**：备题、批改、答疑、个性化辅导都靠人力堆。

Alethicode 的回答是：把 **真实判题、AI 多 Agent 教学、课件 RAG、课堂协作、学情画像** 整合在同一个数据闭环里——学生的每一次提交都是真实的学习信号，AI 角色的每一句话都基于学生的真实历史。

> 项目代号 *Alethicode*：**Aletheia**（希腊语「真理 / 不被遮蔽」）+ **Code**——让代码学习从遮蔽中走向澄明。

---

## 核心特性

| 模块 | 能力 | 状态 |
| ----- | ------ | :--: |
| **在线评测** | Python3 / C / C++ / Java 实时判题，限流 + 防爬 + Judge Server 集群 | GA |
| **AI 导学 FSM** | 7 阶段状态机：READING → IDEATING → SCAFFOLDING → CODING → ERROR_FEEDBACK → AC_REVIEW → TRANSFER | GA |
| **EvidencePack + Reflection** | 单次 LLM 调用 + 检索证据 + Producer-Critic 自检（按 CardType 分维度评估） | GA |
| **课件 RAG** | `@courseware:<lpId>` token 在对话内引用课件，关键词 + 向量混合检索（pgvector） | GA |
| **学习者画像** | KC 掌握度、misconception 追踪、学习记忆、frustration / confidence 代理指标 | GA |
| **课堂协作** | WebSocket 实时编程、教师监控仪表盘、AI 自动出题 + 人审 | GA |
| **学习者笔记本** | 错题归档、KC 视图、复盘对话 | GA |
| **课件问答** | 学生对单个语言包的开放问答，grounded（带引用 + 拒答兜底） | GA |
| **视频生成** | LLM 分镜 → TTS → 渲染，4–7 镜头 / 45–90 秒讲解视频 | Beta |
| **多模型 LLM 网关** | DeepSeek（生产）/ MiniMax-M2.7（备选），Spring AI 抽象层 | GA |
| **可观测性** | Sentry/GlitchTip 错误追踪 + Micrometer + Prometheus + Jaeger + JaCoCo | GA |
| **A/B 灰度 + Bandit** | 自研 RolloutPolicyService，支持按 user / KC / classroom 维度切流 | GA |
| **TypeScript 渐进** | tsconfig + vue-tsc，新代码可逐文件迁移，旧 JS 不强约束 | Phase 1 |

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                          用户层 (User Layer)                         │
│  学生 OJ 端 │ 教师管理端 │ 课堂协作端 │ 课件问答端  (Vue 3 + Vite SPA) │
└──────┬──────────────┬──────────────┬──────────────┬─────────────────┘
       │              │              │              │
       ▼              ▼              ▼              ▼
┌─────────────────────────────────────────────────────────────────────┐
│        API Gateway / Spring Security (Session + CSRF + CORS)        │
└────────────────────────────┬────────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│              Spring Boot 3.4 / Java 21 / Virtual Threads            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────────┐   │
│  │ Account  │ │ Problem  │ │Submission│ │  AI Tutor (FSM +     │   │
│  │ Service  │ │ Service  │ │ Service  │ │  Memory + RAG +      │   │
│  └──────────┘ └──────────┘ └──────────┘ │  Reflection)         │   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ └──────────────────────┘   │
│  │Classroom │ │LangPack  │ │ Video    │ ┌──────────────────────┐   │
│  │ Service  │ │ Service  │ │ Job      │ │  AiModelGateway      │   │
│  └──────────┘ └──────────┘ └──────────┘ │  (Spring AI 抽象)    │   │
│                                         └──────────────────────┘   │
└────────────┬───────────┬────────────┬──────────────┬───────────────┘
             ▼           ▼            ▼              ▼
       ┌──────────┐ ┌────────┐ ┌─────────────┐ ┌─────────────────┐
       │PostgreSQL│ │ Redis  │ │File Storage │ │ External LLM /  │
       │+ pgvector│ │Session │ │ /deploy/    │ │ Embedding APIs  │
       └──────────┘ └────────┘ └─────────────┘ └─────────────────┘
                                                       │
                                                       ▼
                            ┌──────────────────────────────────────┐
                            │  tutor-graph (LangGraph)  │ alethicode-rag  │
                            │  Python micro-service     │ Python micro    │
                            └──────────────────────────────────────┘
```

> 完整分层架构、调用流程、AI 导学子系统、语言包管线、课堂协作、数据库 ER 图，请见 **[`PROJECT.md`](./PROJECT.md)**（1000+ 行内部技术规格）。

---

## 技术栈

### 后端 / Backend

| 层 | 技术 |
| ----- | ----- |
| Runtime | Java 21（启用 Virtual Threads） |
| Framework | Spring Boot 3.4.4 |
| Web | Spring Web MVC + WebSocket（STOMP / 原生） |
| Security | Spring Security 6 + Spring Session Data Redis |
| Database | PostgreSQL 15+（含 `pgvector`）+ Flyway 38 个 versions |
| ORM | Spring Data JPA + JdbcTemplate |
| AI | Spring AI（Chat / Embedding / Tool）+ 自研 LayeredPrompt + Reflection |
| Observability | Micrometer Tracing + OpenTelemetry OTLP + Sentry / GlitchTip + JaCoCo |
| Resilience | Resilience4j（熔断 / 限流 / 重试） |
| API Docs | springdoc-openapi（Swagger UI） |
| Build | Maven |

### 前端 / Frontend

| 层 | 技术 |
| ----- | ----- |
| Framework | Vue 3.5 + Composition API |
| Build | Vite 7 |
| Router / State | Vue Router 4 + Vuex 4 + TanStack Query |
| UI | Element Plus 2.13 + 自研主题 |
| Editor | CodeMirror 6（Python / C / C++ / Java / Go） |
| Charts | ECharts + Chart.js + D3 + Mermaid |
| Math | KaTeX |
| i18n | vue-i18n 9 |
| Observability | `@sentry/vue` + web-vitals |
| Type System | TypeScript（渐进式，`vue-tsc --noEmit`） |
| Test | Jest（单元）+ Playwright（E2E + 视觉回归） |

### 微服务 / Microservices

| 服务 | 技术 | 端口 |
| ------ | ----- | ----: |
| `tutor-graph` | Python + LangGraph + FastAPI | 8100 |
| `alethicode-rag` | Python + FastAPI + pgvector + 通义 Embedding | 8200 |
| Judge Server | 基于 [QingdaoU/Judger](https://github.com/QingdaoU/Judger) | 12358 |

### 基础设施 / Infrastructure

PostgreSQL · Redis · NATS · Temporal · Memgraph · Nginx · Docker Compose · GitHub Actions · Dependabot · Prometheus · Grafana · Jaeger · GlitchTip

---

## 快速开始

### 依赖要求

```
Java 21+      Maven 3.9+      Node.js >= 20.19      npm >= 10.8
PostgreSQL 15+  (含 pgvector 扩展)
Redis 7+
Docker / Docker Compose（推荐，覆盖 Judge / tutor-graph / alethicode-rag）
```

### 一键本地启动

```bash
git clone git@github.com:cypre5s/Alethicode.git
cd Alethicode

# 配置环境变量
cp backend/.env.example backend/.env
cp deploy/.env.example   deploy/.env
# 至少填入 OPENAI_API_KEY / EMBEDDING_API_KEY / JUDGE_SERVER_TOKEN

# 一键启动（拉取镜像、跑迁移、起后端、起前端、起 RAG/tutor-graph/judge）
./start.sh
```

启动后默认可访问：

| 服务 | URL |
| ----- | ----- |
| 前端 | http://localhost:8080 |
| 后端 API | http://localhost:8081 |
| Swagger UI | http://localhost:8081/swagger-ui/index.html |
| Grafana | http://localhost:3000  （`admin@localhost / admin`） |
| Jaeger | http://localhost:16686 |
| Prometheus | http://localhost:9090 |

> 默认管理员账号 / 演示账号见 `.local-credentials.md`（已 git-ignore，不入仓）。

### Docker Compose 启动（推荐生产）

```bash
cd deploy
cp .env.example .env       # 填入生产 secret
docker compose up -d
```

详见 **[`deploy/README.md`](./deploy/README.md)**。

---

## 本地开发

### 后端

```bash
cd backend
cp .env.example .env
mvn clean compile -DskipTests
mvn spring-boot:run                      # 开发模式（热重载需配合 IDE）
```

### 前端

```bash
cd frontend
npm ci
npm run dev                              # http://localhost:8080
npm run typecheck                        # vue-tsc 渐进类型检查
npm run lint                             # ESLint
```

### 微服务

```bash
# tutor-graph (LangGraph FSM 工作流)
cd services/tutor-graph
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8100

# alethicode-rag
cd services/alethicode-rag
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8200
```

---

## 项目结构

```
Alethicode/
├── backend/              # Spring Boot 后端（Java 21）
│   ├── src/main/java/com/alethicode/
│   │   ├── controller/   # REST 端点（30+ controller）
│   │   ├── service/      # 业务服务（含 aitutor / classroom / languagepack 子系统）
│   │   ├── dto/          # request / response DTO
│   │   ├── entity/       # JPA 实体
│   │   ├── middleware/   # SessionAuthFilter / CSRF / RateLimit
│   │   └── config/       # SecurityConfig / WebSocketConfig / Properties
│   └── src/main/resources/db/migration/   # Flyway V1..V38
│
├── frontend/             # Vue 3 + Vite 前端
│   ├── src/pages/oj/     # 学生 OJ 端
│   ├── src/pages/admin/  # 教师管理端
│   ├── src/components/   # 通用组件
│   ├── src/types/        # TypeScript 声明
│   └── tests/            # Jest 单测 + Playwright E2E + 视觉回归
│
├── services/             # Python 微服务
│   ├── tutor-graph/      # LangGraph FSM 工作流（备线）
│   └── alethicode-rag/   # 课件 RAG 服务
│
├── deploy/               # 生产部署
│   ├── docker-compose.yml
│   ├── nginx/            # 反向代理配置
│   ├── observability/    # Prometheus / Grafana 配置
│   └── chaos/            # Chaos engineering
│
├── contracts/            # API / 工作流合约（IDL）
│   ├── tutor_workflow/
│   └── nfk/              # NFK 评测合约
│
├── docs/                 # 详细文档
│   ├── adr/              # Architecture Decision Records
│   ├── plans/            # 设计稿（按日期归档）
│   ├── reports/          # 评估报告 / 检查清单
│   └── todos/            # 长期路线图（含 Agent + Harness 计划）
│
├── nfk/                  # NFK 实验目录（research）
├── research/             # 学术研究目录
├── scripts/              # 运维 / 数据脚本
├── tools/                # 开发工具
│
├── start.sh              # 一键本地启动脚本（覆盖所有依赖服务）
├── PROJECT.md            # 完整技术说明书（内部规范）
├── AGENTS.md             # 协作 / 编码 / 命名规范
├── CHANGELOG.md          # 中文变更日志
└── README.md             # 本文档
```

---

## 配置参考

### 必需环境变量

```ini
# LLM
OPENAI_API_KEY=sk-...                # 主 LLM（DeepSeek / MiniMax 兼容协议）
LLM_BASE_URL=https://api.deepseek.com/v1
LLM_MODEL=deepseek-chat

# Embedding
EMBEDDING_API_KEY=sk-...             # 阿里通义
EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
EMBEDDING_MODEL=text-embedding-v4

# 判题
JUDGE_SERVER_TOKEN=...               # Judge Server 认证

# 错误追踪（可选；为空时 SDK no-op）
SENTRY_DSN=                          # 指向 self-hosted GlitchTip / Sentry
```

### 关键功能开关

| 开关 | 默认 | 说明 |
| ----- | ----- | ----- |
| `QA_GROUNDING_CRITIC_ENABLED` | `false` | 课件问答启用 grounding critic（无证据时拒答） |
| `LLM_TOOL_USE_PROMPT_FALLBACK` | `false` | tool-use 回退为 prompt-based（兼容不支持 tool 的模型） |
| `VIDEO_TTS_PROVIDER` | `stub` | 视频 TTS 供应商（stub / 阿里 / 自研） |
| `VIDEO_RENDER_PROVIDER` | `stub` | 视频渲染供应商 |
| `GLITCHTIP_PROFILE` | `glitchtip` | docker compose profile，置 `disabled` 关闭 |

完整配置见 [`backend/.env.example`](./backend/.env.example) 与 [`deploy/.env.example`](./deploy/.env.example)。

---

## 测试

### 后端

```bash
cd backend
mvn test                                            # 全量
mvn -Dtest=ParsonsDistractorGeneratorTest test      # 单测
mvn -Dtest='*IntegrationTest' test                  # 集成测试
mvn jacoco:report                                   # 覆盖率报告 → target/site/jacoco/
```

### 前端

```bash
cd frontend
npm test                                            # Jest 单测
npm run test:coverage                               # 覆盖率
npm run test:e2e:auth                               # Playwright E2E（需先 npm run setup:visual-libs）
npm run test:replacement:visual                     # 视觉回归
```

### CI 工作流

GitHub Actions：
- **`ci.yml`**：后端 Maven test + JaCoCo artifact，前端 lint + typecheck + build
- **`codeql`**：Java + JS/TS SAST
- **Dependabot**：Maven / npm / pip / Actions / Docker 自动 PR（按生态分组，忽略 semver-major）

---

## 部署

### 生产推荐拓扑

```
              Internet
                 │
                 ▼
          ┌──────────────┐
          │    Nginx     │  443 / 80（TLS termination）
          └──────┬───────┘
                 │
       ┌─────────┼─────────┐
       ▼         ▼         ▼
  Frontend SPA  Spring Boot  WebSocket
  （静态文件）  （8080，    （/ws/workflow/*
                Virtual      /ws/classroom/*）
                Threads）
                 │
       ┌─────────┼──────────┬───────────┐
       ▼         ▼          ▼           ▼
   PostgreSQL  Redis  File Storage  External LLM
  (+ pgvector)
```

详细部署、扩缩容、灾备、压测、Chaos 演练流程，请见：

- [`deploy/README.md`](./deploy/README.md) — Docker Compose 部署
- [`deploy/loadtest/k6/README.md`](./deploy/loadtest/k6/README.md) — k6 压测脚本
- [`deploy/chaos/README.md`](./deploy/chaos/README.md) — Chaos 演练

---

## 安全

| 防护层 | 实现 |
| ----- | ----- |
| **认证** | Session-based（Redis）+ CSRF Token + TOTP 双因素 |
| **授权** | `admin_type`（Regular / Admin / Super Admin）+ 课堂角色（student / ta / teacher / owner）+ 服务层鉴权 |
| **SQL 注入** | 全部参数化查询（JdbcTemplate `?`） |
| **XSS** | Jackson JSON 序列化（无 HTML 注入），DOMPurify 前端净化 |
| **Path Traversal** | 文件名消毒（`Path.getFileName`） |
| **SSRF** | URL scheme / host 白名单（视频生成、外部 fetch） |
| **IDOR** | 资源所有权校验（`userId` / `classroomId` / `sessionId` 强绑定） |
| **凭证** | API Key 仅环境变量，Judge Token 返回前掩码，密码 BCrypt |
| **依赖审计** | Dependabot + GitHub Security Advisories |

历史安全修复明细见 [`CHANGELOG.md`](./CHANGELOG.md) 中标记 `[安全/...]` 的条目。

---

## 可观测性

```
Application
    │
    ├── Logs ──────► Logback + Sentry/GlitchTip Logback Appender
    │                       ↓
    │                  GlitchTip self-hosted（境内合规）
    │
    ├── Metrics ───► Micrometer ──► Prometheus ──► Grafana
    │                                                 ↓
    │                                          预置 Dashboard
    │                                          （JVM / HTTP / DB / LLM）
    │
    ├── Traces ────► OpenTelemetry SDK ──► OTLP ──► Jaeger
    │
    └── Coverage ──► JaCoCo ──► CI artifact (backend-jacoco-report)
```

前端通过 `@sentry/vue` 上报错误 + Vue Router 性能埋点；`web-vitals` 采集 LCP / CLS / INP。

---

## 文档索引

| 文档 | 内容 |
| ----- | ----- |
| [`PROJECT.md`](./PROJECT.md) | **完整技术说明书**：架构、API、数据库、AI 子系统（1000+ 行） |
| [`AGENTS.md`](./AGENTS.md) | 协作约定、命名规范、方案规范、增强路线图 |
| [`CHANGELOG.md`](./CHANGELOG.md) | 中文变更日志（按 Keep a Changelog） |
| [`docs/adr/`](./docs/adr/) | 架构决策记录（ADR） |
| [`docs/plans/`](./docs/plans/) | 设计稿（按日期归档） |
| [`docs/todos/todo-agent-harness/`](./docs/todos/todo-agent-harness/) | Agent + Harness 工程路线图（Phase 0.5 / 1 / 2 / 3 / 4+5） |
| [`backend/README.md`](./backend/README.md) | 后端开发说明 |
| [`deploy/README.md`](./deploy/README.md) | 部署运维说明 |
| [`services/tutor-graph/README.md`](./services/tutor-graph/README.md) | tutor-graph 微服务 |
| [`contracts/tutor_workflow/README.md`](./contracts/tutor_workflow/README.md) | 工作流合约 |
| Swagger UI | http://localhost:8081/swagger-ui/index.html |

---

## 路线图

详见 **[`docs/todos/todo-agent-harness/`](./docs/todos/todo-agent-harness/)**。

| Phase | 主题 | 状态 |
| ----- | ----- | :--: |
| **0.5** | Spring AI 试点基线 | Done |
| **1** | Context Layering 与 Memory 升级 | In Progress |
| **2** | RAG 治理与 QA Harness 升级 | Planning |
| **3** | ToolContext 与工具治理 | Planning |
| **4 + 5** | Harness 主体闭环与 HITL（Human-in-the-Loop） | Planning |

`AGENTS.md` 的「Alethicode-Academy 增强路线图」描述游戏化教学（角色扮演 + 真实判题 + AI 导学 + 错误记忆）的 Phase A / B / C 计划。

---

## 代码规范

完整规范见 **[`AGENTS.md`](./AGENTS.md)**。核心要点：

```
Java          类/文件 PascalCase   方法/变量 camelCase   常量 UPPER_SNAKE_CASE
              包名 lowercase      DB 列名 snake_case（注解映射，不扩散到 Java）

Vue / 前端    组件 PascalCase.vue  工具模块 camelCase.js
              变量/函数 camelCase   常量 UPPER_SNAKE_CASE
              i18n 文件 zh-CN.js / en-US.js

Python        文件/函数/变量 snake_case   类 PascalCase   常量 UPPER_SNAKE_CASE

API           路径 kebab-case（/api/language-pack-qa）
              JSON 字段 snake_case（answer_markdown）
              统一响应包装 ApiResponse<T>

Commit        Conventional Commits（feat / fix / chore / docs / test / refactor / perf / build / ci）
```

**约束**：
- 同一语义禁止多种拼写并存（如 `infoCard` / `InfoCard` / `inforCard` 三选一全链路统一）
- 重命名必须全链路同步（定义、引用、导入路径、文档）后再结束任务
- 不写防御性逻辑，failfast 优先；不引入兜底/降级逻辑造成业务偏移
- 禁止过度设计，最短路径实现优先

---

## 更新日志

完整中文变更日志见 **[`CHANGELOG.md`](./CHANGELOG.md)**（遵循 Keep a Changelog 思想）。

最新里程碑：

- **v1.0.0** — 首个正式稳定版（2026-05-03）
  - `@courseware:<lpId>` 课件 RAG 引用上线
  - `/guide` 产品化重构 + 重置密码邮件链路
  - logout 真终止 session（HIGH 级安全加固）
  - Sentry/GlitchTip + JaCoCo + TS 渐进入口
  - Parsons SQL 干扰项修复 + UX 优化
  - 移除遗留多 Agent 架构，收敛到 `InternalAITutorTool` 单一入口
  - 部署：start.sh Grafana 缓存、postgres `max_connections` 40→80、backend extra_hosts 文档化

---

## License

本项目衍生自开源 [QingdaoU/OnlineJudge](https://github.com/QingdaoU/OnlineJudge)，前端遵循其 **MIT License**（见 [`frontend/LICENSE`](./frontend/LICENSE)）。

后端、AI 导学、课件管线、课堂协作、tutor-graph、alethicode-rag 等模块为本项目自研代码，**根级 LICENSE 待统一发布**——在统一前，外部使用、Fork、二次分发请先与维护者联系：[`@cypre5s`](https://github.com/cypre5s)。

```
MIT License (frontend, inherited from QingdaoU/OnlineJudge)
Copyright (c) 2017-present OnlineJudge

(其余模块) © 2025–2026 Alethicode Authors. All rights reserved (pending OSS release).
```

---

## 致谢

- **[QingdaoU/OnlineJudge](https://github.com/QingdaoU/OnlineJudge)** — 提供 OJ 基线（判题协议、用户体系、提交记录骨架）
- **[Spring AI](https://spring.io/projects/spring-ai)** — LLM / Embedding / Tool 抽象层
- **[LangGraph](https://github.com/langchain-ai/langgraph)** — `tutor-graph` 工作流编排
- **[pgvector](https://github.com/pgvector/pgvector)** — PostgreSQL 向量检索扩展
- **[Element Plus](https://element-plus.org/)** — Vue 3 UI 组件库
- **[Vite](https://vite.dev/)** / **[CodeMirror](https://codemirror.net/)** / **[ECharts](https://echarts.apache.org/)** / **[KaTeX](https://katex.org/)** / **[Mermaid](https://mermaid.js.org/)** — 前端核心生态

> Alethicode 是一个开放、学术导向的工程实验平台。感谢每一位贡献者、合作教师与早期试用学生提供的反馈。

---

<div align="center">

**Made with care · for non-CS Python beginners.**

[![Repo](https://img.shields.io/badge/GitHub-cypre5s%2FAlethicode-181717.svg?logo=github)](https://github.com/cypre5s/Alethicode)
[![Issues](https://img.shields.io/github/issues/cypre5s/Alethicode.svg)](https://github.com/cypre5s/Alethicode/issues)
[![Last Commit](https://img.shields.io/github/last-commit/cypre5s/Alethicode.svg)](https://github.com/cypre5s/Alethicode/commits/main)

</div>
