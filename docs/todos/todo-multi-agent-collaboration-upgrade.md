# Todo - 多 Agent 协同升级

## 阶段目标
让多 Agent 在服务端编排下共享统一学生状态与证据，保证跨阶段回答连续一致。

## 执行清单
- [x] 迁移：打通主 Agent、辅助 Agent、共享证据、编排决策日志。
- [x] 自检：确认协同决策仅在服务端发生且可回放。
- [x] 单元测试：覆盖主辅 Agent 选择规则与共享上下文消费规则。
- [x] 集成测试：覆盖跨阶段链路连续性（`IDEATING → CODING → ERROR_FEEDBACK → CHAT`）。
- [x] 通过确认：对照 `8.8` 期望验收结果逐条确认。

## 通过确认标准
- [x] 同题多阶段输出无明显上下文断裂。
- [x] trace 可清晰记录主 Agent / 辅助 Agent 参与信息。
- [x] `ERROR_FEEDBACK` 能消费上游阶段状态。
- [x] `CHAT` 可读取当前工作流状态。

## 通过确认记录（2026-03-29）
1. 自检结果  
`EvidencePack` 已增加 `orchestration`，`AITutorWorkflowAdminServiceImpl` 输出 `orchestration_context` 与结构化 `decision`，主辅 Agent 与证据消费可回放。
2. 单元测试结果  
`backend/src/test/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImplTest.java` 覆盖编排决策输出字段与阶段推进规则。
3. 集成测试结果  
`backend/src/test/java/com/alethicode/integration/AITutorWorkflowStateMachineIntegrationTest.java`、`backend/src/test/java/com/alethicode/integration/AITutorWorkflowGovernanceIntegrationTest.java` 已通过。
4. 期望验收结果  
同一会话跨阶段问答可连续，`CHAT` 与 `ERROR_FEEDBACK` 不再割裂，trace 中可见主 Agent/辅助信号与决策依据。
5. 风险结论  
当前编排策略仍是规则+轻量 bandit 的首期版本，策略参数需要后续线上数据迭代，不影响本期一致性目标。

## 依赖与顺序
- 本阶段完成后，才可启动“AI 变量运行可视化”。
