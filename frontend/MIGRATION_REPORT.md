# frontend -> frontend_new Vue3 纯化迁移报告

## 1. 迁移摘要
- 是否已创建 `frontend_new`：是。
- 是否确认 `frontend` 未被修改：是（本轮修改 `frontend_new`、`backend` websocket 注册与文档，未改旧 `frontend`）。
- 是否已完成 Vue3 纯化：是（移除 `@vue/compat`、`iview`、`element-ui`、`vue-echarts@2`）。
- 当前架构：`vue@3 + vue-router@4 + vuex@4 + vue-i18n@9(legacy:false) + view-ui-plus + element-plus + vite@7`。

## 2. 本轮关键改造
1. 组件命名规范收口（AGENTS）
- 对齐组件 `name` 与文件语义。
- 补齐缺失组件名（如 `TopNav`）。

2. 入口与运行时纯化
- 移除 `configureCompat` 调用与 `vue$ -> @vue/compat` 别名。
- 指令钩子从 `bind/componentUpdated` 迁移到 Vue3 `beforeMount/updated`。
- `router-view + transition` 改为 Vue3 标准 `v-slot` 写法。
- 模板层批量迁移 `slot="..."` 到 Vue3 `#slotName` 语法，彻底清理 Vue2 旧插槽写法。

3. Vue2-only 依赖替换
- `iview` -> `view-ui-plus`
- `element-ui` -> `element-plus`
- `vue-echarts@2` -> 项目内 Vue3 图表组件（`src/pages/oj/components/ECharts.vue`）
- `i18n` UI 语言包同步改为 `view-ui-plus` + `element-plus`。

4. UI 样式桥接与实时链路修复
- admin 侧补充 `element-plus` 样式桥接，收口输入框、输入组、禁用态、分页、弹窗等基础控件的默认视觉漂移。
- OJ 侧新增 `view-ui-plus` 样式桥接，修复文本框、下拉框、数字框等常见表单控件的颜色填充错误。
- 移除 websocket 配置与处理器上错误的 `@ConditionalOnBean(JdbcTemplate.class)`，恢复工作流与课堂 `/ws/*` 端点注册。

5. 性能与维护优化
- 删除历史遗留 `static/js/vendor.dll.e29d9bd.js`。
- 视觉对比脚本由 `fullPage` 改为固定视口截图，降低误报。
- 执行 `eslint --fix` 并清理未使用变量/导入；lint 收口到 0 error、0 warning。

6. Vue CLI -> Vite 7 工具链迁移
- 删除 `vue.config.js`、`build/`、`config/` 与 Vue CLI 依赖，仓库内只保留一条 Vite 7 构建链路。
- 根入口重建为 `index.html` + `admin/index.html` 双入口，多页产物仍输出到 `dist`，静态资源仍输出到 `dist/static`。
- 开发态通过自定义 HTML fallback 保持 `/` 与 `/admin/*` 双基座 history 路由可用。
- 新增 `runtimeEnv`、`runtimeErrorFilter`、`echarts` helper，清理浏览器侧 `process.env`、动态 `require`、`module.exports` 与 `webpackChunkName` 残留。
- 本地/脚本/容器 Node 基线统一提升到 `20.19.0`。

## 3. 验证结果
- 构建：`cd frontend && /tmp/node20bin/node_modules/node-linux-x64/bin/node ./node_modules/vite/bin/vite.js build --config vite.config.mjs` 通过。
- Lint：`cd frontend && npm run lint` 通过（0 error，0 warning）。
- 单测：`cd frontend && npm run test -- --runInBand` 通过（23/23 suite，47/47 case）。
- Smoke：`cd frontend && node tests/test_frontend_smoke.js` 通过（16/16）。
- Vite 契约：`tests/unit/dev-server-hmr-config.spec.js`、`tests/unit/dev-server-overlay-runtime-errors.spec.js`、`tests/unit/runtime-env.spec.js`、`tests/unit/i18n-loader-contract.spec.js`、`tests/unit/runtime-env-usage-contract.spec.js`、`tests/unit/router-lazy-chunk-contract.spec.js` 通过。
- 样式桥接契约：`cd frontend && npm test -- --runInBand tests/unit/admin-style-bridge-contract.spec.js tests/unit/oj-style-bridge-contract.spec.js` 通过。
- 开发态入口探测：使用 Node 20 本地二进制启动 Vite dev server 后，`Accept: text/html` 条件下 `/` 命中 OJ 入口，`/admin/` 与 `/admin/user` 命中 Admin 入口。
- websocket 注册回归：`cd backend && mvn -q -Dtest=WebSocketRegistrationSourceContractTest,WorkflowWebSocketHandlerTest,WorkflowRealtimeSupportTest test` 通过。
- 真实工作流 websocket：使用真实登录态创建 workflow session 后，浏览器通过当前站点 origin 的 `/ws/workflow/<sessionId>` 建立握手，并由 dev server / nginx 同源代理转发到后端。
- 依赖纯化校验：`npm ls iview element-ui vue-echarts @vue/compat --depth=0` 为空；`npm ls vue --depth=3` 仅 `vue@3.5.31`。
- 模板纯化校验：`rg -n "\\sslot=\\"" src --glob "*.vue"` 结果为 `0`。
- Vue2 API 残留校验：`$listeners/$scopedSlots/.native/beforeDestroy/destroyed/.sync/slot-scope` 关键字扫描结果为 `0`。

## 4. 当前风险与后续建议
1. 包体积仍偏大
- `chunk-vendors` 仍较大（>3MiB），需要下一轮按页面进一步拆分与依赖瘦身。

2. 仍有 Vue2 风格命名残留
- 例如样式文件名 `iview-custom.less` 仅作为历史命名保留，不再依赖 `iview` 运行时。

3. 视觉对比需联通双站复验
- 视觉脚本已修复策略，但要得到有效差异报告，仍需同时拉起 old/new 环境并执行对比。
- Vite 7 切换后，本轮尚未重跑依赖真实后端与双站并行环境的 parity / visual 套件。

## 5. 结论
- `frontend_new` 已从“Vue3 + compat 过渡态”升级到“纯 Vue3 运行态”。
- 在不改动 `frontend` 的前提下，已完成核心依赖纯化、Vite 7 工具链切换、命名规范收口与质量门禁收口。

## 6. 等价替换验收结论（2026-03-28）
- 本轮新增了“静态契约审计 + 真实后端登录态回归 + old/new parity + 像素级视觉对比 + 部署入口检查”五段验收链路。
- 当前已通过的替换项：
  - `frontend_new` 默认部署入口已经切换成功。
  - 真实后端登录态回归已通过，说明 Vue3 站点基础可用。
  - 静态层面路由数与 API 导出数与旧站一致。
  - old/new parity 已全部通过：公开页、登录后主页面、管理端、WebSocket、部署入口检查均为绿灯。
- 当前唯一未通过的严格项：
  - 像素级视觉对比仍未达到“0 diff”；残余差异集中在少数 admin 页面，最高约 `3.73%`。
- 本轮已额外补齐 admin/OJ 基础控件样式桥接，并修复后端 websocket 注册缺失；下一轮若继续追求 0 diff，应直接基于新视觉报告做逐页微调。
- 当前视觉差异最高页面：
  - `/admin/kc-management`
  - `/admin/problems`
  - `/admin/conf`
  - `/admin/user`
- 结论更新为：
  - `frontend_new` 已是“纯 Vue3 基线”。
  - 按功能、交互、实时链路和部署口径，`frontend_new` 已可替代 `frontend` 投入使用。
  - 按严格像素级“页面完全相同”口径，当前仍有少量 admin 样式漂移，不宜表述为 0 差异。
  - 替换验收详细状态见 [REPLACEMENT_ACCEPTANCE_MATRIX.md](/home/cypress/Alethicode/frontend/REPLACEMENT_ACCEPTANCE_MATRIX.md)。
