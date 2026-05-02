# 全栈主干拆解基线冻结（批次0）

## 1. 目标与约束
- 目标：在不改变外部接口契约前提下，冻结当前测试与行为基线，作为后续拆分回归标准。
- 约束：路径/方法/参数/响应结构保持不变；不改数据库 schema 与 Flyway 历史迁移。

## 2. 基线命令与结果（2026-03-26）

### 2.1 后端全量测试
- 命令：`cd backend && mvn test`
- 结果：失败（`Tests run: 99, Errors: 38`）
- 关键失败点：
  - Flyway `V16__enable_pg_stat_and_add_top5_indexes.sql` 在测试库执行失败（`column \"create_time\" does not exist`）。
  - 多个 service 单测与当前异常体系不匹配（预期旧返回模式，实际抛出 `BusinessException`/`UnauthorizedException`）。
  - 集成测试因应用上下文初始化失败被连带跳过（failure threshold exceeded）。

### 2.2 前端单元测试
- 命令：`cd frontend && npm run test`
- 结果：失败（`Test Suites: 1 failed, 2 passed; Tests: 2 failed, 11 passed`）
- 关键失败点：
  - `tests/unit/ai-terminology-consistency.spec.js` 断言与当前文案不一致（SubmissionDetails / Problem 页面字符串）。

### 2.3 前端 smoke
- 命令：`cd frontend && node tests/test_frontend_smoke.js`
- 结果：通过（`TOTAL: 16 | PASS: 16 | FAIL: 0`）

## 3. 拆解任务清单（执行顺序）
1. 批次1：Classroom 主链路拆分（controller 先拆、service 再拆）。
2. 批次2：AITutor 主链路拆分（workflow/admin 分层）。
3. 批次3：Submission + Account + AdminProblemCommand 拆分。
4. 批次4：前端 API HTTP 层统一与按域拆分。
5. 批次5：Problem.vue 容器化拆分。
6. 批次6：MonitorDashboard.vue 容器化拆分。
7. 批次7：公共工具收口与架构文档更新。

## 4. 验收矩阵（每批次固定）
| 验收项 | 执行命令 | 通过标准 |
|---|---|---|
| 后端目标测试 | `cd backend && mvn -Dtest=<TargetTests> test` | 目标用例全绿 |
| 后端全量回归 | `cd backend && mvn test` | 无新增失败（允许继承批次0已知失败） |
| 前端目标测试 | `cd frontend && npm run test -- <TargetSpec>` | 目标用例全绿 |
| 前端全量回归 | `cd frontend && npm run test` | 无新增失败（允许继承批次0已知失败） |
| 前端 smoke | `cd frontend && node tests/test_frontend_smoke.js` | 16/16 通过 |
| 代码审查 | `code-reviewer` 流程 | Security/Performance/Correctness/Maintainability 无阻断项 |
| 变更记录 | `CHANGELOG.md` | 每批次新增中文记录（时间、范围、验证） |

## 5. 已知前置风险（冻结）
- 当前仓库在批次0前已存在测试失败，不属于本次拆解新引入问题。
- 后续批次必须先修复与本批次直接相关的失败，再推进下一批次。
