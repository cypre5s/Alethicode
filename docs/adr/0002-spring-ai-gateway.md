# ADR-0002: 用 Spring AI 作为 LLM 调用的唯一底层，并抽取 `AiModelGateway` 作为业务边界

- **Status**: Accepted
- **Date**: 2026-04-21
- **Authors**: Alethicode core team

## 背景

原 `LlmClient.java`（~1600 行）同时承担 HTTP 调用、JSON 解析、`<think>` / code fence
修复、embedding 投影、ReAct 工具循环、配置解析、熔断、指标等职责。这样做的坏处：

- 业务类越过 HTTP 细节注入 `LlmClient`，无法模拟 / 替换
- Spring AI 生态的工具（ChatClient / Observation / ToolCallback）无法直接使用
- 多 provider（MiniMax / 通义千问 / OpenAI-compatible）切换全部通过 `envPrefix`
  手工路由

## 约束

- 国内生产只用 OpenAI-compatible API（MiniMax、字节火山、通义千问）
- 不能双跑 Native + Spring AI 在学生生产流量上
- 不能把业务类直接耦合到 Spring AI 具体类（否则替换框架仍是全链路改）

## 决策

1. **项目级接口** `AiModelGateway`：业务类只依赖它，覆盖 JSON / 内容 / 工具循环 / embedding / 配置读取
2. **唯一生产实现** `SpringAiModelGateway`：用 Spring AI `ChatModel` / `EmbeddingModel`
3. **装饰器** `CachingAiModelGateway`（`@Primary`）：JSON 缓存走这里
4. **工具循环**：Spring AI user-controlled tool execution（`internalToolExecutionEnabled=false`），
   业务侧保留 `ToolDefinition` / `ToolExecutor` / `ToolTraceEntry`
5. **配置解析** `AiModelProfileResolver`：Beta override → DB → env → .env → 默认值
6. **删除** `LlmClient` 及 Native HTTP 实现（1579 行 + 测试）

验证手段：管理端资源化 API `POST /api/admin/super/ai-config/validation-runs`
覆盖 JSON / content / embedding / tool-loop 四类固定样本。

## 后果

**正面**
- 37 个业务类 / 11 个测试类全部迁移到 `AiModelGateway`
- Spring AI observability（chat.client / tool / embedding）自动纳入 Micrometer
- `profilePrefix`（`INIT_LLM_` / `INIT_LLM_REGEN_`）改到 Spring AI options 层级，一致路由

**负面 / 绑定**
- 绑定 Spring AI 1.1.x BOM；升级 2.x 时需要重新评估 ChatClient API
- Spring AI + MiniMax 的兼容性（reasoning_content / tool_choice="required"）需要持续验证
- embedding 维度仍然走 `EmbeddingProjectionService.project` 到 16 维；provider 若返回其他维度要显式处理

## 后续

- 持续监控 `ai_circuit_breaker_*` 指标
- 下个季度引入 LLMOps（Langfuse / PromptLayer）时，`AiModelGateway` 是接入点
- 若 Spring AI 2.x 提供 native ReAct / LangGraph 支持，再评估是否把 `SpringAiToolLoopService` 删除
