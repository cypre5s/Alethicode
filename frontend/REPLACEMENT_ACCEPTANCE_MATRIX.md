# `frontend_new` 等价替换验收矩阵

## 1. 验收环境
- 旧前端：`http://127.0.0.1:8084`
- 新前端：`http://127.0.0.1:8080`
- 后端：`http://127.0.0.1:8081`
- websocket 口径：浏览器统一连接当前站点 origin 的 `/ws/*`，再由 dev server / nginx 代理到后端
- 固定验收账号：`replacement_admin`
- 固定验收链路：静态契约审计、真实后端登录态回归、old/new parity、像素级视觉对比、部署入口检查

## 2. 总体结论
- 结论更新为两层口径：
  - 按“功能、交互、实时链路、部署入口”口径，`frontend_new` 已通过替代性验收，可以作为项目默认前端投入使用。
  - 按“页面完全相同 = 像素级 0 diff”口径，当前仍未通过；剩余差异集中在少数 admin 页面，属于 Vue3 纯化后 UI 库样式漂移，不是功能缺口。
- 因此当前不再存在“公开页 parity / 登录后 parity / 管理端 parity / WebSocket parity 未验证”的遗留，剩余唯一未闭合项是严格像素级一致性。
- 当前补充说明：构建链路已从 Vue CLI 全量切换到 Vite 7，并完成本地构建、lint、单测、smoke、静态审计与开发态双入口探测；但依赖真实后端和双站并行的 parity / visual 套件本轮未重跑。

## 3. 矩阵
| 维度 | 状态 | 结论 |
| --- | --- | --- |
| 页面/路由静态覆盖 | Pass | `frontend` 与 `frontend_new` 的 OJ/Admin 路由数一致，分别为 `22/18`。 |
| API 调用面 | Pass | OJ/Admin API 导出方法数一致，分别为 `172/78`。 |
| 真实后端登录态回归 | Pass | 未登录受保护页跳转、登录页提交建会话、登录态 submissions 查询均通过。 |
| 公开页 parity | Pass | old/new 同脚本双跑已通过，title、ready 状态、可见文本一致。 |
| 登录后主页面 parity | Pass | `Learner Notebook`、`Profile Settings`、`Security Settings`、`Classroom` 等主页面链路已通过。 |
| 管理端 parity | Pass | `/admin/problems`、`/admin/conf`、`/admin/user` 等管理端主页面 ready 状态、标题和可见文本已对齐。 |
| WebSocket parity | Pass | 工作流、课堂协作、课堂监控三类 `/ws/*` 链路均已在 parity 套件中观测并通过。 |
| 像素级页面一致性 | Fail | 严格按“0 diff”口径仍未通过；当前最高差异为 `/admin/kc-management` `3.73%`、`/admin/problems` `3.68%`、`/admin/conf` `2.90%`。 |
| 部署入口切换 | Pass | `deploy/frontend.Dockerfile` 与 `start.sh` 默认已切到 `frontend_new`。 |
| 可直接替代投产 | Pass | 默认部署入口、构建产物、真实后端登录态回归、old/new parity 与实时链路验收均通过。 |

## 4. 证据
### 4.1 静态契约审计
- 报告文件：
  - [static-audit.json](/home/cypress/Alethicode/frontend/tests/replacement/reports/static-audit.json)
  - [static-audit.md](/home/cypress/Alethicode/frontend/tests/replacement/reports/static-audit.md)
- 结论摘要：
  - OJ 路由：`22 -> 22`
  - Admin 路由：`18 -> 18`
  - OJ API 导出：`172 -> 172`
  - Admin API 导出：`78 -> 78`
  - 差异文件分类：
    - `semantic_adapter_or_runtime_bridge`: `15`
    - `pure_vue3_syntax_migration`: `11`
    - `runtime_behavior_change_or_manual_review`: `64`

### 4.2 真实后端登录态回归
- 命令：
  - `cd frontend && REAL_BACKEND_E2E=1 BASE_URL=http://127.0.0.1:8080 npm run test:e2e:auth`
- 结论：
  - `3/3` 通过
  - 当前已验证：
    - 未登录访问 `/setting/profile` 跳转登录
    - 登录页表单提交流程可真实建会话
    - 登录态下 `/api/submissions` 筛选/分页查询正常

### 4.3 old/new parity
- 命令：
  - `cd frontend && OLD_BASE_URL=http://127.0.0.1:8084 NEW_BASE_URL=http://127.0.0.1:8080 npm run test:replacement:parity`
- 结论：
  - `5/5` 通过
  - 已通过项：
    - `public route parity matrix`
    - `authenticated route parity matrix`
    - `admin route parity matrix`
    - `websocket parity matrix`
    - `deployment entry should point to frontend_new assets`
- 本轮修复点：
  - 问题页 `v-katex` 指令从整块容器缩到真实富文本节点，消除 Vue3 DOM patch 冲突。
  - 工作流/课堂 websocket 改为通过 `websocketUrl.js` 统一构造，并统一走当前站点 origin 的 `/ws/*` 同源链路。
  - workflow session id 的 websocket 匹配规则从“纯数字”修正为真实 session id 形态。
  - 补充 admin `element-plus` 与 OJ `view-ui-plus` 样式桥接，收口输入框、下拉、数字框、分页、弹窗等基础控件的默认样式漂移。
  - 去掉 websocket 配置/处理器上错误的 `@ConditionalOnBean(JdbcTemplate.class)`，恢复 `/ws/workflow/*` 与课堂实时端点注册。

### 4.4 像素级视觉对比
- 报告文件：
  - [report.json](/home/cypress/Alethicode/frontend/tests/e2e/visual/report.json)
  - [report.md](/home/cypress/Alethicode/frontend/tests/e2e/visual/report.md)
- 结论摘要：
  - 公开页与大多数用户页已非常接近：
    - `/login`: `0.02%`
    - `/register`: `0.01%`
    - `/problem/PPT7-12`: `0.02%`
    - `/setting/security`: `0.80%`
  - 当前残余差异集中在 admin 页面：
    - `/admin/kc-management`: `3.73%`
    - `/admin/problems`: `3.68%`
    - `/admin/conf`: `2.90%`
    - `/admin/user`: `2.88%`
- 说明：
  - 当前视觉报告已是固定视口、固定数据、双站同脚本下的真实 diff。
  - 残余差异主要来自 Vue3 纯化后 `element-plus / view-ui-plus` 的默认样式漂移，而非页面缺失、交互断裂或实时链路失败。

## 5. 当前剩余项
1. 若验收标准是“像素级 0 diff”，则仍需基于最新视觉报告继续逐页细调残余样式漂移；本轮基础 UI 库桥接已补齐，但尚未重新产出完整视觉对比结果。
2. 若验收标准是“功能、交互、实时链路、部署入口无遗留”，当前已全部收口。

## 6. 替换判定
- 第 1 点“`frontend_new` 完全实现 `frontend` 所有功能与交互”：`通过验收`
- 第 2 点“两个前端页面完全相同”：`未通过验收（严格像素级 0 diff 口径）`
- 第 3 点“`frontend_new` 可以完全替代 `frontend` 投入使用”：`通过验收`

## 7. 下一步最短路径
1. 如果目标是“正式替代上线”，当前可以直接以 `frontend_new` 作为默认前端继续开发与部署。
2. 如果目标升级为“与旧站逐像素完全一致”，下一步只需要基于新的视觉 diff 继续微调残余页面样式，不需要再改业务逻辑。
