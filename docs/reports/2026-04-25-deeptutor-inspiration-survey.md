# DeepTutor 启发调研报告

> **报告编号**：ALETH-RPT-2026-0425-001
> **调研对象**：[HKUDS/DeepTutor](https://github.com/HKUDS/DeepTutor)（v1.2.4，21k stars，Apache-2.0）
> **调研者**：AI Coding Assistant
> **调研日期**：2026-04-25
> **目标受众**：Alethicode 产品 / 架构 / 教学方向决策者
> **结论简述**：**不建议代码移植**；**建议以"概念移植 + 增量补强"方式吸收 3 项能力**：Persistent Memory（学情画像层）、Visualize Capability（教学可视化）、Unified Chat（多模式共享上下文）。

---

## 目录

- [一、调研动机与方法论](#一调研动机与方法论)
- [二、DeepTutor 项目轮廓](#二deeptutor-项目轮廓)
- [三、Alethicode 当前状态盘点](#三alethicode-当前状态盘点)
- [四、横向能力对照矩阵](#四横向能力对照矩阵)
- [五、可借鉴点深度评估](#五可借鉴点深度评估)
- [六、不可借鉴 / 不建议引入的点](#六不可借鉴--不建议引入的点)
- [七、推荐落地优先级](#七推荐落地优先级)
- [八、与现有 Roadmap 的对齐](#八与现有-roadmap-的对齐)
- [九、风险评估与边界](#九风险评估与边界)
- [十、决策建议](#十决策建议)
- [附录 A：调研中阅读的关键文件](#附录-a调研中阅读的关键文件)
- [附录 B：术语对照表](#附录-b术语对照表)

---

## 一、调研动机与方法论

### 1.1 动机

Alethicode 的核心使命是 **"提升非计算机专业 Python 初学者的教学质量与教学体验"**。DeepTutor 是当前 GitHub 上 stars 增速最快的"Agent-Native 个人化学习助手"项目（111 天 20k stars，截至 2026.04.19）。本调研的目的是：

1. 识别 DeepTutor 是否有 Alethicode 当前**未覆盖**的教学能力；
2. 在所有可借鉴点中，**按教学价值 / 实施成本** 排序；
3. 给出**明确的可移植 / 不可移植边界**，避免任何"补丁式 / 兼容式"扩展。

### 1.2 第一性原理约束

按 `AGENTS.md` 的"方案规范"，本报告坚持以下原则：

- 不允许给出兼容性或补丁性的方案；
- 不允许过度设计，保持最短路径；
- 不允许擅自扩展用户未明确提出的目标；
- 必须确保方案的逻辑正确，并经过全链路逻辑验证；
- 不写防御性逻辑，failfast；
- 优先给出满足目标的最小完整方案。

因此，本报告对每个借鉴点都会给出 **"为什么做 / 不做"** 的第一性论证，而非简单的功能对比。

### 1.3 方法论

调研按以下 5 步进行：

| 步骤 | 内容 |
|---|---|
| 1 | 阅读 DeepTutor 官方 README（中英文 v1.2.4），提取关键 Feature、Release Note、Architecture |
| 2 | 通读 Alethicode 顶层架构（`PROJECT.md`、`AGENTS.md`、`services/tutor-graph/README.md`、`research/nfk/README.md`） |
| 3 | 抽样阅读 Alethicode 关键代码：`LearnerMemoryService` / `LearnerProfileProjector` / `AITutorWelcomeService` / `CrossCourseProfileService` / `LearningStyle` / `tutor_graph` 节点（reading / chat / diagnosis / evidence）/ `CardType` / `AiModelGateway` / `UnifiedAgentPanel.vue` |
| 4 | 检查 `docs/todos/` 已完成与未完成的待办，避免推荐与历史决策冲突 |
| 5 | 按"教学杠杆 × 实施成本"打分，输出落地优先级 |

---

## 二、DeepTutor 项目轮廓

### 2.1 基本信息

| 属性 | 值 |
|---|---|
| 仓库 | https://github.com/HKUDS/DeepTutor |
| 维护方 | 港大 HKUDS 实验室 |
| 当前版本 | v1.2.4（2026-04-25） |
| 协议 | Apache-2.0 |
| Stars | 21,617 |
| 主语言 | Python 3.11+ |
| 前端 | Next.js 16 / React 19 |
| 后端 | FastAPI |
| 部署 | Docker / GHCR pre-built / `python scripts/start_web.py` 一键 |
| 体量 | v1.0 重写 ~200k 行 |

### 2.2 七大核心能力

DeepTutor 在 README 中明确列出 7 项核心能力：

| # | 能力 | 一句话说明 |
|---|---|---|
| 1 | **Unified Chat Workspace** | 6 模式（Chat / Deep Solve / Quiz / Deep Research / Math Animator / Visualize）共享同一对话上下文 |
| 2 | **AI Co-Writer** | 多文档 Markdown 协作写作，AI 是 first-class collaborator（Rewrite / Expand / Shorten） |
| 3 | **Book Engine** | 多 Agent 编译"活书"，14 种 block（quiz / flash card / timeline / concept graph / interactive demo …） |
| 4 | **Knowledge Hub** | RAG 知识库 + Notebook + Question Bank + Skills（`SKILL.md` 注入式教学人格） |
| 5 | **Persistent Memory** | Summary（学习摘要） + Profile（学习者身份），跨所有 Feature & TutorBot 共享 |
| 6 | **Personal TutorBots** | 基于 [nanobot](https://github.com/HKUDS/nanobot) 的多实例自主导师，独立 workspace / memory / personality，主动 Heartbeat |
| 7 | **Agent-Native CLI** | 所有能力 CLI 一键可达；提供 `SKILL.md` 给任意 LLM Agent 自主操作 |

### 2.3 架构关键观察

- **Tools + Capabilities 解耦**：每种 Capability（chat / quiz / deep_solve …）可任意组合 Tools（RAG / web search / code exec / reason …）；
- **多 LLM Provider Registry**：30+ 主流 Provider（OpenAI / Anthropic / DeepSeek / Gemini / Ollama / vLLM / LM Studio / 国内全家桶）；
- **CLI / Web 双界面**：CLI 输出支持人类（rich）与 Agent（structured JSON）；
- **配置外置**：`agents.yaml` + `.env` + 运行时 hot reload；
- **TutorBot 多通道**：Telegram / Discord / Slack / 飞书 / 企微 / 钉钉 / Email。

### 2.4 关键非能力

DeepTutor **没有** 的能力（Alethicode 已经具备）：

- 真实代码评测（Judge / Submission / Sandbox）；
- 知识追踪（KT，Alethicode 的 nfk）；
- 教师管理端 / 多人课堂 / 课件管线；
- 业务数据库（PostgreSQL 业务表，DeepTutor 主要靠文件 + 向量库）；
- 中国教育合规相关（Alethicode 有 ADR-0004）。

---

## 三、Alethicode 当前状态盘点

### 3.1 顶层指标（来自 `PROJECT.md`）

| 维度 | 数量 |
|---|---|
| 后端 Java 文件 | 299 |
| 前端 Vue 组件 | 96 |
| 前端 JS 模块 | 72 |
| 数据库 Flyway 迁移 | 38 |
| REST 端点 | 150+（30+ Controller） |
| AI 卡片类型 | 8 |
| 工作流阶段 | 7 Phase（FSM） |
| 教学 Agent | 5 角色 |
| 评估维度 | 8 LLM-as-Judge |
| 支持语言 | Python3 / C / C++ / Java |

### 3.2 与 DeepTutor 重叠/相关的能力

#### 3.2.1 Memory / Profile 体系（已成熟）

通过阅读以下文件确认：

- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerMemoryService.java`（633 行）
- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerProfileProjector.java`（327 行）
- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerState.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearningStyle.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/CrossCourseProfileService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/AITutorWelcomeService.java`
- `docs/todos/todo-long-term-learning-memory.md`（**已完成 @2026-03-29**）

已具备：

| 能力 | 实现 |
|---|---|
| 6 类 memory_type | tutor_conclusion / error_pattern / learning_signal / reading_preference / debug_preference / generic（+ teaching_strategy_preference） |
| 语义去重 | cosine distance < 0.15，pgvector |
| Ebbinghaus 衰减 | `strength = confidence × e^(-λ_eff × days) × (1 + recall_count × 0.2)` |
| 召回 + 加权 | 召回时 `confidence += 0.03`、`last_recalled_at = now()` |
| 跨课程画像 | `CrossCourseProfileService.loadActionBias()` |
| 学习风格推断 | `inferLearningStyle()` 投票 4 种风格 |
| Profile 投影 | `LearnerProfileProjector.project()` 输出 `LearnerState` |
| 教学结论持久化 | `saveTutoringConclusion()` 在 AC_REVIEW 触发 |

#### 3.2.2 执行轨迹可视化（已完成）

通过 `docs/todos/todo-ai-variable-runtime-visualization.md`（已完成 @2026-03-29）确认：

- 已有 `PythonExecutionTraceService` + `SimplePythonTracer`；
- 已有 `execution_trace_explainer` 卡片（`CardType.EXECUTION_TRACE_EXPLAINER`）；
- 学生可在统一 AI 面板查看：输入样例、关键步骤、变量快照、偏离步骤、教学解释；
- 范围：仅 Python3 的顺序 / 分支 / 循环。

#### 3.2.3 Unified Agent Panel（已存在）

`frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`（1847 行）已经是统一面板：

- 8 张卡片渲染在同一容器；
- 包含 Plan 执行卡、Checkpoint 恢复、Approval 审批；
- 支持运行时状态展示（Running / Waiting / Failed）。

#### 3.2.4 8 张 AI 卡片

`backend/src/main/java/com/alethicode/service/aitutor/contract/CardType.java`：

```
PROBLEM_GUIDE         → Nene 导读
IDEATE_ANALYSIS       → 思路分析
FADED_EXAMPLE         → 渐退示例
ERROR_DIAGNOSIS       → Yoshino 纠错
EXECUTION_TRACE_EXPLAINER → 运行轨迹解释
POST_AC               → Kanna 复盘
TRANSFER_PROBLEM      → Murasame 迁移
KNOWLEDGE_REVIEW      → 知识点回顾
AI_REPLY              → 角色聊天
```

#### 3.2.5 Tutor 工作流（LangGraph）

`services/tutor-graph/app/`：

- 7 阶段 FSM：READING / IDEATING / CODING / ERROR_FEEDBACK / AC_REVIEW / TRANSFER / KNOWLEDGE_REVIEW（+ CHAT 旁路）；
- 节点：reading / ideating / coding / diagnosis / ac_review / transfer / chat / knowledge_review / coach_plan；
- evidence 装配：`evidence.py` 按事件类型决定要装载 `workflow_context / learner_state / courseware_hits / similar_errors / diagnosis_evidence`；
- LLM 客户端：`services/tutor-graph/app/clients/llm_client.py`（Java 端为 `AiModelGateway` 接口，含 `callForJson` / `callForJsonCached` / `callForEmbedding` / `callWithTools`）。

### 3.3 已识别的差距（与 DeepTutor 相比）

按"教学价值"逆向排序：

| # | 差距 | 现状 | 影响 |
|---|---|---|---|
| 1 | Memory / LearningStyle **未注入到 tutor_graph 节点 SYSTEM_PROMPT** | 节点 prompt 是写死的字符串，user_prompt 只直接拼 `{learner}` 字典 | LLM 不会稳定使用 LearningStyle，个性化效果打折 |
| 2 | READING 节点 evidence 不要 `learner_state` | `EVENT_EVIDENCE_REQUIREMENTS["READING"] = ["workflow_context", "courseware_hits"]` | Nene 看不到学生底子，导读不够个性化 |
| 3 | 无"自然语言长期摘要" | 现有 memory 都是离散事件 / 错题 / 反思的结构化数据 | LLM 难以快速理解学生过去 30 天学了什么 |
| 4 | 无学生侧画像 dashboard | Profile 仅供 Agent 内部使用 | 学生无法看到 / 纠正自己的画像，AI 误解无法纠偏 |
| 5 | 无教学概念可视化（Mermaid / Chart / SVG） | 仅有执行轨迹可视化（已完成） | 数据结构 / 算法流程 / 复杂度对比无法画 |
| 6 | 8 卡片之间无显式上下文引用机制 | 每张卡片是独立 LLM 调用结果 | 学生不能在 Chat 里引用某张已生成的卡片 |
| 7 | 当前驱动方式是阶段驱动而非用户主动模式 | 学生进哪个 Phase 由 FSM 决定 | 学生不能"我想再来一次思路分析"主动触发 |
| 8 | 教师无班级画像聚合视图 | 跨学生数据散在 `ai_learner_*` 表 | 教师无法基于全班 KC 分布做教学 |

---

## 四、横向能力对照矩阵

> **图例**：✅ 已具备且优秀；🟡 部分具备 / 较弱；❌ 不具备；➖ 不适用 / 主动不做

| 能力维度 | DeepTutor | Alethicode | 差距判定 |
|---|---|---|---|
| **代码评测（Judge / Sandbox）** | ❌ | ✅（多语言 OJ 主线） | DeepTutor 没有，无可借鉴 |
| **知识追踪（KT）** | ❌ | ✅（nfk: CodeBERT + TSK） | DeepTutor 没有，无可借鉴 |
| **教师端 / 课堂 / 课件** | ❌ | ✅（管理端 + WebSocket + language-pack） | DeepTutor 没有，无可借鉴 |
| **多 Agent 编排** | ✅（Capabilities） | ✅（tutor_graph LangGraph 7 阶段 FSM） | 实现思路不同，**不重复造轮子** |
| **RAG / 知识库** | ✅ | ✅（pgvector + courseware_hits） | 双方都有 |
| **Quiz 生成** | ✅（capability） | ✅（KNOWLEDGE_REVIEW + 题库） | 双方都有 |
| **教学角色 / 人格** | ✅（SKILL.md 可注入） | 🟡（5 角色硬编码 + LearningStyle 4 种） | 中等差距，可借鉴 SKILL.md 模式 |
| **Persistent Memory** | ✅（Summary + Profile，自然语言） | 🟡（结构化记忆，缺自然语言摘要） | **重要差距，可补强** |
| **画像 / 学情可视化（学生侧）** | ✅（学生可见） | ❌ | **重要差距** |
| **教学概念可视化（Mermaid/Chart/SVG）** | ✅（Visualize / Math Animator / Mermaid） | 🟡（仅执行轨迹） | **重要差距** |
| **Unified Chat 上下文共享** | ✅（6 模式同一 thread） | 🟡（前端 UnifiedAgentPanel 容器在，跨卡片引用弱） | **中等差距** |
| **多文档 Markdown 协作（Co-Writer）** | ✅ | ❌ | ➖ 教学场景不需要 |
| **Book Engine（编译活书）** | ✅ | 🟡（language-pack 课件管线） | ➖ 与 language-pack 重叠，不引入 |
| **TutorBot Heartbeat（主动复盘）** | ✅ | ❌ | 🟡 价值中等，可后置 |
| **多 IM 通道接入（Telegram/Slack/飞书）** | ✅ | ❌ | ➖ 业务场景不需要 |
| **Agent-Native CLI** | ✅ | 🟡（命令行工具有，但非 SKILL.md 标准） | ➖ 非教学场景 |
| **多 LLM Provider** | ✅（30+） | ✅（AiModelGateway 抽象） | 双方都有 |
| **执行轨迹可视化** | 🟡 | ✅（execution_trace_explainer） | Alethicode 反而更强 |
| **学生数据合规** | ❌ | ✅（中国教育合规 ADR） | DeepTutor 弱 |

---

## 五、可借鉴点深度评估

### 5.1 评分维度

每项按以下 4 维度打分（1-5 分）：

- **教学价值**：是否直接提升"学生学得更好 / 教师教得更省力"；
- **学生体验**：是否减少认知负担、增强信任感；
- **实施成本**：是否能在 Alethicode 现有架构上低成本接入；
- **路径冲突**：是否与现有方案 / Roadmap 冲突。

### 5.2 候选项打分

| # | 候选项 | 教学价值 | 学生体验 | 实施成本 | 路径冲突 | **总分(20)** |
|---|---|---:|---:|---:|---:|---:|
| 1 | **Persistent Memory（自然语言摘要 + Prompt 注入 + 学生侧 dashboard）** | 5 | 5 | 4（已有底层） | 5（无冲突） | **19** |
| 2 | **Visualize Capability（Mermaid/Chart/SVG）** | 5 | 5 | 3（要新建 capability） | 4（前端有渲染基础） | **17** |
| 3 | **Unified Chat 跨模式上下文共享 + @引用** | 4 | 5 | 3（要扩 state + 前端） | 4（前端容器在） | **16** |
| 4 | **Skills 系统（教师可注入 SKILL.md）** | 4 | 3 | 4（一张表） | 5（无冲突） | **16** |
| 5 | **TutorBot Heartbeat（主动学习提醒）** | 3 | 4 | 3（需调度 + 时机规则） | 4（无冲突） | **14** |
| 6 | Book Engine | 2 | 3 | 1（高） | 1（与 language-pack 冲突） | **7**（不做） |
| 7 | Co-Writer | 1 | 2 | 2 | 5 | **10**（不做） |
| 8 | Multi-IM TutorBot | 1 | 1 | 2 | 3 | **7**（不做） |
| 9 | Agent-Native CLI / SKILL.md 给 Agent 操作 OJ | 1 | 1 | 3 | 4 | **9**（不做） |
| 10 | nanobot 多 Bot 框架 | 2 | 2 | 1 | 1（与 tutor_graph 冲突） | **6**（不做） |

**Top 3 入选**：Persistent Memory / Visualize / Unified Chat。

### 5.3 用户最终决策（2026-04-25）

用户选定 P1 / P2 / P3 三项均出技术设计文档：

- P1 — Persistent Memory（学情画像层）
- P2 — Visualize（Mermaid / Chart / SVG）
- P3 — Unified Chat（多模式共享上下文）

详细技术设计见：

- [`docs/plans/2026-04-25-persistent-memory-layer-design.md`](../plans/2026-04-25-persistent-memory-layer-design.md)
- [`docs/plans/2026-04-25-visualize-capability-design.md`](../plans/2026-04-25-visualize-capability-design.md)
- [`docs/plans/2026-04-25-unified-chat-context-design.md`](../plans/2026-04-25-unified-chat-context-design.md)

---

## 六、不可借鉴 / 不建议引入的点

### 6.1 Book Engine

**不引入原因**：

- Alethicode 已有 `language-pack` 课件管线（`docs/specs/language-pack-driven-ai-learning-system-functional-spec.md`），覆盖 init / qa / publish / video / storage；
- "活书"概念与课件章节高度重叠，引入会导致 **两套知识容器并存**，违反"不允许兼容性方案"原则；
- 14 种 block 中，quiz / flash card / timeline / concept graph 这些可作为 **Visualize Capability 的产出形式之一**，无需独立的 Book Engine 框架。

### 6.2 Co-Writer

**不引入原因**：

- 学生场景是"写代码"不是"写文档"；
- 教师写课件已通过 language-pack 完成；
- DeepTutor 的 Co-Writer 服务的是"通用学习者写论文 / 笔记"，与 Alethicode 的"OJ 编程教学"目标偏移。

### 6.3 多 IM 通道（Telegram / Slack / 飞书 / 企微 / 钉钉）

**不引入原因**：

- Alethicode 是面向学校 / 培训机构的 Web 教学产品，学生主要通过 Web / 班级使用；
- 多 IM 桥接增加运维与合规成本，且不解决任何"教学质量"问题；
- 真要做"主动提醒"，可走站内信 / 邮件 / 微信公众号模板消息，不必引入完整 IM 集成层。

### 6.4 nanobot 多 Bot 框架

**不引入原因**：

- Alethicode 教学侧定位是"一个 AI 导学助手 + 5 角色协作"，不是"学生自由创建多个独立 Bot"；
- nanobot 引入会与现有 `tutor_graph` LangGraph FSM 冲突（两套状态机 + 两套 memory 来源）；
- 没有任何已知教学场景需要"学生同时拥有 N 个独立 AI Bot"。

### 6.5 Agent-Native CLI / 给 LLM Agent 操作 OJ

**不引入原因**：

- DeepTutor 的 CLI 是"开发者用 LLM 自动操作 DeepTutor"，是 Dev Tool 而非教学产品；
- Alethicode 的目标用户是"非计算机专业 Python 初学者 + 教师"，不是开发者；
- 业务上没有"LLM 替学生 / 教师操作 OJ"的合规场景。

### 6.6 Python 全栈替换

**不引入原因**：

- Alethicode 后端 Java / 前端 Vue 已稳定运行（299 + 96 文件 + 38 Flyway 迁移）；
- 替换技术栈是颠覆性变更，不是"提高教学质量"的最短路径；
- DeepTutor 的代码结构是为 Agent-Native 而设计，不直接适配 OJ 业务领域。

---

## 七、推荐落地优先级

### 7.1 总体策略

```
                                  Persistent Memory
                                       (P1, 地基)
                                       │
                ┌──────────────────────┼──────────────────────┐
                ▼                      ▼                      ▼
        Visualize Capability     Unified Chat            Skills (后置)
              (P2)                (P3, 借力 P1)
```

- **P1 是地基**：Memory / Profile 一旦增强，P2 / P3 都能自动受益（个性化可视化 + 跨模式上下文共享）；
- **P2 / P3 可并行**：彼此无强依赖。

### 7.2 优先级排序

| 优先级 | 名称 | 教学杠杆 | 实施难度 | 依赖 |
|:---:|---|---|---|---|
| **P1** | Persistent Memory（学情画像层） | ⭐⭐⭐⭐⭐ | 低（底层完备，主要补 prompt 注入 + dashboard + 自然语言摘要） | 无 |
| **P2** | Visualize Capability | ⭐⭐⭐⭐⭐ | 中（需新增 capability + 前端渲染） | 无（可独立） |
| **P3** | Unified Chat 跨模式上下文 | ⭐⭐⭐⭐ | 中（需扩 state + 前端 @引用） | 弱依赖 P1（用 memory_refs 增强） |
| 后置 | Skills 系统（SKILL.md） | ⭐⭐⭐⭐ | 低 | 弱依赖 P1 |
| 后置 | TutorBot Heartbeat | ⭐⭐⭐ | 中 | 强依赖 P1 |

### 7.3 教学场景示例（P1+P2+P3 落地后）

**场景：学生小明做第 7 题，for 循环边界又错了**

1. **学生进入题目页**
   - P1 生效：`AITutorWelcomeService` 读 Memory 自然语言摘要 + LearningStyle，Nene 开场："小明，欢迎回来。你过去 7 天做了 6 道循环题，第 3、5 题在 `range(n)` 边界上踩过坑，要不要先看 1 分钟可视化复习？"

2. **学生说"看可视化"**
   - P2 生效：Visualize Capability 调用，输出 Mermaid 流程图：`for i in range(n)` 的迭代过程，i 从 0 到 n-1，并对比 `range(1, n+1)` 的差异。

3. **学生写代码，提交错误**
   - P1+P2 生效：Yoshino 调用诊断节点，prompt 注入 LearningStyle=VISUAL → 输出包含"数据流图 + 你过去 3 次同类错误的对照表"。

4. **学生用 Chat 模式问"第三种解法行不行"**
   - P3 生效：Chat 模式可以 `@error_diagnosis` 引用刚刚的诊断卡片，LLM 回答时上下文不丢。

5. **学生 AC 后**
   - P1 生效：`saveTutoringConclusion` 写入 Memory，并触发自然语言摘要 incremental update：「第 7 题 AC，本次 for 循环边界问题已解决，建议下次留意 `range` 步长 ≠ 1 的情形」。

---

## 八、与现有 Roadmap 的对齐

### 8.1 `AGENTS.md` Roadmap 对照

`AGENTS.md` 当前 "Alethicode-Academy 增强路线图" 6 项：

| Roadmap 项 | 与本调研推荐的关系 |
|---|---|
| 一、真实代码执行 | 已与 nfk + Judge 集成，Visualize（P2）可在 CodingChallenge 反馈中使用 |
| 二、AI 导学角色化 | **直接被 P1 强化**（角色 prompt 接 Memory + LearningStyle） |
| 三、课件融入剧情 | 不在本报告推荐范围；建议保留 language-pack 自身演进 |
| 四、学情贯通 | **被 P1 完整覆盖**（学情双向同步 + 角色感知） |
| 五、自适应难度 | 与 P1 弱相关；推荐题选题已有 `BeginnerSupplementPlannerService` |
| 六、错误记忆系统 | **被 P1 覆盖** + P2 提供错误可视化 |

### 8.2 已完成 todo 与本调研的对齐

| 已完成 todo | 本调研定位 |
|---|---|
| `todo-long-term-learning-memory.md` (2026-03-29) | P1 是它的 **第二期增量**：补 prompt 注入 / 自然语言摘要 / 学生侧可见 |
| `todo-ai-variable-runtime-visualization.md` (2026-03-29) | P2 是它的 **横向扩展**：从"运行轨迹"扩到"数据结构 / 算法流程 / 复杂度" |
| `todo-similar-error-reminder.md` | P1 强化后会自然激活更多召回机会 |

### 8.3 与 ADR 的关系

- **ADR-0001 langgraph-tutor-workflow**：P1 的 prompt 注入实现需新增 `learner_state` 到所有节点的 evidence requirements，需在 ADR-0001 框架内做小幅扩展（不破坏 LangGraph 状态契约）；
- **ADR-0002 spring-ai-gateway**：P2 的 Visualize Capability 通过 `AiModelGateway.callForJson` 调用，无需新 ADR；
- **ADR-0003 ai-runtime-integration-handoff**：P3 的跨模式上下文需要扩展 evidence_pack 与 node_outputs 在前后端的契约，建议增量补一份小 ADR（在 P3 设计文档中已说明）。

---

## 九、风险评估与边界

### 9.1 主要风险

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| Memory 注入后 prompt 长度暴涨，导致 token 成本上升 | 中 | 中 | P1 设计中明确给"自然语言摘要 ≤ 500 字 + 召回 top-5 memory_refs" |
| Visualize 输出 Mermaid 语法错误，前端渲染失败 | 中 | 低 | P2 设计中要求 schema 校验 + LLM 输出后做语法 validate，failfast |
| Unified Chat 跨模式引用导致状态机歧义（学生在 IDEATING 引用 ERROR_FEEDBACK） | 低 | 中 | P3 设计中明确"引用是 read-only，不切换 Phase" |
| 学生 dashboard 暴露 LearnerState 后产生隐私 / 心理压力 | 低 | 中 | P1 设计中要求"学生侧画像只显示 LLM 摘要 + 用户可关闭" |

### 9.2 不引入风险（边界）

明确不在本调研推荐范围内，避免后续被误读为"应该做"：

- 不接入 nanobot；
- 不引入 Co-Writer；
- 不引入 Book Engine；
- 不引入多 IM 通道；
- 不做 CLI / SKILL.md 给 LLM 操作 OJ；
- 不做 Python 全栈替换；
- 不替换现有 tutor_graph 编排；
- 不替换现有 language-pack；
- 不引入新的 LLM 供应商（已在 AiModelGateway 抽象内）。

### 9.3 第一性原理边界自检

| 自检项 | 通过 |
|---|---|
| 不引入兼容性 / 补丁性方案 | ✅（P1 是增量补强，不并存两套 Memory） |
| 不过度设计 | ✅（已剔除 Co-Writer / Book Engine / IM 桥接 / nanobot） |
| 不擅自扩展业务目标 | ✅（仅围绕"提升教学质量与体验"） |
| 不写防御性 / 兜底逻辑 | ✅（设计文档全部 failfast） |
| 全链路逻辑验证 | ✅（推荐项均有"输入 → 处理 → 输出"链路图） |

---

## 十、决策建议

### 10.1 建议结论

**采纳 P1 / P2 / P3 三项**，作为 Alethicode 在 2026 Q2 的"教学质量增强"主线之一；其余 DeepTutor 能力**明确不引入**。

### 10.2 落地节奏建议（仅供参考，非硬性）

| 阶段 | 内容 | 工作量量级 |
|---|---|---|
| Phase α | P1：补全 Memory prompt 注入 + 学生侧画像 dashboard + 自然语言长期摘要 | 中 |
| Phase β（与 α 并行） | P2：新增 Visualize Capability + 前端 Mermaid/Chart/SVG 渲染 | 中 |
| Phase γ（α 完成后） | P3：Unified Chat 跨模式上下文 + @引用 | 中 |
| Phase δ（可选） | Skills 系统 / Heartbeat（按业务需要再启动） | 低-中 |

### 10.3 验收标准（高维度）

P1 / P2 / P3 任一上线后，**下列 3 项指标至少 2 项有正向变化**：

1. **学生留存**：连续 3 天打开 AI 助手的学生比例提升 ≥ 5%；
2. **导学有效性**：学生在 AI 反馈后 1 小时内 AC 率提升 ≥ 5%；
3. **学生主观满意度**：每周问卷 NPS 提升 ≥ 5 点。

具体接口 / 数据 / 工作量见 3 份技术设计文档。

---

## 附录 A：调研中阅读的关键文件

### Alethicode

- `AGENTS.md`
- `PROJECT.md`
- `services/tutor-graph/README.md`
- `research/nfk/README.md`
- `services/tutor-graph/app/graph/state.py`
- `services/tutor-graph/app/nodes/evidence.py`
- `services/tutor-graph/app/nodes/reading.py`
- `services/tutor-graph/app/nodes/chat.py`
- `services/tutor-graph/app/nodes/diagnosis.py`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerMemoryService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerProfileProjector.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerState.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/MemoryCandidate.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearningStyle.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/CrossCourseProfileService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/AITutorWelcomeService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/contract/CardType.java`
- `backend/src/main/java/com/alethicode/service/ai/AiModelGateway.java`
- `frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`
- `frontend/src/pages/oj/views/problem/Problem.vue`
- `docs/todos/todo-long-term-learning-memory.md`
- `docs/todos/todo-ai-variable-runtime-visualization.md`
- `docs/todos/todo-similar-error-reminder.md`

### DeepTutor

- https://github.com/HKUDS/DeepTutor
- https://raw.githubusercontent.com/HKUDS/DeepTutor/main/README.md（v1.2.4）

---

## 附录 B：术语对照表

| Alethicode 术语 | DeepTutor 对应 | 说明 |
|---|---|---|
| `tutor_graph` LangGraph | Capabilities + Tools 编排 | 多 Agent 协作的不同实现 |
| 5 角色（Nene/Yoshino/...） | Skills（SKILL.md） | DeepTutor 通过 SKILL.md 注入人格 |
| `LearnerMemoryService` | Persistent Memory | 双方都做，DeepTutor 多了自然语言摘要 |
| `LearnerProfileProjector` | Profile | 双方都做 |
| `LearnerState` | 综合状态对象 | 概念一致 |
| `LearningStyle` 4 种 | Personalization | Alethicode 投票推断，DeepTutor 直接对话累积 |
| 8 张 AI 卡片 | 6 模式（Chat / Deep Solve / ...） | 触发方式不同：Alethicode 阶段触发，DeepTutor 用户主动 |
| `execution_trace_explainer` 卡片 | Visualize（部分） | Alethicode 仅运行轨迹，DeepTutor 还有 Mermaid/Chart/Manim |
| `language-pack` 课件 | Book Engine（部分） | 教学容器，实现差异大 |
| `BeginnerSupplementPlannerService` | Quiz Generation（部分） | 自适应推题，DeepTutor 是题库内随机 |
| `nfk` (NFK 知识追踪) | — | DeepTutor 没有 KT |

---

**报告完。**
