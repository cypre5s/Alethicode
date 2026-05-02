# Todo - AI 变量运行可视化

## 阶段目标
让学生看懂程序执行过程（变量变化、循环迭代、分支判断），用于教学解释与定位偏差步骤。

## 执行清单
- [x] 迁移：打通执行轨迹抽取、关键步裁剪、变量快照与解释卡片生成。
- [x] 自检：确认轨迹与解释一致，失败态显式暴露。
- [x] 单元测试：覆盖简单 Python 场景轨迹抽取与协议字段。
- [x] 集成测试：覆盖 `CODING / ERROR_FEEDBACK` 中真实调用与前端展示。
- [x] 通过确认：对照 `7.8` 期望验收结果逐条确认。

## 通过确认标准
- [x] 简单 Python 代码可稳定生成关键运行轨迹。
- [x] 解释与轨迹一一对应，不依赖模型幻觉。
- [x] 失败时前端可见明确原因。
- [x] 学生可定位“哪一步开始偏离预期”。

## 通过确认记录（2026-03-29）
1. 自检结果
`PythonExecutionTraceService` 与 `SimplePythonTracer` 已接入工作流主链，轨迹事实先生成再解释，失败态输出 `status=failed + failure_reason`。
2. 单元测试结果
`backend/src/test/java/com/alethicode/service/aitutor/schema/CardSchemaValidatorTest.java` 已覆盖 `execution_trace_explainer` 成功/失败结构校验。
3. 集成测试结果
`backend/src/test/java/com/alethicode/integration/AITutorWorkflowEvidenceIntegrationTest.java` 已通过，验证 `CODING/ERROR_FEEDBACK` 中轨迹卡片写入与响应输出；`frontend/tests/unit/workflow-private-ai-contract.spec.js` 已通过，验证卡片契约渲染。
4. 期望验收结果
学生可在统一 AI 面板查看输入样例、关键步骤、变量快照、偏离步骤与教学解释；无法安全生成时可见明确失败原因，不出现伪造步骤。
5. 风险结论
首期仅保证 Python3 初学者常见顺序/分支/循环场景；复杂动态语义暂未纳入本期能力边界。

## 依赖与顺序
- 本阶段为五阶段最后一项，已完成。
