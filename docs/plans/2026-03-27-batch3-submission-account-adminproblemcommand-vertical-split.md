# 批次3：Submission + Account + AdminProblemCommand 垂直拆分闭环记录

## 1. 模块分析（rules: Phase 1）
- 输入：`/api/submission*`、`/api/sessions`、`/api/profile`、`/api/admin/problems*` 等主干请求。
- 处理流程：Controller -> 大服务类（SubmissionServiceImpl/AccountServiceImpl/AdminProblemCommandServiceImpl）。
- 状态变化：保持原判题、鉴权、导入导出行为，只进行内部职责拆分。
- 输出：所有 API 路径、方法、参数、响应结构保持不变。
- 上下游影响：
  - 上游前端调用零变更。
  - 下游数据库 schema 不变，继续使用既有 SQL 与表结构。

## 2. 映射规划（rules: Phase 2）

### 2.1 旧结构 -> 新结构
| 旧结构 | 新结构 |
|---|---|
| `SubmissionController` 直接依赖 `SubmissionService` | `SubmissionController` 依赖 `SubmissionCommandDomainService` / `SubmissionQueryDomainService` / `SubmissionJudgeDispatchDomainService` |
| `SubmissionServiceImpl` 单体命令/查询/派发混合 | `service/submission/*DomainService` 分治委托 |
| `AccountController` 直接依赖 `AccountService` | `AccountController` 依赖 `AccountAuthDomainService` + `AccountProfileDomainService` |
| `AdminAccountController` 直接依赖 `AccountService` | `AdminAccountController` 依赖 `AccountAdminDomainService` |
| `AdminProblemController` 直接依赖 `AdminProblemCommandService` | `AdminProblemController` 依赖 mutation/import/export/fps 四域服务 |

### 2.2 新增域服务
- Submission：`SubmissionCommandDomainService`、`SubmissionQueryDomainService`、`SubmissionJudgeDispatchDomainService`
- Account：`AccountAuthDomainService`、`AccountProfileDomainService`、`AccountAdminDomainService`
- AdminProblemCommand：`AdminProblemMutationDomainService`、`AdminProblemImportDomainService`、`AdminProblemExportDomainService`、`AdminProblemFpsDomainService`

## 3. 最短路径实现（rules: Phase 3）
- 控制器不改路由，仅改内部注入与方法分发目标。
- 域服务实现统一委托原服务，不引入额外兜底逻辑。
- 修复判题异步提交竞态：`SubmissionServiceImpl` 改为事务提交后调度，避免未提交读导致状态卡住。
- 补齐 legacy 错误契约：新增 `LegacyBusinessException`，由全局异常处理器统一返回 HTTP 200 + `{error,data}`。

## 4. 测试验证（rules: Phase 4）
### 4.1 本批次目标测试
- `cd backend && mvn -Dtest=AITutorControllerContractTest,AITutorWorkflowAdminControllerContractTest,AccountControllerContractTest,AdminAccountControllerContractTest,AdminProblemControllerContractTest,SubmissionModuleIntegrationTest,AccountAnnouncementAiIntegrationTest,AITutorWorkflowAdminIntegrationTest test` -> 通过（`Tests run: 25, Failures: 0, Errors: 0`）。

### 4.2 全量回归
- `cd backend && mvn test` -> 通过（`Tests run: 99, Failures: 0, Errors: 0`）。

## 5. code-reviewer 审查（rules: Phase 5）
- Security：鉴权入口保留在既有服务校验路径，未新增权限放宽。
- Performance：事务后派发消除重试/空转成本，未增加查询负担。
- Correctness：修复异步判题竞态，恢复提交结果推进稳定性。
- Maintainability：命令/查询/派发职责边界明确，控制器更薄。
- 阻断项：0。

## 6. 结论
- 批次3完成闭环且无对外契约变更。
- Submission/Account/AdminProblemCommand 主链路完成可维护性拆分。
