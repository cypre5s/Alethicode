# Agent + Harness 执行计划

**目标文件**：`/home/cypress/Alethicode/docs/todos/todo-agent-harness/2026-04-04-agent-harness-execution-plan.md`

**目标**：在不重写现有 AI 主骨架的前提下，系统化完善记忆系统、RAG、工具治理与 Harness 闭环，使做题导学与课件 QA 都具备可评估、可回放、可灰度的工程能力。

**执行原则**：

- 保留现有 `LlmClient / ReAct / Reflection / OrchestratorAgent / WebSocket` 主链路。
- 采用“业务骨架自研 + 通用基础设施逐步迁入 Spring AI”的混合路线，不做一次性整体重构。
- 默认 fail-fast，不做兜底性补丁设计。
- 每个阶段都必须先补 trace 与评测，再开放灰度。
- 每个阶段完成后都要更新 `CHANGELOG.md`。

## Harness 定义与边界

### Harness 定义

本计划中的 `Harness` 不是某个模型、某句 prompt，也不是某个单独框架能力。

这里的 `Harness` 指围绕 AI 导学助手与 AI 问答建立的一层 **执行与治理层**。它负责把模型、工具、上下文、状态、安全、恢复、客户端集成、评测和灰度接成一个可以长期运行的系统，而不是一次性对话流程。

结合本项目，成熟 Harness 的 6 个核心组成部分如下：

1. `agent runtime`
   - 负责 agent loop、状态流转、事件驱动和客户端交互
2. `tool orchestrator`
   - 负责工具注册、参数约束、调用 trace 和工具域隔离
3. `memory/state manager`
   - 负责上下文分层、会话状态、长期记忆和 checkpoint 状态
4. `permission/safety wrapper`
   - 负责权限约束、fail-fast、拒答协议和风险控制
5. `failure recovery system`
   - 负责 replay、failure bucket、checkpoint restore、重启恢复和长任务续跑
6. `client/runtime integration`
   - 负责对接 Web、WebSocket、后台任务、管理端和稳定运行时协议

除上述 6 层核心结构外，本项目还额外强调一层治理能力：

- `human approval/workflow layer`
  - 负责人工审批、受控自治、灰度门禁和恢复决策
  - 在实现上横跨 `permission/safety wrapper` 与 `failure recovery system`

### Harness 与其他层的关系

为避免文档继续把不同层次混写，本计划明确区分 3 类内容：

1. `Harness 本体`
   - 指执行与治理层本身
   - 例如 agent loop、tool governance、runtime contract、rollout gate、checkpoint、approval、replay、grader

2. `业务能力建设`
   - 指 AI 导学助手或 AI 问答的能力增强
   - 例如错误诊断、脚手架、query rewrite、grounding critic、learner memory

3. `基础设施迁移`
   - 指底层通用实现的接入或替换
   - 例如 Spring AI `ChatClient / EmbeddingModel / Observability / PGVector`

### 文档解释规则

- 当某个 Phase 主要建设执行与治理层时，标记为 `Harness 本体`
- 当某个 Phase 主要提升导学或 QA 能力时，标记为 `业务能力建设`
- 当某个 Phase 主要用于接入 Spring AI 等通用底层时，标记为 `基础设施迁移`
- 若一个阶段同时覆盖两层内容，以主要目标为主、次要目标为辅，不把三层概念混写成同一件事

### Harness Engineering 在本项目中的含义

本计划中的 `Harness Engineering` 不再等同于提示工程，而是指围绕这层运行系统做持续工程化迭代，核心包括：

1. 让仓库对 agent 更可读
   - 把工具协议、trace 结构、dataset、grader、阶段文档沉淀到 repo
2. 让知识沉淀到 repo 而不是只存在对话里
   - 把运行规则、失败样本、评测标准、审批点固化为文档和代码契约
3. 通过反馈回路让 agent 持续纠错
   - 用 replay、grader、failure bucket、灰度门禁反向约束 agent 行为
4. 控制熵增和系统漂移
   - 新能力必须带 dataset、grader、trace 和阈值，禁止黑箱扩张
5. 坚持 `humans steer, agents execute`
   - 人类定义边界、审批高风险动作，agent 负责执行受约束任务

## Spring AI 迁移边界

### 总体判断

本计划不再把 Spring AI 视为“暂不考虑”的外部框架，而是明确吸收其在以下三个高收益方向上的成熟能力：

1. **模型调用层**
   - 将现有 `callForJson / callForEmbedding` 逐步迁到 Spring AI 的 `ChatClient / ChatModel / EmbeddingModel`
   - 减少自维护 HTTP、重试、部分 provider 适配代码
2. **通用 RAG 基建**
   - 优先评估 `Advisors / VectorStore / PGVector / Evaluator`
   - 把 QA 管线中可标准化的部分迁入 Spring AI，而不是重写整个 QA 业务链路
3. **Observability**
   - 用 Spring AI 官方 observability 把 `ChatClient / Advisor / Tool / EmbeddingModel / VectorStore` 接到 Micrometer / tracing
   - 利用项目现有 `actuator / prometheus` 依赖快速获得统一 AI tracing 能力

### 迁移硬边界

- 不重写教学 FSM、`OrchestratorAgent`、`ReflectionService`、checkpoint / interrupt 协议
- 不把 Spring AI 变成新的业务主架构，只把它作为底层 AI 基础设施
- 不要求导学与 QA 同时全面切换，允许先在 QA 做试点
- 不因为引入 Spring AI 而破坏现有 `MiniMax/OpenAI-compatible` 行为语义

### 优先迁入模块

- `LlmClient` 下层 provider adapter
- QA 模块的 memory / advisor / PGVector 试点
- Spring AI observability tracing

### 暂不迁入模块

- 教学 phase / event 状态机
- Tutor Agent 编排与 `OrchestratorAgent`
- 自定义 Reflection 质检策略
- WebSocket 实时回推链路

## AI 导学助手 与 AI 问答 的边界约束

### 业务定位边界

#### AI 导学助手（做题界面）

- 面向题目求解过程
- 强绑定 `problem / submission / phase / learner_state`
- 负责教学干预、脚手架、错误诊断、AC 复盘、迁移练习
- 允许使用 learner long-term memory
- 允许进入 `pending_human_action / checkpoint / interrupt`

#### AI 问答（独立界面）

- 面向课件知识问答
- 强绑定 `language_pack / session / citations`
- 只回答课件中有证据的问题
- 不参与题目 phase 调度
- 不生成教学 phase 卡片
- 不以 learner long-term memory 作为主回答依据
- 不进入教学式 phase checkpoint 流程

### 基础设施共用边界

以下能力允许两者共用：

- 模型调用层
- Spring AI adapter 层
- Observability / tracing
- Trace / Harness 框架
- Tool governance 基础规范
- 部分短期会话 memory 机制

### 禁止交叉污染

- QA 不得调用导学 phase / event 工作流
- 导学助手不得退化为“课件聊天窗口”
- QA 不得直接输出 learner long-term memory 推断作为答案依据
- 导学助手不得把 grounded citation 协议作为主输出协议
- QA 的拒答协议不得被教学 phase 卡片协议替代
- 导学工具与 QA 工具必须做工具域隔离，不得默认混用

### 上下文来源边界

#### AI 导学助手主上下文

- `workflow context`
- `problem context`
- `submission/code context`
- `learner memory`
- `courseware retrieval`
- `similar errors`

#### AI 问答主上下文

- `session context`
- `retrieval context`
- `language pack scope`
- `citations / evidence pages`
- 可选短期 chat memory

### 评测边界

#### AI 导学助手主评测维度

- pedagogy fit
- answer leakage
- action appropriateness
- scaffold quality
- learner fit

#### AI 问答主评测维度

- retrieval recall
- grounding accuracy
- refusal correctness
- citation precision
- answer completeness

---

## Phase 0：工程落盘与边界冻结

**适用界面**：两者共用
**阶段属性**：Harness 本体

### 目标

建立 `docs/todos/todo-agent-harness/` 作为唯一执行入口，把本次工作从“想法集合”变成“阶段化工程”。

### 实现流程

1. 创建目录 `docs/todos/todo-agent-harness/`
2. 创建以下文档：
   - `README.md`
   - `phase-1-context-memory.md`
   - `phase-2-rag-harness.md`
   - `phase-3-tools-trace-rollout.md`
   - `phase-4-hitl-and-agent-runtime.md`
3. 在 `README.md` 中固定：
   - 范围：做题导学 + 课件 QA
   - 不做：Spring AI 主链路重构、SSE 替换、MCP 内部主调用栈化
   - 阶段依赖关系
   - 统一术语表：`ToolContext / AgentTrace / RetrievalTrace / MemoryCandidate / TraceGradeResult`
4. 在 `docs/` 中增加一份正式设计索引文档，指向 `docs/todos/todo-agent-harness/`。
5. 明确所有后续实现必须对应阶段文档、测试与验收记录。
6. 在 `README.md` 中新增一节“Spring AI 渐进迁移策略”，明确：
   - 为什么不是整体转向 Spring AI
   - 为什么优先迁入模型调用层、通用 RAG 基建和 observability
   - 为什么先在 QA 模块试点

### 主要落点

- `docs/todos/todo-agent-harness/`
- `docs/PROJECT.md`
- `docs/architecture/agent-architecture-workflow.md`

### 验收标准

- 团队只看 `docs/todos/todo-agent-harness/` 就能理解全局路线。
- 边界明确，不会把本次工作误做成框架重构项目。
- 阶段依赖顺序清晰，无交叉含糊项。
- 团队能清楚区分“Spring AI 迁入范围”和“保留自研范围”。

---

## Phase 0.5：Spring AI 试点基线建立

**适用界面**：两者共用
**阶段属性**：基础设施迁移

### 目标

在不改变现有业务行为的前提下，为 Spring AI 渐进迁入建立最小可验证基线，先验证 provider、依赖、配置和 tracing 接通。

### 实现流程

1. 在 `backend/pom.xml` 中评估并引入所需 Spring AI 依赖：
   - `ChatClient / ChatModel`
   - `EmbeddingModel`
   - `Observability`
   - 视试点结果再决定是否加入 `VectorStore / PGVector / Advisors`
2. 新增 Spring AI 配置层，但不直接替换现有 `LlmClient` 入口。
3. 为现有 `LlmClient` 增加一层 facade / adapter 设计：
   - 默认仍走当前实现
   - 允许通过配置切换到 Spring AI backend
4. 为模型调用建立“双实现一致性”验证：
   - 当前实现输出
   - Spring AI 实现输出
   - 结构化输出字段一致性检查
5. 接入 Spring AI observability：
   - Chat 调用 tracing
   - Embedding 调用 tracing
   - Tool / Advisor tracing
6. 打通 Micrometer 指标与 trace 标签，确认能被现有监控链路消费。

### 主要落点

- `backend/pom.xml`
- `backend/src/main/java/com/alethicode/service/LlmClient.java`
- `backend/src/main/java/com/alethicode/config/`
- `backend/src/test/java/com/alethicode/service/LlmClientTest.java`

### 测试

- Spring AI provider 能在当前环境正常初始化。
- `callForJson` 与 Spring AI adapter 在固定样本上结构化输出一致。
- `callForEmbedding` 与 Spring AI embedding adapter 在向量维度和基本可用性上满足现有调用要求。
- tracing 数据能进入现有 observability 链路。

### 验收标准

- Spring AI 在项目中“可接、可测、可观测”，但尚未强制替换生产调用链。
- 模型调用层完成可回退的双实现基线。
- 后续迁移不再依赖“先整体重写再验证”。

---

## Phase 1：Context Layering 与 Memory 升级

**适用界面**：以 AI 导学助手为主，AI 问答部分受益
**阶段属性**：业务能力建设（兼具 Harness 状态管理）

### 目标

把现有“最近会话 + learner memory”升级为统一的分层上下文系统，让导学与 QA 使用同一套上下文命名、装配和召回规则。

### 实现流程

1. 定义统一上下文层模型：
   - `runtime_context`
   - `session_context`
   - `retrieval_context`
   - `learner_memory`
   - `institutional_context`
   - `policy_context`
2. 扩展或重构上下文装配入口，建议以现有 `EvidencePackAssembler` 为主，不新起第二套并行系统。
3. 改造 QA 的 `ConversationContextService`：
   - 从纯文本拼接升级为结构化输出
   - 增加最近引用页缓存
   - 增加会话摘要字段
4. 改造 `LearnerMemoryService`：
   - 拆出 `memory_candidate`
   - 拆出 `memory_save_decision`
   - 拆出 `memory_scope`
5. 将当前全量刷新写入逻辑改为事件驱动增量写入：
   - ERROR_FEEDBACK 完成后
   - AC_REVIEW 完成后
   - 用户显式纠正模型后
6. 新增记忆排序策略：
   - 相似度
   - 新鲜度
   - 置信度
   - 来源可靠性
   - 当前 phase 相关性
7. 对高价值记忆写入引入审批点：
   - `confirm_memory_save`
8. 将最终上下文快照纳入 trace 日志。
9. 在 QA 模块试点 Spring AI 的 memory / advisor：
   - 优先用于会话级短期记忆
   - 不替代 learner long-term memory
   - 与现有 `ConversationContextService` 做并行对比
10. 明确记忆分层边界：
   - Spring AI `ChatMemory/Advisor` 只服务 QA 对话短期上下文
   - `LearnerMemoryService` 继续承载长期学习记忆和教学资产
11. 明确导学与 QA 的记忆使用边界：
   - 导学使用 learner long-term memory 作为教学核心上下文
   - QA 不得把 learner long-term memory 作为主回答证据

### 主要落点

- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerMemoryService.java`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/ConversationContextServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/aitutor/evidence/EvidencePackAssembler.java`

### 测试

- 新增记忆候选不会直接污染长期记忆。
- 同一用户追问时能正确继承最近证据页与摘要。
- 缺失关键上下文字段时直接失败。
- 记忆召回顺序可预测，不再只靠更新时间。
- Spring AI memory 试点与现有 QA 上下文机制在固定样本上可对比。

### 验收标准

- 导学与 QA 都能输出统一命名的 context snapshot。
- 记忆写入具有明确触发点与可解释状态。
- 用户纠正模型后，系统可以形成候选记忆而不是静默丢弃。
- 任何回答都能解释“用了哪些记忆层”。
- QA 短期记忆具备 Spring AI 试点结果，并能明确判断是否值得扩大范围。

---

## Phase 2：RAG 治理与 QA Harness 升级

**适用界面**：AI 问答（独立界面）
**阶段属性**：业务能力建设（兼具 Harness 评测与回放）

### 目标

把当前课件 QA 从“能回答”升级为“可治理、可回放、可量化”。

### 实现流程

1. 在 QA 链路加入检索前处理层：
   - query normalize
   - query rewrite
   - query decomposition
   - reference resolution
2. 固定 QA 执行流：
   - 规范化问题
   - 改写/拆解
   - 首轮检索
   - 可选 ReAct 补检索
   - synthesis
   - grounding critic
3. 改造 `PageRetrievalService` 返回结构，从“只给 hits”升级为“hits + retrieval trace”。
4. 改造 `AnswerSynthesisService` 返回结构，从“答案对象”升级为“答案 + synthesis trace + critic verdict”。
5. 扩充 `QaEvalHarness`：
   - retrieval_eval
   - grounding_eval
   - answer_eval
   - refusal_eval
6. 建立 QA dataset：
   - 单页可答
   - 多页整合
   - 应拒答
   - 指代追问
   - 错误页码引用
   - 容易误召回的概念题
7. 加入 failure bucket：
   - `insufficient_evidence`
   - `conflicting_evidence`
   - `citation_mismatch`
   - `query_rewrite_regression`
   - `out_of_scope`
8. 新增回放入口：
   - 指定 sample 重新跑 QA 全链路
   - 输出 retrieval/synthesis/critic 全 trace
9. 在 QA 模块试点 Spring AI 通用 RAG 基建：
   - 评估 `Advisors`
   - 评估 `VectorStore`
   - 评估 `PGVector` 集成
   - 评估 `Evaluator`
10. 明确试点策略：
   - 只迁通用 RAG 基建
   - 不迁 QA 业务拒答协议
   - 不迁 grounded answer 业务结构
11. 对比两套实现：
   - 当前自研检索链
   - Spring AI 试点检索链
   - 输出召回、引用、拒答和延迟对比报告

### 主要落点

- `backend/src/main/java/com/alethicode/service/languagepack/PageRetrievalService.java`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/PageRetrievalServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/languagepack/AnswerSynthesisService.java`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/AnswerSynthesisServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/QaEvalHarness.java`

### 测试

- query rewrite 开关前后做同一批样本对比。
- QA 应拒答样本不能被错误回答。
- grounding critic 的拒答原因必须结构化。
- 回放结果与线上一次执行的阶段顺序一致。
- Spring AI RAG 试点必须和当前实现做同批数据集对比。

### 验收标准

- QA 报告能输出 `retrieval recall / grounding accuracy / refusal accuracy / citation precision`。
- 任一失败样本都可一键回放。
- query rewrite 是否有效能被量化，而不是只看体感。
- 课件 QA 从“模型效果问题”转化为“具体哪一层出问题”的工程问题。
- 团队能明确判断哪些 QA 通用 RAG 环节适合迁入 Spring AI，哪些必须保留自研。

---

## Phase 3：ToolContext、工具治理与 ACI 文档化

**适用界面**：两者共用，但 AI 导学助手收益更明显
**阶段属性**：Harness 本体

### 目标

把当前工具链从“注册若干 lambda”升级为“有上下文、有约束、有 trace、有 agent-facing 文档”的稳定接口层。

### 实现流程

1. 定义统一 `ToolContext`：
   - `userId`
   - `sessionId`
   - `problemId`
   - `languagePackId`
   - `phase`
   - `event`
   - `locale`
   - `permissions`
2. 升级工具定义结构，至少包含：
   - schema
   - executor
   - guard
   - trace summary
   - agent-facing description
3. 改造 `ToolExecutor` 签名为上下文感知模式。
4. 改造 `TutorToolRegistry` 为规范化注册中心，而非静态定义集合。
5. 在 `callWithTools` 中补全 trace：
   - iteration
   - selected tool
   - args
   - guard pass/fail
   - latency
   - result summary
   - abort reason
6. 为每个工具补 ACI 文档：
   - 什么时候调
   - 不该什么时候调
   - 参数含义
   - 常见失败原因
   - 输出结构
7. 增加 fail-fast 规则：
   - 上下文缺失报错
   - 越权报错
   - scope 不匹配报错
8. 预留 MCP 边界：
   - 把 `search_language_pack_pages` 标记为未来可外开放候选
   - 当前内部仍用本地 service 调用
9. 明确工具域隔离：
   - 导学工具域：`search_courseware / search_similar_errors / get_learner_history`
   - QA 工具域：`search_language_pack_pages`
   - 默认不跨域暴露

### 主要落点

- `backend/src/main/java/com/alethicode/service/aitutor/react/ToolExecutor.java`
- `backend/src/main/java/com/alethicode/service/aitutor/react/ToolDefinition.java`
- `backend/src/main/java/com/alethicode/service/aitutor/react/TutorToolRegistry.java`
- `backend/src/main/java/com/alethicode/service/LlmClient.java`

### 测试

- 缺少 `languagePackId` 的 QA 检索工具必须直接失败。
- 缺少 `userId` 的 learner history 工具必须直接失败。
- 工具重复调用、空结果、执行异常都能落到不同 trace 状态。
- agent-facing 文档能作为测试断言依据。

### 验收标准

- 所有工具调用都可追溯上下文。
- 工具层失败原因可以结构化分类。
- 新增工具时不需要再靠 prompt 填补语义缺口。
- 工具注册中心能支撑后续扩展，而不继续堆静态方法。

---

## Phase 4：Harness Engineering 主体落地

**适用界面**：两者共用
**阶段属性**：Harness 本体

### 目标

把评测、追踪、回放、门禁真正接到工程主流程上，形成“改一处、测一处、判一处”的闭环。

### 实现流程

1. 定义统一 trace 模型：
   - `AgentTrace`
   - `RetrievalTrace`
   - `ToolTraceEntry`
   - `TraceGradeResult`
2. 定义统一 runtime contract：
   - `session_id`
   - `task_id`
   - `checkpoint_id`
   - `trace_id`
   - `runtime_state`
   - `client_event`
   - `server_event`
3. 定义统一 lifecycle state model：
   - `queued`
   - `running`
   - `waiting_tool`
   - `waiting_human_approval`
   - `interrupted`
   - `restoring`
   - `failed`
   - `completed`
   - `expired`
4. 升级 `TraceGradeService`：
   - schema correctness
   - pedagogy fit
   - retrieval sufficiency
   - grounding soundness
   - answer leakage
   - action appropriateness
   - interruption safety
5. 升级 `TutorEvalHarness` 为 dataset-aware：
   - 支持按 sample 跑
   - 支持输出 failure bucket
   - 支持 trace 评分
6. 保留 `OffPolicyEvalService` 作为 rollout 辅助，不再承担主质量判定。
7. 将离线 harness 接入 rollout 门禁：
   - 不达标不允许 gray
8. 建立阶段化报表：
   - phase 维度
   - agent 维度
   - tool 维度
   - language pack 维度
9. 固定回归入口：
   - 每次改 prompt / tool / retrieval / memory，都跑固定数据集
10. 形成最小工程约束：
   - 新能力必须带 dataset
   - 新策略必须带 grader
   - 新灰度必须有明确阈值
11. 将 Spring AI observability tracing 接入 Harness：
   - Chat trace
   - Advisor trace
   - Tool trace
   - Embedding trace
   - VectorStore trace
12. 建立“当前实现 vs Spring AI 试点实现”对比报表：
   - 延迟
   - 失败率
   - 引用质量
   - grounding 质量
   - tracing 完整度
13. 分离两套 grader 维度：
   - 导学 grader
   - QA grader
   - 禁止把同一套 rubric 直接套到两个界面
14. 定义 client/runtime integration 规范：
   - Web 前端通过稳定 workflow API / QA API 与 harness 交互
   - WebSocket 只承担运行时事件推送，不承载业务判定
   - 管理端、回放入口、灰度入口复用同一套 runtime identifiers
15. 定义 recovery contract：
   - 进程重启后如何恢复 `running / waiting_human_approval / interrupted` 任务
   - checkpoint restore 后如何恢复 trace 关联关系
   - stale task / orphan task 的判定和过期规则

### `runtime_state` 状态机表

| 状态 | 含义 | 允许进入条件 | 允许迁出到 | 持久化要求 |
| --- | --- | --- | --- | --- |
| `queued` | 任务已创建，尚未开始执行 | 创建 session / task 后尚未分配执行 | `running`, `expired`, `failed` | 必须持久化 `session_id / task_id / trace_id / create_time` |
| `running` | agent 正在执行主循环 | 从 `queued` 开始执行，或从 `restoring` 恢复继续执行 | `waiting_tool`, `waiting_human_approval`, `interrupted`, `failed`, `completed` | 必须持久化当前 step、最近一次输入快照、当前上下文快照 |
| `waiting_tool` | 已发起工具调用，等待工具结果回填 | 在 agent loop 中发出合法 tool request | `running`, `failed`, `interrupted` | 必须持久化 `tool_name / tool_args / tool_call_id / started_at` |
| `waiting_human_approval` | 到达高风险节点，等待人工审批 | 命中审批点或高风险动作 guard | `running`, `expired`, `failed` | 必须持久化 `pending_human_action / checkpoint_id / approval_payload` |
| `interrupted` | 被用户或系统主动中断 | 用户中断、系统熔断、重复调用检测、超时保护 | `restoring`, `failed`, `expired` | 必须持久化中断原因、中断时刻、最近安全输出 |
| `restoring` | 正在从 checkpoint 或恢复流程重新装载状态 | 管理端恢复、系统重启恢复、人工批准后恢复 | `running`, `failed`, `expired` | 必须持久化 `source_checkpoint_id / source_trace_id / restore_token` |
| `failed` | 执行失败且不再继续 | 非可恢复错误、恢复失败、合同校验失败 | 无 | 必须持久化 failure bucket、error reason、最后有效上下文 |
| `completed` | 任务成功完成 | agent 输出最终结果且通过本轮必要校验 | 无 | 必须持久化最终输出、trace 收尾、完成时间 |
| `expired` | 长时间未继续，按生命周期策略过期 | 审批等待超时、排队过久、孤儿任务清理 | 无 | 必须持久化过期原因、过期时间、是否允许新建替代任务 |

### `runtime_state` 迁移规则

1. `queued -> running`
   - 仅允许由 runtime scheduler 或显式恢复流程触发
2. `running -> waiting_tool`
   - 仅允许在已记录 `tool_call_id` 后发生
3. `waiting_tool -> running`
   - 仅允许在工具结果写入 observation 后发生
4. `running -> waiting_human_approval`
   - 仅允许在 checkpoint 已落盘后发生
5. `waiting_human_approval -> running`
   - 仅允许在审批结果、审批人、审批时间落盘后发生
6. `interrupted -> restoring`
   - 仅允许由用户续跑、管理端恢复或系统自恢复触发
7. `restoring -> running`
   - 仅允许在 checkpoint、trace、上下文三者重新绑定成功后发生
8. 任意活跃态进入 `failed`
   - 必须写入结构化失败原因，不能只写自由文本
9. 任意等待态进入 `expired`
   - 必须有明确 TTL 和来源规则，不能无限悬挂

### `runtime contract` 字段表

| 字段 | 含义 | 生产方 | 消费方 | 约束 |
| --- | --- | --- | --- | --- |
| `session_id` | 会话主标识 | runtime backend | Web 前端、管理端、日志系统 | 整个会话生命周期内稳定不变 |
| `task_id` | 单次执行任务标识 | runtime backend | 前端轮询、回放、灰度系统 | 同一 `session_id` 下可多次出现，但单次执行唯一 |
| `checkpoint_id` | 可恢复检查点标识 | checkpoint subsystem | 恢复入口、审批流、管理端 | 仅在可恢复状态下必填 |
| `trace_id` | 统一链路追踪标识 | tracing subsystem | eval、报表、回放、观测系统 | 单次任务唯一且可跨恢复延续 |
| `runtime_state` | 当前运行状态 | runtime backend | 前端、管理端、恢复流程 | 必须来自状态机枚举，不允许自由文本 |
| `client_event` | 客户端触发事件 | Web/管理端 | runtime backend | 必须经过 schema 校验 |
| `server_event` | 服务端推送事件 | runtime backend | WebSocket、管理端监听器 | 只承载状态与摘要，不承载最终业务判定逻辑 |
| `approval_state` | 审批状态 | approval subsystem | 前端、管理端、恢复流程 | 与 `waiting_human_approval` 强关联 |
| `failure_bucket` | 失败分类 | runtime backend / grader | 回放、报表、恢复流程 | 失败或中断时必须结构化写入 |

### `recovery contract` 表

| 场景 | 恢复前置条件 | 恢复动作 | 恢复后约束 |
| --- | --- | --- | --- |
| 进程重启恢复 `running` 任务 | 存在最近一次有效 trace、上下文快照、未过期 task | 进入 `restoring`，重建 runtime context，再进入 `running` | 不得丢失 `trace_id`，不得重复执行已完成工具调用 |
| 人工审批后恢复 | 存在 `checkpoint_id`、审批结果、审批人信息 | 从 `waiting_human_approval` 进入 `restoring`，加载 checkpoint 后继续执行 | 恢复后必须保留审批记录与审批前上下文来源 |
| 用户手动续跑 `interrupted` 任务 | 任务未过期，存在最近安全输出与中断原因 | 从 `interrupted` 进入 `restoring` 再进入 `running` | 必须把“这是恢复执行”写入 trace |
| checkpoint restore | checkpoint 完整、源 session 可校验、trace 可回链 | 重新装载 state snapshot、context snapshot、approval state | 恢复后禁止切换业务域，不允许导学恢复成 QA 或反之 |
| stale task 清理 | 达到 TTL，无活跃客户端，无审批推进 | 从等待态或中断态进入 `expired` | 只能新建替代任务，不允许直接把过期任务改回 `running` |
| orphan task 处理 | 运行记录存在，但客户端和调度器都失联 | 标记为 `expired` 或 `failed`，并记录 orphan 原因 | 必须进入 failure report，不能静默消失 |

### 当前代码映射与缺口

#### 已有基础

1. `agent runtime`
   - AI 导学助手已有：
     - `backend/src/main/java/com/alethicode/controller/AITutorWorkflowController.java`
     - `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
   - AI 问答已有：
     - `backend/src/main/java/com/alethicode/controller/LanguagePackQaController.java`
     - `backend/src/main/java/com/alethicode/service/languagepack/impl/LanguagePackQaServiceImpl.java`
   - 通用模型循环已有：
     - `backend/src/main/java/com/alethicode/service/LlmClient.java`

2. `client/runtime integration`
   - WebSocket 运行时事件推送已有：
     - `backend/src/main/java/com/alethicode/websocket/WorkflowRealtimeSupport.java`
   - Web 前端调用入口已有：
     - workflow API
     - language-pack-qa API

3. `failure recovery / human approval`
   - checkpoint / interrupt / admin 恢复基础已有：
     - `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
   - 审批动作枚举已有：
     - `backend/src/main/java/com/alethicode/service/aitutor/contract/PendingHumanAction.java`

4. `task lifecycle / traceability`
   - agent 任务生命周期记录已有：
     - `backend/src/main/java/com/alethicode/service/aitutor/agent/AgentTaskTracker.java`
   - tutor / QA harness 基础已有：
     - `backend/src/main/java/com/alethicode/service/aitutor/eval/TutorEvalHarness.java`
     - `backend/src/main/java/com/alethicode/service/aitutor/eval/QaEvalHarness.java`

#### 当前缺口

1. 缺少统一的 `RuntimeState` 枚举
   - 当前导学、QA、WebSocket、管理端各自有状态语义，但还没有共享的运行时状态枚举

2. 缺少统一的 `runtime contract` 数据结构
   - 当前有 session、task、checkpoint、trace 的局部字段
   - 但还没有一套对 Web 前端、管理端、回放入口统一暴露的稳定 contract

3. 缺少完整的 lifecycle contract
   - 当前已有 interrupt / checkpoint restore
   - 但 `queued / waiting_tool / expired / orphan task` 这些状态还没有被明确建模

4. 缺少恢复后的强一致约束
   - 当前恢复能力存在
   - 但还没明确要求恢复后必须保留 `trace_id / checkpoint_id / approval_state / context source`

5. 缺少 QA 与导学并列的 runtime 事件协议说明
   - 当前两条链路已经分开
   - 但还没有正式写成“Problem 页消费什么事件，QA 页消费什么事件”的契约

#### 第一阶段落地（最短闭环）

1. 在后端新增统一运行时枚举与 contract 类型
   - 例如放在 `backend/src/main/java/com/alethicode/service/aitutor/contract/`

2. 让 `AITutorWorkflowAdminServiceImpl` 成为导学 runtime contract 的首个落点
   - 先把 `session_id / checkpoint_id / runtime_state / approval_state / trace_id` 明确输出

3. 让 `LanguagePackQaServiceImpl` 成为 QA runtime contract 的首个落点
   - 先补 `session_id / task_id / runtime_state / trace_id / failure_bucket`

4. 让 `WorkflowRealtimeSupport` 只推送标准化 `server_event`
   - 不再依赖松散 payload 约定

5. 让 `AgentTaskTracker` 与 harness trace 对齐
   - 先补 task state 到 runtime state 的映射关系

#### 完全实现范围（目标上限）

这里的“第一阶段落地”不是最终目标，只是为了先把 harness 的核心运行时协议闭合起来。

如果按“最大程度实现”推进，本阶段之后还应继续完成以下内容：

1. 完整实现统一 runtime contract
   - 导学与 QA 都输出统一基础字段
   - Web、管理端、回放入口都消费同一套 contract

2. 完整实现 lifecycle state model
   - `queued / running / waiting_tool / waiting_human_approval / interrupted / restoring / failed / completed / expired`
   - 每个状态都有明确 TTL、进入条件、迁移条件、持久化字段

3. 完整实现 recovery contract
   - checkpoint restore
   - approval resume
   - interrupted resume
   - restart recovery
   - stale task / orphan task cleanup

4. 完整实现标准化 `server_event`
   - WebSocket 不再发送松散业务 payload
   - 所有运行时事件都以 `server_event + runtime_state + identifiers` 驱动前端

5. 完整实现前端运行时界面协议
   - Problem 页完整消费导学 runtime 状态
   - QA 页完整消费 QA runtime 状态
   - 恢复态、失败态、审批态、过期态全部可视化

6. 完整实现 Harness 报表与回放闭环
   - trace 聚合
   - failure bucket 聚合
   - dataset eval
   - grader
   - gray rollout gate
   - replay by `task_id / trace_id`

7. 完整实现导学与 QA 的边界约束
   - 共用基础设施
   - 不共用业务协议
   - 不共用页面事件协议
   - 不共用恢复语义

#### 结论

这部分不是从零开始重写。

项目当前已经具备：

- workflow runtime
- QA runtime
- WebSocket 推送
- checkpoint / interrupt / approval
- eval harness

真正缺的是把这些现有能力 **收敛为统一的 runtime contract、lifecycle state model 和 recovery contract**，而不是再造一套新系统。

这里的“第一阶段落地”只是首个可闭环版本，不是能力上限。  
如果你要按“最大程度实现”推进，文档已经允许继续向“完全实现范围”收敛，而不是停留在最小版本。

#### 后端任务清单

1. 新增统一 runtime contract 类型
   - 新增 `RuntimeState`
   - 新增 `RuntimeContract`
   - 新增 `ServerEvent`
   - 新增 `RecoveryReason` 或等价结构化恢复原因

2. 收敛导学 runtime 输出
   - 在 `AITutorWorkflowAdminServiceImpl` 中统一输出：
     - `session_id`
     - `task_id`
     - `checkpoint_id`
     - `trace_id`
     - `runtime_state`
     - `approval_state`
     - `failure_bucket`

3. 收敛 QA runtime 输出
   - 在 `LanguagePackQaServiceImpl` 中补齐：
     - `session_id`
     - `task_id`
     - `trace_id`
     - `runtime_state`
     - `failure_bucket`

4. 规范 WebSocket 事件协议
   - 让 `WorkflowRealtimeSupport` 只推送标准化 `server_event`
   - 统一事件载荷字段
   - 禁止业务层直接拼接随意 payload

5. 建立 runtime state 持久化规则
   - queued / running / waiting_human_approval / interrupted / restoring / failed / completed / expired
   - 明确每个状态最少要落哪些字段

6. 建立恢复入口约束
   - checkpoint restore
   - interrupted resume
   - approval resume
   - restart recovery

7. 让 `AgentTaskTracker` 对齐 runtime state
   - 先建立 task state 到 runtime state 的映射
   - 再补 trace_id / failure_bucket 关联

8. 让 harness 报表读取统一字段
   - `TutorEvalHarness`
   - `QaEvalHarness`
   - `TraceGradeService`
   - `RolloutPolicyService`

#### 前端任务清单

1. 明确 Problem 页 runtime 消费协议
   - 只消费导学 workflow runtime 事件
   - 不消费 QA runtime 事件

2. 明确 QA 页 runtime 消费协议
   - 只消费 QA session / retrieval-grounded runtime 事件
   - 不消费导学 workflow phase 事件

3. 统一前端状态字段
   - session id
   - task id
   - runtime state
   - checkpoint id
   - approval state
   - failure bucket

4. 对齐 WebSocket 事件处理逻辑
   - 前端不再依赖“猜字段”
   - 只按 `server_event + runtime_state` 更新界面

5. 增加恢复态展示
   - `interrupted`
   - `restoring`
   - `waiting_human_approval`
   - `expired`

6. 增加失败态展示
   - 结构化展示 failure bucket
   - 区分“系统失败”“证据不足”“人工审批等待”“恢复中”

7. 固定页面边界
   - Problem 页不展示 QA grounded citation 交互协议
   - QA 页不展示导学 phase / scaffold / transfer 审批协议

#### 验收测试清单

1. 后端 contract 测试
   - 导学接口返回统一 runtime contract 字段
   - QA 接口返回统一 runtime contract 字段
   - runtime_state 只能来自约定枚举

2. 生命周期测试
   - queued -> running
   - running -> waiting_tool
   - running -> waiting_human_approval
   - interrupted -> restoring -> running
   - waiting_human_approval -> expired

3. 恢复测试
   - checkpoint restore 后 `trace_id / checkpoint_id / approval_state` 不丢失
   - interrupted resume 后不会重复执行已完成步骤
   - restart recovery 后任务能回到正确状态

4. 前端协议测试
   - Problem 页不会消费 QA 事件
   - QA 页不会消费导学事件
   - WebSocket 事件字段缺失时 fail-fast 暴露错误

5. Harness 报表测试
   - 报表能按 `runtime_state / failure_bucket / trace_id` 聚合
   - 回放入口能通过 `task_id / trace_id` 复现一次执行

6. 灰度门禁测试
   - 新策略在 dataset 不达标时不能进入 gray
   - 发生漂移时能从报表中定位到具体 layer

7. 边界测试
   - QA 恢复流程不能切回导学 runtime
   - 导学恢复流程不能切回 QA runtime
   - 两个界面的 contract 共享基础字段，但不共享业务协议

#### 执行建议

最短路径建议按以下顺序推进：

1. 先做后端统一 contract
2. 再做 WebSocket 事件标准化
3. 再做前端消费协议收敛
4. 最后补恢复测试、报表测试和灰度门禁

这样可以确保先把运行时协议稳定下来，再去收口前端和评测链路。

### 主要落点

- `backend/src/main/java/com/alethicode/service/aitutor/eval/TutorEvalHarness.java`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/QaEvalHarness.java`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/TraceGradeService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/OffPolicyEvalService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/rollout/RolloutPolicyService.java`
- `backend/src/main/java/com/alethicode/websocket/`
- `backend/src/main/java/com/alethicode/controller/`

### 测试

- 任一 dataset 样本都可输出 trace-aware 评分结果。
- rollout 决策必须引用明确指标输入。
- 回归失败时能定位是 memory、retrieval、tool、critic 还是 routing 出错。
- 灰度门禁对低质量样本有效。
- Spring AI tracing 数据能进入统一 Harness 报表，而不是独立孤岛。
- Web 前端、管理端、回放入口使用同一套 runtime identifiers。
- 重启后 `waiting_human_approval / interrupted` 任务可以按 contract 恢复。
- stale task / orphan task 能被正确标记而不是无限悬挂。

### 验收标准

- 评测成为上线前硬门槛，而不是事后观察项。
- trace 不再只是日志，而是评估输入。
- 任何策略变更都能回归验证。
- 质量问题可以归因到具体阶段而不是“模型不稳定”。
- Spring AI 的 observability 能真实降低 AI 链路排障成本，而不是只新增埋点复杂度。
- harness 对 WebSocket、workflow API、QA API、回放入口暴露稳定运行时协议。
- 长任务在重启、恢复、审批等待等状态下具备明确生命周期语义。

---

## Phase 5：Human-in-the-Loop 扩展与可控自治

**适用界面**：以 AI 导学助手为主，AI 问答仅使用受控追问子集
**阶段属性**：Harness 本体（兼具业务自治能力建设）

### 目标

把当前 `pending_human_action + checkpoint + interrupt` 从点状功能扩展为可控自治框架。

### 实现流程

1. 扩展审批点定义：
   - `confirm_scaffold`
   - `confirm_transfer`
   - `confirm_memory_save`
   - `confirm_high_risk_tool_use`
   - `confirm_retrieval_override`
2. 在导学链路补高风险暂停条件：
   - 可能泄题
   - 大跨度 phase 跳转
   - 记忆保存
   - critic 连续失败
3. 在 QA 链路补受控追问分支：
   - 证据不足时不强答
   - 请求用户缩小范围
   - 请求指定章节/文档
4. 加入 stopping conditions：
   - 最大迭代数
   - 重复工具调用次数
   - critic fail 次数
   - 超时阈值
5. checkpoint 标签规范化：
   - phase
   - context source
   - evidence source
   - approval state
6. 将 `orchestrator-workers` 只用在复杂错误诊断，不扩散到 QA 自由对话。
7. 将 `evaluator-optimizer` 只用在高价值节点：
   - ERROR_FEEDBACK
   - AC_REVIEW
   - grounded QA refinement
8. 明确 QA 的 HITL 边界：
   - QA 只允许“缩小问题范围 / 指定章节 / 继续拒答”的受控交互
   - QA 不引入教学 phase 式人工审批流
9. 明确 lifecycle / recovery 规则：
   - `running` 超时后进入 `failed` 或 `interrupted`
   - `waiting_human_approval` 超过阈值后进入 `expired`
   - `restoring` 必须带原始 `checkpoint_id / trace_id`
   - 恢复后禁止丢失审批状态与上下文来源
10. 明确 client integration 边界：
   - Problem 页只消费导学 workflow runtime 事件
   - QA 页只消费 retrieval-grounded QA runtime 事件
   - 管理端只通过 admin/runtime 入口做审批、恢复、回放，不直接调用业务 agent 内部方法

### 主要落点

- `backend/src/main/java/com/alethicode/service/aitutor/contract/PendingHumanAction.java`
- `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
- `backend/src/main/java/com/alethicode/controller/AITutorWorkflowController.java`
- `frontend/src/pages/oj/views/problem/`
- `frontend/src/pages/oj/views/languagepack/`

### 测试

- 高风险工具调用会进入审批而不是直接执行。
- critic 连续失败会中断，不会死循环。
- checkpoint 恢复后状态与审批信息一致。
- QA 在证据不足时稳定引导缩小问题范围。
- 任务恢复后 `checkpoint_id / trace_id / approval_state` 关联不丢失。
- Problem 页与 QA 页不会消费对方的 runtime 事件协议。

### 验收标准

- Agent 行为更强，但不会更失控。
- 用户和教师都能看懂“为什么停在这里”。
- 复杂诊断可以拆 worker，但不会把系统变成不可控多 Agent 网络。
- 自治能力与可调试性同时提升。
- 生命周期状态、恢复动作、前端消费协议三者保持一致，不出现“恢复了但前端不知道当前处于什么状态”的问题。

---

## 最大程度实现排期（建议）

### 排期假设

- 按“单人主导、以后端为主、前端同步收口”的方式估算
- 以“尽量做满完整 harness 能力”作为目标，而不是只追求首个可运行闭环
- 若中途不插入大块新需求，完整实现建议按 `12-16 周` 规划

### Sprint 1：边界冻结与基础设施基线

**建议周期**：第 1-2 周

**对应范围**：
- Phase 0
- Phase 0.5

**目标**：
- 完成文档边界冻结
- 建立 Spring AI 试点基线
- 打通 observability tracing
- 固定术语表、contract 命名和目录落点

**必须交付**：
- `docs/todos/todo-agent-harness/` 文档骨架
- Spring AI 依赖与配置基线
- `LlmClient` 双实现可验证入口
- tracing 可被现有监控链路消费

### Sprint 2：上下文、记忆与运行时 contract 基础

**建议周期**：第 3-4 周

**对应范围**：
- Phase 1
- Phase 4 中 runtime contract 基础部分

**目标**：
- 完成 context layering
- 收敛 `RuntimeState / RuntimeContract`
- 明确导学与 QA 的主上下文来源
- 建立记忆候选、写入决策与状态管理基础

**必须交付**：
- 统一上下文命名
- QA 结构化 session context
- learner memory 候选写入机制
- 后端统一 runtime contract 类型定义

### Sprint 3：工具治理、事件协议与前端消费收敛

**建议周期**：第 5-6 周

**对应范围**：
- Phase 3
- Phase 4 中 client/runtime integration 基础部分

**目标**：
- 完成 ToolContext 与工具域隔离
- 标准化 `server_event`
- 收敛 Problem 页与 QA 页的 runtime 消费协议

**必须交付**：
- 导学工具域与 QA 工具域彻底拆开
- `WorkflowRealtimeSupport` 推送标准化事件
- 前端只按 `server_event + runtime_state` 更新状态
- 工具 trace、guard、ACI 文档基本齐备

### Sprint 4：QA RAG 治理、回放与 Spring AI 试点深化

**建议周期**：第 7-9 周

**对应范围**：
- Phase 2

**目标**：
- 把 QA 从“能回答”推进到“可量化、可回放、可对比”
- 深化 Spring AI 在 QA 侧的 memory / advisor / PGVector / evaluator 试点

**必须交付**：
- query rewrite / decomposition / reference resolution
- retrieval trace + synthesis trace + critic verdict
- QA dataset + replay 入口
- 当前实现 vs Spring AI 试点实现对比报告

### Sprint 5：Harness 主体闭环与生命周期治理

**建议周期**：第 10-12 周

**对应范围**：
- Phase 4 主体部分

**目标**：
- 完成 lifecycle state model
- 完成 recovery contract
- 完成灰度门禁与报表聚合
- 让 harness 真正成为统一执行治理层

**必须交付**：
- `queued / running / waiting_tool / waiting_human_approval / interrupted / restoring / failed / completed / expired`
- checkpoint / approval / restart / interrupted resume 全恢复链路
- failure bucket 聚合
- rollout gate 接入离线 harness

### Sprint 6：HITL 扩展、自治强化与边界硬化

**建议周期**：第 13-14 周

**对应范围**：
- Phase 5

**目标**：
- 完成审批点扩展
- 完成高风险动作暂停与恢复
- 完成 QA 受控追问边界
- 完成恢复态、失败态、审批态的前端闭环

**必须交付**：
- `confirm_scaffold / confirm_transfer / confirm_memory_save / confirm_high_risk_tool_use / confirm_retrieval_override`
- 导学高风险节点暂停
- QA 受控拒答与缩小范围引导
- Problem 页与 QA 页恢复协议完全隔离

### Sprint 7：全面收口与答辩级交付

**建议周期**：第 15-16 周

**对应范围**：
- 全局收口

**目标**：
- 补齐剩余测试、报表、文档和演示链路
- 把系统从“能开发”推进到“能展示、能答辩、能长期维护”

**必须交付**：
- 全量验收测试通过
- 核心演示链路稳定
- 失败回放可演示
- 文档、实现、评测、运行时协议完全对齐

### 全量目标的优先级原则

如果以“最大程度实现”为目标，优先级必须始终保持如下顺序：

1. 先完成运行时 contract 与 lifecycle
2. 再完成工具治理与事件协议
3. 再完成 QA 治理与 replay
4. 再完成灰度门禁与报表
5. 最后把 HITL、自主性和演示体验做满

原因是：

- 前三项决定 harness 是否真正成立
- 第四项决定系统是否可运营
- 第五项决定系统是否足够完整和可展示

---

## 实施与验收勾选清单

### Sprint 1：边界冻结与基础设施基线

- [ ] `docs/todos/todo-agent-harness/` 文档骨架创建完成
- [ ] Spring AI 依赖与配置基线建立完成
- [ ] `LlmClient` 双实现可验证入口建立完成
- [ ] tracing 数据可进入现有监控链路
- [ ] Spring AI 迁入范围与保留自研范围在文档中完全对齐

### Sprint 2：上下文、记忆与运行时 contract 基础

- [ ] 统一上下文命名完成
- [ ] QA 结构化 session context 完成
- [ ] learner memory 候选写入机制完成
- [ ] `RuntimeState / RuntimeContract` 基础类型定义完成
- [ ] 导学与 QA 主上下文来源边界明确落地

### Sprint 3：工具治理、事件协议与前端消费收敛

- [ ] 导学工具域与 QA 工具域彻底拆开
- [ ] `ToolContext`、guard、trace、ACI 文档完成
- [ ] `WorkflowRealtimeSupport` 推送标准化 `server_event`
- [ ] Problem 页只消费导学 runtime 事件
- [ ] QA 页只消费 QA runtime 事件
- [ ] 前端按 `server_event + runtime_state` 更新状态

### Sprint 4：QA RAG 治理、回放与 Spring AI 试点深化

- [ ] query rewrite / decomposition / reference resolution 完成
- [ ] retrieval trace + synthesis trace + critic verdict 完成
- [ ] QA dataset 建立完成
- [ ] QA replay 入口建立完成
- [ ] 当前实现 vs Spring AI 试点实现对比报告完成
- [ ] QA refusal / grounding / citation 指标可量化输出

### Sprint 5：Harness 主体闭环与生命周期治理

- [ ] `queued / running / waiting_tool / waiting_human_approval / interrupted / restoring / failed / completed / expired` 全部落地
- [ ] runtime contract 字段统一输出完成
- [ ] recovery contract 完成
- [ ] checkpoint / approval / restart / interrupted resume 全恢复链路完成
- [ ] failure bucket 聚合完成
- [ ] rollout gate 接入离线 harness 完成

### Sprint 6：HITL 扩展、自治强化与边界硬化

- [ ] `confirm_scaffold / confirm_transfer / confirm_memory_save / confirm_high_risk_tool_use / confirm_retrieval_override` 完成
- [ ] 导学高风险节点暂停机制完成
- [ ] QA 受控拒答与缩小范围引导完成
- [ ] Problem 页与 QA 页恢复协议完全隔离
- [ ] 恢复态、失败态、审批态前端闭环完成

### Sprint 7：全面收口与答辩级交付

- [ ] 全量验收测试通过
- [ ] 核心演示链路稳定
- [ ] 失败回放可演示
- [ ] 文档、实现、评测、运行时协议完全对齐
- [ ] 答辩表达可清晰说明 Harness、Agent、Spring AI 三者边界

### 全局功能验收

- [ ] 导学与 QA 都能输出 trace、context snapshot、可解释失败原因
- [ ] 记忆、检索、工具调用不再是黑箱
- [ ] rollout 决策有明确指标来源
- [ ] QA 问答具备 grounded、refusal、citation 三类可解释结果
- [ ] 导学工作流具备审批、恢复、受控自治能力

### 全局工程验收

- [ ] 每阶段都有对应文档、测试、CHANGELOG 记录
- [ ] 每个新增核心能力都带 dataset 与 grader
- [ ] 没有新增“只靠 prompt 撑住”的黑盒逻辑
- [ ] WebSocket、workflow API、QA API、回放入口暴露稳定运行时协议
- [ ] 长任务具备明确生命周期语义，不会无限悬挂

### 全局边界验收

- [ ] 导学与 QA 共用基础设施，但不共用业务协议
- [ ] Problem 页不会消费 QA runtime 事件
- [ ] QA 页不会消费导学 workflow phase 事件
- [ ] QA 恢复流程不能切回导学 runtime
- [ ] 导学恢复流程不能切回 QA runtime

### 全局业务验收

- [ ] 导学回答的教学适切性更稳定
- [ ] QA 的拒答与引用更可信
- [ ] 系统整体更像“可运营 Agent 平台”，而不是“能力点堆叠”
- [ ] 文档、实现、演示三者表达一致

---

## 统一实现顺序

1. Phase 0
2. Phase 1
3. Phase 3
4. Phase 2
5. Phase 4
6. Phase 5

**顺序理由**：

- 先落盘边界。
- 再统一 context。
- 再治理 tools。
- 再升级 RAG。
- 然后接 Harness 门禁。
- 最后扩 Human-in-the-Loop。

---

## 统一验收标准

### 功能验收

- 导学与 QA 都能输出 trace、context snapshot、可解释失败原因。
- 记忆、检索、工具调用不再是黑箱。
- rollout 决策有明确指标来源。

### 工程验收

- 每阶段都有对应文档、测试、CHANGELOG 记录。
- 每个新增核心能力都带 dataset 与 grader。
- 没有新增“只靠 prompt 撑住”的黑盒逻辑。

### 业务验收

- 导学回答的教学适切性更稳定。
- QA 的拒答与引用更可信。
- 整体系统更像“可运营 Agent 平台”，而不是“堆能力点”。

---

## 默认假设

- 当前采用“自研业务骨架 + Spring AI 基础设施渐进迁入”的混合模式。
- WebSocket 继续保留，实时链路不切 SSE。
- MCP 仅做未来对外开放候选，不进内部主调用栈。
- 本次所有文档落点都以 `docs/todos/todo-agent-harness/` 为中心。
- 进入实现阶段后，每完成一个阶段都同步更新 `CHANGELOG.md`。
- Spring AI 的优先迁入范围是：模型调用层、通用 RAG 基建、Observability。
- QA 是 Spring AI memory / advisor / PGVector 的首个试点场景。
