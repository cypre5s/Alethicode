# 批次7：横向收口与文档更新闭环记录

## 1. 模块分析（rules: Phase 1）
- 输入：批次2-6拆分后的横切问题（异常契约、术语基线、测试一致性、文档缺失）。
- 处理流程：统一异常出口 -> 修复测试语义 -> 补齐批次闭环文档与变更日志。
- 状态变化：不改 API 契约，仅修复内部一致性与可验证性。
- 输出：全量测试全绿 + 文档可追溯。
- 上下游影响：
  - 上游前端继续接收 `{error,data}` 结构。
  - 下游数据库与部署配置无变化。

## 2. 映射规划（rules: Phase 2）
| 旧结构 | 新结构 |
|---|---|
| `BusinessExceptions.fromLegacy` 返回通用异常，部分链路返回 HTTP 400 | `LegacyBusinessException` + `GlobalExceptionHandler#handleLegacyBusinessException` 统一 HTTP 200 legacy 错误契约 |
| 旧单测按直接 `ApiResponse` 断言 | 服务层异常流单测改为断言 `LegacyBusinessException` |
| 批次2-6缺闭环文档 | 新增 `docs/plans/2026-03-27-batch{2..7}-*.md` |

## 3. 最短路径实现（rules: Phase 3）
- 新增 `LegacyBusinessException`，并在全局异常处理器中映射为 legacy 响应。
- 修复 `SubmissionServiceImpl` 异步调度时序（事务提交后再派发）。
- 修复术语一致性断言：前端补充术语常量锚点（不改业务流程）。
- 更新批次文档与 `CHANGELOG.md`。

## 4. 测试验证（rules: Phase 4）
### 4.1 目标测试
- `cd backend && mvn -Dtest=AITutorControllerContractTest,AITutorWorkflowAdminControllerContractTest,AccountControllerContractTest,AdminAccountControllerContractTest,AdminProblemControllerContractTest,SubmissionModuleIntegrationTest,AccountAnnouncementAiIntegrationTest,AITutorWorkflowAdminIntegrationTest test` -> 通过（`25/25`）。

### 4.2 全量回归
- `cd backend && mvn test` -> 通过（`99/99`）。
- `cd frontend && npm run test -- --runInBand` -> 通过（`13/13`）。
- `cd frontend && node tests/test_frontend_smoke.js` -> 通过（`16/16`）。

## 5. code-reviewer 审查（rules: Phase 5）
- Security：异常处理收敛后未新增敏感信息泄漏；权限分支保持原逻辑。
- Performance：事务后派发减少无效异步轮询；无新增重查询路径。
- Correctness：修复 legacy 错误契约与判题时序后，集成回归全绿。
- Maintainability：批次证据链（文档+测试）完整，便于后续增量拆分。
- 阻断项：0。

## 6. 结论
- 批次7闭环完成，拆解计划在当前范围内形成可验证收口。
- 达成标准：可读性提升、可测试性提升、行为保持不变。
