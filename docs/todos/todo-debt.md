# 技术债审计报告

> 审计日期：2026-04-04
> 仓库：`/home/cypress/Alethicode`
> 审计方式：只读静态审计 + 基线命令复核
> 报告定位：本报告是当前仓库技术债的事实基线，结论只建立在已读取代码、配置、脚本、测试入口和命令结果上；历史 TODO 文档仅作为对照，不作为事实来源。

## 1. 审计范围与排除范围

### 审计范围

- 根目录配置与治理文件
- `backend` 源码、资源、测试、Maven 配置
- `frontend` 源码、测试、Vite / ESLint / Jest / Playwright 配置
- `deploy` 部署编排、容器与 nginx 配置
- `scripts` 里的校验、启动、基线、同步脚本
- `tests` 与 `docs`

### 排除范围

- `frontend/node_modules`
- `frontend/dist`
- `backend/target`
- `.git`
- `db_backups`
- `deploy/data`
- 其他生成物、缓存目录、第三方依赖目录

### 当前仓库规模快照

- 后端主源码：`319` 个 Java 文件
- 后端测试：`80` 个 Java 文件
- 前端页面/组件：`96` 个 `.vue` 文件
- 前端脚本/模块：`75` 个 `.js` 文件
- 文档：`61` 个 Markdown 文件

## 2. 基线命令与当前事实

### 基线命令

```bash
cd /home/cypress/Alethicode/backend && mvn -q -DskipTests compile
cd /home/cypress/Alethicode/frontend && npm run lint -- --quiet
cd /home/cypress/Alethicode && rg -n "catch \\(Exception ignored\\)|catch \\(Exception e\\)|catch \\(Exception exception\\)" backend/src/main/java | wc -l
```

### 当前事实

- 后端 `compile` 当前通过。
- 前端 `lint` 当前失败，报 `194` 个 error。
- 后端异常吞噬 / 宽泛异常捕获扫描结果当前为 `95` 处。
- 现有债务文档 [docs/todos/todo-improve-debt.md](./todo-improve-debt.md) 中的旧基线已漂移：
  - 前端 lint：旧文档为 `189`，当前实测为 `194`
  - 异常捕获：旧文档为约 `90`，当前实测为 `95`
  - 多个超大文件行数也已继续增长

## 3. 总览

| 技术债类型 | 严重级别 | 当前证据 | 影响范围 | 治理优先级 |
| --- | --- | --- | --- | --- |
| 安全边界与敏感配置债 | P0 | `SecurityConfig` 全局 `permitAll`；管理上传接口无控制器层鉴权；配置与部署文件含默认口令 / token | 后端安全边界、上传链路、部署配置 | 1 |
| 工程质量门与工具链债 | P1 | Vue 3 / Vite 7 与 ESLint 3 / Jest 23 并存；前端 lint 194 错误；测试入口分散且有过期说明 | 前端研发效率、静态校验可信度 | 2 |
| 结构复杂度与职责混杂债 | P1 | 4.5k 行服务类、3k 行页面、1.3k 行状态机、27 个依赖注入集中在单类 | 后端维护性、前端回归风险、认知成本 | 3 |
| 正确性 / 可观测性 / 数据访问模式债 | P1 | `95` 处宽泛异常捕获；多处循环内逐条写库；WebSocket / 视频链路存在 `ignored` catch | 故障定位、批量性能、运行时一致性 | 4 |
| 仓库治理与文档漂移债 | P2 | 历史债务文档基线过期；脚本/文档含绝对路径与旧目录名；忽略规则和验收入口分散 | 文档可信度、环境可移植性、协作成本 | 5 |

## 4. 详细债项

### 4.1 安全边界与敏感配置债

**债务类型**

- 安全边界错误
- 敏感配置泄露风险

**现象与证据**

- [backend/src/main/java/com/alethicode/config/SecurityConfig.java](backend/src/main/java/com/alethicode/config/SecurityConfig.java)
  - 当前主安全模型为：
  - `requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()`
  - `.anyRequest().permitAll()`
- [backend/src/main/java/com/alethicode/controller/AdminUploadController.java](backend/src/main/java/com/alethicode/controller/AdminUploadController.java)
  - `/api/admin/upload-image`
  - `/api/admin/upload-file`
  - 控制器层没有 `@PreAuthorize`
- [backend/src/main/java/com/alethicode/service/impl/AdminUploadServiceImpl.java](backend/src/main/java/com/alethicode/service/impl/AdminUploadServiceImpl.java)
  - 服务层直接写入上传目录，未承担鉴权职责
- [backend/src/main/java/com/alethicode/middleware/SessionAuthenticationFilter.java](backend/src/main/java/com/alethicode/middleware/SessionAuthenticationFilter.java)
  - 当前会把 `Admin`、`Super Admin`、`Teacher` 映射为 `ROLE_ADMIN`
  - 这意味着系统本身已经有角色能力，但上传接口没有落在边界上
- [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)
  - 数据库密码默认值：`ChangeMeBeforeDeploy_2026!`
  - Judge token 默认值：`ChangeMeBeforeDeploy_Token_2026!`
- [backend/src/main/resources/application-dev.yml](backend/src/main/resources/application-dev.yml)
  - Redis 密码默认值：`AlethicodeRedis2026!`
- [backend/src/main/java/com/alethicode/config/AlethicodeProperties.java](backend/src/main/java/com/alethicode/config/AlethicodeProperties.java)
  - `JudgeServer.token` 仍带代码默认值
- [deploy/docker-compose.yml](deploy/docker-compose.yml)
  - PostgreSQL、Redis、Judge token 仍以明文默认值出现在受版本控制文件中

**影响链路**

- 输入：未登录用户、普通用户或任意可访问接口的客户端请求管理上传接口
- 处理流程：`SecurityFilterChain` 全局放行，请求直接进入未加角色约束的上传控制器
- 状态变化：文件被写入 `upload-dir`
- 输出：接口返回 `file_path`
- 上下游影响：上传产物会进入后续内容引用链路，风险不是单个请求失败，而是边界本身失效

**根因判断**

- 项目采用了“默认放行 + 局部接口记得加注解”的安全模型
- 上传接口刚好落在“没人拦”的缝隙里
- 敏感配置同时存在“环境变量入口”和“代码 / yml 默认值”，导致运行时可能继续依赖版本库里的秘密

**最短正确解决方案**

1. 在 `SecurityConfig` 中移除 `.anyRequest().permitAll()` 作为主模型，改为“显式 public allowlist + 其他接口按认证/角色收口”。
2. 在 `AdminUploadController` 上直接加管理员角色约束，复用现有 `ROLE_ADMIN` / `ROLE_SUPER_ADMIN` 体系，不再把上传权限寄托在 service 或调用方记忆上。
3. 清除 `application.yml`、`application-dev.yml`、`AlethicodeProperties.java`、`deploy/docker-compose.yml` 中的默认口令、默认 token、默认密码。
4. 统一改为从 `backend/.env`、`deploy/.env` 注入；受版本控制文件只保留占位符，不保留真实秘密，也不保留“ChangeMe”类默认值。
5. 对关键秘密项做启动时非空校验；缺失直接 fail-fast，不允许静默回退到代码默认值。

**改造顺序**

1. 先收口 `SecurityFilterChain`
2. 再锁定上传控制器
3. 再迁移敏感配置到 `.env`
4. 最后补上传接口契约测试

**验收命令 / 验收场景**

- 未登录访问管理上传接口：返回 `401` 或 `403`
- 普通用户访问管理上传接口：返回 `403`
- 管理员访问管理上传接口：成功返回
- 搜索版本库中的默认秘密：

```bash
cd /home/cypress/Alethicode && rg -n "ChangeMeBeforeDeploy|AlethicodeRedis2026|ChangeMeBeforeDeploy_Token" backend/src/main/resources backend/src/main/java deploy
```

预期：只允许出现在示例模板或显式标注的占位文档中，不能出现在运行配置与源码默认值里。

### 4.2 工程质量门与工具链债

**债务类型**

- 静态质量门失效
- 前端工具链断层

**现象与证据**

- [frontend/package.json](frontend/package.json)
  - 运行时栈：`vue@3.5.13`、`vite@7.1.5`
  - 校验栈：`eslint@3.19.0`、`babel-eslint@7.1.1`、`eslint-loader@1.7.1`、`jest@23.6.0`
- [frontend/.eslintrc](frontend/.eslintrc)
  - 仍是旧版配置方式，依赖 `babel-eslint` 与 `eslint-plugin-html`
- 基线命令实测：
  - `npm run lint -- --quiet` 当前失败，`194` 个 error
  - 主要错误集中在 `object-property-newline`、`comma-style`、`no-multiple-empty-lines`
- [frontend/tests/test_frontend_smoke.js](frontend/tests/test_frontend_smoke.js)
  - 头部说明仍写 `cd frontend_new && node tests/test_frontend_smoke.js`
  - 与当前目录结构不一致，说明测试入口文案已过期
- [frontend/tests/e2e/playwright.config.js](frontend/tests/e2e/playwright.config.js)
  - Playwright 仍依赖本机库路径拼接和默认 `http://127.0.0.1:80`
- 现状不是“没有测试”，而是：
  - lint
  - jest
  - smoke
  - playwright
  - replacement audit
  - 分散在多个入口，且没有统一收口

**影响链路**

- 输入：开发者提交前端改动
- 处理流程：lint 先天不干净，测试入口又分散且有过期说明
- 状态变化：团队对“改完是否真的过线”没有单一可信判断
- 输出：新改动容易在旧错误噪音中继续堆积
- 上下游影响：前端迁移、样式修复、页面拆分都会因为缺少干净基线而放大成本

**根因判断**

- Vue 3 / Vite 迁移发生了，但 lint / test 工具链没有同步升级
- 结果是运行时栈已经现代化，静态校验栈仍停留在旧年代
- 质量门没有被定义成一个唯一入口，而是散落在多个脚本里

**最短正确解决方案**

1. 以前端当前实际运行栈为准，直接升级到与 Vue 3 / Vite 7 对齐的 lint 方案，不再继续维护 `eslint@3 + babel-eslint + eslint-loader`。
2. 采用单一现代 lint 栈，例如 `ESLint 9 + vue-eslint-parser + @babel/eslint-parser + eslint-plugin-vue`，并把配置迁移为当前受支持的格式。
3. 同一批次内清零当前 `194` 个 lint 错误；不接受通过局部关闭规则、批量 `eslint-disable`、或“先记账后再修”来过线。
4. 固定一个仓库级校验入口，至少串联：
   - 后端 `compile`
   - 前端 `lint`
   - 前端 `npm test -- --runInBand`
5. 同步修正所有过期测试说明与目录名，例如 `frontend_new` 这类旧路径。

**改造顺序**

1. 先升级 lint 工具链
2. 再清理现有 `194` 个错误
3. 再收口统一校验脚本
4. 最后修正测试说明和环境约束文案

**验收命令 / 验收场景**

```bash
cd /home/cypress/Alethicode/frontend && npm run lint -- --quiet
```

预期：返回 `0`

```bash
cd /home/cypress/Alethicode && rg -n "frontend_new" frontend/tests docs scripts
```

预期：返回空

```bash
cd /home/cypress/Alethicode/frontend && npm test -- --runInBand
```

预期：返回 `0`，并成为仓库级统一校验入口的一部分。

### 4.3 结构复杂度与职责混杂债

**债务类型**

- 巨石服务类
- 巨石页面 / 巨石状态机

**现象与证据**

- 后端超大类：
  - [backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java](backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java)：`4584` 行
  - [backend/src/main/java/com/alethicode/service/impl/ClassroomServiceImpl.java](backend/src/main/java/com/alethicode/service/impl/ClassroomServiceImpl.java)：`4488` 行
  - [backend/src/main/java/com/alethicode/service/impl/AITutorServiceImpl.java](backend/src/main/java/com/alethicode/service/impl/AITutorServiceImpl.java)：`2422` 行
  - [backend/src/main/java/com/alethicode/service/impl/SubmissionServiceImpl.java](backend/src/main/java/com/alethicode/service/impl/SubmissionServiceImpl.java)：`2305` 行
- 前端超大模块：
  - [frontend/src/pages/oj/views/problem/Problem.vue](frontend/src/pages/oj/views/problem/Problem.vue)：`3050` 行
  - [frontend/src/pages/oj/views/classroom/MonitorDashboard.vue](frontend/src/pages/oj/views/classroom/MonitorDashboard.vue)：`1742` 行
  - [frontend/src/pages/oj/views/problem/workflowStateMachine.js](frontend/src/pages/oj/views/problem/workflowStateMachine.js)：`1345` 行
  - [frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue](frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue)：`1219` 行
- `AITutorWorkflowAdminServiceImpl` 在文件头部连续持有 `27` 个 `private final` 依赖，且公开方法覆盖 session、checkpoint、variant、kc、misconception、preflight 等多个子域。
- `Problem.vue` 同时承担：
  - 题面渲染
  - 代码编辑器装配
  - 客观题答题区
  - AI 学习助手接线
  - 工作流状态机 mixin 协调
- `workflowStateMachine.js` 同时承担：
  - WebSocket 生命周期
  - runtime contract 归一化
  - 本地缓存
  - quick action 规则
  - UI 驱动状态
- `UnifiedAgentPanel.vue` 同时承担：
  - runtime banner
  - 时间线渲染
  - 多种消息卡片
  - 用户反馈
  - 输入与快捷操作

**影响链路**

- 输入：任何一个子功能改动
- 处理流程：开发者必须进入同一大文件同时理解多条业务支线
- 状态变化：非相关逻辑被连带触碰
- 输出：回归风险和评审成本显著放大
- 上下游影响：后续安全加固、性能治理、页面重构都会被这些巨石文件卡住

**根因判断**

- 业务功能持续叠加，但边界没有同步抽象成独立子模块
- 旧入口文件被反复追加职责，最终从“编排层”膨胀成“编排 + 查询 + 命令 + 状态 + UI”

**最短正确解决方案**

1. 后端按子域拆，不按“把文件拆小一点”拆。
2. `AITutorWorkflowAdminServiceImpl` 拆为：
   - 工作流会话服务
   - checkpoint 服务
   - 变体审核服务
   - KC 管理服务
   - misconception 审核服务
   - preflight / 诊断服务
3. `ClassroomServiceImpl` 拆为：
   - 查询服务
   - 命令服务
   - 课件导入服务
   - 题目导入服务
   - 监控聚合服务
4. `AITutorServiceImpl` 拆为：
   - 学习事件写入服务
   - 快照服务
   - 学习画像 / 统计投影服务
5. `SubmissionServiceImpl` 拆为：
   - 提交命令服务
   - 提交查询服务
   - 客观题判题服务
   - 提交结果投影服务
6. 前端按容器与子组件拆：
   - `Problem.vue` 只保留页面编排，题面、编辑区、客观题区、AI 工作流区各自独立
   - `workflowStateMachine.js` 拆为 runtime client、状态迁移器、缓存适配器、动作策略
   - `UnifiedAgentPanel.vue` 拆为 runtime banner、timeline renderer、composer / quick action
7. 拆分后原入口只保留编排职责，不再继续直接持有全部查询、写库、渲染和状态细节。

**改造顺序**

1. 先拆 `AITutorWorkflowAdminServiceImpl`
2. 再拆 `ClassroomServiceImpl`
3. 再拆 `Problem.vue + workflowStateMachine.js`
4. 最后拆 `AITutorServiceImpl`、`SubmissionServiceImpl`、`UnifiedAgentPanel.vue`

**验收命令 / 验收场景**

- 上述 8 个超大文件不再承担多子域职责
- `AITutorWorkflowAdminServiceImpl` 不再直接持有 20+ 协作者
- 入口文件只负责编排，子域逻辑迁移到按职责命名的新模块
- 拆分后保持原有 compile / lint / 关键页面回归通过

### 4.4 正确性 / 可观测性 / 数据访问模式债

**债务类型**

- 宽泛异常捕获
- 异常吞噬
- 循环内逐条写库 / 查询

**现象与证据**

- 基线扫描结果：后端 `catch (Exception ignored|e|exception)` 当前 `95` 处
- 典型异常吞噬点：
  - [backend/src/main/java/com/alethicode/service/languagepack/impl/VideoJobServiceImpl.java](backend/src/main/java/com/alethicode/service/languagepack/impl/VideoJobServiceImpl.java) 存在 `catch (Exception ignored) {}`
  - 多个 WebSocket handler / support 文件存在 `catch (Exception ignored)` 或 `catch (Exception e)`
- 典型逐条写库点：
  - [backend/src/main/java/com/alethicode/service/impl/ClassroomServiceImpl.java](backend/src/main/java/com/alethicode/service/impl/ClassroomServiceImpl.java)
    - `importLanguagePackLessons`：循环内逐条 `insert classroom_lesson`
    - `importLanguagePackProblems`：循环内逐条 `insert classroom_problem`
  - [backend/src/main/java/com/alethicode/service/impl/AITutorServiceImpl.java](backend/src/main/java/com/alethicode/service/impl/AITutorServiceImpl.java)
    - `learningEventsBatch`：`events` 循环内逐条 `insert ai_learning_event`
  - [backend/src/main/java/com/alethicode/service/languagepack/impl/KcExtractionServiceImpl.java](backend/src/main/java/com/alethicode/service/languagepack/impl/KcExtractionServiceImpl.java)
    - `insertCanonicalKcs`：每个 KC `upsert + returning id`，随后再按页逐条插 `language_pack_kc_page_mapping`
- 只读扫描还确认：
  - `ClassroomServiceImpl`
  - `AITutorWorkflowAdminServiceImpl`
  - `AITutorServiceImpl`
  - `SubmissionServiceImpl`
  - `DocumentNormalizationServiceImpl`
  - `ExampleExtractionServiceImpl`
  - `KcExtractionServiceImpl`
  - `ProblemGenerationServiceImpl`
  中均存在“循环附近紧邻 DB 调用”的高频模式

**影响链路**

- 输入：批量导入、批量学习事件、语言包抽取、WebSocket 实时链路
- 处理流程：
  - 异常被宽泛捕获后丢失上下文
  - 批处理流程逐条往返数据库
- 状态变化：
  - 失败信息不完整
  - SQL 往返次数与数据量线性膨胀
- 输出：问题难复现、难定位、吞吐差
- 上下游影响：一旦数据量、并发、课件规模上来，这类模式会直接限制吞吐和排障速度

**根因判断**

- service 层同时承担 orchestration、SQL、异常翻译与批处理写入
- 项目缺少统一的异常处理策略和批量持久化策略

**最短正确解决方案**

1. 先清理真正的吞异常：
   - `catch (Exception ignored)` 在核心业务链路中不允许继续存在
   - 允许转换异常，但必须记录上下文并明确 rethrow
2. 统一异常处理原则：
   - 业务可预期异常：转为明确业务异常
   - 基础设施异常：带 `taskId / sessionId / problemId / userId` 记录日志并继续向上抛
   - 最终禁止“既不记日志也不改变状态”的空 catch
3. 对已确认热点改成批量持久化：
   - 课件导入：改 `batchUpdate`
   - 题目导入：改 `batchUpdate`
   - 学习事件批量写入：改 `batchUpdate`
   - KC / 页映射：改“两阶段批量 upsert + 单次回查 id + 批量映射写入”
4. 把 SQL 热点从巨石 service 中抽到专门的 store / repository 类，避免每个业务编排器自己拼 SQL、自己控制循环、自己决定异常策略。
5. 对批处理链路接入 SQL 基线观测，至少统计关键流程的 SQL 次数和耗时，不能只凭体感判断是否优化成功。

**改造顺序**

1. 先清空核心链路里的 `ignored` catch
2. 再批量化课件导入、学习事件写入、KC 映射写入
3. 再抽离热点 SQL 到专门 repository / store
4. 最后补 SQL 基线和日志上下文字段

**验收命令 / 验收场景**

```bash
cd /home/cypress/Alethicode && rg -n "catch \\(Exception ignored\\)" backend/src/main/java
```

预期：核心业务链路不再出现空吞异常

```bash
cd /home/cypress/Alethicode && rg -n "batchUpdate\\(" backend/src/main/java/com/alethicode/service/impl backend/src/main/java/com/alethicode/service/languagepack/impl
```

预期：上述批量入口完成改造后，热点路径能看到明确的批量写入实现，而不是继续逐条 `update`

### 4.5 仓库治理与文档漂移债

**债务类型**

- 文档漂移
- 脚本环境耦合
- 仓库级治理入口分散

**现象与证据**

- [docs/todos/todo-improve-debt.md](./todo-improve-debt.md)
  - 仍记录旧的 `189` / `90` 基线
  - 当前已经漂移到 `194` / `95`
- [frontend/tests/test_frontend_smoke.js](frontend/tests/test_frontend_smoke.js)
  - 注释仍写 `cd frontend_new`
- [scripts/m12/verify_alethicode_readonly.sh](scripts/m12/verify_alethicode_readonly.sh)
  - 依赖固定路径：
    - `ROOT="/home/cypress/Alethicode"`
    - `SRC="/home/cypress/Alethicode"`
  - 还依赖仓库外基线文件
- [backend/src/main/java/com/alethicode/config/AlethicodeProperties.java](backend/src/main/java/com/alethicode/config/AlethicodeProperties.java)
  - 多个目录默认值直接写死 `/home/cypress/Alethicode/...`
- [deploy/README.md](deploy/README.md)
  - 启停和验证命令都直接写死绝对路径
- 忽略规则分散在：
  - [/.gitignore](.gitignore)
  - [backend/.gitignore](backend/.gitignore)
  - [frontend/.gitignore](frontend/.gitignore)
  - 根 `.gitignore` 只覆盖少量项，仓库级规则与子目录规则没有统一治理说明

**影响链路**

- 输入：新同学接手、换机器部署、写自动化校验、更新文档
- 处理流程：不同文档和脚本给出不同入口、不同路径前提
- 状态变化：文档可信度下降，脚本越来越依赖单台机器路径
- 输出：迁移环境、接手项目、补 CI 时成本增大
- 上下游影响：技术债报告本身都会过期，治理动作难以连续

**根因判断**

- 本地开发路径和一次性排障脚本直接沉淀成了仓库标准
- 缺少单一事实源来维护基线命令、审计数字和统一入口

**最短正确解决方案**

1. 把根目录 `todo_debt.md` 作为当前技术债事实基线；旧债务文档不得继续单独维护另一套数字。
2. 修正所有共享文档和脚本里的过期路径、旧目录名、单机绝对路径。
3. 共享脚本中的路径一律改为按脚本位置推导仓库根目录；运行时目录、数据目录和密钥路径一律改为环境变量注入，不再把 `/home/cypress/...` 写成仓库默认值。
4. 统一仓库级校验入口和说明文档，避免同一件事在 README、TODO、脚本头注释里各写一套。
5. 梳理 `.gitignore` 归属：
   - 仓库级产物规则由根 `.gitignore` 明确承担
   - 子目录只保留工具局部规则
   - 不再让忽略策略散落而无说明

**改造顺序**

1. 先修正文档与脚本漂移
2. 再参数化绝对路径
3. 再统一仓库校验入口
4. 最后收口 `.gitignore` 规则归属

**验收命令 / 验收场景**

```bash
cd /home/cypress/Alethicode && rg -n "frontend_new|/home/cypress/Alethicode|/home/cypress/Alethicode" docs scripts frontend/tests backend/src/main/java
```

预期：共享脚本与共享文档中不再把单机绝对路径当作默认标准；如必须保留，必须明确标注用途与环境前提。

## 5. 治理优先级顺序

1. **先修安全边界与敏感配置**
   - 这是当前唯一直接触及系统边界的 P0 债务。
2. **再恢复质量门**
   - 没有干净 lint 和统一校验入口，后续拆分与加固都没有可靠回归基线。
3. **再治理异常策略与批量写库热点**
   - 这是运行稳定性和性能成本的共同底板。
4. **再拆巨石服务与巨石页面**
   - 结构不拆，前 3 步的收益会被重新吞回去。
5. **最后收口仓库治理与文档漂移**
   - 这一步负责让前 4 步的基线长期不再失真。

## 6. 假设与未验证前提

- 本报告只基于当前仓库静态代码与本地命令结果，不包含生产流量、生产 SQL trace、真实压测结果。
- 旧债务文档中提到的“N+1 查询”方向，本次静态审计已能直接确认的是“循环内逐条 DB 调用和高频 SQL 往返模式”；读侧是否还存在额外 N+1，需要结合 SQL 观测脚本与运行态日志再做一次定量确认。
- 当前工作区存在大量未提交改动；本报告不把这些进行中改动本身视为技术债结论，只把仓库中已经可见的结构和基线作为审计对象。
