# Agent + Harness 实施进度追踪

> 本文件记录每个阶段的实施状态，每完成一个子任务即更新。

## 总体进度

| 阶段 | 状态 | 开始时间 | 完成时间 | 备注 |
|------|------|----------|----------|------|
| Phase 0：工程落盘与边界冻结 | 已完成 | 2026-04-04 | 2026-04-04 | 文档骨架+索引+CHANGELOG |
| Phase 0.5：Spring AI 试点基线 | 已完成 | 2026-04-04 | 2026-04-04 | pom+config+LlmClient双实现 |
| Phase 4 基础：Runtime Contract | 已完成 | 2026-04-04 | 2026-04-04 | 枚举+contract+Flyway+主链路接线 |
| Phase 1：Context Layering 与 Memory | 已完成 | 2026-04-04 | 2026-04-04 | 候选记忆+事件写入+SessionContext+主链路 |
| Phase 3：ToolContext 与工具治理 | 已完成 | 2026-04-04 | 2026-04-04 | ToolContext+域隔离+guard+trace+callWithTools |
| Phase 2：RAG 治理与 QA Harness | 已完成 | 2026-04-04 | 2026-04-04 | RetrievalTrace+SynthesisTrace+replay+主链路 |
| Phase 4 完整 + Phase 5：HITL | 已完成 | 2026-04-04 | 2026-04-04 | PendingHumanAction+StoppingCondition+灰度门禁 |
| 前端 Phase 0–2：Problem 页 Runtime | 已完成 | 2026-04-04 | 2026-04-04 | runtimeContract+workflowStateMachine+UnifiedAgentPanel |
| 前端 Phase 3–4：QA 页 Runtime | 已完成 | 2026-04-04 | 2026-04-04 | QaWebSocket+async dispatch+LanguagePackQaPage |

## Phase 0：工程落盘与边界冻结

- [x] `docs/todos/todo-agent-harness/README.md` — 全局路线图、术语表、迁移策略
- [x] `docs/todos/todo-agent-harness/phase-0-5-spring-ai-baseline.md`
- [x] `docs/todos/todo-agent-harness/phase-1-context-memory.md`
- [x] `docs/todos/todo-agent-harness/phase-2-rag-harness.md`
- [x] `docs/todos/todo-agent-harness/phase-3-tools-trace-rollout.md`
- [x] `docs/todos/todo-agent-harness/phase-4-hitl-and-agent-runtime.md`
- [x] `docs/PROJECT.md` 增加索引
- [x] `CHANGELOG.md` 更新

## Phase 0.5：Spring AI 试点基线

- [x] `pom.xml` 引入 Spring AI 1.1.4（Maven profile `spring-ai`，默认不激活）
- [x] `config/SpringAiConfig.java` 新增（ConditionalOnClass + ConditionalOnProperty）
- [x] `LlmClient.java` 双实现路由（NATIVE/SPRING_AI，反射调用避免编译依赖）
- [x] `application.yml` 新增 spring.ai 配置块（默认 disabled）
- [ ] `LlmClientSpringAiTest.java` 一致性测试（待 Spring AI 依赖下载后补充）
- [x] `CHANGELOG.md` 更新

## Phase 4 基础：Runtime Contract 与状态枚举

- [x] `RuntimeState.java` 枚举
- [x] `RuntimeContract.java` record（含 Builder 和 toMap）
- [x] `ServerEvent.java` 枚举
- [x] `FailureBucket.java` 枚举
- [x] `RecoveryReason.java` 枚举
- [x] `AgentTaskTracker.java` 对齐 RuntimeState（含 traceId 生成和持久化）
- [x] `AgentTaskStatus.java` 增加 toRuntimeState/fromRuntimeState 双向映射
- [x] `WorkflowRealtimeSupport.java` 新增 broadcastEvent 标准化方法
- [x] `AITutorWorkflowAdminServiceImpl.java` 统一 contract 输出（TASK_STARTED/TASK_COMPLETED/TASK_FAILED 全部使用 broadcastEvent）
- [x] `LanguagePackQaServiceImpl.java` 已接入 SessionContext + RetrievalTrace + query 前处理
- [x] `V39__harness_runtime_contract.sql` Flyway 迁移
- [x] `CHANGELOG.md` 更新

## Phase 1：Context Layering 与 Memory 升级

- [x] `MemoryCandidate.java` record（含 toMap）
- [x] `MemorySaveDecision.java` 枚举（SAVE/DEFER/DISCARD）
- [x] `MemoryScope.java` 枚举
- [x] `LearnerMemoryService.java` 改造（createCandidate, evaluateCandidate, persistCandidate, onEventCompleted）
- [x] `onEventCompleted` 已接入导学主链路（ERROR_FEEDBACK/AC_REVIEW 完成后自动写入候选记忆）
- [x] `SessionContext.java` record（sessionId, recentDialogue, sessionSummary, recentCitedPageIds）
- [x] `ConversationContextService.java` 接口新增 buildSessionContext
- [x] `ConversationContextServiceImpl.java` 结构化升级 + 修复 cited_pages 字段（改用 language_pack_chat_retrieval_log.page_hit_json）
- [x] `buildSessionContext` 已接入 QA 主链路（LanguagePackQaServiceImpl.sendMessage）
- [x] `EvidencePack.java` 新增 contextSnapshot()
- [x] `contextSnapshot()` 已接入导学主链路（processWorkflowEvent 响应中包含 context_snapshot）
- [x] `CHANGELOG.md` 更新

## Phase 3：ToolContext、工具治理与 ACI 文档化

- [x] `ToolContext.java` record（含 require* fail-fast 方法）
- [x] `ToolDomain.java` 枚举（TUTOR / QA）
- [x] `ToolExecutor.java` 签名升级（新增 execute(args, context) 默认方法）
- [x] `ToolDefinition.java` 增加 domain, guard, agentDescription + checkGuard 方法
- [x] `ToolTraceEntry.java` record + 已接入 ReactResult
- [x] `TutorToolRegistry.java` 四个工具全部标注域（TUTOR/QA）、guard、ACI 描述
- [x] `getLearnerHistoryExecutor` 改为 fail-fast（不再返回空列表）
- [x] `LlmClient.callWithTools` 已接入 guard check + ToolTraceEntry 生成 + ToolContext 传递
- [x] `ReactResult.java` 扩展 toolTraceEntries 字段
- [x] `CHANGELOG.md` 更新

## Phase 2：RAG 治理与 QA Harness 升级

- [x] `RetrievalTrace.java` record
- [x] `SynthesisTrace.java` record
- [x] `PageRetrievalService.java` 接口新增 retrieveWithTrace
- [x] `PageRetrievalServiceImpl.java` 实现 retrieveWithTrace
- [x] `AnswerSynthesisService.java` 接口新增 synthesizeWithTrace
- [x] `AnswerSynthesisServiceImpl.java` 实现 synthesizeWithTrace
- [x] `LanguagePackQaServiceImpl.java` 使用 retrieveWithTrace 替代 retrieve + 新增 resolveQueryReferences
- [x] `QaEvalHarness.java` 修复表名/字段名 bug（language_pack_qa_message → language_pack_chat_message, answer_payload → answer_json）
- [x] `QaEvalHarness.java` failure bucket 分类 + replaySample 回放入口
- [x] `CHANGELOG.md` 更新

## Phase 4 完整 + Phase 5：Harness 主体闭环 + HITL

- [x] `PendingHumanAction.java` 扩展（新增 CONFIRM_MEMORY_SAVE, CONFIRM_HIGH_RISK_TOOL_USE, CONFIRM_RETRIEVAL_OVERRIDE）
- [x] `StoppingCondition.java` record（maxIterations, maxRepeatToolCalls, maxCriticFails, timeoutSeconds）
- [x] `TutorEvalHarness.java` failure bucket 分类 + replaySample 回放入口
- [x] `RolloutPolicyService.java` 新增 evaluateHarnessGate 灰度门禁方法
- [x] Lifecycle state model 已通过 RuntimeState + V39 迁移落地
- [x] RuntimeContract 已接入导学 WebSocket 推送
- [x] `CHANGELOG.md` 更新

## 前端 Phase 0–2：Problem 页 Runtime 事件消费与状态 UI

- [x] `frontend/src/utils/runtimeContract.js`：runtime contract 归一化模块（normalizeRuntimeEvent, isTerminalRuntimeState, isBlockingRuntimeState, isApprovalRuntimeState, assertAllowedForProblemPage, assertAllowedForQaPage）
- [x] `workflowStateMachine.js`：WebSocket 主分支从 `node_start/result` 切换到 `runtime_event`，新增 `_handleRuntimeEvent`、`runtimeContext` 状态跟踪
- [x] `UnifiedAgentPanel.vue`：新增审批态（确认/拒绝）、恢复态（checkpoint 恢复中）、失败态（failureBucket + 恢复路径）三种 runtime banner
- [x] `Problem.vue`：透传 runtimeContext / pendingHumanAction，接入 approve/reject/recover/restart 事件
- [x] `frontend/tests/unit/runtime-contract.spec.js`：10 个单测
- [x] `frontend/tests/unit/workflow-runtime-event-contract.spec.js`：15 个单测
- [x] `frontend/tests/unit/problem-runtime-ui-contract.spec.js`：14 个单测
- [x] `frontend/tests/unit/workflow-state-machine-restore-cache.spec.js`：修复 runtimeContract mock

## 前端 Phase 3–4：QA 页 Runtime 事件消费与状态 UI

- [x] `backend/src/main/java/com/alethicode/websocket/QaWebSocketHandler.java`：QA WebSocket `/ws/qa/{sessionId}`，复用 WorkflowRealtimeSupport
- [x] `WorkflowWebSocketConfig.java`：注册 QA WebSocket 路由
- [x] `LanguagePackQaService.java`：新增 `sendMessageAsync` 接口
- [x] `LanguagePackQaServiceImpl.java`：注入 WorkflowRealtimeSupport，实现异步 dispatch（TASK_STARTED / TASK_COMPLETED / TASK_FAILED 推送）
- [x] `LanguagePackQaController.java`：`sendMessage` 支持 `?async=true` 参数
- [x] `websocketUrl.js`：新增 `buildQaWebSocketPath`
- [x] `api.js`：`sendLanguagePackQaMessage` 支持 async option
- [x] `LanguagePackQaPage.vue`：qaRuntimeContext + QA WebSocket + 异步 dispatch + runtime 消费 + 状态 UI（QUEUED/RUNNING/FAILED/EXPIRED banner）+ 会话/语言包切换清理 + 重试
- [x] `frontend/tests/unit/language-pack-qa-runtime-contract.spec.js`：28 个单测

## 遗留项（需要独立环境或更长会话完成）

- [ ] `LlmClientSpringAiTest.java`：需在可下载 Spring AI 依赖的环境中编写
- [ ] `mvn -Pspring-ai compile` 验证：需在可访问 repo.spring.io 的网络环境中执行
