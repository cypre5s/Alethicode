# Alethicode Vision: One-Click Course Engine

> 上传一本教材，30 分钟得到一门完整的自适应编程课程。
> 教师从"出题人"变成"审核人"，学生从"被动做题"变成"被引导学习"。

---

## 核心理念

当前所有编程教育产品的底层假设是：**教师出题 → 学生做题 → AI 辅导**。

Alethicode 打破这个三角：**教材 → AI 生成课程 → 学生自适应学习 → 教师观察洞察**。

这不是一个功能，而是一个平台范式转换——从"OJ + AI 辅导"到"AI 自动课程引擎"。

### 为什么只有 Alethicode 能做这件事

| 能力 | Alethicode 现状 | 竞品 |
|---|---|---|
| 教材 → 知识点抽取 | 已有（KC extraction pipeline） | OpenMAIC 有，但无 OJ |
| 知识点 → OJ 题目生成 | 已有（ProblemGenerationService） | 无 |
| 题目质量验证（Judge） | 已有（Judge 自动执行标准答案验证） | 无 |
| 知识状态追踪（BKT/NFK） | 已有（MasteryService） | Khan 有 BKT，但无 OJ |
| 自适应推题 | 已有（BeginnerSupplementPlannerService） | 部分 |
| 多 Agent 导学 | 已有（5 Agent） | OpenMAIC 有，但无代码执行 |

结论：**没有任何竞品同时拥有"教材→题目→Judge验证→知识追踪→自适应→AI导学"的完整闭环。**

---

## 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                  One-Click Course Engine                 │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐          │
│  │ Document  │    │   KC     │    │ Problem  │          │
│  │ Ingestion │───▶│ Graph    │───▶│ Factory  │          │
│  │ Pipeline  │    │ Builder  │    │          │          │
│  └──────────┘    └──────────┘    └─────┬────┘          │
│       │               │               │                 │
│       ▼               ▼               ▼                 │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐          │
│  │ Courseware│    │Prerequis.│    │  Judge   │          │
│  │ RAG Index│    │ Detector │    │ Verifier │          │
│  └──────────┘    └──────────┘    └──────────┘          │
│                                                         │
├─────────────────────────────────────────────────────────┤
│                  Adaptive Learning Engine                │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐          │
│  │ Mastery  │    │ Path     │    │ Problem  │          │
│  │ Tracker  │◀──▶│ Optimizer│───▶│ Selector │          │
│  │ (BKT/NFK)│    │          │    │          │          │
│  └──────────┘    └──────────┘    └──────────┘          │
│       │               │               │                 │
│       ▼               ▼               ▼                 │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐          │
│  │ Learner  │    │ Transfer │    │   Agent  │          │
│  │ Memory   │    │ Verifier │    │ Pipeline │          │
│  │ (Vector) │    │ (Judge)  │    │ (5 Agent)│          │
│  └──────────┘    └──────────┘    └──────────┘          │
│                                                         │
├─────────────────────────────────────────────────────────┤
│                  Teacher Intelligence                    │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐          │
│  │ Class    │    │ Error    │    │Intervene │          │
│  │ Mastery  │    │ Pattern  │    │ Effect   │          │
│  │ Heatmap  │    │ Ranking  │    │ Tracker  │          │
│  └──────────┘    └──────────┘    └──────────┘          │
│                                                         │
├─────────────────────────────────────────────────────────┤
│                  MCP Protocol Layer                      │
├─────────────────────────────────────────────────────────┤
│  submit_code │ get_profile │ search_courseware │ ...     │
└─────────────────────────────────────────────────────────┘
```

---

## 三层引擎详解

### Layer 1: Course Compiler（已有 80%，需补 20%）

**已有**：
- Document Ingestion Pipeline（PDF/PPT 解析 → 标准化）
- KC Extraction（从课件抽取知识点和示例）
- Problem Factory（从示例生成 OJ 题目）
- Judge Verifier（标准答案自动验证）
- Courseware RAG Index（课件向量检索）

**需补**：
- **KC Prerequisite Detector** — 自动检测知识点之间的前置依赖关系（例如"for 循环"依赖"变量"和"条件判断"）。当前 KC 是扁平列表，没有依赖图。
  - 技术方案：LLM 对每对 KC 判定"A 是否是 B 的前置条件"，构建有向无环图
  - 数据落表：`language_pack_kc_prerequisite`（已有表结构，但目前空数据）

- **Difficulty Calibration** — 自动标定每道题的难度等级。当前题目没有难度属性。
  - 技术方案：基于学生做题数据（首次 AC 率、平均提交次数）自动校准，无需人工标注
  - 冷启动：LLM 预估初始难度，后续用真实数据校准

### Layer 2: Adaptive Learning Engine（已有 60%，需补 40%）

**已有**：
- Mastery Tracker（BKT + 可选 NFK，per-KC mastery value）
- Learner Memory（向量嵌入 + 衰减 + 语义去重）
- Agent Pipeline（5 个角色化 Agent）
- Supplement Planner（基于薄弱 KC 推荐补充练习）

**需补**：
- **Path Optimizer** — 根据 KC 依赖图 + 当前掌握度，计算"最优下一步"。
  - 核心算法：拓扑排序 + 掌握度加权，优先推送"前置条件已满足但掌握度最低"的 KC
  - 输出：一个有序的"学习路径"，前端展示为进度条/路线图

- **Problem Selector** — 在某个 KC 下选择最合适的题目。
  - 选题策略：优先选"难度接近学生能力边界"的题（ZPD - 最近发展区理论）
  - 基于 BKT mastery 动态调整：mastery < 0.3 选简单题，0.3-0.7 选中等，> 0.7 推变式题

- **Transfer Verifier** — 学生 AC 一道题后，自动生成变式题验证知识迁移。
  - 当前 TransferAgent 已有这个能力，但变式题是 LLM 生成的纯文本，没有 Judge 验证
  - 创新点：变式题也通过 Judge 验证，确保题目正确性（这是 Alethicode 的独家能力）

- **Proactive Intervention** — AI 不等学生提交就主动干预。
  - 基于 Code Snapshot（前端已有 `codeSnapshot` API），AI 分析学生正在写的代码
  - 当检测到"明显的方向性错误"时（例如完全没用 for 循环但题目需要循环），主动弹出提示
  - 注意：不是每行都提示（那太烦），而是只在"战略性错误"时介入

### Layer 3: Teacher Intelligence（已有 20%，需补 80%）

**已有**：
- AI 助教工作台（运维级面板）
- Agent Trace 回放
- Prompt Variant ELO 对比

**需补**：
- **Class Mastery Heatmap** — 横轴 KC、纵轴学生、颜色 = mastery
- **Error Pattern Ranking** — 按 KC 分组的高频错误排行
- **Intervention Effect Tracker** — AI 介入前后 AC 率对比
- **Course Progress Dashboard** — 每个学生在"学习路径"上的当前位置
- **Content Gap Alert** — 当大量学生在某个 KC 上 mastery 偏低，系统自动提醒教师"这个知识点需要加强讲解"

---

## 对外叙事

### 30 秒 Elevator Pitch

"Alethicode 是 AI 自动课程引擎。老师上传一本 Python 教材，30 分钟后得到一门完整的自适应编程课程——每道题都由真实 Judge 验证过正确性，AI 根据每个学生的掌握水平自动推荐下一道题，5 个角色化 AI 助教分别负责审题、诊断、反思、迁移和对话。教师只需要看热力图就知道班上哪些学生在哪些知识点卡住了。"

### 技术标签

- "One-Click Course Compiler" — 一键生成自适应编程课程
- "Judge-Verified Problem Generation" — 每道生成的题目都有 Judge 验证的标准答案
- "BKT/NFK Knowledge Tracing" — 贝叶斯知识追踪 + 神经遗忘核
- "Multi-Agent Tutoring Pipeline" — 5 Agent 角色化导学
- "Proactive Intervention" — AI 主动干预，不等提交就引导
- "MCP-Native Education Infrastructure" — 首个支持 MCP 协议的编程教育平台

### 竞品差异化一句话

- vs OpenMAIC："他们生成课堂，我们生成课程+题目+判题+自适应全链路"
- vs Khan Academy："他们用 BKT 追踪掌握度，我们用 BKT 驱动自动出题+自动判题"
- vs Replit："他们执行代码，我们执行+判对错+诊断原因+推荐下一题"
- vs HUSTOJ："他们是 OJ，我们是 OJ + AI 自动课程引擎"

---

## 实施路线图

### Phase A: KC 依赖图 + 学习路径（1-2 周）
- 补充 KC Prerequisite Detector
- 实现 Path Optimizer（拓扑排序 + mastery 加权）
- 前端"学习路线图"组件

### Phase B: 智能选题 + 知识迁移验证（2-3 周）
- 实现 Problem Selector（ZPD 理论）
- Transfer Verifier：变式题 Judge 验证
- Difficulty Calibration：基于真实数据校准题目难度

### Phase C: 教师 Intelligence 面板（1-2 周）
- Class Mastery Heatmap
- Error Pattern Ranking
- Intervention Effect Tracker

### Phase D: 主动干预引擎（2-3 周）
- Code Snapshot 实时分析
- 战略性错误检测模型
- 前端主动提示 UI

### Phase E: MCP 协议层（2-3 周）
- Spring AI MCP Server
- 6 Tool + 3 Resource
- Claude Desktop 集成测试

---

## 不做的事

- 不做语音交互（TTS/ASR 基础设施投入太大，且非科班初学者更习惯文字）
- 不做实时视频课堂（那是 OpenMAIC 的路线，不是我们的壁垒）
- 不引入外部框架（LangChain/CrewAI），保持自研 Agent 栈的控制权
- 不重建 L1/L2 自主度系统（用 Path Optimizer 取代，更优雅）
