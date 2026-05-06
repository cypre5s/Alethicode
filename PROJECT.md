# Alethicode 项目技术说明书

> **文档编号**：ALETH-SPEC-2026-001  
> **版本**：v2.0  
> **发布日期**：2026-04-02  
> **系统版本**：2026.03-java-m2  
> **分类**：内部技术文档（机密）

---

## 目录

1. [项目概述](#一项目概述)
2. [系统架构总览](#二系统架构总览)
3. [技术栈明细](#三技术栈明细)
4. [代码结构](#四代码结构)
5. [核心功能模块](#五核心功能模块)
6. [AI 导学助手架构](#六ai-导学助手架构)
7. [语言包管线架构](#七语言包管线架构)
8. [课堂协作架构](#八课堂协作架构)
9. [数据库设计](#九数据库设计)
10. [API 端点规范](#十api-端点规范)
11. [安全架构](#十一安全架构)
12. [部署架构](#十二部署架构)
13. [配置参考](#十三配置参考)
14. [开发指南](#十四开发指南)
15. [附录](#十五附录)
16. [Agent + Harness 工程路线图](#十六agent--harness-工程路线图)

---

## 一、项目概述

### 1.1 项目定位

Alethicode 是一个面向 **非计算机专业 Python 初学者** 的智能在线评测系统（OJ），将传统 OJ 的自动判题能力与 AI 驱动的个性化教学辅导相融合。

### 1.2 核心指标

```
┌──────────────────────────────────────────────────────────────┐
│                    Alethicode 系统指标                        │
├──────────────────┬───────────────────────────────────────────┤
│ 后端源文件        │ 299 Java files                           │
│ 前端组件          │ 96 Vue + 72 JS files                     │
│ 数据库迁移        │ 38 Flyway versions                       │
│ REST 端点         │ 150+ endpoints across 30+ controllers    │
│ AI 卡片类型       │ 8 CardTypes                              │
│ 工作流阶段        │ 7 Phases (FSM)                           │
│ 教学 Agent        │ 5 specialized agents                     │
│ 评估维度          │ 8 LLM-as-Judge dimensions                │
│ 支持语言          │ Python3, C, C++, Java                    │
└──────────────────┴───────────────────────────────────────────┘
```

---

## 二、系统架构总览

### 2.1 分层架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           用户层 (User Layer)                           │
│   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌──────────────┐    │
│   │  学生 OJ 端  │ │  教师管理端  │ │  课堂协作端  │ │  课件问答端   │    │
│   │  (Vue 3)    │ │  (Vue 3)    │ │ (WebSocket) │ │  (Vue 3)     │    │
│   └──────┬──────┘ └──────┬──────┘ └──────┬──────┘ └──────┬───────┘    │
│          │               │               │               │             │
└──────────┼───────────────┼───────────────┼───────────────┼─────────────┘
           │               │               │               │
┌──────────┼───────────────┼───────────────┼───────────────┼─────────────┐
│          ▼               ▼               ▼               ▼             │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │              API Gateway / Spring Security                   │     │
│   │          (SessionAuth + CSRF + CORS + API Key)               │     │
│   └──────────────────────────┬───────────────────────────────────┘     │
│                              │                                         │
│   ┌──────────────────────────┼───────────────────────────────────┐     │
│   │                    Controller Layer (30+)                    │     │
│   │  Account │ Problem │ Submission │ AI Tutor │ Classroom │ QA │     │
│   └──────────────────────────┬───────────────────────────────────┘     │
│                              │                                         │
│   ┌──────────────────────────┼───────────────────────────────────┐     │
│   │                    Service Layer                              │     │
│   │  ┌──────────┐ ┌────────┐ ┌──────────┐ ┌──────────────────┐  │     │
│   │  │ Account  │ │Problem │ │Submission│ │  AI Tutor         │  │     │
│   │  │ Service  │ │ Query  │ │ Service  │ │ (FSM + Memory +   │  │     │
│   │  └──────────┘ └────────┘ └──────────┘ │  Layered Prompt)  │  │     │
│   │  ┌──────────┐ ┌────────────────────┐  └──────────────────┘  │     │
│   │  │Classroom │ │LanguagePack Service│  ┌──────────────────┐  │     │
│   │  │ Service  │ │(Init/QA/Publish/   │  │  AiModelGateway  │  │     │
│   │  │          │ │ Video/Storage)     │  │  (Spring AI)     │  │     │
│   │  └──────────┘ └────────────────────┘  └──────────────────┘  │     │
│   │                                                              │     │
│   └──────────────────────────────────────────────────────────────┘     │
│                              │                                         │
│   ┌──────────────────────────┼───────────────────────────────────┐     │
│   │                    Data Layer                                 │     │
│   │  ┌──────────┐  ┌─────────┐  ┌──────────┐  ┌──────────┐     │     │
│   │  │PostgreSQL│  │  Redis  │  │  File    │  │ External │     │     │
│   │  │(pgvector)│  │(Session)│  │ Storage  │  │ LLM API  │     │     │
│   │  └──────────┘  └─────────┘  └──────────┘  └──────────┘     │     │
│   └──────────────────────────────────────────────────────────────┘     │
│                         Application Layer                              │
└────────────────────────────────────────────────────────────────────────┘
```

### 2.2 请求处理流程

```
Client Request
     │
     ▼
┌─────────────────────┐
│ SessionAuthFilter   │◄── Redis Session 查找
│ (middleware/)       │    ┌──────────────┐
│                     │───►│ 解析 admin_type│
│                     │    │ 设置 Authority │
└─────────┬───────────┘    └──────────────┘
          │
          ▼
┌─────────────────────┐
│ SecurityConfig      │    permitAll() — 依赖服务层鉴权
│ CSRF Protection     │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│ @RestController     │    路由到对应 Controller
│ Input Validation    │    @Valid + DTO
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│ @Service            │    业务逻辑 + 权限检查
│ @Transactional      │    JdbcTemplate / JPA
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│ Response            │    ApiResponse<T> 包装
│ (JSON)              │
└─────────────────────┘
```

---

## 三、技术栈明细

### 3.1 后端

```
┌─────────────────────────────────────────────────────────────┐
│                     Backend Tech Stack                       │
├─────────────────┬───────────────────────────────────────────┤
│ Runtime         │ Java 21 (Virtual Threads)                  │
│ Framework       │ Spring Boot 3.4.4                          │
│ Web             │ Spring Web MVC + WebSocket                 │
│ Security        │ Spring Security 6.x                        │
│ Session         │ Spring Session Data Redis                  │
│ Database        │ PostgreSQL 15+ (pgvector extension)        │
│ ORM             │ Spring Data JPA + JdbcTemplate             │
│ Migration       │ Flyway                                     │
│ Serialization   │ Jackson (snake_case)                       │
│ Validation      │ Jakarta Bean Validation                    │
│ HTTP Client     │ java.net.http.HttpClient (for LLM)         │
│ Monitoring      │ Micrometer + Prometheus                    │
│ API Docs        │ springdoc-openapi (Swagger UI)             │
│ Build           │ Maven                                      │
│ HTML Parse      │ Jsoup 1.18.3                               │
└─────────────────┴───────────────────────────────────────────┘
```

### 3.2 前端

```
┌─────────────────────────────────────────────────────────────┐
│                     Frontend Tech Stack                      │
├─────────────────┬───────────────────────────────────────────┤
│ Framework       │ Vue 3.5.13                                 │
│ Build           │ Vite 7.1.5                                 │
│ Router          │ Vue Router 4                               │
│ State           │ Vuex 4                                     │
│ UI Library      │ Element Plus 2.13.6                        │
│ i18n            │ vue-i18n 9.2.2                             │
│ HTTP            │ Axios                                      │
│ Code Editor     │ CodeMirror 6                               │
│ Charts          │ ECharts 3 + D3                             │
│ Math            │ KaTeX                                      │
│ Highlight       │ highlight.js                               │
│ Node            │ >= 20.19.0                                 │
└─────────────────┴───────────────────────────────────────────┘
```

### 3.3 AI / LLM

```
┌─────────────────────────────────────────────────────────────┐
│                     AI / LLM Stack                           │
├─────────────────┬───────────────────────────────────────────┤
│ LLM             │ DeepSeek（生产）/ MiniMax-M2.7（备选）     │
│ Embedding       │ 阿里通义 text-embedding-v4                  │
│ Prompt Layers   │ LayeredPromptBuilder（已下线）→ 现已收敛到   │
│                 │ EvidencePack + LearnerProfile + Courseware  │
│ Reflection      │ 自研 Producer-Critic (CardType 分维度)       │
│ Workflow Engine │ Java FSM (Phase/Event) + tutor-graph        │
│                 │ (LangGraph) 双轨；ReAct/Multi-Agent 已下线   │
│ Evaluation      │ LLM-as-Judge (8 维度 rubric) + Red Team CI  │
│ A/B Testing     │ 自研 RolloutPolicyService                   │
│ Video Gen       │ LLM 分镜 + 外部 TTS/Render (可插拔)          │
└─────────────────┴───────────────────────────────────────────┘
```

---

## 四、代码结构

### 4.1 后端包结构

```
com.pytutor/
├── config/                          # 配置
│   ├── SecurityConfig               # 安全配置
│   ├── AlethicodeProperties         # 业务配置
│   ├── JacksonConfig                # JSON 序列化
│   ├── WorkflowWebSocketConfig      # Tutor WebSocket
│   ├── ClassroomWebSocketConfig     # 课堂 WebSocket
│   └── LoadTestProfileConfig        # 压测配置
│
├── controller/                      # REST 端点
│   ├── AccountController            # /api/login, /api/register, ...
│   ├── ProblemController            # /api/problems
│   ├── SubmissionController         # /api/submission
│   ├── AITutorController            # /api/ai/tutor/*, /api/ai/skill/*
│   ├── AITutorWorkflowController    # /api/ai/workflow/*
│   ├── LanguagePackQaController     # /api/language-pack-qa/*
│   ├── AdminProblemController       # /api/admin/problems
│   ├── AdminLanguagePackController  # /api/admin/language-packs/*
│   ├── PublicAssetController        # /public/*
│   └── classroom/                   # 课堂子控制器 (6 个)
│
├── service/
│   ├── ai/                          # LLM gateway（多 provider 装配）
│   │   ├── AiModelGateway           #   核心接口（Spring AI 封装）
│   │   ├── SpringAiModelGateway     #   生产实现
│   │   └── CachingAiModelGateway    #   缓存装饰器
│   │
│   ├── aitutor/                     # AI 导学助手子系统
│   │   ├── observability/           # 观测链路
│   │   │   ├── AgentTraceRecorder   #   span 持久化
│   │   │   ├── AgentTraceContext    #   span 上下文
│   │   │   └── AgentObservabilityService # admin 驾驶舱
│   │   │
│   │   ├── reflection/              # Reflection 框架
│   │   │   ├── ReflectionService    #   接口
│   │   │   ├── ReflectionResult     #   结果
│   │   │   └── ReflectionServiceImpl#   Producer-Critic 实现
│   │   │
│   │   ├── contract/                # CardType, Phase
│   │   ├── evidence/                # EvidencePack 组装
│   │   ├── eval/                    # 评估 Harness
│   │   ├── execution/               # 代码执行追踪
│   │   ├── language/                # 多语言支持
│   │   ├── policy/                  # 动作策略 + Bandit
│   │   ├── profile/                 # 学习者画像
│   │   ├── retrieval/               # 检索服务
│   │   ├── rollout/                 # 灰度 + A/B
│   │   ├── scaffolding/             # 脚手架策略
│   │   └── schema/                  # 卡片 Schema
│   │
│   ├── languagepack/                # 语言包子系统
│   │   ├── LanguagePackInitService  #   初始化管线
│   │   ├── LanguagePackQaService    #   课件问答
│   │   ├── VideoJobService          #   视频生成
│   │   ├── PageRetrievalService     #   混合检索
│   │   ├── AnswerSynthesisService   #   答案合成
│   │   └── impl/                    #   实现 (15+ 文件)
│   │
│   ├── impl/                        # 核心服务实现
│   │   ├── AITutorWorkflowAdminServiceImpl  # 4300+ 行
│   │   ├── ClassroomServiceImpl     #   4400+ 行
│   │   └── SubmissionServiceImpl    #   2000+ 行
│   │
│   ├── classroom/                   # 课堂域服务
│   ├── submission/                  # 提交域服务
│   └── account/                     # 账户域服务
│
├── dto/                             # 数据传输对象
│   ├── request/  (48 records)
│   └── response/ (15+ records)
│
├── entity/                          # JPA 实体
├── exception/                       # 异常体系
├── middleware/                      # 认证过滤器
├── repository/                      # Spring Data
├── util/                            # 工具类
└── websocket/                       # WebSocket 处理器
```

### 4.2 前端路由结构

```
/                           ─── Home (首页)
├── /login                  ─── 登录
├── /register               ─── 注册
├── /problem                ─── 题目列表
│   └── /problem/:id        ─── 题目详情 + AI 面板
├── /status                 ─── 提交记录
│   └── /status/:id         ─── 提交详情
├── /user-home              ─── 个人主页
├── /learner-notebook       ─── 学习笔记本
├── /setting                ─── 设置
│   ├── /profile            ─── 个人资料
│   ├── /account            ─── 账号安全
│   └── /security           ─── 双因素认证
├── /classroom              ─── 课堂列表
│   ├── /classroom/join     ─── 加入课堂
│   ├── /classroom/:id      ─── 课堂详情
│   └── /classroom/:id/collab/:sessionId  ─── 协作编程
├── /language-packs         ─── 语言包目录
├── /language-pack-qa       ─── 课件问答 (需登录)
├── /review-package/:id     ─── 错误审查 (需登录)
└── /*                      ─── 404
```

---

## 五、核心功能模块

### 5.1 在线评测 (OJ)

```
学生提交代码
     │
     ▼
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│ Submission   │────►│ Throttle     │────►│ Judge Server │
│ Controller   │     │ Service      │     │ (HTTP)       │
│              │     │ (限流)       │     │              │
└──────────────┘     └──────────────┘     └──────┬───────┘
                                                  │
                                                  ▼
                                          ┌──────────────┐
                                          │ 判题结果      │
                                          │ AC/WA/TLE/RE │
                                          └──────┬───────┘
                                                  │
                      ┌───────────────────────────┼───────────────┐
                      ▼                           ▼               ▼
               ┌──────────────┐         ┌──────────────┐  ┌──────────────┐
               │ 更新统计      │         │ AI Tutor     │  │ 课堂作业     │
               │ submission   │         │ ERROR_FEEDBACK│  │ 评分         │
               │ statistics   │         │ 触发诊断      │  │              │
               └──────────────┘         └──────────────┘  └──────────────┘
```

### 5.2 Career Bridging Closure（专业 × 编程 4 模块闭环）

为 12 个非 CS 专业（生物 / 化学 / 医学 / 药学 / 临床医学 / 工商管理 / 经济 / 金融 / 统计 / 心理 / 机械工程 / 土木工程）补齐 Why / How / What / Map 四层闭环，**判题不动、Judge Server 协议不动、IO schema 不动**。

| 模块 | 触发链路 | 关键产物 | 灰度 experiment_id |
|---|---|---|---|
| **Career Bridging（Why）** | 5 类里程碑（enrollment / kc_cluster_graduated / chapter_entered / project_completed / path_node_unlocked）→ A/B 分组 → LLM + Reflection critic → `career_bridging_report` | Why 报告 | `career_bridging_v1`（A/B） |
| **Coding Lens（How / Variant）** | 学生在题目页切「我专业版」→ rollout → LLM 重写题面（IO schema 不变 + 测试样例语义不偏移）→ `problem_domain_variant` | 专业化题面变体 | `coding_lens` |
| **Project Studio（What）** | 学生在 Studio 选 KC → LLM 出题 + critic → **reference solution 真判题自验证**（100% AC 自身 test_cases）→ 落 `problem` 表 + `career_micro_project` | Python 微项目 + 作品集 Markdown 卡片 | `career_micro_project` |
| **Career Path Map（Map）** | 学生看路径地图 → 拓扑排序 + mastery 三态判断（unlocked / in_progress / locked）→ GraphRAG why_md | 12 专业 × 5 KC 路径 DAG | `career_path` |

**触发联动（todo 10 + 13）**：
1. `JudgeCompletedEventListener` 在 mastery 写入后调用 `CareerMilestoneEventListener.onMasteryUpdated`，KC 跨过 0.7 触发 `kc_cluster_graduated`
2. `LearnerCourseProgressService.getOrCreateProgress` 首次访问课件包触发 `chapter_entered`
3. AC 微项目对应 problem → `JudgeCompletedEventListener.handleMicroProjectCompletion` → `MicroProjectStudioService.markCompletedByJudgeProblem` → 写 `project_completed` + 立即 `generateForMilestone` 重激活 Why 报告

**关闭路径（todo 15）**：
- 教师锁定（考试模式）：`POST /api/coding-lens/variants/{id}/lock` 后任意 major 请求都返回锁定 variant，确保所有学生看同一份题面
- 学生级面板：`PUT /api/career/preferences` 可独立关闭 4 个模块；service 入口 `isModuleDisabled` 短路

```
关键 Flyway 表（V83 / V84 / V85 / V86 / V88）
├── user_profile (扩展) ── major_code / career_intent / career_profile_completed_at
│                       └─ 4 个 disabled BOOLEAN 列（V88）
├── career_major_dictionary ── 12 专业字典 + seed_use_cases
├── career_bridging_milestone (5 类 type) + career_bridging_report
├── problem_domain_variant ── (problem_id, major_code) 题面变体缓存 + locked_for_exam
├── career_micro_project ── (user_id, judge_problem_id, status, rollout_mode, trace_id)
└── career_path_node ── (major_code, kc_code) DAG + sort_order + 60 行人工种子
```

进度详见 [`docs/todos/todo-career-bridging-closure-progress.md`](./docs/todos/todo-career-bridging-closure-progress.md)。

### 5.3 学习者画像

```
┌─────────────────────────────────────────────────────────────┐
│                    LearnerState                              │
├──────────────────┬──────────────────────────────────────────┤
│ calibrated       │ 是否已校准                                │
│ masteryByKc      │ { "变量": 0.8, "循环": 0.3, ... }         │
│ weakKcs          │ ["循环", "函数"]                           │
│ misconceptions   │ { "off_by_one": 0.6, ... }               │
│ recentBehavior   │ { consecutiveErrors: 3, ... }             │
│ frustrationLevel │ "low" | "moderate" | "severe"             │
│ confidenceProxy  │ "low" | "medium" | "high"                 │
│ memoryRefs       │ [{ key: "error_pattern_1", ... }]         │
└──────────────────┴──────────────────────────────────────────┘
        │
        │ 数据来源
        ▼
┌───────────────────────────────────────────────────────────────┐
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐     │
│  │提交记录   │  │错误笔记本 │  │学习记忆   │  │KC 掌握度  │     │
│  │submission │  │notebook  │  │memory    │  │mastery   │     │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘     │
└───────────────────────────────────────────────────────────────┘
```

---

## 六、AI 导学助手架构

### 6.1 工作流状态机 (FSM)

```
                          ┌───────────────┐
                          │   READING     │
                          │  (审题导学)    │
                          └───────┬───────┘
                                  │
                                  ▼
                          ┌───────────────┐
                          │   IDEATING    │
                          │  (思路引导)    │
                          └───────┬───────┘
                                  │
                                  ▼
                    ┌─────────────────────────────┐
                    │        SCAFFOLDING          │
                    │   worked / faded / parsons  │
                    │   / minimal hint            │
                    └─────────────┬───────────────┘
                                  │
                                  ▼
         ┌────────────────────────────────────────────┐
         │                 CODING                      │
         │            (学生编码提交)                     │
         └────────┬───────────────────────┬────────────┘
                  │ WA/RE/TLE            │ AC
                  ▼                      ▼
         ┌────────────────┐     ┌────────────────┐
         │ ERROR_FEEDBACK │     │   AC_REVIEW    │
         │  (错误诊断)    │     │  (AC 后引导)   │
         └────────┬───────┘     └────────┬───────┘
                  │                      │
                  │ 再次提交              ▼
                  └──────►CODING  ┌────────────────┐
                                 │   TRANSFER     │
                                 │  (知识迁移)    │
                                 └────────────────┘
```

### 6.2 Reflection (Producer-Critic)

```
┌──────────────────────────────────────────────────────────┐
│                    Reflection 流程                        │
│                                                          │
│  Producer LLM ──► initialOutput                          │
│       │                                                  │
│       ▼                                                  │
│  ┌──────────────────────────────────────────────────┐    │
│  │               Critic LLM                         │    │
│  │  评估维度（按 CardType 定制）：                     │    │
│  │  ┌─────────────────┐ ┌──────────────────┐       │    │
│  │  │ 事实一致性       │ │ 教学适切性        │       │    │
│  │  └─────────────────┘ └──────────────────┘       │    │
│  │  ┌─────────────────┐ ┌──────────────────┐       │    │
│  │  │ schema 完整性    │ │ 答案泄露检测      │       │    │
│  │  └─────────────────┘ └──────────────────┘       │    │
│  └────────────────────────┬─────────────────────────┘    │
│                           │                              │
│              ┌────────────┼────────────┐                 │
│              ▼            ▼            │                 │
│         pass=true    pass=false        │                 │
│              │            │            │                 │
│              ▼            ▼            │                 │
│         输出原始     Refine LLM        │                 │
│                       (修正)           │                 │
│                           │            │                 │
│                           ▼            │                 │
│                      修正后输出 ────────┘                 │
│                                   (最多 maxRounds 轮)     │
└──────────────────────────────────────────────────────────┘
```

### 6.3 AI 导学事件分发（FSM 主线）

```
┌──────────────────────────────────────────────────────────────┐
│           AITutorWorkflowAdminServiceImpl.processEvent       │
│                   (phase, event) 直接派发                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ERROR_FEEDBACK  → buildErrorDiagnosisPayload                │
│                   + EvidencePack + LearnerProfile + RAG      │
│                   + ReflectionService（强制 critic）         │
│                                                              │
│  SCAFFOLDING     → buildScaffoldPayload                      │
│                   + mastery<0.3→worked, 0.3-0.7→faded,        │
│                     >0.7→hint + Reflection                   │
│                                                              │
│  READING/IDEATING → buildGuidePayload                        │
│                   + 单次 LLM call，无 Reflection              │
│                                                              │
│  TRANSFER/AC_REVIEW → buildTransferPayload                   │
│                   + AC_REVIEW 时启用 Reflection              │
│                                                              │
│  CHAT            → buildChatPayload                          │
│                   + 最轻量，延迟敏感无 Reflection             │
│                                                              │
│  每次派发 → AgentTraceRecorder 写 ai_workflow_event 的 span   │
│  (DISPATCH / EVIDENCE_ASSEMBLY / LLM_CALL / GUARDRAIL ...)   │
│                                                              │
│  备注：早期设计有 Multi-Agent + ReAct（OrchestratorAgent /    │
│  DiagnosticsAgent 等），评估后于 2026-05-03 整包下线，         │
│  收敛回单次 LLM + RAG + Reflection 的稳定管线。              │
└──────────────────────────────────────────────────────────────┘
```

---

## 七、语言包管线架构

### 7.1 初始化流水线

```
管理员上传课件文档
     │
     ▼
┌──────────────────────────────────────────────────────────────┐
│                   初始化管线 (8 阶段)                         │
│                                                              │
│  upload-documents ──► normalize ──► parse-pages               │
│       (上传)           (格式化)      (页面解析+embedding)      │
│                                          │                   │
│                                          ▼                   │
│                                    extract-kcs                │
│                                  (KC 抽取, 自适应               │
│                                   批次 32→16→8→4→2→1)        │
│                                          │                   │
│                                          ▼                   │
│                                   extract-examples            │
│                                    (例题抽取)                  │
│                                          │                   │
│                                          ▼                   │
│                                  generate-problems            │
│                                   (题目生成)                   │
│                                          │                   │
│                                          ▼                   │
│                                  validate-problems            │
│                                 (自动判题验证)                  │
│                                          │                   │
│                                          ▼                   │
│                                      publish                  │
│                                (覆盖率检查 → 发布)              │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 课件问答 (QA)

```
用户提问: "变量是什么？"
     │
     ▼
┌─────────────────┐
│ OJ 解题探测      │──► "这道题怎么做" → 拒答
│ (启发式)        │
└────────┬────────┘
         │ 非 OJ 问题
         ▼
┌─────────────────┐
│ 上下文组装       │◄── 最近 6 条对话
│ ConversationCtx │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│              PageRetrievalService (混合检索)              │
│                                                         │
│  ┌──────────────┐        ┌──────────────┐               │
│  │ 关键词检索    │        │ 向量检索      │               │
│  │ ts_rank_cd   │        │ 1 - cosine   │               │
│  │ (权重 0.65)  │        │ (权重 0.35)  │               │
│  └──────┬───────┘        └──────┬───────┘               │
│         │                       │                       │
│         └───────────┬───────────┘                       │
│                     ▼                                   │
│              ┌──────────────┐                           │
│              │ 合并去重排序  │                           │
│              │ Top 4 pages  │                           │
│              └──────────────┘                           │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────┐
│            AnswerSynthesisService                       │
│                                                        │
│  callForJson (单轮 LLM + 已检索的 page hits)            │
│                                                        │
│  ┌─ QA_GROUNDING_CRITIC_ENABLED? ─────────────┐       │
│  │ true:  Critic LLM 验证 grounding            │       │
│  │        └ grounded=false → 降级拒答           │       │
│  └─────────────────────────────────────────────┘       │
│                         │                              │
│                         ▼                              │
│              ┌──────────────────┐                      │
│              │ GroundedAnswer   │                      │
│              │ ├ answer_markdown│                      │
│              │ ├ citations[]   │                      │
│              │ ├ grounded      │                      │
│              │ └ refusal_reason│                      │
│              └──────────────────┘                      │
└────────────────────────────────────────────────────────┘
```

### 7.3 视频生成 (Beta)

```
Admin 点击 "生成讲解视频"
     │
     ▼
┌──────────────────────────────────────────────────────┐
│              VideoJobService 流水线                    │
│                                                      │
│  1. 校验: grounded=true && citations非空 && admin     │
│                     │                                │
│  2. 创建 job: status=queued                           │
│                     │                                │
│  3. 异步执行 (虚拟线程):                                │
│     ┌───────────────┼───────────────┐                │
│     │               ▼               │                │
│     │  status=planning (10%)        │                │
│     │  读取 question + answer        │                │
│     │  + top 3 citation 页内容       │                │
│     │               │               │                │
│     │               ▼               │                │
│     │  LLM 生成分镜脚本              │                │
│     │  4-7 scene, 45-90秒           │                │
│     │  每 scene 绑定 citation page   │                │
│     │               │               │                │
│     │  status=rendering (50%)       │                │
│     │               │               │                │
│     │               ▼               │                │
│     │  调用外部 TTS + Render API     │                │
│     │  (或 stub 模式跳过)            │                │
│     │               │               │                │
│     │               ▼               │                │
│     │  下载 mp4/poster 到本地        │                │
│     │  SSRF 校验: scheme/host 白名单 │                │
│     │               │               │                │
│     │  status=completed (100%)      │                │
│     └───────────────────────────────┘                │
│                                                      │
│  4. 前端每 5 秒轮询 GET /video-jobs/{id}              │
│                                                      │
│  5. 完成后侧栏切换为视频播放器                          │
└──────────────────────────────────────────────────────┘
```

---

## 八、课堂协作架构

```
┌──────────────────────────────────────────────────────────┐
│                   课堂协作系统                             │
│                                                          │
│  ┌──────────┐     ┌──────────┐     ┌──────────┐         │
│  │ 教师端    │     │ 学生端    │     │ 学生端    │         │
│  │ (浏览器)  │     │ (浏览器)  │     │ (浏览器)  │         │
│  └────┬─────┘     └────┬─────┘     └────┬─────┘         │
│       │ WebSocket       │                │               │
│       ▼                 ▼                ▼               │
│  ┌─────────────────────────────────────────────────┐     │
│  │            WebSocket Layer                       │     │
│  │  ┌──────────────────┐  ┌──────────────────┐     │     │
│  │  │ ClassroomCollab  │  │ ClassroomMonitor │     │     │
│  │  │ WebSocketHandler │  │ WebSocketHandler │     │     │
│  │  │ (协作编程)       │  │ (监控仪表盘)     │     │     │
│  │  └──────────────────┘  └──────────────────┘     │     │
│  └─────────────────────────────────────────────────┘     │
│                         │                                │
│  ┌──────────────────────┼────────────────────────────┐   │
│  │              ClassroomService                      │   │
│  │  ┌─────────┐ ┌──────────┐ ┌───────────┐          │   │
│  │  │ 成员管理 │ │ 作业系统  │ │ AI 出题   │          │   │
│  │  │ 邀请码  │ │ 提交/评分 │ │ 审核/发布  │          │   │
│  │  └─────────┘ └──────────┘ └───────────┘          │   │
│  │  ┌─────────┐ ┌──────────┐ ┌───────────┐          │   │
│  │  │ 课件管理 │ │ 协作会话  │ │ 实时监控  │          │   │
│  │  │ Lesson  │ │ Session  │ │ 编码状态  │          │   │
│  │  └─────────┘ └──────────┘ └───────────┘          │   │
│  └───────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
```

---

## 九、数据库设计

### 9.1 迁移版本一览

```
V1  ─── 初始 schema (user, problem, submission, ...)
V2  ─── 初始数据 (admin 账号, 默认配置)
V3  ─── Judge Server 指标字段对齐
V4  ─── Admin 依赖 bootstrap
V5  ─── 题目读取 schema
V6  ─── 用户权限扩展
V7  ─── 账户/公告/AI 核心
V8  ─── 工作流扩展
V9  ─── 工作流 Admin
V10 ─── 课堂核心
V11 ─── 课堂协作/监控
V12 ─── 课堂 AI 出题
V13 ─── KC 回填 (from tags)
V14 ─── 热点索引
V15 ─── 监控索引优化
V16 ─── pg_stat + Top5 索引
V17 ─── OJ AI 统一核心
V18 ─── 学习记忆 + 相似度
V19 ─── 脚手架渐退
V20 ─── Judge Monitor 表
V21 ─── Judge Monitor v2
V22 ─── 语言包核心
V23 ─── 语言包文档
V24 ─── 语言包页面
V25 ─── 语言包知识
V26 ─── 语言包例题
V27 ─── 语言包题目发布
V28 ─── 笔记本语言字段修复
V29 ─── 语言包强隔离
V30 ─── 课堂单语言包
V31 ─── 语言包 QA
V32 ─── 管线对齐
V33 ─── 管线修复
V34 ─── 阶段扩展
V35 ─── 批次运行
V36 ─── 文档排序
V37 ─── 错误分类统一
V38 ─── 视频任务表
```

### 9.2 核心表关系

```
┌──────────┐     ┌──────────────┐     ┌──────────────┐
│  "user"  │────►│  submission  │────►│   problem    │
│          │     │              │     │              │
│ id       │     │ user_id(FK)  │     │ id           │
│ username │     │ problem_id   │     │ title        │
│ admin_type│    │ result       │     │ description  │
│ email    │     │ code         │     │ test_case_id │
└──────┬───┘     └──────────────┘     └──────────────┘
       │
       │         ┌──────────────────┐
       └────────►│ classroom_member │
                 │ classroom_id(FK) │
                 │ user_id(FK)      │
                 │ role             │
                 └──────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────┐
│                     classroom                         │
│  id │ name │ course_code │ language_pack_id(FK)       │
└──────────────────────────┬───────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────┐
│                   language_pack                       │
│  id │ name │ primary_language │ status                │
├─────────────┬────────────────────────────────────────┤
│             │                                        │
│   ┌─────────▼─────────┐  ┌──────────────────┐       │
│   │ language_pack_     │  │ language_pack_   │       │
│   │ document           │  │ chapter          │       │
│   │ ├ original_filename│  │ ├ title          │       │
│   │ └ sort_order       │  │ └ chapter_index  │       │
│   └─────────┬──────────┘  └────────┬─────────┘       │
│             │                      │                 │
│   ┌─────────▼──────────┐  ┌───────▼─────────┐       │
│   │ language_pack_page │  │ language_pack_kc │       │
│   │ ├ page_no          │  │ ├ canonical_name │       │
│   │ ├ page_text        │  │ └ chapter_id(FK) │       │
│   │ ├ page_embedding   │  └─────────────────┘       │
│   │ └ search_tsv       │                             │
│   └────────────────────┘                             │
└──────────────────────────────────────────────────────┘
```

---

## 十、API 端点规范

### 10.1 响应格式

```json
{
  "error": null,
  "data": { ... }
}
```

错误响应：
```json
{
  "error": "error-code",
  "data": "错误描述文本"
}
```

### 10.2 认证方式

```
┌──────────────────────────────────────────────────┐
│              认证方式                              │
├──────────────┬───────────────────────────────────┤
│ Session      │ Cookie-based (Spring Session)      │
│ CSRF         │ X-CSRFToken header                 │
│ API Key      │ HTTP_APPKEY header                 │
│ Judge Server │ X-Judge-Server-Token (SHA-256)     │
│ SSO          │ Bearer token                       │
└──────────────┴───────────────────────────────────┘
```

---

## 十一、安全架构

```
┌──────────────────────────────────────────────────────────────┐
│                    安全防护层                                 │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ 认证 (Authentication)                                │    │
│  │  • Session-based (Redis)                             │    │
│  │  • CSRF Token                                        │    │
│  │  • TOTP 双因素                                       │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ 授权 (Authorization)                                 │    │
│  │  • admin_type: Regular / Admin / Super Admin         │    │
│  │  • 课堂角色: student / ta / teacher / owner          │    │
│  │  • 服务层鉴权 (非 URL 规则)                           │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ 输入防护                                              │    │
│  │  • SQL: 全部参数化查询 (JdbcTemplate ?)               │    │
│  │  • XSS: Jackson JSON 序列化 (无 HTML 注入)            │    │
│  │  • Path Traversal: 文件名消毒 (Path.getFileName)      │    │
│  │  • SSRF: URL scheme/host 白名单                       │    │
│  │  • IDOR: 资源所有权校验 (userId/classroomId 绑定)      │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ 凭证保护                                              │    │
│  │  • API Key: 环境变量 (.env), 不入日志                  │    │
│  │  • Judge Token: 返回前掩码处理                         │    │
│  │  • Password: BCrypt hash                              │    │
│  └──────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

---

## 十二、部署架构

```
┌──────────────────────────────────────────────────────────────────┐
│                        生产部署拓扑                                │
│                                                                  │
│  ┌──────────────┐     ┌──────────────────────────────────┐      │
│  │   Nginx      │────►│     Spring Boot Application      │      │
│  │   (反向代理)  │     │     (Port 8080, Virtual Threads) │      │
│  │   Port 80/443│     │                                  │      │
│  └──────────────┘     │  ┌──────────────────────────┐    │      │
│                       │  │ WebSocket Handler         │    │      │
│  ┌──────────────┐     │  │ /ws/workflow/*            │    │      │
│  │   Frontend   │     │  │ /ws/classroom/*           │    │      │
│  │   (Vite SPA) │     │  └──────────────────────────┘    │      │
│  │   静态文件    │     └──────────────┬───────────────────┘      │
│  └──────────────┘                    │                           │
│                            ┌─────────┼─────────┐                │
│                            ▼         ▼         ▼                │
│                     ┌───────────┐ ┌─────┐ ┌──────────┐          │
│                     │PostgreSQL │ │Redis│ │File Store│          │
│                     │ (pgvector)│ │     │ │ /deploy/ │          │
│                     │ Port 5432 │ │6379 │ │ data/    │          │
│                     └───────────┘ └─────┘ └──────────┘          │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │                  外部服务                                 │    │
│  │  ┌─────────────┐ ┌──────────────┐ ┌────────────────┐    │    │
│  │  │ MiniMax API │ │ 通义 Embedding│ │ Judge Server   │    │    │
│  │  │ (LLM)       │ │ (向量)       │ │ (判题集群)     │    │    │
│  │  └─────────────┘ └──────────────┘ └────────────────┘    │    │
│  └──────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
```

---

## 十三、配置参考

### 13.1 必须环境变量

```
OPENAI_API_KEY=sk-...           # MiniMax LLM API Key
EMBEDDING_API_KEY=sk-...        # 通义 Embedding API Key
EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
EMBEDDING_MODEL=text-embedding-v4
LLM_MODEL=MiniMax-M2.7
LLM_BASE_URL=https://api.minimaxi.com/v1
JUDGE_SERVER_TOKEN=...          # Judge Server 认证令牌
```

### 13.2 功能开关

```
# AI 推理 / LLM 兼容（Beta）
QA_GROUNDING_CRITIC_ENABLED=false   # QA 启用 grounding critic
LLM_TOOL_USE_PROMPT_FALLBACK=false  # tool-use 回退为 prompt-based

# Video Generation (Beta)
VIDEO_TTS_PROVIDER=stub             # TTS 供应商
VIDEO_RENDER_PROVIDER=stub          # 渲染供应商
# VIDEO_RENDER_API_URL=...          # 外部渲染 API
# VIDEO_RENDER_API_KEY=...          # 外部渲染 API Key
```

### 13.3 application.yml 关键配置

```yaml
server:
  port: 8080
spring:
  threads.virtual.enabled: true
  datasource:
    url: jdbc:postgresql://localhost:5432/alethicode
  flyway:
    enabled: true
    baseline-on-migrate: true
  jackson:
    property-naming-strategy: SNAKE_CASE
alethicode:
  website:
    base-url: http://127.0.0.1
    name: Alethicode
  judge-server:
    token: ${JUDGE_SERVER_TOKEN}
  system:
    test-case-dir: /path/to/test_case
    upload-dir: /path/to/upload
  language-pack:
    storage-root: /path/to/language_pack
```

---

## 十四、开发指南

### 14.1 环境准备

```bash
# 依赖要求
Java 21+
Node.js 20.19+ / npm 10.8+
PostgreSQL 15+ (含 pgvector 扩展)
Redis 7+
```

### 14.2 本地开发

```bash
# 后端
cd backend
cp .env.example .env  # 填入 API Key
mvn clean compile -DskipTests
mvn spring-boot:run

# 前端
cd frontend
npm install
npm run dev           # http://localhost:8080
```

### 14.3 测试

```bash
# 全部测试
cd backend && mvn test

# 单个测试
mvn -Dtest=LlmClientTest test

# 集成测试
mvn -Dtest=LanguagePackInitIntegrationTest test
mvn -Dtest=ClassroomModuleIntegrationTest test
```

### 14.4 代码规范

```
Java:
  类名/文件名    → PascalCase (JudgeServerServiceImpl)
  方法名/变量名  → camelCase  (lastHeartbeat)
  常量           → UPPER_SNAKE_CASE (MAX_RETRY_COUNT)
  包名           → lowercase (com.pytutor.service)
  DB 列名        → snake_case (create_time)

Vue/前端:
  组件文件名     → PascalCase.vue (LanguagePackQaPage.vue)
  变量名/函数名  → camelCase (loadPacks)
  常量           → UPPER_SNAKE_CASE

API:
  路径           → kebab-case (/api/language-pack-qa)
  JSON 字段      → snake_case (answer_markdown)
  响应包装       → ApiResponse<T>
```

---

## 十五、附录

### A. 卡片类型 (CardType)

| 枚举值 | messageType | outputKey | 说明 |
|--------|------------|-----------|------|
| PROBLEM_GUIDE | problem_guide | problem_guide | 审题导学 |
| IDEATE_ANALYSIS | ideate_analysis | ideate | 思路分析 |
| WORKED_EXAMPLE | worked_example | scaffolding | 完整示例 |
| FADED_EXAMPLE | faded_example | scaffolding | 渐退示例 |
| PARSONS_PROBLEM | parsons_problem | scaffolding | 排序题 |
| MINIMAL_HINT | minimal_hint | scaffolding | 最小提示 |
| ERROR_DIAGNOSIS | error_diagnosis | error_diagnosis | 错误诊断 |
| POST_AC | post_ac | post_ac | AC 后引导 |
| TRANSFER_PROBLEM | transfer_problem | transfer | 迁移题 |
| AI_REPLY | ai_reply | chat | 对话回复 |

### B. 评估维度 (EvalDimension)

| 维度 | 中文 | 说明 |
|------|------|------|
| FACTUAL_CORRECTNESS | 事实正确性 | 与 evidence 客观事实一致 |
| PEDAGOGICAL_FIT | 教学适切性 | 匹配初学者水平 |
| SCAFFOLD_LEVEL_MATCH | 脚手架层级匹配 | scaffold 与 mastery 对应 |
| ANSWER_LEAKAGE | 答案泄露 | 是否暴露完整解题代码 |
| GUIDANCE_QUALITY | 引导质量 | 逐步引导而非直接答案 |
| KC_ALIGNMENT | KC 对齐 | related_kcs 与题目知识点一致 |
| COMPREHENSIBILITY | 可理解性 | 语言清晰初学者能懂 |
| ENCOURAGEMENT | 鼓励性 | 包含合适情感支持 |

### C. RAG 检索（前置 retrieval，已替代旧 ReAct 工具调用循环）

| 服务 | 作用 | 适用场景 |
|------|------|---------|
| `CoursewareContextProvider` | 按 `@courseware:<lpId>` token 拉取 RAG top-k chunks | AI 导学对话 + 错误诊断 |
| `SimilarErrorRetrievalService` | pgvector 相似错例检索 | ERROR_FEEDBACK |
| `LanguagePackQaService.PageRetrieval` | 语言包页面混合检索 | QA |
| `LearnerProfileProjector` + `MasteryService` | 学生历史提交画像 | ERROR_FEEDBACK / SCAFFOLDING |

---

---

## 十六、Agent + Harness 工程路线图

Agent + Harness 工程计划的完整文档位于 [`docs/todos/todo-agent-harness/`](./docs/todos/todo-agent-harness/) 目录。

| 文档 | 内容 |
|------|------|
| [README.md](./docs/todos/todo-agent-harness/README.md) | 全局路线图、范围声明、术语表、Spring AI 迁移策略 |
| [phase-0-5-spring-ai-baseline.md](./docs/todos/todo-agent-harness/phase-0-5-spring-ai-baseline.md) | Phase 0.5：Spring AI 试点基线 |
| [phase-1-context-memory.md](./docs/todos/todo-agent-harness/phase-1-context-memory.md) | Phase 1：Context Layering 与 Memory 升级 |
| [phase-2-rag-harness.md](./docs/todos/todo-agent-harness/phase-2-rag-harness.md) | Phase 2：RAG 治理与 QA Harness 升级 |
| [phase-3-tools-trace-rollout.md](./docs/todos/todo-agent-harness/phase-3-tools-trace-rollout.md) | Phase 3：ToolContext 与工具治理 |
| [phase-4-hitl-and-agent-runtime.md](./docs/todos/todo-agent-harness/phase-4-hitl-and-agent-runtime.md) | Phase 4 + 5：Harness 主体闭环与 HITL |
| [progress.md](./docs/todos/todo-agent-harness/progress.md) | 实施进度追踪 |

---

> **文档结束**  
> 本文档由系统自动生成，基于源代码静态分析。  
> 如有疑问请联系项目负责人。
