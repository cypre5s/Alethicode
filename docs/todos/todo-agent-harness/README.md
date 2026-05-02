# Agent + Harness 工程路线图

> 本目录是 Agent + Harness 工程的唯一执行入口。所有阶段文档、验收标准和术语定义都以本目录为准。

## 范围

### 做什么

- 做题导学（AI 导学助手）的记忆系统、工具治理、评测闭环、灰度门禁
- 课件 QA（AI 问答）的 RAG 治理、检索质量评测、拒答协议、回放入口
- 统一运行时 contract、lifecycle 状态机和 recovery 协议
- Spring AI 渐进迁入（模型调用层、通用 RAG 基建、Observability）

### 不做什么

- 不重写教学 FSM、`OrchestratorAgent`、`ReflectionService`、checkpoint/interrupt 协议
- 不把 Spring AI 变成新的业务主架构
- 不做 SSE 替换（WebSocket 继续保留）
- 不做 MCP 内部主调用栈化（仅标记未来候选）
- 不做一次性整体重构

## 阶段依赖关系

```
Phase 0（边界冻结）
  └── Phase 0.5（Spring AI 基线）
        └── Phase 4 基础（Runtime Contract）
              ├── Phase 1（Context & Memory）
              │     └── Phase 3（ToolContext & 工具治理）
              │           └── Phase 2（RAG & QA Harness）
              └── Phase 4 完整（Harness 主体闭环）
                    └── Phase 5（HITL & 可控自治）
```

实施顺序：Phase 0 → 0.5 → 4基础 → 1 → 3 → 2 → 4完整 → 5

## 统一术语表

| 术语 | 定义 | 所属层 |
|------|------|--------|
| `ToolContext` | 工具执行时的上下文环境（userId, sessionId, problemId, languagePackId, phase, event, locale, permissions） | Harness 本体 |
| `AgentTrace` | 一次 agent 任务执行的完整追踪记录（包含 tool calls, iterations, context snapshots） | Harness 本体 |
| `RetrievalTrace` | 一次检索执行的完整追踪（query, rewrite, hits, scores, latency） | 业务能力 |
| `MemoryCandidate` | 待决策的记忆候选项（memory_key, summary, confidence, source, scope） | 业务能力 |
| `TraceGradeResult` | trace 级别的质量评分结果（schema_pass, pedagogy_pass, helpfulness, answer_leak） | Harness 本体 |
| `RuntimeState` | 统一运行时状态枚举（QUEUED, RUNNING, WAITING_TOOL, WAITING_HUMAN_APPROVAL, INTERRUPTED, RESTORING, FAILED, COMPLETED, EXPIRED） | Harness 本体 |
| `RuntimeContract` | 对前端、管理端、回放入口统一暴露的运行时契约 | Harness 本体 |
| `ServerEvent` | 标准化服务端推送事件 | Harness 本体 |
| `FailureBucket` | 结构化失败分类 | Harness 本体 |
| `RecoveryReason` | 结构化恢复原因 | Harness 本体 |
| `MemorySaveDecision` | 记忆保存决策（SAVE / DEFER / DISCARD） | 业务能力 |
| `SessionContext` | QA 会话的结构化上下文（recent_messages, summary, recent_citations） | 业务能力 |
| `SynthesisTrace` | QA 答案合成的追踪（answer, grounding_verdict, critic_result） | 业务能力 |
| `EvidencePack` | 导学证据包（problem, workflow, submission, code, learner_state, courseware, similar_errors, retrieval） | 业务能力 |

## 文档层级分类规则

| 标记 | 含义 |
|------|------|
| Harness 本体 | 执行与治理层本身（agent loop, tool governance, runtime contract, rollout gate, checkpoint, approval, replay, grader） |
| 业务能力建设 | AI 导学助手或 AI 问答的能力增强（错误诊断, 脚手架, query rewrite, grounding critic, learner memory） |
| 基础设施迁移 | 底层通用实现的接入或替换（Spring AI ChatClient / EmbeddingModel / Observability / PGVector） |

## AI 导学助手 与 AI 问答 的边界

### 业务定位

| 维度 | AI 导学助手（做题界面） | AI 问答（独立界面） |
|------|------------------------|---------------------|
| 面向 | 题目求解过程 | 课件知识问答 |
| 强绑定 | problem / submission / phase / learner_state | language_pack / session / citations |
| 记忆 | 允许 learner long-term memory | 不以 learner long-term memory 作为主回答依据 |
| 审批 | 允许 pending_human_action / checkpoint / interrupt | 不进入教学式 phase checkpoint |
| 工具域 | search_courseware / search_similar_errors / get_learner_history | search_language_pack_pages |

### 基础设施共用

模型调用层、Spring AI adapter、Observability/tracing、Trace/Harness 框架、Tool governance 基础规范、部分短期会话 memory 机制。

### 禁止交叉污染

- QA 不得调用导学 phase/event 工作流
- 导学不得退化为"课件聊天窗口"
- QA 不得输出 learner long-term memory 推断作为答案依据
- 导学不得把 grounded citation 协议作为主输出协议
- 导学工具与 QA 工具必须工具域隔离

### 评测维度

| AI 导学助手 | AI 问答 |
|-------------|---------|
| pedagogy fit | retrieval recall |
| answer leakage | grounding accuracy |
| action appropriateness | refusal correctness |
| scaffold quality | citation precision |
| learner fit | answer completeness |

## Spring AI 渐进迁移策略

### 为什么不整体转向 Spring AI

项目已有成熟的 `LlmClient / ReAct / Reflection / OrchestratorAgent` 主链路。教学 FSM、checkpoint/interrupt 协议、WebSocket 实时推送等核心业务逻辑与 Spring AI 无直接对应关系。整体转向会引入不必要的重构风险和业务中断。

### 为什么优先迁入模型调用层、通用 RAG 基建和 Observability

1. **模型调用层**：当前 `LlmClient` 自维护 HTTP 请求、重试、provider 适配。Spring AI `ChatClient / ChatModel / EmbeddingModel` 提供标准化的模型调用抽象，减少维护成本。
2. **通用 RAG 基建**：Spring AI 的 `Advisors / VectorStore / PGVector / Evaluator` 可以标准化 QA 管线中的通用部分，同时保留业务拒答协议等自研逻辑。
3. **Observability**：项目已有 `actuator / prometheus` 依赖。Spring AI 的 observability 可以直接把 AI 调用接入 Micrometer tracing，无需从零搭建。

### 为什么先在 QA 模块试点

QA 模块的 memory/advisor/检索链路相对独立，与教学 FSM 无耦合。在 QA 做试点可以验证 Spring AI 集成效果，同时不影响导学主链路。

### 迁入范围

| 迁入 | 保留自研 |
|------|----------|
| LlmClient 下层 provider adapter | 教学 phase/event 状态机 |
| QA memory / advisor / PGVector 试点 | OrchestratorAgent 编排 |
| Spring AI observability tracing | 自定义 Reflection 质检 |
| | WebSocket 实时推送 |

## 阶段文档索引

| 文件 | 内容 |
|------|------|
| [phase-0-5-spring-ai-baseline.md](phase-0-5-spring-ai-baseline.md) | Phase 0.5：Spring AI 试点基线建立 |
| [phase-1-context-memory.md](phase-1-context-memory.md) | Phase 1：Context Layering 与 Memory 升级 |
| [phase-2-rag-harness.md](phase-2-rag-harness.md) | Phase 2：RAG 治理与 QA Harness 升级 |
| [phase-3-tools-trace-rollout.md](phase-3-tools-trace-rollout.md) | Phase 3：ToolContext、工具治理与 ACI 文档化 |
| [phase-4-hitl-and-agent-runtime.md](phase-4-hitl-and-agent-runtime.md) | Phase 4 + Phase 5：Harness 主体闭环与 HITL |
| [progress.md](progress.md) | 实施进度追踪 |

## 默认假设

- 采用"自研业务骨架 + Spring AI 基础设施渐进迁入"的混合模式
- WebSocket 继续保留，实时链路不切 SSE
- MCP 仅做未来对外开放候选，不进内部主调用栈
- 本次所有文档落点都以 `docs/todos/todo-agent-harness/` 为中心
- 进入实现阶段后，每完成一个阶段都同步更新 `CHANGELOG.md`
- Spring AI 优先迁入范围：模型调用层、通用 RAG 基建、Observability
- QA 是 Spring AI memory/advisor/PGVector 的首个试点场景
- 项目中 ReAct 默认关闭
