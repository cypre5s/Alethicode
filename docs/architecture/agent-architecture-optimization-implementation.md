# Agent Architecture Optimization — 实现说明书

> 对应计划文件：`agent_architecture_optimization_99cd2180.plan.md`
> 实施日期：2026-04-02
> 状态：全部 9 项 TODO 已完成代码落地

---

## 一、实施概览

本次实现基于五大模式（ReAct、Reflection、Agentic Workflows、A2A、Harness）对系统中两个独立的 AI 交互界面进行分层优化，共涉及 22 个新增文件和 7 个修改文件。所有改造在现有 FSM / CardSchema 框架内进行，不改变外部 API。

### 界面归属

本次优化涉及的两个用户界面：

| 界面 | 前端入口 | 后端入口 | 核心服务 |
|------|---------|---------|---------|
| **做题界面 AI 导学面板** | `Problem.vue` → `UnifiedAgentPanel.vue` | `AITutorController` / `AITutorWorkflowController` | `AITutorWorkflowAdminServiceImpl` |
| **课件问答页** | `/language-pack-qa` → `LanguagePackQaPage.vue` | `LanguagePackQaController` | `LanguagePackQaServiceImpl` → `AnswerSynthesisServiceImpl` |

### 改造全景

```
=== 共享基础设施 ===
P0 ─ ReAct 基础设施 ──────────── LlmClient.callWithTools           [两者共用]
P0 ─ Reflection 框架 ────────── ReflectionService (Producer-Critic) [两者共用]

=== 做题界面 AI 导学面板 ===
P1 ─ ERROR_FEEDBACK ReAct ───── DiagnosticsAgent + 3 工具 (search_courseware,
                                search_similar_errors, get_learner_history)
P2 ─ Agent 化重构 ───────────── TutorAgent + 5 个独立 Agent + OrchestratorAgent
P2 ─ Scaffolding Reflection ── ScaffoldingAgent 内嵌 Reflection
P3 ─ A2A 概念模型 ───────────── AgentCapability + AgentTaskTracker
P3 ─ A/B 测试框架 ───────────── RolloutPolicyService.assignAbTest + recordReward

=== 课件问答页 ===
P1 ─ QA 自适应检索 ──────────── AnswerSynthesisServiceImpl + search_language_pack_pages
P1 ─ Grounding Critic ────────── 独立 Critic LLM 验证答案事实性

=== 跨界面评估 ===
P1 ─ 离线评估 Harness ────────── TutorEvalHarness (做题界面)
                                QaEvalHarness (课件问答页)
```

---

## 二、各模块详细说明

### 2.1 ReAct 基础设施（P0）— 共享基础设施，服务两个界面

**新增文件：**
- `service/aitutor/react/ToolDefinition.java` — 工具定义 record，镜像 OpenAI function-calling tool schema
- `service/aitutor/react/ToolExecutor.java` — 函数式接口，执行工具并返回可序列化结果
- `service/aitutor/react/ReactResult.java` — 循环执行结果，含迭代计数和工具调用日志
- `service/aitutor/react/TutorToolRegistry.java` — 工具工厂，封装 4 个内部工具的 Definition + Executor

**修改文件：**
- `service/LlmClient.java` — 新增 `callWithTools()` 方法

**工作原理：**
```
callWithTools(systemPrompt, messages, tools, executors, maxIterations)
│
├─ 构建 transcript = [system, ...messages]
├─ 循环 (1..maxIterations):
│   ├─ 发送请求 (含 tools spec)
│   ├─ 解析响应
│   ├─ if finish_reason == "tool_calls":
│   │   ├─ 提取 tool_calls
│   │   ├─ 执行每个 tool -> 得到 observation
│   │   ├─ 追加 assistant message (tool_calls) + tool messages
│   │   └─ 继续循环
│   └─ else:
│       └─ 解析 content 为 JSON -> 返回 ReactResult
└─ 超过 maxIterations -> 抛出异常
```

**环境变量配置：**
- `LLM_TOOL_USE_PROMPT_FALLBACK=true` — 若模型不支持原生 function-calling，回退为 prompt-based tool-use
- `TUTOR_REACT_MAX_ITERATIONS=4` — ERROR_FEEDBACK 的 ReAct 最大迭代数
- `QA_REACT_MAX_ITERATIONS=3` — QA 的 ReAct 最大迭代数

**内部工具列表：**

| 工具名 | 作用 | 适用场景 |
|--------|------|---------|
| `search_courseware` | 按 KC/章节/关键词检索课件 | ERROR_FEEDBACK |
| `search_similar_errors` | 向量检索相似错误 | ERROR_FEEDBACK |
| `search_language_pack_pages` | 检索语言包页面 | QA |
| `get_learner_history` | 获取学习者最近 N 次提交 | ERROR_FEEDBACK |

---

### 2.2 Reflection 框架（P0）— 共享基础设施，服务两个界面

**新增文件：**
- `service/aitutor/reflection/ReflectionService.java` — 接口
- `service/aitutor/reflection/ReflectionResult.java` — 结果 record
- `service/aitutor/reflection/ReflectionServiceImpl.java` — 实现

**工作原理（Producer-Critic 模型）：**
```
initialOutput (Producer)
     │
     ▼
Critic LLM ── 按 CardType 分维度评估
     │
     ├─ pass=true → 直接输出
     └─ pass=false → Refine LLM (根据 feedback 修正) → 输出
```

**评估维度按 CardType 定制：**

| CardType | 维度 |
|----------|------|
| ERROR_DIAGNOSIS | 事实一致性、教学适切性、schema完整性、答案泄露检测 |
| WORKED_EXAMPLE / FADED_EXAMPLE | 事实一致性、scaffold_level 与 mastery 匹配、schema完整性、答案泄露 |
| POST_AC | 事实一致性、教学适切性、schema完整性、逻辑相关性 |

**启用位置：**
- ERROR_FEEDBACK → 强制启用
- SCAFFOLDING → 强制启用
- AC_REVIEW → 强制启用
- CHAT → 跳过（延迟敏感）

---

### 2.3 ERROR_FEEDBACK ReAct 改造（P1）— 做题界面 AI 导学面板

**修改文件：**
- `service/impl/AITutorWorkflowAdminServiceImpl.java`

**改造方式：**
- `buildErrorDiagnosisPayload` 检查环境变量 `TUTOR_REACT_ENABLED`
- 启用时调用 `generateErrorDiagnosisViaReact`：LLM 可主动调用 3 个工具补充上下文
- 生成后无论 ReAct 是否启用，都经过 `ReflectionService.reflectAndRefine` 质检

**开关：**
```bash
export TUTOR_REACT_ENABLED=true  # 默认 false
```

---

### 2.4 QA 自适应检索（P1）— 课件问答页

**修改文件：**
- `service/languagepack/AnswerSynthesisService.java` — 新增 `synthesizeAnswer(question, hits, languagePackId)` 重载
- `service/languagepack/impl/AnswerSynthesisServiceImpl.java` — 新增 ReAct + grounding critic
- `service/languagepack/impl/LanguagePackQaServiceImpl.java` — 传递 `languagePackId`

**改造方式：**
- 初始检索 4 条 → `callWithTools` 中 LLM 可调用 `search_language_pack_pages` 补充检索
- 生成后新增独立的 grounding critic：验证答案是否真正基于引用的证据页
- critic 判定 grounded=false 时降级为 refusal

**开关：**
```bash
export QA_REACT_ENABLED=true  # 默认 false
```

---

### 2.5 离线评估 Harness（P1）— 跨界面

**新增文件：**
- `service/aitutor/eval/EvalDimension.java` — 8 维度枚举
- `service/aitutor/eval/EvalResult.java` — 单条评估结果
- `service/aitutor/eval/TutorEvalHarness.java` — 导学卡片评估
- `service/aitutor/eval/QaEvalHarness.java` — QA 评估

**TutorEvalHarness 评估维度（参考 MRBench）：**
1. FACTUAL_CORRECTNESS — 事实正确性
2. PEDAGOGICAL_FIT — 教学适切性
3. SCAFFOLD_LEVEL_MATCH — 脚手架层级匹配
4. ANSWER_LEAKAGE — 答案泄露
5. GUIDANCE_QUALITY — 引导质量
6. KC_ALIGNMENT — KC 对齐
7. COMPREHENSIBILITY — 可理解性
8. ENCOURAGEMENT — 鼓励性

**QaEvalHarness 评估维度：**
- grounding_accuracy — 答案 grounding 正确率
- refusal_accuracy — 拒答合理性
- avg_citation_coverage — 引用覆盖率

**数据来源：**
- TutorEvalHarness → `ai_generation_log` 表
- QaEvalHarness → `language_pack_qa_message` 表

---

### 2.6 Agent 化重构（P2）— 做题界面 AI 导学面板

**新增文件：**
- `service/aitutor/agent/TutorAgent.java` — Agent 接口
- `service/aitutor/agent/AgentCapability.java` — 自描述能力 record
- `service/aitutor/agent/AgentContext.java` — 执行上下文 record
- `service/aitutor/agent/OrchestratorAgent.java` — 路由分发
- `service/aitutor/agent/DiagnosticsAgent.java` — ERROR_FEEDBACK
- `service/aitutor/agent/ScaffoldingAgent.java` — SCAFFOLDING
- `service/aitutor/agent/GuideAgent.java` — READING + IDEATING
- `service/aitutor/agent/TransferAgent.java` — TRANSFER + AC_REVIEW
- `service/aitutor/agent/ChatAgent.java` — CHAT

**架构：**
```
OrchestratorAgent
├── DiagnosticsAgent  ── canHandle("ERROR_FEEDBACK") ── 内置 ReAct + Reflection
├── ScaffoldingAgent  ── canHandle("SCAFFOLDING")     ── 内嵌 Reflection
├── GuideAgent        ── canHandle("READING"/"IDEATING")
├── TransferAgent     ── canHandle("TRANSFER"/"AC_REVIEW") ── AC 后 Reflection
└── ChatAgent         ── canHandle("CHAT")            ── 无 Reflection
```

**兼容性：**
- `applyPhaseOutput` 原有 switch-case 保持不变
- Agent 可通过 `OrchestratorAgent.dispatch()` 独立调用
- `TransitionPolicy`、`CardSchemaValidator` 不受影响

---

### 2.7 A2A 概念模型对齐（P3）— 做题界面 AI 导学面板

**新增文件：**
- `service/aitutor/agent/AgentTaskStatus.java` — Task 状态枚举
- `service/aitutor/agent/AgentTaskTracker.java` — Task 生命周期追踪

**概念映射：**

| A2A 概念 | 项目实现 |
|----------|---------|
| AgentCard | `AgentCapability` record |
| Task 状态 (submitted → working → completed/failed) | `AgentTaskTracker.submit/complete/fail` |
| Task artifact | `ai_workflow_event.agent_name/agent_status/agent_duration_ms` |

**扩展预留：**
- 当前所有 Agent 在同一 JVM 内部，不引入 A2A HTTP 绑定层
- 若需拆分为微服务（如独立沙箱执行），可在 `TutorAgent` 上层包装 A2A JSON-RPC 通信

---

### 2.8 A/B 测试框架（P3）— 做题界面 AI 导学面板

**修改文件：**
- `service/aitutor/rollout/RolloutPolicyService.java`

**新增方法：**
- `assignAbTest(experimentId, userId, treatmentRate)` — 基于稳定哈希分流
- `recordReward(experimentId, userId, rewardType, rewardValue)` — 记录奖励信号

**奖励信号来源：**
- 用户 thumbs up/down
- 后续提交结果（AC/WA）
- 行为指标（停留时间、编辑频率）

---

## 三、风险与缓解

| 风险 | 缓解措施 |
|------|---------|
| ReAct 延迟增加 (3-5 轮 × 3-5 秒) | 默认关闭，仅 ERROR_FEEDBACK/SCAFFOLDING 启用；CHAT 跳过 |
| Reflection 额外 1-2 次 LLM 调用 | CHAT 场景跳过；maxRounds=1 控制上限 |
| LLM 调用成本增加 2-4 倍 | 通过环境变量按场景灰度启用；MiniMax-M2.7 单价较低 |
| MiniMax function-calling 兼容性 | 提供 `LLM_TOOL_USE_PROMPT_FALLBACK` 回退为 prompt-based |
| 测试覆盖 | LlmClientTest 通过；Agent/Reflection 为新增代码，暂无存量测试依赖 |

---

## 四、启用指南

### 最小启用（仅 Reflection）
```bash
# 无需额外配置，ReflectionService 作为 Spring Bean 自动注入
# ERROR_FEEDBACK / SCAFFOLDING / AC_REVIEW 自动经过 Reflection 质检
```

### 启用 ReAct
```bash
export TUTOR_REACT_ENABLED=true    # ERROR_FEEDBACK 使用 ReAct
export QA_REACT_ENABLED=true       # QA 使用 ReAct 自适应检索
export TUTOR_REACT_MAX_ITERATIONS=4
export QA_REACT_MAX_ITERATIONS=3
```

### 启用 A/B 测试
```java
RolloutPolicyService rollout = ...;
AbTestAssignment assignment = rollout.assignAbTest("react_vs_baseline", userId, 0.2);
// assignment.group() == "treatment" 或 "control"
```

### 运行离线评估
```java
TutorEvalHarness harness = ...;
Map<String, Object> report = harness.evaluateBatch("error_diagnosis", 100);
// report 包含 avg_overall_score、avg_dimension_scores、flag_distribution
```
