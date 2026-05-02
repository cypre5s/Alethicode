# Phase 0.5：Spring AI 试点基线建立

**适用界面**：两者共用
**阶段属性**：基础设施迁移

## 目标

在不改变现有业务行为的前提下，为 Spring AI 渐进迁入建立最小可验证基线，先验证 provider、依赖、配置和 tracing 接通。

## 实现流程

1. 在 `backend/pom.xml` 中引入所需 Spring AI 依赖：
   - `spring-ai-bom` 1.1.4
   - `spring-ai-openai-spring-boot-starter`（ChatClient / ChatModel / EmbeddingModel）
   - `spring-ai-spring-boot-starter`（Observability）
   - 视试点结果再决定是否加入 `VectorStore / PGVector / Advisors`
2. 新增 Spring AI 配置层（`config/SpringAiConfig.java`），但不直接替换现有 `LlmClient` 入口。
3. 为现有 `LlmClient` 增加一层 facade / adapter 设计：
   - 默认仍走当前实现（`NATIVE`）
   - 允许通过环境变量 `LLM_BACKEND=native|spring_ai` 切换到 Spring AI backend
4. 为模型调用建立"双实现一致性"验证：
   - 当前实现输出
   - Spring AI 实现输出
   - 结构化输出字段一致性检查
5. 接入 Spring AI observability：
   - Chat 调用 tracing
   - Embedding 调用 tracing
   - Tool / Advisor tracing
6. 打通 Micrometer 指标与 trace 标签，确认能被现有监控链路消费。

## 主要落点

- `backend/pom.xml`
- `backend/src/main/java/com/alethicode/config/SpringAiConfig.java`
- `backend/src/main/java/com/alethicode/service/LlmClient.java`
- `backend/src/test/java/com/alethicode/service/LlmClientSpringAiTest.java`

## 测试

- Spring AI provider 能在当前环境正常初始化。
- `callForJson` 与 Spring AI adapter 在固定样本上结构化输出一致。
- `callForEmbedding` 与 Spring AI embedding adapter 在向量维度和基本可用性上满足现有调用要求。
- tracing 数据能进入现有 observability 链路。

## 验收标准

- Spring AI 在项目中"可接、可测、可观测"，但尚未强制替换生产调用链。
- 模型调用层完成可回退的双实现基线。
- 后续迁移不再依赖"先整体重写再验证"。
