# 前端依赖健康报告

> 生成时间：2026-04-22
> 扫描目标：`frontend/package.json`
> 工具：`npm outdated` + `npm audit`

## TL;DR（给初学者的一句话）

仓库里有 **74 个已知安全漏洞**（13 个严重、26 个高危），主要来自 `axios` / `echarts` / `jest` / `d3` 等"年纪很大"的包。这些包多数存在 2-5 个 major 版本的代差，**一次性升级会大概率导致业务代码跑不通**，需要按下文清单**分批小步升级**。

本次优化阶段 5 **没有**自动化升级这些包，因为：

1. `npm audit fix`（自动修补）在当前依赖树会失败：`vite@7.x` 需要 `less@^4.0.0` 作为可选 peer，但项目里仍 pin `less@^3.8.1`，npm 会直接拒绝
2. 即使强制（`--force`）升级，会同时把 `echarts` 拉到 6.x、`axios` 拉到 1.x 等 major 版本，破坏大量调用点
3. 初学者友好的路径是：看懂下表，按"风险递增"的顺序**每次升级 1 个包**，每次都跑一遍冒烟测试

## 一、建议立刻升级（patch/minor，不会破坏代码）

这些版本的升级属于 semver 语义下的"向后兼容"，package.json 的 `^` 已经允许，但 `package-lock.json` 没跟上。

| 包 | 当前 | 目标 | 动作 |
| --- | --- | --- | --- |
| `@vitejs/plugin-vue` | 6.0.5 | 6.0.6 | `npm install @vitejs/plugin-vue@6.0.6` |
| `@vue/compiler-sfc` | 3.5.31 | 3.5.32 | `npm install @vue/compiler-sfc@3.5.32` |
| `dompurify` | 3.3.1 | 3.4.1 | `npm install dompurify@3.4.1` |
| `element-plus` | 2.13.6 | 2.13.7 | `npm install element-plus@2.13.7` |
| `eslint` | 10.2.0 | 10.2.1 | `npm install --save-dev eslint@10.2.1` |
| `eslint-plugin-vue` | 10.8.0 | 10.9.0 | `npm install --save-dev eslint-plugin-vue@10.9.0` |
| `globals` | 17.4.0 | 17.5.0 | `npm install --save-dev globals@17.5.0` |
| `vite` | 7.3.1 | 7.3.2 | `npm install --save-dev vite@7.3.2` |
| `vue` | 3.5.31 | 3.5.32 | `npm install vue@3.5.32` |

> 建议：这一批可以合并为一个 commit。升级后跑 `npm run lint && npm run test && npm run build`，全部通过就合入。

## 二、中风险：major 升级但生态成熟（有迁移指南）

每一个都要单独评估、独立 PR、跑完整回归测试。

### 2.1 `axios` 0.18.1 → 1.x

**漏洞**：CVE 范围包含 SSRF（CVSS 5.9）、CSRF（CVSS 6.5）、ReDoS（CVSS 7.5，**高危**）等。

**主要 breaking changes**：
- 默认导出形态变更：`import axios from 'axios'` 仍可用，但部分命名导出路径变
- 错误对象结构调整：`error.config` 的某些字段语义变
- `axios.create()` 的 `paramsSerializer` 改为对象形式

**迁移建议**：先升到 `0.28.x` 作为过渡（修复所有 CVE 但 API 兼容更好），稳定后再考虑 1.x。

### 2.2 `echarts` 3.8.5 → 5.x / 6.x

**漏洞**：`zrender` 原型污染（通过 echarts 传递）。

**主要 breaking changes**：
- 3.x → 4.x 引入全新的注册机制（`echarts.use(...)`，tree-shakable）
- 组件按需引入：以前 `import echarts from 'echarts'` 拿到全量，现在推荐按需引入
- 某些图表配置键名重命名

**迁移建议**：先尝试升到 `4.9.0`（与 3.x 配置更接近），验证所有图表渲染正常，再看是否升到 5.x。迁移影响面：所有使用 `echarts` 的统计/可视化页面。

### 2.3 `d3` 5.16.0 → 7.x

**主要 breaking changes**：
- 全面 ESM 化；`require('d3')` 默认不再可用，只能 `import * as d3`
- `d3.event` 被移除，事件处理改为 `d3.pointer(event)`
- 选择器的一些链式 API 改动

**迁移建议**：先搜索工程里 `d3.event` 和 CommonJS 调用点，改完再升。

### 2.4 `jest` 23.6.0 → 29.x（跨 6 个大版本！）

**漏洞**：`yargs-parser` 等传递性依赖历史 CVE。

**主要 breaking changes**：
- Jest 27 起默认使用 ESM
- Jest 28 起默认 testEnvironment 是 `node`（原默认是 `jsdom`）
- 配置文件格式、`jest.config.js` 的导出写法变

**迁移建议**：这是改动最大的一项，建议**单独一个专项**来做。当前项目里 86 个 spec 里已有不少失败（jest 23 无法处理 ESM import），升级到 29 应该会**修复**这些失败。

### 2.5 `katex` 0.10.2 → 0.16.x

- breaking 主要在 LaTeX 宏支持的细节
- 渲染 API 基本保持兼容

### 2.6 `less` 3.13.1 → 4.x

- 主要 breaking 是 JavaScript API（`.render()` 等）的签名调整
- 但通过 Vite 的 `css.preprocessorOptions.less` 使用时基本无感

### 2.7 `autoprefixer` 7.2.6 → 10.x

- 需要 PostCSS 8+
- 配置格式变化

## 三、高风险：需要评估替代方案的包

### 3.1 `raven-js`（已废弃，需替换）

`raven-js` 是 Sentry 的 **老 SDK**，官方早已停止更新，推荐使用 `@sentry/browser`。

**动作**：
1. 先搜索代码里 `raven-js` 的使用点，评估替换成本
2. 替换为 `@sentry/browser` 或 `@sentry/vue`

### 3.2 `vue-analytics`（不兼容 Vue 3）

该包主要面向 Vue 2，Vue 3 生态通常改用 `vue-gtag` 或官方 GA4 脚本。

**动作**：确认项目是否真的需要前端 analytics，如不需要可直接移除。

### 3.3 `vue-clipboard2`（Vue 2 时代）

Vue 3 里可直接用原生 `navigator.clipboard.writeText()`，几乎不需要库。

### 3.4 `moment` 2.22.1（已进入"legacy 维护"阶段）

moment 官方声明已进入"legacy 项目"状态，推荐替换为 `day.js`（API 近乎一致，体积是 moment 的 1/10）或 `date-fns`。

### 3.5 `glob` 7.2.3（已废弃）

`glob` 7.x 被作者标记废弃，最新是 10.x（API 差异较大）。如果仅在构建脚本里使用，可考虑换成 `tinyglobby` 或 `fast-glob`。

### 3.6 `papaparse` 4.6.3 → 5.x

5.x 主要 API 兼容，但 TypeScript 类型、Node.js 相关 API 有轻微变化。

### 3.7 `screenfull` 3.3.3 → 6.x

跨越了 3 个 major 版本。6.x 是全 ESM、仅支持现代浏览器。需要评估是否还需要全屏功能。

## 四、推荐升级顺序（从低风险到高风险）

1. **第 1 批**：一节提到的 9 个 patch/minor → 零风险
2. **第 2 批**：`dompurify`（一节已含），`element-plus`（一节已含）
3. **第 3 批**：`axios` 0.18 → 0.28（只修 CVE，不跨 major）
4. **第 4 批**：`katex` 0.10 → 0.16（渲染层改动小）
5. **第 5 批**：`papaparse` 4 → 5
6. **第 6 批**：`less` 3 → 4，同时把 `autoprefixer` 7 → 10 一起升（它们相关）
7. **第 7 批（独立大项目）**：`jest` 23 → 29 + `babel-jest` 跟着升
8. **第 8 批（独立大项目）**：`echarts` 3 → 5 / 6
9. **第 9 批（独立大项目）**：`d3` 5 → 7
10. **清理**：替换 `raven-js` / `moment` / `vue-clipboard2` / `vue-analytics`

每一批升级后必做：
- `npm run lint`
- `npm run test`
- `npm run build`
- 手动跑一次 `./start.sh` 进主要页面冒烟验证

## 五、如果只做一件事，做哪个

**做第 1 批的 9 个 patch/minor 升级**：零风险、零代码修改、只更新 `package-lock.json`；能立刻清掉几个次要的 advisory。命令：

```bash
cd frontend
npm install \
  @vitejs/plugin-vue@6.0.6 \
  @vue/compiler-sfc@3.5.32 \
  dompurify@3.4.1 \
  element-plus@2.13.7 \
  eslint@10.2.1 \
  eslint-plugin-vue@10.9.0 \
  globals@17.5.0 \
  vite@7.3.2 \
  vue@3.5.32
```

> 注意：由于 `vite@7.3.2` 的 peer dep 开始要求 `less@^4.0.0`，这一命令**可能会报**
> `ERESOLVE` 冲突。此时保持 `vite@7.3.1` 不动（相差一个 patch 不会影响开发），
> 只升级其他 8 个包即可：从命令中删掉 `vite@7.3.2` 那一行。
