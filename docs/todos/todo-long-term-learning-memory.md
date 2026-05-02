# Todo - 长期学习记忆

## 阶段目标
把错题、反思、学习事件沉淀为可召回、可衰减、可更新的长期学习记忆。

## 执行清单
- [x] 迁移：打通记忆抽取、写入、过期/衰减、召回链路。
- [x] 自检：确认短期会话上下文与长期记忆边界清晰。
- [x] 单元测试：覆盖记忆写入规则、过期规则、召回规则。
- [x] 集成测试：覆盖错题本/反思/复盘到记忆的真实链路。
- [x] 通过确认：对照 `9.8` 期望验收结果逐条确认。

## 通过确认标准
- [x] 系统可稳定使用长期记忆生成个性化提示。
- [x] 陈旧或低置信记忆不会长期污染决策。
- [x] 新反思/复盘可转化为记忆单元。
- [x] 记忆缺失时系统仍可正常运行。

## 通过确认记录（2026-03-29）
1. 自检结果  
`ai_learner_memory` 已升级为结构化记忆存储，`LearnerMemoryService` 已实现从错题本与学习事件抽取、写入、衰减、召回，`LearnerProfileProjector` 消费记忆摘要。
2. 单元测试结果  
`backend/src/test/java/com/alethicode/service/aitutor/schema/CardSchemaValidatorTest.java` 与 `backend/src/test/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImplTest.java` 覆盖新增记忆字段参与输出的基础行为。
3. 集成测试结果  
`backend/src/test/java/com/alethicode/integration/AITutorWorkflowEvidenceIntegrationTest.java` 已通过，验证记忆抽取、记忆引用与 profile 投影链路。
4. 期望验收结果  
系统可在同一学生历史下稳定输出 `memory_refs` 摘要，记忆为空时仍保持工作流可用。
5. 风险结论  
当前衰减规则为固定参数，后续需结合线上样本继续调参；不影响本期功能闭环。

## 依赖与顺序
- 本阶段完成后，才可启动“相似错误提醒”。
