# Todo - 相似错误提醒

## 阶段目标
在 `ERROR_FEEDBACK` 中融合历史相似错误证据，帮助学生识别重复错误模式。

## 执行清单
- [x] 迁移：将相似错误检索 + rerank + 证据注入接入 `ERROR_FEEDBACK`。
- [x] 自检：检查 query 生成、召回、排序、卡片输出链路。
- [x] 单元测试：覆盖召回逻辑、排序逻辑、空历史分支。
- [x] 集成测试：覆盖提交报错后进入 `ERROR_FEEDBACK` 并命中历史错误。
- [x] 通过确认：对照 `6.8` 期望验收结果逐条确认。

## 通过确认标准
- [x] 重复错误场景可稳定命中至少 1 条历史记录。
- [x] 卡片展示相似错误摘要与引用来源。
- [x] 无历史时回退到标准诊断，不报错不空白。
- [x] 严格学生隔离，不出现跨学生引用。

## 通过确认记录（2026-03-29）
1. 自检结果  
`SimilarErrorRetrievalService` 已完成 `metadata filter + vector recall + rerank`，检索严格按当前学生 `user_id` 限定，并注入 `EvidencePack.similarErrors`。
2. 单元测试结果  
`backend/src/test/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImplTest.java` 已覆盖 `ERROR_FEEDBACK` 输出新增字段与空历史分支。
3. 集成测试结果  
`backend/src/test/java/com/alethicode/integration/AITutorWorkflowEvidenceIntegrationTest.java` 已通过，验证 `repeat_pattern_detected`、`similar_error_summary`、`similar_error_refs` 与 `ai_retrieval_log` 写入。
4. 期望验收结果  
重复错误会在错误诊断卡片展示“历史相似错误”信息，无历史命中时保持标准诊断输出。
5. 风险结论  
相似结果排序受 embedding 投影维度影响，当前为首期可用版本；后续可在线上反馈驱动优化 rerank 权重。

## 依赖与顺序
- 本阶段完成后，才可启动“多 Agent 协同升级”。
