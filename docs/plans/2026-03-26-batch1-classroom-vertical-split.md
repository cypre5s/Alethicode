# 批次1：Classroom 主链路垂直拆分闭环记录

## 1. 模块分析（rules: Phase 1）
- 输入：`/api/classroom/**` 全量 HTTP 请求（含 classroom/member/lesson/assignment/session/monitor/ai-generated-problem）。
- 处理流程：Controller 接口层 -> `ClassroomService` 业务层 -> JDBC/Facade。
- 状态变化：本批次仅重组 Controller/Domain Service 结构，不改变数据库与业务状态机。
- 输出：`ApiResponse<Object>` 与 `LessonFile` 下载响应保持原契约。
- 上下游影响：
  - 上游前端 `frontend/src/pages/oj/api.js` 与 Classroom 页面调用路径保持不变。
  - 下游仍使用既有 `ClassroomServiceImpl`、`JdbcTemplate`、`ClassroomMonitorFacade`，无 schema 变更。

## 2. 映射规划（rules: Phase 2）

### 2.1 旧结构 -> 新结构
| 旧结构 | 新结构 |
|---|---|
| `controller/ClassroomController.java` | `controller/classroom/ClassroomCoreController.java` |
| `controller/ClassroomController.java` | `controller/classroom/ClassroomMemberController.java` |
| `controller/ClassroomController.java` | `controller/classroom/ClassroomLessonController.java` |
| `controller/ClassroomController.java` | `controller/classroom/ClassroomAssignmentController.java` |
| `controller/ClassroomController.java` | `controller/classroom/ClassroomSessionController.java` |
| `controller/ClassroomController.java` | `controller/classroom/ClassroomMonitorController.java` |
| `controller/ClassroomController.java` | `controller/classroom/ClassroomAiProblemController.java` |
| `service/ClassroomServiceImpl.java`（对外暴露） | `service/classroom/*DomainService` + `service/classroom/impl/*DomainServiceImpl`（内部委托层） |

### 2.2 路由映射校验
- 对比命令：
  - `git show 4b10629:backend/src/main/java/com/alethicode/controller/ClassroomController.java | rg -o '"/api/classroom[^" ]*"' | sort -u`
  - `rg -o '"/api/classroom[^" ]*"' backend/src/main/java/com/alethicode/controller/classroom/*.java | sed 's/^[^:]*://' | sort -u`
- 结果：旧/新路由均为 `90` 条，`missing=0`，`extra=0`。

## 3. 最短路径实现（rules: Phase 3）
- 新增 7 个 domain service 接口（按 core/member/lesson/assignment/session/monitor/aiProblem 划分）。
- 新增 7 个 domain service 实现类，全部委托 `ClassroomService`，不引入兼容分支与新业务逻辑。
- 删除单体 `ClassroomController.java`，改为 7 个资源 Controller，保留全部原路径、方法、参数、返回结构。

## 4. 测试验证（rules: Phase 4）

### 4.1 本批次目标测试
- `cd backend && mvn -Dtest=ClassroomControllerContractTest test` -> 通过（1/1）。
- `cd backend && mvn -Dtest=ClassroomModuleIntegrationTest,ClassroomM10IntegrationTest,ClassroomM11IntegrationTest,ClassroomMonitorScaleIntegrationTest test` -> 失败（Flyway V16 基线问题导致上下文失败，非本批新增）。

### 4.2 全量回归
- `cd backend && mvn test` -> 失败（`Tests run: 99, Errors: 38`，与批次0基线一致）。
- `cd frontend && npm run test` -> 失败（`ai-terminology-consistency.spec.js` 两处断言，和批次0一致）。
- `cd frontend && node tests/test_frontend_smoke.js` -> 通过（`16/16`）。

## 5. code-reviewer 审查（rules: Phase 5）
- Security：无新增鉴权绕过与输入拼接风险（仅委托调用，未新增 SQL/外部输入执行）。
- Performance：无新增 I/O 或循环复杂度，调用链增加 1 层委托，影响可忽略。
- Correctness：路由逐条对比一致，契约测试通过，无参数/返回结构漂移。
- Maintainability：Controller 与域职责拆分完成，文件粒度收敛，后续可按域独立演进。
- 阻断项：0。

## 6. 结论
- 批次1已完成“分析 -> 映射 -> 实现 -> 测试 -> 审查”闭环。
- 当前遗留失败均为批次0已冻结基线问题，未发现批次1新增回归。
