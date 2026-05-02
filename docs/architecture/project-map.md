# Alethicode 项目地图（2026-05-02 快照）

> 该文件是项目结构的**导航索引**，目的是让任何成员（含 AI 协作者）能在 5 分钟内
> 了解项目的边界、模块职责和关键依赖关系。具体文件列表已经多到无法在地图里逐一
> 罗列；地图给出"哪个目录是干什么的 + 该去哪里读"，再链到具体 README / 设计文档。

## 0. 项目定位

- 面向**非计算机专业 Python 初学者**的在线教学平台（OJ + AI 导学 + 班级与作业）。
- 后端 Java（Spring Boot 3 + JDK 21），前端 Vue 3 + Element Plus，AI 流程走 Python（FastAPI + LangGraph），部署用 Docker Compose / Helm。
- AI 教学闭环关键概念：**Language Pack（语言包）→ KC（Knowledge Component）→ Problem → Submission → Mastery → Review Package**；详见 `docs/specs/language-pack-driven-ai-learning-system-functional-spec.md`。

## 1. 顶层目录

| 目录 | 体量 | 职责 |
| --- | --- | --- |
| `backend/`            | 38 MB | Java 主后端：REST API、领域服务、Flyway 迁移、Spring Security |
| `frontend/`           | 1.1 GB（含 node_modules / dist） | Vue 3 前端：OJ + Admin 双面板，Pinia 状态、Element Plus UI |
| `services/`           | 1.6 MB | Python 微服务：`tutor-graph`（LangGraph 导学工作流）、`alethicode-rag`（LightRAG 课件检索） |
| `nfk/`                | 648 KB | NFK（Neural Filter-based Knowledge tracing）训练 pipeline |
| `research/`           | 364 KB | NFK 训练契约校验 / 实验脚本（与 `nfk/` 互补） |
| `tutor_graph/`        | 8 KB  | 历史目录占位（实际代码在 `services/tutor-graph/`） |
| `contracts/`          | 84 KB | 跨语言数据契约（JSON Schema）：NFK、Tutor Workflow Cards |
| `deploy/`             | 11 GB（含本地数据卷）| Docker Compose + Helm + 数据持久化目录 + 部署 .env |
| `docs/`               | 140 MB | ADR、架构、规范、计划、报告、release notes、安全、运维等 |
| `scripts/`            | 676 KB | 部署 / 维护 / 数据迁移 / SBOM / 备份脚本 |
| `tools/`              | — | 一次性工具脚本 |
| `release/`            | — | 历史发布产物 / changelog 归档 |

## 2. 后端 `backend/` Java 模块速览

包路径：`com.alethicode`

```
backend/src/main/java/com/alethicode/
├── config/             # Spring 配置：SecurityConfig、MultiTierCacheConfig、AlethicodeProperties、TemporalLanguagePackWorkflowConfig
├── controller/
│   ├── classroom/      # 班级 / 作业 / AI 出题 / 协作 / 监控 controllers
│   ├── internal/       # 仅服务间调用（受 InternalServiceKeyValidator 保护）
│   └── *.java          # 顶层公共 controllers（Account、Problem、Submission、TutorWorkflow、AdminLanguagePack、AdminNfk 等）
├── dto/                # request / response DTO
├── entity/             # JPA 实体（迁移到 boundless DDD 后剩很少）
├── exception/          # ErrorCode、BusinessException、Domain failfast
├── mcp/                # AlethicodeMcpToolProvider（暴露 MCP 工具）
├── middleware/         # RateLimitFilter、SessionAuthenticationFilter
├── repository/         # Spring Data JPA repositories
├── service/            # 领域 / 应用服务（详见下表）
├── util/               # AuthUserResolver、ServiceParseUtils
└── websocket/          # ClassroomMonitorWebSocketHandler / TutorWorkflowWebSocketHandler
```

### `service/` 子包职责

| 子包 | 职责 | 关键服务 |
| --- | --- | --- |
| `account/`         | 账号、登录、头像、密码改 | `AccountServiceImpl` |
| `admin/`           | Admin 上传、配置 | `AdminUploadService` |
| `adminproblemcommand/` | Admin 出题 / 编辑题命令 | `AdminProblemCommand*` |
| `ai/`              | LLM 网关 | `AiModelGateway`（Spring AI / DeepSeek） |
| `aitutor/`         | AI 导学全集（最大子包，下文展开） |
| `announcement/`    | 公告 | `ReleaseNotesService` |
| `betafeedback/`    | beta 用户反馈 |
| `classroom/`       | 班级 / 作业 / 出题 / 监控 / 协作 |
| `compliance/`      | 大陆合规审计、敏感日志（V59 后） |
| `languagepack/`    | 语言包初始化 / 课件解析 / KC 抽取 / 例题抽取 / 题目生成 / 发布 / Q&A / 视频 |
| `monitor/`         | 班级监控 / Risk detection |
| `nats/`            | NatsStreamSupport（JetStream subject 兜底） |
| `nfk/`             | NFK 训练数据导出（→ NfkDataExportService） |
| `problem/`         | 题目读 / RelatedExampleQuery |
| `rag/`             | HttpRagServiceClient（→ alethicode-rag 微服务） |
| `submission/`      | submission 查询 / 节流 / 去敏感 |
| `system/`          | 平台配置 / 系统选项 / 管理员 |
| `usagestats/`      | 平台使用量统计 |

### `service/aitutor/` 二级子包

| 子包 | 职责 |
| --- | --- |
| `admin/`        | misconception 挖掘、admin 管理 |
| `agent/`        | Agent 调度（已收编到 tutor-graph，仅留薄壳） |
| `assessment/`   | 评估 / Mastery 投影辅助 |
| `context/`      | ConversationContextService（Unified Chat 跨卡上下文） |
| `contract/`     | ErrorTaxonomy、FailureBucket、Phase、ClientEvent 等枚举 |
| `eval/`         | TutorEvalHarness（运行时评测） |
| `events/`       | LearningEventPublisher（Nats）+ ClassroomAssignmentEventSubscriber |
| `evidence/`     | EvidencePackAssembler（学情画像注入卡片） |
| `execution/`    | 异步执行（已部分迁移到 LangGraph） |
| `graph/`        | TutorGraphClient + Authorizer + ProjectionService（与 tutor-graph 微服务互调） |
| `impl/`         | AITutorServiceImpl（recommendByMastery 等） + AITutorWorkflowAdminServiceImpl |
| `language/`     | 多语种 prompt / Tutor 语言归一 |
| `nfk/`          | NfkInferenceService（线上 NFK 推理 client） |
| `observability/`| Langfuse / 链路指标 |
| `parsons/`      | Faded Parsons 渐退拼装题 |
| `path/`         | LearningPathOptimizerService、DifficultyCalibrationService、`MasteryAdaptiveProblemSelector`（Phase 0） |
| `policy/`       | RuntimeStatePolicy（接口 / 实现） |
| `profile/`      | MasteryService、LearnerNarrativeSummaryService、AITutorWelcomeService |
| `quota/`        | AiTutorQuotaService（Daily LLM run + Active session 配额） |
| `react/`        | ReAct 推理（默认关闭） |
| `reflection/`   | LLM 反思 |
| `retrieval/`    | CoursewareRetrievalService、SimilarErrorRetrievalService |
| `review/`       | ErrorReviewPackageService（FSRS 错题复习包） |
| `rlhf/` / `rollout/` | RLHF 数据收集 / Rollout 控制（实验态） |
| `schema/`       | Card schema 加载 |
| `supplement/`   | BeginnerSupplementPlannerService（统一补给计划） |
| `transfer/`     | 迁移题（举一反三） |
| `visualize/`    | 可视化（流程图 / 数据流） |

### `service/classroom/` 关键服务

| 服务 | 职责 |
| --- | --- |
| `ClassroomCoreDomainService`         | 班级 CRUD、邀请码、成员管理 |
| `ClassroomMemberDomainService`       | 成员角色 / 提拔 / 降级 |
| `ClassroomLessonDomainService`       | 课件上传 / 索引 / 分页 |
| `ClassroomSessionDomainService`      | 协作会话 / 中继 token |
| `ClassroomAssignmentDomainService`   | 作业（manual + smart_kc 智能组卷 + tutor-context） |
| `ClassroomMonitorDomainService` / `MonitorService` / `MonitorQueryService` / `MonitorFacade` | 班级监控 |
| `ClassroomAiProblemService` + `ClassroomAiProblemDomainService` | AI 出题（Phase A：lp_first/llm_first/lp_only/llm_only 分流 + 反写 KC） |
| `ClassroomAnalyticsService`          | 周脉冲 / KC 热力图 / 学生风险 |
| `ClassroomAccessHelper`              | 权限统一辅助 |
| `LearnerCourseProgressService`       | 学习者课程进度 |
| `CourseInsightService`               | 班级 KC mastery 视角 |
| `ai/ClassroomKcResolver` / `ai/ClassroomAssignmentSmartComposer` | Phase A/B 新增的薄适配器 |

### `controller/`

| Controller | 端点前缀 |
| --- | --- |
| `AccountController`             | `/api/account/*`、`/api/login`、`/api/register` |
| `ProblemController`             | `/api/problem/*` |
| `SubmissionController`          | `/api/submission/*` |
| `TutorWorkflowController`       | `/api/ai/tutor-workflow-sessions/*`（Phase C 接 context） |
| `CourseProgressController`      | `/api/course-progress/*`、`/api/learning-path`、`/api/recommend/next-problem` |
| `AdminLanguagePackController`   | `/api/admin/language-pack/*` |
| `AdminNfkController`            | `/api/admin/nfk/training-data/{export,readiness}` |
| `BetaFeedbackController` / `AdminBetaFeedbackController` | `/api/beta-feedback/*` |
| `classroom/*`                   | `/api/classroom/*`（含 AI 出题、作业、监控、协作 5 个 controller） |
| `internal/*`                    | `/api/internal/*`（仅 tutor-graph / alethicode-rag 等内部服务调用） |

### Flyway 迁移

`backend/src/main/resources/db/migration/` 共 75 个 V*.sql。最近 3 次：

- `V81__classroom_ai_problem_kc_link.sql`（Phase A，2026-05-02）
- `V82__classroom_assignment_smart_compose.sql`（Phase B，2026-05-02）
- `V78__problem_log_materialized_at.sql`（旧）

## 3. 前端 `frontend/` Vue 3

```
frontend/src/
├── api/
│   └── modules/        # 跨页面 API 聚合（admin / classroom / submission / language-pack / 等）
├── assets/             # 静态资源
├── components/         # 通用组件（Pagination 等）
├── composables/
│   └── problem/        # useSubmission / useEditor 等
├── i18n/               # 国际化
├── pages/
│   ├── admin/          # /admin 路由：AdminLayout + views
│   │   └── views/
│   │       ├── general/      # SecretsAiConfig、BetaFeedback、平台设置
│   │       └── problem/      # admin 出题 / 编辑
│   └── oj/             # /oj 路由：学生与教师视角
│       ├── api/        # OJ 自家 API（aiTutor / classroom / shared / 等）
│       ├── components/ # 全局组件（NavBar / BetaFeedbackButton / SkillProfile）
│       ├── router/     # 路由
│       └── views/
│           ├── classroom/    # ClassroomList、ClassroomDetail、ClassroomAssignment、AssignmentDetail、AssignmentGrading、AIGeneratedProblems
│           ├── general/      # 首页 / Dashboard
│           ├── languagepack/ # LP 浏览 / 学习路径
│           ├── manual/       # 用户手册
│           ├── problem/      # Problem 主页 + UnifiedAgentPanel + LearningPathMap + cards / parsons
│           ├── review/       # 错题复习包
│           ├── setting/      # 个人设置
│           ├── submission/   # 提交列表 / 详情
│           └── user/         # 用户主页 / Notebook / MisconceptionTagCloud
├── plugins/            # vue-plugin 注册
├── store/              # Pinia
├── styles/             # 全局样式
└── utils/              # time / sanitize / echarts / 等
```

测试入口：

- `tests/unit/`：~115 个 spec（含 `*-contract.spec.js` 跨语言契约测试）
- `tests/e2e/`：playwright 视觉与流程

## 4. 微服务 `services/`

```
services/
├── tutor-graph/                 # LangGraph 导学工作流（Python 3.11+ + FastAPI）
│   └── app/
│       ├── main.py              # FastAPI 入口（创建 thread / run / events / checkpoints）
│       ├── config.py            # env 加载（含 anti_cheating context 默认值）
│       ├── clients/llm_client.py# 替换调用层（OpenAI 兼容协议 → DeepSeek）
│       ├── graph/state.py       # TutorGraphState（含 Phase C 新增 context: dict）
│       ├── graph/builder.py     # LangGraph 节点接线
│       ├── nodes/               # 节点实现（reading / ideating / coding / diagnosis / chat / ac_review / transfer / parsons / visualize / 等）
│       ├── eval/                # red_team / anti_cheating_judge（Phase C+ 新增）
│       └── tests/               # pytest
└── alethicode-rag/              # LightRAG 课件检索（pgvector + Memgraph）
    └── app/
        ├── main.py              # FastAPI 入口
        ├── config.py            # env 配置
        ├── auth.py              # 内部 token 校验
        ├── rag/                 # llm / embedding / lightrag wrapper
        ├── routes/              # /v1/rag/query 等
        └── tests/
```

## 5. NFK `nfk/` + `research/nfk/`

```
nfk/                              # 训练 pipeline 主体
├── data/
│   ├── dataset.py                # KTDataset / KTCollator
│   ├── preprocessor.py           # 入口：assistments / ednet / progsnap2 / synthetic / alethicode
│   ├── preprocessor_alethicode.py# Phase NFK 新增：吃后端 5 字段 CSV
│   ├── preprocessor_assistments.py
│   ├── preprocessor_ednet.py
│   ├── preprocessor_progsnap2.py
│   ├── preprocessor_synthetic.py
│   └── download.py
├── models/                       # Component A/B/C + NFK 主模型
├── training/                     # NFKTrainer / loss / metrics_logger
├── evaluation/                   # metrics / visualizer
├── inference/                    # predictor / exporter（ONNX）
├── run_local.py                  # 本地 GPU 入口（quick / full）
├── autodl_train.py               # AutoDL 长序列训练
├── tests/                        # pytest（含 alethicode preprocessor smoke）
└── EXPERIMENT_LOG.md

research/
└── nfk/
    └── data/contract_validator.py # 5 字段 CSV 契约校验（与 backend NfkTrainingRowValidator 双向）
```

## 6. 契约 `contracts/`

```
contracts/
├── nfk/
│   ├── training_dataset.schema.json   # NFK 训练 5 字段
│   └── README.md
└── tutor_workflow/cards/                # tutor-graph 卡片 schema
    ├── problem_guide.schema.json
    ├── ideate_analysis.schema.json
    ├── error_diagnosis.schema.json
    ├── post_ac.schema.json
    ├── transfer_problem.schema.json
    ├── parsons_*.schema.json
    └── …
```

跨语言契约：Java 写 + Python 读（NFK CSV）；Python 生成 + Java 读（Tutor Workflow 卡片）。

## 7. 部署 `deploy/`

```
deploy/
├── docker-compose.yml             # 主部署：postgres、redis、pgbouncer、temporal、nats、memgraph、prometheus、grafana、backend、frontend、tutor-graph、alethicode-rag、judge
├── frontend.Dockerfile            # 前端构建 + nginx
├── frontend-nginx.conf            # nginx（限流 + 静态 + 反代）
├── .env / .env.example             # 部署级 env
├── data/                           # 数据卷（test_case / language_pack / 上传 / heatmap）
└── helm/alethicode/                # Helm chart（生产 K8s 部署）
    ├── values.yaml
    └── templates/
        ├── backend-deployment.yaml
        ├── tutor-graph-deployment.yaml
        ├── alethicode-rag-deployment.yaml
        └── secrets.yaml
```

## 8. 文档 `docs/` 索引

| 子目录 | 用途 |
| --- | --- |
| `adr/`           | ADR（含 `0001-langgraph-tutor-workflow / 0007-nfk-training-data-contract` 等） |
| `architecture/`  | 架构图 / UML / agent workflow（**本文件就是这里**） |
| `archives/`      | 历史决议归档 |
| `baseline/`      | M0 baseline、性能基线 |
| `competition/`   | 比赛设计 / 项目 factsheet |
| `guides/`        | 操作指南 |
| `overview/`      | 整体概览 |
| `plans/`         | 设计 plan + 工作日志（最新：`2026-05-02-classroom-aiteach-2026q2-worklog.md`） |
| `release-notes/` | 升级手册 |
| `reports/`       | 调研、bug review、渗透测试报告（含 `2026-05-02-pentest-report-v2.md`） |
| `security/`      | 安全策略 |
| `specs/`         | 功能规范、UML、迁移规则 |
| `sre/`           | 运维 / 备份 / 监控 |
| `todos/`         | TODO 列表（master / debt / langgraph / nfk-integration 等） |
| `worklog/`       | 历次工作日志 |

## 9. 关键依赖关系图

```mermaid
flowchart LR
  subgraph FE[Vue 3 前端]
    OJ["/oj 学生 + 教师视角"]
    ADMIN["/admin 平台管理"]
    OJ -- /api/* --> BE
    ADMIN -- /api/admin/* --> BE
  end

  subgraph BE[Java 后端]
    Controller -->|JdbcTemplate| Postgres
    Controller -->|StringRedisTemplate| Redis
    Controller -->|Spring AI Gateway| LLM[(DeepSeek<br/>OpenAI 兼容)]
    Controller --> EvtPub[LearningEventPublisher]
    EvtPub -->|JetStream| Nats
    EvtPub -->|in-process| Subscriber[ClassroomAssignmentEventSubscriber]
    Subscriber --> Mastery
    Subscriber --> ReviewPkg[ErrorReviewPackageService]
    Mastery --> NfkInfer[NfkInferenceService<br/>ONNX runtime]
    Controller -->|HTTP /internal/graph/*| TutorGraph
    Controller -->|HTTP /v1/rag/*| RagSvc
  end

  subgraph TutorGraph[tutor-graph 微服务]
    LangGraph[LangGraph 编排] --> Reading[reading.py]
    LangGraph --> Diagnosis[diagnosis.py]
    LangGraph --> ACReview[ac_review.py]
    LangGraph --> Transfer[transfer.py]
    LangGraph --> Parsons[parsons.py]
    LangGraph -. context.anti_cheating .-> Reading
    LangGraph -. context.anti_cheating .-> Diagnosis
    Reading -->|LLM| LLM
    Diagnosis -->|LLM| LLM
    LangGraph -- AsyncPostgresSaver --> Postgres
  end

  subgraph RagSvc[alethicode-rag 微服务]
    LightRAG --> Memgraph
    LightRAG --> PgVector[Postgres + pgvector]
    LightRAG -->|LLM 关键词抽取| LLM
    LightRAG -->|Embedding| EmbedAPI[(BigModel / DashScope)]
  end

  subgraph Training[NFK 训练]
    BE -- /api/admin/nfk/training-data/export --> CSV
    CSV --> ContractValidator[contracts/nfk + research/nfk validator]
    CSV --> NFK[nfk/run_local.py --dataset alethicode]
    NFK --> ONNX
    ONNX --> NfkInfer
  end

  subgraph Infra[基础设施]
    Postgres
    Redis
    Nats[(NATS JetStream)]
    Temporal[(Temporal)]
    Memgraph
    PgVector
  end
```

## 10. 关键约定

- **命名规范**：见 `AGENTS.md`（Java PascalCase / camelCase / snake_case 表名；Vue PascalCase 文件 / 组件名）。
- **Plan 规范**：禁止补丁式 / 兜底式 / 防御性逻辑，failfast 优先；输出方案必须经过链路验证。
- **API 设计**：见 `api-design-principles` skill；RESTful + 版本化；统一 `ApiResponse{error,data}`。
- **前端视觉**：见 `ui-ux-pro-max` skill；Element Plus 主色 + 圆角卡片化 + 受控间距。
- **跨语言契约**：所有跨语言数据字段必须在 `contracts/` 下定义 schema 并双向校验（典型：NFK CSV）。
- **AI 推理**：默认 LLM 走 DeepSeek（OpenAI 兼容）；评测脚本（red_team / anti_cheating_judge）默认 `deepseek-v4`，主链路 `deepseek-v4-flash`。

## 11. 我下次要快速了解某主题时该去哪里读

| 想看 | 优先读 |
| --- | --- |
| Tutor 工作流状态机 | `services/tutor-graph/app/graph/state.py` + `app/main.py._execute_run` + `docs/specs/2026-04-07-adaptive-problem-orchestration-and-ai-tutor-state-machine.md` |
| NFK 训练数据契约 | `contracts/nfk/training_dataset.schema.json` + `docs/adr/0007-nfk-training-data-contract.md` |
| 班级 / 作业领域规则 | `backend/.../service/classroom/impl/ClassroomAssignmentDomainServiceImpl.java` + `docs/plans/2026-05-02-classroom-aiteach-2026q2-worklog.md` |
| Language Pack 全链路 | `docs/specs/language-pack-driven-ai-learning-system-functional-spec.md` + `service/languagepack/impl/*` |
| 安全模型 | `config/SecurityConfig.java` + `middleware/RateLimitFilter.java` + `docs/reports/2026-05-02-pentest-report-v2.md` |
| 运维 / 部署 | `deploy/docker-compose.yml` + `deploy/.env.example` + `start.sh` |

