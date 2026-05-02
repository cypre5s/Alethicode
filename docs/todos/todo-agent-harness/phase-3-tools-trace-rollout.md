# Phase 3：ToolContext、工具治理与 ACI 文档化

**适用界面**：两者共用，但 AI 导学助手收益更明显
**阶段属性**：Harness 本体

## 目标

把当前工具链从"注册若干 lambda"升级为"有上下文、有约束、有 trace、有 agent-facing 文档"的稳定接口层。

## 实现流程

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

## 主要落点

- `backend/src/main/java/com/alethicode/service/aitutor/react/ToolExecutor.java`
- `backend/src/main/java/com/alethicode/service/aitutor/react/ToolDefinition.java`
- `backend/src/main/java/com/alethicode/service/aitutor/react/ToolContext.java`
- `backend/src/main/java/com/alethicode/service/aitutor/react/TutorToolRegistry.java`
- `backend/src/main/java/com/alethicode/service/LlmClient.java`

## 测试

- 缺少 `languagePackId` 的 QA 检索工具必须直接失败。
- 缺少 `userId` 的 learner history 工具必须直接失败。
- 工具重复调用、空结果、执行异常都能落到不同 trace 状态。
- agent-facing 文档能作为测试断言依据。

## 验收标准

- 所有工具调用都可追溯上下文。
- 工具层失败原因可以结构化分类。
- 新增工具时不需要再靠 prompt 填补语义缺口。
- 工具注册中心能支撑后续扩展，而不继续堆静态方法。
