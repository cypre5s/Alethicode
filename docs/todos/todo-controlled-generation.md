# Todo - 受控生成

## 阶段目标
建立统一受控生成框架，确保关键节点输出结构稳定、可校验、可拦截、可回放。

## 执行清单
- [x] 迁移：将 `READING / IDEATING / CODING / ERROR_FEEDBACK / AC_REVIEW / TRANSFER / CHAT` 的生成请求统一收口到受控生成链路。
- [x] 自检：检查输入结构化、schema 校验、风险拦截、失败暴露、generation log 全链路闭合。
- [x] 单元测试：覆盖 schema 缺失、风险命中、重试规则、失败分支。
- [x] 集成测试：覆盖真实工作流节点调用与日志落库。
- [x] 通过确认：对照 `10.8` 期望验收结果逐条确认。

## 通过确认标准
- [x] 输出协议稳定，无关键字段缺失。
- [x] 越界生成可被服务端拦截。
- [x] 失败原因可明确返回前端。
- [x] trace 与 generation log 可回放完整链路。

## 通过确认记录（2026-03-29）
1. 自检结果  
输入通过 `workflowEvent -> processWorkflowEvent` 统一入链；输出统一走 schema 校验与 guardrail；失败路径会显式写入 `trace` 与 `generation_log`。
2. 单元测试结果  
`backend/src/test/java/com/alethicode/service/aitutor/schema/CardSchemaValidatorTest.java` 已覆盖 `execution_trace_explainer` 成功/失败态 schema 校验。
3. 集成测试结果  
`backend/src/test/java/com/alethicode/integration/AITutorWorkflowGovernanceIntegrationTest.java`、`backend/src/test/java/com/alethicode/integration/AITutorWorkflowStateMachineIntegrationTest.java` 已通过。
4. 期望验收结果  
关键卡片输出字段稳定，失败时前端能拿到明确错误，服务端可在 `ai_tutor_trace` 与 `ai_tutor_generation_log` 回放节点行为。
5. 风险结论  
本阶段已落到主链路并可验证；当前已知边界为迁移后日志量上升，需要后续关注归档与保留策略。

## 依赖与顺序
- 本阶段已完成，可进入后续阶段。
