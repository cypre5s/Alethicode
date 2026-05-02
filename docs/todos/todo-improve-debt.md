# 技术债分阶段治理 TODO（交接版）

> 文档状态：待执行
> 更新日期：2026-04-03
> 适用仓库：`/home/cypress/Alethicode`
> 执行方式：单人推进，按风险优先顺序串行治理
> 唯一目标：只围绕当前已经确认、且已经有代码证据支撑的硬技术债，做一份可落地、可验收、可交接的分阶段治理方案，不引入推测项，不扩展业务目标。
> 当前基线命令：
> - 后端编译：`cd /home/cypress/Alethicode/backend && mvn -q -DskipTests compile`
> - 前端静态检查：`cd /home/cypress/Alethicode/frontend && npm run lint -- --quiet`
> - 异常吞噬扫描：`cd /home/cypress/Alethicode && rg -n "catch \\(Exception ignored\\)|catch \\(Exception e\\)|catch \\(Exception exception\\)" backend/src/main/java | wc -l`
> 当前基线事实：
> - 后端 `compile` 当前可通过
> - 前端 `lint` 当前失败，报 `189` 个 error
> - 后端当前约有 `90` 处 `catch (Exception...)`

---

## 0. 这份文档的定位与硬性原则

### 0.1 这份文档解决什么

- [ ] 这份 TODO 只处理已经确认的硬技术债，不处理推测项，不顺手追加新的“未来想做项”。
- [ ] 这份 TODO 的作用不是“记录问题”，而是定义分阶段治理顺序、必须改的点和未过即失败的验收门槛。
- [ ] 这份 TODO 面向当前仓库维护者本人，默认读者已经了解项目背景和代码目录，但不默认读者会自己补齐治理边界。

### 0.2 这份文档不解决什么

- [ ] 不处理未证实的潜在安全问题、潜在性能问题或未来可能出现的代码异味。
- [ ] 不扩展到业务路线调整，不引入新产品目标，不把“技术债治理”变成新功能规划。
- [ ] 不展开到每一个 symbol 级别的重命名或所有实现细节，这里只定义治理边界与验收口径。

### 0.3 硬性要求

- [ ] 不允许补丁式兼容，不允许为了短期通过而保留旧结构长期并行。
- [ ] 不允许把未确认问题混入本次治理，所有债项都必须有代码证据或命令结果支撑。
- [ ] 不允许跳过阶段直接做后续重构，任何阶段未过验收，下一阶段不得开始。
- [ ] 不允许“看起来优化了”但没有可执行验收命令、可核对事实或可复现回归场景。
- [ ] 不允许把安全、质量门、架构拆债、长期治理揉成一个大阶段，必须按风险顺序逐阶段收口。

---

## 1. 当前已确认技术债快照

### 1.1 本次只纳入的 6 类技术债

- [ ] 管理上传接口匿名可写。
- [ ] 配置文件与代码中存在默认密钥、默认口令或默认 token，而不是统一从受版本控制之外的 `.env` 读取。
- [ ] 全局安全边界过宽，权限控制过度依赖业务层零散判断。
- [ ] 前端 lint 质量门失效，仓库当前不具备稳定静态质量约束。
- [ ] 后端与前端都存在巨石类、巨石页面，单文件职责严重混杂。
- [ ] 已确认存在 N+1 查询与异常吞噬问题，影响性能和故障定位。

### 1.2 当前基线事实

- [ ] 后端命令 `mvn -q -DskipTests compile` 当前可通过。
- [ ] 前端命令 `npm run lint -- --quiet` 当前失败，报 `189` 个 error。
- [ ] 后端异常吞噬扫描结果当前约为 `90` 处。
- [ ] 当前巨石文件基线如下：
  - `backend/src/main/java/com/alethicode/service/impl/ClassroomServiceImpl.java`：`4474` 行
  - `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`：`4446` 行
  - `backend/src/main/java/com/alethicode/service/impl/AITutorServiceImpl.java`：`2410` 行
  - `backend/src/main/java/com/alethicode/service/impl/SubmissionServiceImpl.java`：`2305` 行
  - `frontend/src/pages/oj/views/problem/Problem.vue`：`3035` 行
  - `frontend/src/pages/oj/views/classroom/MonitorDashboard.vue`：`1742` 行
  - `frontend/src/pages/oj/views/problem/workflowStateMachine.js`：`1225` 行
  - `frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`：`981` 行

### 1.3 已确认债务证据文件

- [ ] 安全边界证据：
  - `backend/src/main/java/com/alethicode/config/SecurityConfig.java`
  - `backend/src/main/java/com/alethicode/controller/AdminUploadController.java`
  - `backend/src/main/java/com/alethicode/service/impl/AdminUploadServiceImpl.java`
- [ ] 默认密钥 / 口令证据：
  - `backend/src/main/resources/application.yml`
  - `backend/src/main/resources/application-dev.yml`
  - `backend/src/main/java/com/alethicode/config/AlethicodeProperties.java`
  - `.gitignore` 中已忽略的 `backend/.env`
  - `.gitignore` 中已忽略的 `deploy/.env`
- [ ] 工程质量门证据：
  - `frontend/package.json`
  - `.gitignore`
- [ ] 结构与性能债证据：
  - `backend/src/main/java/com/alethicode/service/impl/ClassroomServiceImpl.java`
  - `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
  - `backend/src/main/java/com/alethicode/service/impl/AITutorServiceImpl.java`
  - `backend/src/main/java/com/alethicode/service/impl/SubmissionServiceImpl.java`
  - `frontend/src/pages/oj/views/problem/Problem.vue`
  - `frontend/src/pages/oj/views/classroom/MonitorDashboard.vue`
  - `frontend/src/pages/oj/views/problem/workflowStateMachine.js`
  - `frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`

### 1.4 本次不纳入的范围

- [ ] 不把“也许过时的依赖版本”单独立项，除非它与已确认质量门或安全边界债直接绑定。
- [ ] 不把所有文档、所有测试、所有老代码样式问题都纳入治理。
- [ ] 不把业务逻辑优化、页面体验优化、功能重做伪装成技术债治理。

---

## 2. 分阶段治理方案

### 阶段 0：冻结债务基线，禁止继续放大

本阶段目标：

- 把当前已确认硬债固定为统一基线。
- 后续治理全部以这份基线为准，不再边做边扩债。
- 在文档中明确本次治理处理什么、不处理什么。

必须改的点：

- 固化本次只处理的 6 类硬技术债。
- 固化当前可复现命令、关键失败事实和关键证据文件。
- 固化阶段顺序和阶段依赖，不允许执行时自由变更。

专项验收步骤：

1. 技术债清单仅包含已确认的 6 类问题，不新增猜测项。
2. 每条债都能在文档中找到对应证据文件或命令结果。
3. 当前基线命令被固定记录为：
   - `mvn -q -DskipTests compile`
   - `npm run lint -- --quiet`
   - `rg -n "catch \\(Exception ignored\\)|catch \\(Exception e\\)|catch \\(Exception exception\\)" backend/src/main/java | wc -l`
4. 文档明确写出阶段 `0 -> 1 -> 2 -> 3 -> 4` 的执行顺序，不允许跳阶段。

### 阶段 1：先修安全边界与密钥配置

本阶段目标：

- 先封住真实安全风险。
- 把“默认放行 + 业务层补权限”改回明确边界。
- 清掉 repo 中的默认口令与 token，并统一改为从 `.env` 读取。

必须改的点：

- `SecurityFilterChain` 从 `anyRequest().permitAll()` 改为显式 public allowlist。
- `/api/admin/upload-image` 与 `/api/admin/upload-file` 强制管理员鉴权。
- 管理控制器不再依赖“service 自己记得验权”。
- 所有密钥、密码、token 统一从受版本控制之外的 `.env` 读取。
- 开发态配置统一从 `backend/.env` 读取，部署态配置统一从 `deploy/.env` 读取。
- 数据库密码、Redis 密码、judge token、SMTP 密码、第三方 API key 等敏感配置不再以明文默认值提交到受版本控制文件。
- 缺失关键密钥时直接 failfast，不做静默兜底。

专项验收步骤：

1. 未登录访问管理上传接口时，返回 `401` 或 `403`。
2. 普通用户访问管理上传接口时，返回 `403`。
3. 管理员访问管理上传接口时，返回成功响应。
4. 管理上传接口契约测试必须覆盖：
   - 未登录
   - 普通用户
   - 管理员
5. `application.yml`、`application-dev.yml`、`AlethicodeProperties.java` 中不再出现默认口令、默认密码或默认 token。
6. 所有敏感配置项都能在 `.env` 中找到来源，并且运行时不依赖受版本控制文件中的默认值。
7. 代码结构中不再保留“全局 permitAll + 局部手写兜底权限”的主安全模型。

阶段完成标志：

- [ ] 管理上传接口已从“匿名可写”改为“管理员可写”。
- [ ] 默认明文密钥、密码和口令已移出受版本控制文件，并统一改为从 `.env` 读取。
- [ ] 安全边界已经从零散业务判断收回到明确框架边界。

### 阶段 2：恢复质量门与工程基线

本阶段目标：

- 先让仓库重新具备“改完能验”的基本能力。
- 恢复前端静态质量门。
- 固定最小可执行的工程验收入口。

必须改的点：

- 清零当前前端 lint 错误。
- 固定后端与前端的最小验收命令。
- 补齐最基础的自动化校验入口。
- 修正 `.gitignore`，避免构建产物和本地依赖目录反复污染现场。

专项验收步骤：

1. `npm run lint -- --quiet` 返回 `0`。
2. `mvn -q -DskipTests compile` 返回 `0`。
3. 至少存在一条统一可执行的自动化校验链覆盖以上两个命令。
4. `.gitignore` 至少覆盖：
   - `backend/target/`
   - `frontend/dist/`
   - `frontend/node_modules/`
   - `frontend/test-results/`
5. 前端质量门恢复后，不允许再以“先改功能、最后一起修 lint”为理由绕过静态检查。

阶段完成标志：

- [ ] 前端 lint 已恢复为可通过状态。
- [ ] 后端 compile 与前端 lint 已成为固定验收入口。
- [ ] 本地产物和依赖目录不会继续污染工作区和提交现场。

### 阶段 3：拆巨石模块，清掉高频正确性与性能债

本阶段目标：

- 把当前最危险的巨石类和巨石页面拆到可持续维护的粒度。
- 清掉已经确认的 N+1 和异常吞噬问题。
- 让权限、安全、查询、视图逻辑回到清晰边界。

必须优先拆分的对象：

- 后端优先：
  - `ClassroomServiceImpl`
  - `AITutorWorkflowAdminServiceImpl`
  - `AITutorServiceImpl`
  - `SubmissionServiceImpl`
- 前端优先：
  - `Problem.vue`
  - `MonitorDashboard.vue`
  - `workflowStateMachine.js`
  - `UnifiedAgentPanel.vue`

必须改的点：

- `ClassroomServiceImpl` 不再同时承担课堂、成员、课件、作业、AI 出题等多域职责。
- `AITutorWorkflowAdminServiceImpl` 不再混放 workflow session、事件处理、KC 审核、预检统计、误区治理等多类职责。
- `Problem.vue` 不再同时承担题面渲染、工作流编排、AI 面板协调、编辑区状态管理等全部职责。
- 已确认的 `ClassroomServiceImpl` 提交详情查询 N+1 必须改成批量查询。
- websocket、controller、account/session 等关键路径不再直接吞掉 `Exception`，至少要改成带上下文日志的显式处理。

专项验收步骤：

1. `ClassroomServiceImpl` 已完成按领域职责拆分，原文件不再继续承载新域逻辑。
2. `AITutorWorkflowAdminServiceImpl` 已完成按工作流域与管理域拆分，原文件不再继续堆叠审核、治理、统计等无关职责。
3. `Problem.vue` 已拆分出题面渲染、工作流编排、AI 面板协调、编辑器交互等核心子职责。
4. 已确认的 `ClassroomServiceImpl` 提交详情查询路径不再在 row mapper 内嵌套查 details。
5. 关键路径中直接吞异常的写法已被显式日志或显式失败替代，并且吞异常数量下降到阶段定义的目标范围。
6. 本阶段结束后，目标文件不再继续增长，建议采用以下软阈值作为治理约束：
   - 核心后端服务单文件尽量压到 `2000` 行以内
   - 核心前端页面单文件尽量压到 `1200` 行以内
7. 拆分后必须补充对应测试，不允许“只拆文件不补验证”。

阶段完成标志：

- [ ] 巨石类与巨石页面已经回到清晰职责边界。
- [ ] 已确认的 N+1 查询已消除。
- [ ] 关键路径异常不再被静默吞掉。

### 阶段 4：建立长期治理闭环，防止债务反弹

本阶段目标：

- 把这次还债成果固化成长期约束。
- 避免数周后重新回到现在的状态。

必须改的点：

- 把“安全边界、质量门、巨石文件、异常吞噬”变成固定巡检项。
- 新增管理接口默认要求鉴权测试。
- 新增前端页面或后端服务默认要求通过既定质量门。
- 对巨石文件设置持续监控，不允许再次野蛮膨胀。

专项验收步骤：

1. 新增或修改管理接口时，必须有权限测试。
2. PR 或日常校验链中必须包含前端 lint 与后端 compile。
3. 必须存在固定脚本、固定规则或固定 grep 检查以下高风险模式：
   - `anyRequest().permitAll()`
   - 管理上传接口无权限保护
   - 明文默认口令 / token
   - `catch (Exception ignored)` 这类吞异常
4. `todo_improve_debt.md` 在全部阶段完成后允许归档，但归档前必须逐项勾掉所有验收项。

阶段完成标志：

- [ ] 这次治理成果已经被固定进长期巡检和日常校验链。
- [ ] 新增债务不再能轻易绕过既定约束重新进入主干。

---

## 3. 总体验收口径

### 3.1 必须明确的公共接口与边界变化

- [ ] 安全接口变化：
  - `/api/admin/upload-image`
  - `/api/admin/upload-file`
  必须从“匿名可写”改为“管理员可写”。
- [ ] 配置接口变化：
  - 数据库密码、Redis 密码、judge token，以及其他敏感 key / password / token，统一改为从 `.env` 读取。
  - 开发态使用 `backend/.env`，部署态使用 `deploy/.env`。
  - 缺值时应用 failfast。
- [ ] 工程入口变化：
  - 前端 lint 与后端 compile 成为固定验收入口。
- [ ] 架构边界变化：
  - 管理权限不能继续主要依赖 service 层临时判断。
  - 巨石模块必须拆回清晰领域边界。

### 3.2 必须持续可执行的回归项

- [ ] 安全回归：
  - 匿名请求管理上传接口应失败
  - 普通用户请求管理接口应失败
  - 管理员请求管理接口应成功
- [ ] 配置回归：
  - 受版本控制文件中不再出现明文默认密钥、密码或 token
  - 敏感配置统一从 `.env` 读取
  - 缺少必要配置时应用 failfast
- [ ] 工程回归：
  - `mvn -q -DskipTests compile`
  - `npm run lint -- --quiet`
- [ ] 架构回归：
  - 针对目标巨石模块的测试仍通过
  - 已确认的 N+1 查询路径改为批量拉取
  - 关键路径吞异常数量下降到本阶段定义的目标范围
- [ ] 治理回归：
  - 固定检查脚本或规则可以重新跑出“未反弹”结论

### 3.3 这份 TODO 什么时候算完成

- [ ] 阶段 0 到阶段 4 的所有验收项全部勾掉。
- [ ] 当前确认的 6 类硬技术债都已经被处理，不再停留在“知道问题但尚未治理”的状态。
- [ ] 仓库已经恢复基本工程可信度：能编译、能 lint、能回归、能拒绝高风险模式反弹。
- [ ] 后续再新增管理接口、再新增核心页面、再新增核心服务时，默认会落入既定安全边界和质量门，而不是重新从零治理一遍。

### 3.4 未完成前的禁止事项

- [ ] 在阶段 1 未完成前，不允许继续扩展管理上传、管理接口或安全相关能力。
- [ ] 在阶段 2 未完成前，不允许以“先做功能”为理由继续累积前端 lint 债。
- [ ] 在阶段 3 未完成前，不允许继续向既有巨石类和巨石页面堆叠新域逻辑。
- [ ] 在阶段 4 未完成前，不允许声称“技术债已经治理完成”。
