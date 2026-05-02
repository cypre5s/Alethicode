# 批次2：AI Tutor 主链路垂直拆分闭环记录

## 1. 模块分析（rules: Phase 1）
- 输入：`/api/ai/**` 与 `AITutorWorkflowAdminService` 相关请求（session、analytics、knowledge、workflow、admin review）。
- 处理流程：Controller 路由层 -> AITutor/AITutorWorkflow 业务处理。
- 状态变化：仅调整内部调用拓扑，数据库读写逻辑保持原服务实现。
- 输出：`ApiResponse<Object>` 结构与字段命名保持不变。
- 上下游影响：
  - 上游前端 `@oj/api` 路径、参数、响应契约不变。
  - 下游 `AITutorService` / `AITutorWorkflowAdminService` 作为稳定执行内核继续复用。

## 2. 映射规划（rules: Phase 2）

### 2.1 旧结构 -> 新结构
| 旧结构 | 新结构 |
|---|---|
| `AITutorController` 直接依赖 `AITutorService` | `AITutorController` 依赖 4 个域服务：session/analytics/knowledge/workflow |
| `AITutorWorkflowController` 直接依赖 `AITutorWorkflowAdminService` | `AITutorWorkflowController` 依赖 `AITutorWorkflowDomainService` |
| `AdminAITutorController` 直接依赖 `AITutorWorkflowAdminService` | `AdminAITutorController` 依赖 `AITutorAdminReviewDomainService` |
| `AITutorServiceImpl` / `AITutorWorkflowAdminServiceImpl` 单体对接 | `service/aitutor/*DomainService` + `service/aitutor/impl/*DomainServiceImpl` 委托分层 |

### 2.2 域服务拆分
- `AITutorSessionDomainService`
- `AITutorAnalyticsDomainService`
- `AITutorKnowledgeDomainService`
- `AITutorWorkflowDomainService`
- `AITutorAdminReviewDomainService`

## 3. 最短路径实现（rules: Phase 3）
- 新增 5 个 AI Tutor 域服务接口与实现，全部委托既有 `AITutorService` / `AITutorWorkflowAdminService`，不新增兼容分支。
- `AITutorController` 路由保持不变，仅做内部职责分发。
- `AITutorWorkflowController`、`AdminAITutorController` 改为域服务依赖，移除控制器对单体服务的直接耦合。

## 4. 测试验证（rules: Phase 4）
### 4.1 本批次目标测试
- `cd backend && mvn -Dtest=AITutorControllerContractTest,AITutorWorkflowAdminControllerContractTest,AITutorWorkflowAdminIntegrationTest,AccountAnnouncementAiIntegrationTest test` -> 通过。

### 4.2 全量回归
- `cd backend && mvn test` -> 通过（`Tests run: 99, Failures: 0, Errors: 0`）。
- `cd frontend && npm run test -- --runInBand` -> 通过（`3 suites, 13 tests`）。
- `cd frontend && node tests/test_frontend_smoke.js` -> 通过（`16/16`）。

## 5. code-reviewer 审查（rules: Phase 5）
- Security：未引入新鉴权旁路；控制器权限条件与原实现一致。
- Performance：新增仅为轻量委托层，不增加 SQL 次数或外部 I/O。
- Correctness：契约测试与集成测试均通过，路由/响应未漂移。
- Maintainability：AI Tutor 责任边界清晰化，后续可按域独立演进与测试。
- 阻断项：0。

## 6. 结论
- 批次2完成闭环：分析 -> 映射 -> 实现 -> 测试 -> 审查。
- 对外接口零变更，内部结构从单体服务依赖改为按域组合。
