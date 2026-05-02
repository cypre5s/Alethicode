# Agent + Harness 自查审查记录

**审查时间**：2026-04-04  
**审查范围**：`todo_agent.md`、`docs/todos/todo-agent-harness/progress.md` 对应的本轮实现  
**审查方式**：静态代码核对 + 编译验证 + 关键调用链搜索  

## 结论摘要

当前这批实现**不是空实现**，基础骨架已经搭起来了，尤其是：

- Spring AI 双实现入口已经进入 `LlmClient`
- Runtime Contract 相关类型、枚举、数据库字段已经落盘
- Tool / Memory / QA Harness 的若干新类型已经创建
- 默认后端编译通过：`cd backend && mvn -q -DskipTests compile`

但如果按“是否已经真正接入主链路并可验收”来判断，当前状态更准确地说是：

- **Phase 0.5、Phase 1、Phase 3、Phase 4 基础：多数属于骨架完成 / 部分接线**
- **Phase 2：存在至少一个会导致运行失败的正确性问题**
- **进度文档存在“阶段标记已完成，但关键能力尚未接线”的偏乐观表述**

## 关键发现

### 1. QA replay / harness 查询使用了不存在的表与字段

**严重级别**：高  
**结论**：当前 `QaEvalHarness` 的回放与样本加载逻辑大概率运行即失败。

**证据**：

- `backend/src/main/java/com/alethicode/service/aitutor/eval/QaEvalHarness.java:109`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/QaEvalHarness.java:169`

代码读取的是：

- `language_pack_qa_message`
- `answer_payload`
- `retrieval_payload`

但现有真实表结构是：

- `backend/src/main/resources/db/migration/V31__language_pack_qa.sql:16`

实际表名和字段名是：

- `language_pack_chat_message`
- `answer_json`

业务主链也按这个结构写入：

- `backend/src/main/java/com/alethicode/service/languagepack/impl/LanguagePackQaServiceImpl.java:203`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/LanguagePackQaServiceImpl.java:435`

**影响**：

- `QaEvalHarness.replaySample(...)` 当前不能作为真实回放入口使用
- `evaluateBatch(...)` 的样本加载同样不可信
- 文档中“QA replay 已完成”的说法目前不能成立

---

### 2. ToolContext / guard / trace 只定义了，没有真正接入 ReAct 主链路

**严重级别**：高  
**结论**：Phase 3 当前主要完成了模型定义，没有完成执行治理。

**证据**：

新增能力已经存在：

- `backend/src/main/java/com/alethicode/service/aitutor/react/ToolDefinition.java:17`
- `backend/src/main/java/com/alethicode/service/aitutor/react/ToolExecutor.java:18`
- `backend/src/main/java/com/alethicode/service/aitutor/react/ToolContext.java`
- `backend/src/main/java/com/alethicode/service/aitutor/react/ToolTraceEntry.java`

但真正执行工具时，`LlmClient.callWithTools(...)` 仍然只调用：

- `backend/src/main/java/com/alethicode/service/LlmClient.java:248`

即：

- 仍是 `executor.execute(args)`
- 没有构造 `ToolContext`
- 没有调用 `ToolDefinition.checkGuard(...)`
- 没有生成 `ToolTraceEntry`
- 没有把 guard 失败当成 fail-fast 错误

另外，工具实现内部还保留了非 fail-fast 行为，例如：

- `backend/src/main/java/com/alethicode/service/aitutor/react/TutorToolRegistry.java:131`
- `backend/src/main/java/com/alethicode/service/aitutor/react/TutorToolRegistry.java:136`

`getLearnerHistoryExecutor(...)` 在缺 `userId/problemId` 时直接返回空列表，而不是失败。

**影响**：

- Tool governance 目前还没有真正控制工具执行
- ACI/guard 的治理价值尚未进入主流程
- 文档里关于 fail-fast、trace、权限约束的目标只实现到“数据结构层”

---

### 3. SessionContext 结构化增强存在列名风险，而且尚未接入 QA 主流程

**严重级别**：中高  
**结论**：Phase 1 的 QA 上下文升级目前更像“预备实现”。

**证据**：

结构化接口和实现已经新增：

- `backend/src/main/java/com/alethicode/service/languagepack/ConversationContextService.java:7`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/ConversationContextServiceImpl.java:68`

但读取近期引用页时使用了：

- `backend/src/main/java/com/alethicode/service/languagepack/impl/ConversationContextServiceImpl.java:103`

SQL 依赖 `cited_pages` 字段，而在已核对的迁移中：

- `backend/src/main/resources/db/migration/V31__language_pack_qa.sql:16`
- `backend/src/main/resources/db/migration/V39__harness_runtime_contract.sql:18`

没有看到该字段被创建。

同时，代码搜索没有发现 `buildSessionContext(...)` 被 QA 主链路消费。

**影响**：

- 如果 `cited_pages` 确实不存在，会形成运行期 SQL 错误
- 即使字段存在，这套 `SessionContext` 目前也未真正提升 QA 回答质量

---

### 4. RuntimeContract / runtime event 目前完成了类型与存储，没有完成主链路协议化

**严重级别**：中  
**结论**：Phase 4 基础已经“落盘”，但还没有“跑起来”。

**证据**：

类型和标准化输出能力已经有：

- `backend/src/main/java/com/alethicode/service/aitutor/contract/RuntimeContract.java:7`
- `backend/src/main/java/com/alethicode/websocket/WorkflowRealtimeSupport.java:72`
- `backend/src/main/resources/db/migration/V39__harness_runtime_contract.sql:1`
- `backend/src/main/java/com/alethicode/service/aitutor/agent/AgentTaskTracker.java:53`

但关键搜索结果显示：

- 没有发现 `WorkflowRealtimeSupport.broadcastEvent(...)` 的调用点
- 没有发现 `RuntimeContract.builder()` 在导学或 QA 服务中被真正用于对外广播

**影响**：

- 前端目前还拿不到统一的 runtime contract 事件流
- 文档里“统一 runtime protocol”的目标尚未闭环
- RuntimeContract 目前更像“协议类型存在”，而不是“产品协议生效”

---

### 5. 多个新增能力仍停留在“类型存在”，没有进入真实流程

**严重级别**：中  
**结论**：当前实现完成了不少骨架，但一批关键新能力尚未接线。

**经搜索未发现主流程调用的能力**：

- `LearnerMemoryService.onEventCompleted(...)`
  - `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerMemoryService.java:133`
- `EvidencePack.contextSnapshot()`
  - `backend/src/main/java/com/alethicode/service/aitutor/evidence/EvidencePack.java:22`
- `ConversationContextService.buildSessionContext(...)`
  - `backend/src/main/java/com/alethicode/service/languagepack/impl/ConversationContextServiceImpl.java:68`
- `ToolDefinition.checkGuard(...)`
  - `backend/src/main/java/com/alethicode/service/aitutor/react/ToolDefinition.java:41`
- `ToolTraceEntry`
  - `backend/src/main/java/com/alethicode/service/aitutor/react/ToolTraceEntry.java`
- `RetrievalTrace`
  - `backend/src/main/java/com/alethicode/service/languagepack/RetrievalTrace.java`
- `SynthesisTrace`
  - `backend/src/main/java/com/alethicode/service/languagepack/SynthesisTrace.java`

**影响**：

- 计划中的很多能力目前还不能按“已实现”验收
- 更准确的判断应是“骨架已落地，深度集成尚未完成”

---

### 6. PROGRESS 文档对完成度的表达偏乐观

**严重级别**：中  
**结论**：`progress.md` 当前更像“阶段完成宣告”，而不是“严格的接线验收记录”。

**证据**：

总体进度表中：

- `docs/todos/todo-agent-harness/progress.md:9`
- `docs/todos/todo-agent-harness/progress.md:15`

多个阶段被标记为“已完成”。

但同一文件中仍保留关键未完成项，例如：

- `docs/todos/todo-agent-harness/progress.md:47`
- `docs/todos/todo-agent-harness/progress.md:48`
- `docs/todos/todo-agent-harness/progress.md:73`
- `docs/todos/todo-agent-harness/progress.md:81`
- `docs/todos/todo-agent-harness/progress.md:82`
- `docs/todos/todo-agent-harness/progress.md:83`
- `docs/todos/todo-agent-harness/progress.md:92`
- `docs/todos/todo-agent-harness/progress.md:96`

**影响**：

- 容易让后续验收者误以为“已经接入主链路”
- 会稀释真正需要补的 gap

## 分阶段核对表

| 阶段 | 计划目标 | 当前状态 | 判断 |
|---|---|---|---|
| Phase 0 | 工程落盘与索引 | 文档目录、README、阶段文档已创建 | 已完成 |
| Phase 0.5 | Spring AI 双实现基线 | `pom.xml`、`SpringAiConfig`、`LlmClient` 已加；默认仍可编译 | 部分完成 |
| Phase 0.5 | Spring AI profile 可编译可验证 | `mvn -q -Pspring-ai -DskipTests compile` 因仓库依赖拉取超时失败 | 未证实 |
| Phase 1 | MemoryCandidate / SaveDecision / Scope | 类型已新增 | 已完成 |
| Phase 1 | 事件驱动 memory 写入 | `onEventCompleted(...)` 已实现，但未见主流程调用 | 部分完成 |
| Phase 1 | QA SessionContext 升级 | 接口与实现已加，但未见主链路消费，且 `cited_pages` 有风险 | 部分完成 |
| Phase 3 | ToolContext / ToolDomain / guard / ACI | 类型与定义已加 | 已完成 |
| Phase 3 | Tool guard 真正执行 | 未接入 `callWithTools(...)` | 未完成 |
| Phase 3 | Tool trace 产出 | `ToolTraceEntry` 已定义，但未接入 | 未完成 |
| Phase 2 | QA harness failure bucket / replay | 代码已写，但 replay SQL 指向错误表结构 | 未完成且有 bug |
| Phase 2 | RetrievalTrace / SynthesisTrace 入主链 | 类型存在，未见主流程集成 | 未完成 |
| Phase 4 基础 | RuntimeState / RuntimeContract / DB 字段 | 已落盘 | 已完成 |
| Phase 4 基础 | runtime event 对外协议化 | `broadcastEvent(...)` 未见调用点 | 部分完成 |
| Phase 5 | HITL / stopping 条件扩展 | 类型与部分枚举已加 | 部分完成 |

## 验证记录

### 1. 默认编译

命令：

```bash
cd backend && mvn -q -DskipTests compile
```

结果：通过。

### 2. Spring AI profile 编译

命令：

```bash
cd backend && mvn -q -Pspring-ai -DskipTests compile
```

结果：未通过。当前失败原因为：

- `repo.spring.io` 依赖下载超时

说明：

- 这只能说明本地还**没有验证通过 Spring AI profile 的可编译性**
- 不能直接推出 Java 代码本身一定有问题
- 但 Phase 0.5 的“可验证基线”仍然不能算完成

## 当前最值得优先回头修的 3 个点

1. 修正 `QaEvalHarness` 的表名和字段名，使 replay/batch 真实可运行
2. 把 `ToolContext + guard + trace` 真正接入 `LlmClient.callWithTools(...)`
3. 把 `RuntimeContract` 和 `SessionContext` 接进导学 / QA 主链路，而不是只停留在类型层

## 最终判断

如果按“是否确实实现”来验收，本轮工作更准确的描述应当是：

- **基础骨架已经成型**
- **默认编译保持可用**
- **核心 gap 在于主链路接线不足**
- **少数模块已出现明确正确性问题，需要先修再谈验收完成**

因此，当前不建议把除 Phase 0 以外的后续阶段笼统标注为“已完成”。更稳妥的表述应是：

- `已完成：文档落盘、基础类型、数据库字段、部分入口改造`
- `部分完成：Memory / Tool governance / Runtime contract / Spring AI 基线`
- `未完成：主链路接线、统一事件协议、trace 真正产出`
- `存在 bug：QA replay SQL`
