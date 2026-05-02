# frontend Vue3 纯化改造执行清单（YOLO）

## 目标
- 在不改 `frontend` 的前提下，持续改造 `frontend`，使其更贴近纯 Vue3 架构。
- 严格满足 AGENTS.md 前端命名规范。
- 提升加载速度与维护效率，补齐可复现验证链路。

## 执行总原则
- 只改 `frontend/**` 与项目根文档（`CHANGELOG.md`、本清单）。
- fail-fast：构建/测试/校验失败即修复，不做静默降级。
- 每一步都给出可执行验收命令。

---

## Step 1：命名规范收口（AGENTS 强制）
### 变更文件
- `frontend/src/pages/admin/App.vue`
- `frontend/src/pages/admin/views/Home.vue`
- `frontend/src/pages/admin/views/general/Login.vue`
- `frontend/src/pages/oj/App.vue`
- `frontend/src/pages/oj/views/general/Announcements.vue`
- `frontend/src/pages/oj/views/general/Home.vue`
- `frontend/src/pages/oj/views/general/NotFound.vue`
- `frontend/src/pages/admin/components/TopNav.vue`

### 改造内容
- 组件 `name` 与文件名语义一致（PascalCase）。
- 补齐缺失 `name` 的组件定义。

### 验收命令
- `cd frontend && node -e "const fs=require('fs'),path=require('path');let ok=true;function walk(d){for(const e of fs.readdirSync(d,{withFileTypes:true})){const p=path.join(d,e.name);if(e.isDirectory())walk(p);else if(p.endsWith('.vue')){const b=path.basename(p,'.vue');const t=fs.readFileSync(p,'utf8');const i=t.indexOf('export default');if(i>=0){const seg=t.slice(i,i+800);const m=seg.match(/name\s*:\s*['\"]([^'\"]+)['\"]/);if(m&&m[1]!==b){ok=false;console.log('mismatch',p,m[1],b);}}}}}walk('src');if(!ok)process.exit(1);console.log('component-name-check-pass');"`

---

## Step 2：入口与插件 Vue3 化（去显式 compat 依赖）
### 变更文件
- `frontend/src/pages/oj/index.js`
- `frontend/src/pages/admin/index.js`
- `frontend/vue.config.js`
- `frontend/package.json`
- `frontend/src/plugins/highlight.js`
- `frontend/src/plugins/katex.js`

### 改造内容
- 移除 `configureCompat` 调用与 `vue$ -> @vue/compat` 别名。
- 插件与指令钩子切换到 Vue3（`beforeMount/updated`）。
- 保持业务行为一致。

### 验收命令
- `cd frontend && rg -n "configureCompat|@vue/compat|compatConfig" src vue.config.js package.json`
- `cd frontend && npm run build`

---

## Step 3：性能首轮优化（最短路径）
### 变更文件
- `frontend/src/pages/oj/index.js`
- `frontend/static/js/vendor.dll.e29d9bd.js`（删除）

### 改造内容
- 图表组件改为按需异步加载，减少首包同步负担。
- 删除未被引用的历史 `vendor.dll` 静态产物，降低构建噪音与发布体积。

### 验收命令
- `cd frontend && npm run build`
- `cd frontend && rg -n "vendor\.dll" dist/index.html dist/admin/index.html || true`
- `cd frontend && test ! -f static/js/vendor.dll.e29d9bd.js`

---

## Step 4：视觉回归脚本可信度修复
### 变更文件
- `frontend/tests/e2e/visual-compare.js`

### 改造内容
- 避免 `fullPage` 导致页面高度不同引发 100% 误报。
- 固定视口截图策略，使旧新站可比较。

### 验收命令
- `cd frontend && node tests/e2e/visual-compare.js`（需 OLD/NEW 站点可访问）
- `cd frontend && cat tests/e2e/visual/report.json`

---

## Step 5：质量门禁与可维护性收口
### 变更文件
- `frontend/.eslintrc.js`（必要时）
- 相关触发 lint 错误的文件（按需）

### 改造内容
- 让 lint 与当前工程（浏览器环境）一致。
- 清理关键 lint 错误，保证基本代码质量可持续。

### 验收命令
- `cd frontend && npm run lint`
- `cd frontend && npm run test -- --runInBand`
- `cd frontend && node tests/test_frontend_smoke.js`

---

## Step 6：文档与变更记录
### 变更文件
- `CHANGELOG.md`
- `frontend/MIGRATION_REPORT.md`（按需更新）

### 改造内容
- 用中文记录本轮 Vue3 纯化、性能优化、验证结果与剩余风险。

### 验收命令
- `git diff -- CHANGELOG.md frontend/MIGRATION_REPORT.md`

---

## 完成判定
- `frontend` 构建通过。
- 单测与 smoke 通过。
- 组件命名规范检查通过。
- 明确给出仍待继续迁移的点（如果存在），并写入报告与 changelog。

---

## 执行结果（2026-03-28）
- [x] Step 1 已完成：组件命名收口完成，`TopNav` 补齐 `name`。
- [x] Step 2 已完成：移除 `@vue/compat` 与 `configureCompat`，入口/插件改为 Vue3 语义。
- [x] Step 3 已完成：删除历史 `vendor.dll`，并将图表组件改为项目内 Vue3 组件。
- [x] Step 4 已完成：视觉对比脚本改为固定视口截图，消除 fullPage 高度漂移误报主因。
- [x] Step 5 已完成：`eslint --fix` 并清理未使用代码后 lint 已达 0 error、0 warning。
- [x] Step 6 已完成：`MIGRATION_REPORT.md`、`CHANGELOG.md` 已同步记录。
- [x] 补充纯化：完成 `iview -> view-ui-plus`、`element-ui -> element-plus`、`vue-echarts@2` 移除，`npm ls vue` 仅保留 Vue3。
- [x] 补充纯化：模板层 Vue2 残留 `slot=\"...\"` 已批量迁移为 Vue3 `v-slot/#` 语法，`slot` 残留数量从 96 降为 0。
