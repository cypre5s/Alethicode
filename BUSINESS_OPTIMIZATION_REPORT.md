# Alethicode 业务精进优化报告

> 审查日期：2026-04-18
> 原则：**不增加新功能**，只精进已有业务流程的质量、效率和用户体验
> 参考：IntelliCode (NeurIPS 2024), A4L (Georgia Tech), FSRS (Anki), SocraticLM, FINER, AI Gem

---

## 一、核心业务流程概览

### 1.1 提交判题流程

```mermaid
flowchart TD
    A[学生提交代码] --> B{限流检查}
    B -->|通过| C{题目类型}
    B -->|拒绝| X1[429 Too Many Requests]
    
    C -->|客观题| D[同步判分]
    C -->|编程题| E[插入 submission<br/>result=6 PENDING]
    
    D --> F[同步统计更新]
    
    E --> G{Redis Streams<br/>已启用?}
    G -->|是| H[发布到 Redis Stream]
    G -->|否| I[提交到线程池]
    
    H --> J[StreamConsumer 消费]
    I --> J
    
    J --> K[runJudgeTask]
    K --> L{选择 Judge Server}
    L -->|心跳正常| M[直接选中]
    L -->|心跳过期| N[并行 ping 所有 candidate]
    N --> M
    M --> O[发送判题请求]
    
    O --> P{判题结果}
    P -->|AC| Q[result=0 + 代码质量评估]
    P -->|WA/CE/TLE/RE| R[result=非0]
    P -->|系统错误| S[result=5]
    
    Q --> T[统计同步<br/>SELECT FOR UPDATE]
    R --> T
    S --> T
    
    T --> U[发布 JudgeCompletedEvent]
    U --> V1[异步: 错题笔记本]
    U --> V2[异步: 复习包记录]
    U --> V3[异步: 数据采集]
    U --> V4[异步: mastery 更新]
    
    Q --> W[异步: CodeQualityAssessment<br/>LLM 代码质量分析]
```

### 1.2 AI Tutor 工作流状态机

```mermaid
stateDiagram-v2
    [*] --> READING: 进入题目页
    READING --> IDEATING: IDEATING 事件
    IDEATING --> CODING: CODING 事件
    CODING --> SUBMITTED: SUBMIT 事件
    SUBMITTED --> POST_AC: AC 判题通过
    SUBMITTED --> DIAGNOSING: WA/CE/TLE 判题失败
    DIAGNOSING --> CODING: 继续编码
    POST_AC --> REVIEWING: 进入复习
    POST_AC --> TRANSFER: 迁移变式
    REVIEWING --> [*]
    TRANSFER --> [*]
    
    note right of READING
        GuideAgent 审题引导
        课件关联检索
    end note
    
    note right of IDEATING
        IdeateAgent 思路分析
        骨架代码生成
    end note
    
    note right of DIAGNOSING
        DiagnosticsAgent 错误诊断
        misconception 追踪
    end note
    
    note right of POST_AC
        MetacognitiveAgent 总结反思
        代码质量评估展示
    end note
    
    note right of TRANSFER
        TransferAgent 变式推荐
        ZPD 选题
    end note
```

### 1.3 课件处理流水线

```mermaid
flowchart LR
    A[上传课件文件<br/>PDF/PPTX/DOCX] --> B[文档归一化<br/>DocumentNormalization]
    B --> C[文档解析<br/>DocumentParsing]
    C --> D[页面文本提取<br/>Python 脚本]
    D --> E[KC 知识点抽取<br/>LLM]
    E --> F[课件示例提取<br/>LLM]
    F --> G[题目生成<br/>LLM]
    G --> H[Judge 验证<br/>标准答案判题]
    H --> I[题目发布<br/>写入 problem 表]
    
    style B fill:#4CAF50,color:#fff
    style C fill:#4CAF50,color:#fff
    style D fill:#2196F3,color:#fff
    style E fill:#FF9800,color:#fff
    style F fill:#FF9800,color:#fff
    style G fill:#FF9800,color:#fff
    style H fill:#f44336,color:#fff
    style I fill:#9C27B0,color:#fff
```

### 1.4 学习者画像构建瀑布图

```mermaid
gantt
    title 学习者画像数据源与更新时机
    dateFormat X
    axisFormat %s

    section 实时更新
    提交判题 mastery EMA          :done, a1, 0, 1
    错题笔记本自动写入              :done, a2, 1, 2
    misconception 追踪             :done, a3, 2, 3
    frustration 事件采集            :done, a4, 3, 4

    section 工作流驱动
    AI Agent 对话上下文             :active, b1, 0, 2
    LearnerState 投射               :active, b2, 2, 4
    LearningTwin 数字孪生           :active, b3, 4, 5

    section 离线/周期
    KC Graph 依赖传播              :crit, c1, 0, 3
    NFK 知识追踪模型推理            :crit, c2, 3, 5
    Ebbinghaus 间隔复习调度         :crit, c3, 5, 6
    周报摘要生成                    :crit, c4, 6, 7
```

---

## 二、与业界最佳实践的对标分析

### 2.1 Alethicode vs IntelliCode（NeurIPS 2024）

| 维度 | IntelliCode | Alethicode 现状 | 差距 |
|------|------------|----------------|------|
| Learner State | Centralized, versioned, single-writer | 分散在多表，各 Agent 独立读写 | ⚠️ 缺少版本化 |
| Graduated Hints | 5 级：Metacognitive → Conceptual → Analogical → Structural → Near-solution | GuideAgent 单级提示 | ⚠️ 缺少梯度 |
| Mastery Update | POMDP + reward function | EMA α=0.7 | ⚠️ 公式过简 |
| Spaced Repetition | SM-2 集成 | Ebbinghaus 间隔（数据库存在但调度被动） | ⚠️ 调度不主动 |
| Agent Orchestration | StateGraph, single-writer policy | OrchestratorAgent, 无写冲突保护 | ⚠️ 无审计 |

### 2.2 Alethicode vs FSRS（Anki 新一代算法）

| 维度 | FSRS | Alethicode 现状 |
|------|------|----------------|
| 间隔算法 | 基于遗忘曲线拟合，自适应 stability/difficulty | 固定间隔（无算法） |
| 个性化 | 每个用户每个 KC 独立参数 | 全局统一 |
| 输入信号 | 用户自评（Again/Hard/Good/Easy） | 仅 AC/WA 二元信号 |

### 2.3 Alethicode vs A4L 数据架构（Georgia Tech）

| 维度 | A4L | Alethicode 现状 |
|------|-----|----------------|
| 数据标准 | Caliper/xAPI 学习事件标准 | 自定义 JSON 格式 |
| 数据管线 | 异步流式摄入 + Schema 验证 | 同步写入 PostgreSQL |
| 隐私 | 内置匿名化层 | 无匿名化 |
| 可视化 | 多角色仪表板（学生/教师/研究者） | 教师仪表板（部分） |

---

## 三、优化建议（按业界最佳实践排序）

### OPT-01: Graduated Hints 梯度提示（对标 IntelliCode 5 级提示）

**现状**：`GuideAgent` 生成单级提示，不管学生的掌握度和困难程度，提示的详细程度都一样。

```mermaid
flowchart TD
    subgraph 现状
        A1[学生请求帮助] --> B1[GuideAgent: 生成一段提示]
    end
    
    subgraph 优化后
        A2[学生请求帮助] --> B2{评估掌握度 p̂}
        B2 -->|p̂ > 0.7| C2[Level 0 元认知<br/>你觉得哪里卡住了？]
        B2 -->|0.5 < p̂ ≤ 0.7| D2[Level 1 概念<br/>这道题需要用到哪种数据结构？]
        B2 -->|0.3 < p̂ ≤ 0.5| E2[Level 2 类比<br/>想象你在整理扑克牌...]
        B2 -->|0.1 < p̂ ≤ 0.3| F2[Level 3 结构<br/>你的代码缺少对空列表的边界检查]
        B2 -->|p̂ ≤ 0.1| G2[Level 4 近答案<br/>试试在 for 循环里加 if len > 0]
    end
```

**实施**：在 `GuideAgent.execute()` 中根据 `LearnerState.masteryByKc` 选择提示级别，修改 system prompt 模板加入 `hint_level` 参数。不需要新建 Agent，只需修改 prompt 策略。

---

### OPT-02: 判题结果 WebSocket 实时推送（消除轮询）

**现状**：前端 `setInterval` 轮询 ~500ms。

```mermaid
sequenceDiagram
    participant F as 前端
    participant B as 后端
    participant J as Judge Server
    
    rect rgb(255, 230, 230)
        Note over F,B: 现状：轮询
        F->>B: POST /api/submission
        B-->>F: {submission_id}
        loop 轮询 (500ms × N)
            F->>B: GET /api/submission?id=abc
            B-->>F: {result: 6} (Pending)
        end
        J->>B: 判题完成
        F->>B: GET /api/submission?id=abc
        B-->>F: {result: 0} (AC)
    end
    
    rect rgb(230, 255, 230)
        Note over F,B: 优化后：WebSocket 推送
        F->>B: WS /ws/workflow (已有)
        F->>B: POST /api/submission
        B-->>F: {submission_id}
        J->>B: 判题完成
        B->>F: WS push {type: judge_completed}
    end
```

**收益**：减少每次提交 10-20 次无效 HTTP 请求。已有 `WorkflowRealtimeSupport` 基础设施，只需在 `JudgeCompletedEventListener` 中加一行 WS 广播。

---

### OPT-03: EMA mastery 升级为 FSRS 思想（对标 Anki FSRS）

**现状**：

```
mastery_new = 0.7 × outcome + 0.3 × mastery_old
```

**问题**：
- 固定 α=0.7 导致单次 WA 惩罚过重（0.8 → 0.56，骤降 30%）
- 无稳定性/难度概念，做 10 次 AC 的 KC 和做 1 次 AC 的 KC mastery 相同
- 首次做题 mastery 波动极大

**优化后（FSRS 思想简化版）**：

```
stability = stability_old × (1 + α × (outcome - mastery_old))
difficulty = difficulty_old × (1 - β × (outcome - 0.5))
mastery_new = 1 - e^(-t / stability)
```

```mermaid
flowchart TD
    A[提交判题结果] --> B{AC or WA}
    B -->|AC| C[stability ↑<br/>difficulty ↓]
    B -->|WA| D[stability ↓<br/>difficulty ↑]
    C --> E[mastery = 1 - e^{-t/stability}]
    D --> E
    E --> F[更新 learner_kc_mastery 表]
    F --> G[间隔复习调度:<br/>next_review = stability × desired_retention]
```

**实施范围**：只修改 `LearnerMasteryServiceUnified.updateMastery()` 的 SQL 公式，`learner_kc_mastery` 表加 `stability` 和 `difficulty` 两个字段。

---

### OPT-04: 错题笔记本 evidence 数组化（保留错误频率）

**现状**：同一题同一 `error_taxonomy` 只保留最后一条 evidence（覆盖更新）。

**优化**：`evidence_ptr` 从单条 JSON 改为 JSON 数组（最多 5 条），每次 append。

```mermaid
flowchart LR
    subgraph 现状
        A1[第1次 WA: syntax_error] --> B1[evidence: 记录1]
        B1 --> C1[第2次 WA: syntax_error]
        C1 --> D1[evidence: 记录2<br/>记录1 丢失]
    end
    
    subgraph 优化后
        A2[第1次 WA: syntax_error] --> B2["evidence: [记录1]"]
        B2 --> C2[第2次 WA: syntax_error]
        C2 --> D2["evidence: [记录1, 记录2]<br/>频率: 2次"]
    end
```

---

### OPT-05: Agent 对话上下文复用（对标 IntelliCode dual-memory）

**现状**：每次 Agent dispatch 都完整执行 `LearnerProfileProjector`（5 条 SQL）+ `EvidencePackAssembler`（3 条 SQL）。

```mermaid
flowchart TD
    subgraph "每次 dispatch（现状）"
        A[用户事件] --> B[LearnerProfileProjector<br/>5x SQL]
        B --> C[EvidencePackAssembler<br/>3x SQL]
        C --> D[LLM 调用<br/>2-8s]
    end
    
    subgraph "优化后"
        A2[用户事件] --> B2{session 缓存<br/>LearnerState?}
        B2 -->|命中| C2[直接使用缓存<br/>跳过 8x SQL]
        B2 -->|未命中| D2[完整构建 + 缓存 30s]
        C2 --> E2[LLM 调用]
        D2 --> E2
    end
```

**实施**：在 `AITutorWorkflowAdminServiceImpl` 的 session context 中缓存最近一次 `LearnerState`，设置 30 秒 TTL。同一次做题过程中学生画像变化不大。

---

### OPT-06: 课件处理流水线幂等性

**现状**：任何阶段失败都需要从头开始。

```mermaid
flowchart TD
    subgraph "现状"
        A1[开始解析] --> B1[文档1 ✅]
        B1 --> C1[文档2 ✅]
        C1 --> D1[文档3 ❌ 失败]
        D1 --> E1[整个任务 failed<br/>需要重新上传全部]
    end
    
    subgraph "优化后"
        A2[开始/重试] --> B2{文档1<br/>parse_status?}
        B2 -->|parsed| C2[跳过]
        B2 -->|pending/failed| D2[重新解析]
        C2 --> E2{文档2?}
        D2 --> E2
        E2 -->|parsed| F2[跳过]
        E2 -->|pending| G2[解析]
        F2 --> H2{文档3?}
        G2 --> H2
        H2 -->|failed| I2[重新解析]
        I2 --> J2[只处理失败的文档]
    end
```

---

### OPT-07: 间隔复习主动提醒（对标 FSRS + IntelliCode spaced repetition）

**现状**：间隔复习只有用户主动点击时才触发。

**优化**：
- 用户进入题目页时，`AITutorWelcomeService` 检查该题关联的 KC 是否有到期复习项
- 如有，在 welcome 消息中注入复习提醒
- 利用已有的 `reviewDue` 查询逻辑，在 welcome 流程中复用

```mermaid
flowchart TD
    A[用户打开题目页] --> B[AITutorWelcomeService]
    B --> C{该题 KC 有<br/>到期复习?}
    C -->|是| D["welcome: 这个知识点上次犯过<br/>syntax_error，建议先看看错题笔记"]
    C -->|否| E["welcome: 标准欢迎消息"]
```

---

## 四、数据流全景图

```mermaid
flowchart TB
    subgraph 用户交互层
        IDE[代码编辑器]
        CHAT[AI 对话面板]
        NOTEBOOK[错题笔记本]
        RADAR[技能雷达]
        PATH[学习路线图]
    end
    
    subgraph 业务逻辑层
        SUB[提交服务]
        JUDGE[判题调度]
        WORKFLOW[AI 工作流<br/>OrchestratorAgent]
        PROFILE[学习者画像<br/>LearnerProfileProjector]
        LP[课件处理]
        REVIEW[间隔复习]
    end
    
    subgraph Agent 层
        GUIDE[GuideAgent<br/>审题引导]
        DIAG[DiagnosticsAgent<br/>错误诊断]
        META[MetacognitiveAgent<br/>总结反思]
        TRANS[TransferAgent<br/>变式推荐]
    end
    
    subgraph 数据层
        DB[(PostgreSQL)]
        REDIS[(Redis)]
        LLM[LLM Provider]
        JUDGE_SVR[Judge Server]
    end
    
    IDE -->|提交代码| SUB
    SUB -->|写入| DB
    SUB -->|发布事件| REDIS
    REDIS -->|消费| JUDGE
    JUDGE -->|判题| JUDGE_SVR
    JUDGE -->|JudgeCompletedEvent| PROFILE
    
    CHAT --> WORKFLOW
    WORKFLOW --> GUIDE
    WORKFLOW --> DIAG
    WORKFLOW --> META
    WORKFLOW --> TRANS
    GUIDE -->|LLM| LLM
    DIAG -->|LLM| LLM
    
    PROFILE -->|mastery| WORKFLOW
    LP -->|KC/题目| DB
    REVIEW -->|复习调度| DB
    
    DB --> RADAR
    DB --> NOTEBOOK
    DB --> PATH
```

---

## 五、优化优先级矩阵

```mermaid
quadrantChart
    title 优化项优先级（影响 vs 难度）
    x-axis 低实施难度 --> 高实施难度
    y-axis 低业务影响 --> 高业务影响
    quadrant-1 立即实施
    quadrant-2 规划实施
    quadrant-3 可选优化
    quadrant-4 长期改进
    OPT-01 Graduated Hints: [0.35, 0.9]
    OPT-02 WS推送: [0.25, 0.75]
    OPT-03 FSRS mastery: [0.45, 0.85]
    OPT-04 Evidence数组: [0.15, 0.5]
    OPT-05 Context缓存: [0.4, 0.6]
    OPT-06 幂等流水线: [0.55, 0.45]
    OPT-07 复习提醒: [0.2, 0.55]
```

| 顺序 | 编号 | 优化项 | 影响面 | 难度 | 对标 |
|------|------|--------|--------|------|------|
| ⭐1 | OPT-01 | Graduated Hints 梯度提示 | 全用户 | 中 | IntelliCode 5-level hints |
| ⭐2 | OPT-02 | 判题结果 WS 推送 | 全用户 | 低 | 通用最佳实践 |
| ⭐3 | OPT-03 | FSRS mastery 公式 | 全用户 | 中 | Anki FSRS / IntelliCode POMDP |
| 4 | OPT-04 | Evidence 数组化 | 全用户 | 低 | — |
| 5 | OPT-07 | 间隔复习主动提醒 | 全用户 | 低 | IntelliCode spaced repetition |
| 6 | OPT-05 | Agent Context 缓存 | AI 用户 | 中 | IntelliCode dual-memory |
| 7 | OPT-06 | 课件流水线幂等 | 教师 | 中 | A4L 数据管线 |

---

## 六、实施建议

### Phase 1：快速见效（1-2 天）
- OPT-02：WS 推送（在 `JudgeCompletedEventListener` 加 WS 广播）
- OPT-04：evidence 数组化（改 SQL 的 `evidence_ptr` 更新逻辑）
- OPT-07：welcome 复习提醒（`AITutorWelcomeService` 加 reviewDue 查询）

### Phase 2：核心提升（3-5 天）
- OPT-01：Graduated Hints（改 GuideAgent prompt + mastery 分级逻辑）
- OPT-03：FSRS mastery（`learner_kc_mastery` 加字段 + 改 updateMastery SQL）

### Phase 3：性能优化（1-2 天）
- OPT-05：Agent Context 缓存（session-level LearnerState cache）
- OPT-06：课件幂等性（per-document parse_status tracking）
