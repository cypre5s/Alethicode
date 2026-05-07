# 课件问答页 `language-pack-qa` 新会话 Bug 调试记录

> 日期：2026-05-07
> 现象：`http://47.111.165.48/language-pack-qa?ctx=eyJwIjoiNDMifQ==`
> 1. 「发送问题」按钮始终 disabled，无法点击。
> 2. 输入 `@` 后无法 `@kc` 与 `@page`，只有 `@notebook` 可用。

## 0. 现象初判

- 浏览器自动登录为 `R root`，pack 选择框显示 "Python语言基础 (id=43)"，但
  会话历史侧栏显示 "当前课程内容包还没有问答记录"。
- 输入框残留 `@`（来自 localStorage draft）。
- 发送按钮 disabled，对应 `canSend === false`，唯一未满足条件是
  `activeSessionId` 为空。

## 1. 后端验证

直接 fetch 到 `/api/language-packs/visible`、`/api/language-pack-qa/packs`、
`/api/language-pack-qa/sessions?language_pack_id=43`、`/api/profile`、
`/api/language-pack/43/kc-graph` 全部返回 200。

POST `/api/language-pack-qa/sessions` 在不带 `X-CSRFToken` 时 401，带上后
正常返回 `{id:1,...}`。所以**后端 API 完全正常**。

## 2. 前端调用追踪（fetch hook）

通过往 `nginx` 静态目录注入 `index.html` 中的 inline `<script>` 全局拦
截 `fetch`/`XMLHttpRequest`，把每次请求记录到 `localStorage.__diag_log`。

页面 mount 后实际触发的 API：

```
GET /api/language-packs/visible      200
GET /api/language-pack-qa/packs      200
GET /api/profile                     200 (×3)
GET /api/ai/tutor/notebook           200 (×2)
…常规 dashboard 调用…
```

**没有 `GET /api/language-pack-qa/sessions`，没有 `GET /api/language-pack/43/kc-graph`，
没有 `GET /api/language-packs/43/documents`。**

`buildQaNotebookMentionItems` 不依赖 `selectedLanguagePackId`，所以仍能加
载，对应 `/api/ai/tutor/notebook`；其他三个都依赖
`this.selectedLanguagePackId`，全部静默失败 → 与现象 2 相符。

## 3. 函数级探针（直接 patch dist 的 minified bundle）

为了在生产构建（`console.warn` 已被 strip）下追踪流程，使用 Python 在
`LanguagePackQaPage-CC69ablD.js` 的关键函数入口注入：

```js
window.__lpDiag.push({fn:"...", pid:String(this.selectedLanguagePackId), t:Date.now()});
localStorage.setItem("__lpDiag", JSON.stringify(window.__lpDiag));
```

由于 vite dist 文件名带 hash + nginx 设置 `Cache-Control: immutable`，
浏览器永远不会再次拉取同名文件，需要：

1. 把 patched 后的文件复制为新文件名 `LanguagePackQaPage-XXxxxx.js`。
2. 修改 entry chunk `index-3CvAMJln.js` 中所有引用为新名字，并重命名
   entry chunk 自身。
3. 改 `index.html` 中 `<script type="module" src="...">` 的 entry chunk
   引用为新名字（index.html 是 `no-cache`，每次 revalidate）。

## 4. 探针运行结果

```json
[
  {"fn":"loadPacks_enter",        "pid":"undefined", "t":1778152846267},
  {"fn":"about_to_switchPack",    "arg":"43", "packs":1, "t":1778152846907},
  {"fn":"switchPack_enter",       "pid":"undefined", "arg":"43", "t":1778152846907},
  {"fn":"after_set_pid",          "pid":"undefined", "t":1778152846907},
  {"fn":"before_router_replace",  "pid":"undefined", "t":1778152846907},
  {"fn":"after_router_replace",   "pid":"undefined", "expected":"43", "t":1778152846911},
  {"fn":"after_recheck",          "pid":"undefined", "t":1778152846911},
  {"fn":"about_to_loadSessions",  "pid":"undefined", "sObj":{"id":43,"qa_ready":true}, "t":1778152846911},
  {"fn":"loadSessions_enter",     "pid":"undefined", "t":1778152846911}
]
```

铁证（关键时间点用 `arg`/`pid` 差异说明）：

- `arg="43"` 进入 `switchPack` 是对的；
- `this.selectedLanguagePackId = t` **执行后**，`this.selectedLanguagePackId`
  仍然是 `undefined`；
- `this.selectedLanguagePackId !== t && (this.selectedLanguagePackId = t)`
  校正后**仍然是** `undefined`；
- `resolvePackById` 找到了正确的 pack（`{id:43,qa_ready:true}`），所以走
  了 `await this.loadSessions()` 分支；
- 但 `loadSessions` 入口 `this.selectedLanguagePackId === undefined`，触
  发 `if (!this.selectedLanguagePackId) return` 提前返回（`return` 后
  无 fetch /sessions 与现象 1 完全吻合）。

## 5. 在 probe 中 dump `this.$data` 后定位根因

把 probe 扩展为同时记录 `pid`、`dataPid`、`thisType`、`hasData`、
`Object.keys(this).length`，结果：

```json
[
  {"fn":"loadPacks_enter",   "pid":"undefined","hasData":true,"dataPid":null, "keysCount":75},
  {"fn":"switchPack_enter",  "pid":"undefined","hasData":true,"dataPid":null, "keysCount":76,"arg":"43"},
  {"fn":"after_set_pid",     "pid":"undefined","hasData":true,"dataPid":"43","keysCount":77,"t_arg":"43"},
  {"fn":"loadSessions_enter","pid":"undefined","hasData":true,"dataPid":"43","keysCount":77}
]
```

**铁证**：`this.$data.selectedLanguagePackId === "43"`（赋值生效），但
`this.selectedLanguagePackId === undefined`。即「Vue 实例的 `$data` 被
更新了，但 publicProxy 的属性访问读不到」。

H1/H2/H4 都被否决（this 是 Vue instance、publicProxy 写入路径走 data
正常、Options API 的 data 也确实 hold 住数据）。

最终落到 Vue 3 内部行为：

> 在 `setup()` 阶段，`useChatComposer` 内 `let activeScope = currentScope.value`
> 同步求值传入的 `scopeKey` computed，触发对 `proxy.selectedLanguagePackId`
> 与 `proxy.activeSessionId` 的访问；此时 Options API `data()` 还未运行
> （Vue 3 lifecycle: `setup` → `beforeCreate` → 初始化 props/data/computed →
> `created`），publicProxy `get` 在所有候选源（setupState / data / props /
> ctx）都未命中后，会把 `accessCache[selectedLanguagePackId]` 标记为非
> DATA 来源（`AccessTypes.OTHER`/`CONTEXT`）。
>
> 之后 `data()` 初始化，`this.$data.selectedLanguagePackId` 真实存在；但
> publicProxy `get` 走 fast path：`accessCache[key]` 已有值，直接读取错误来
> 源（不是 data），**永远返回 `undefined`**。
>
> Vue 的 `set` trap 不依赖 `accessCache`，只看 `hasOwn(data, key)`，所以
> `this.selectedLanguagePackId = t` 仍能写入 `$data`，造成「写入成功 但读
> 不到」的诡异现象。

## 6. 修复

`frontend/src/pages/oj/views/languagepack/LanguagePackQaPage.vue` 中
`scopeKey` 的访问路径改为通过 `proxy.$data` 的 shallowReadonly 视图：

```js
const scopeKey = computed(() => {
  const data = proxy && proxy.$data
  const packId = data && data.selectedLanguagePackId ? data.selectedLanguagePackId : 'none'
  const sessionId = data && data.activeSessionId ? data.activeSessionId : 'new'
  return `qa:${packId}:${sessionId}`
})
```

要点：

- `proxy.$data` 在 publicProxy `get` 中走 `key === '$data'` 特殊分支，**不会
  走 accessCache**，因此 setup 阶段访问 `data.selectedLanguagePackId` 不会
  污染 `accessCache[selectedLanguagePackId]`。
- `$data` 返回 `shallowReadonly(reactive(...))`，访问其属性仍会被 reactivity
  追踪，所以 `switchPack` 改 `this.selectedLanguagePackId` 后 computed 会
  重新求值，scopeKey 正确变化。
- `isInputBlocked` 是 lazy computed，setup 阶段不会被求值（`useChatComposer`
  只在 `onKeydown`/`submit` 时 `unwrap`），无需改动。

## 7. 修复后的 probe 证据（同一份探针，不带源码改动重新观察）

```json
[
  {"fn":"loadPacks_enter",     "pid":"null","dataPid":null, "t":...},
  {"fn":"about_to_switchPack", "arg":"43",  "t":...},
  {"fn":"switchPack_enter",    "pid":"null","dataPid":null, "arg":"43", "t":...},
  {"fn":"loadSessions_enter",  "pid":"43",  "dataPid":"43", "t":...},
  {"fn":"buildPages_enter",    "pid":"43",  "dataPid":"43", "t":...},
  {"fn":"buildKcs_enter",      "pid":"43",  "dataPid":"43", "t":...}
]
```

`pid` 不再是 `"undefined"`：
- `loadSessions_enter` 时拿到 `selectedLanguagePackId="43"` → `GET /api/language-pack-qa/sessions?language_pack_id=43` 被发出 → 自动激活已有会话；
- `buildPages_enter` / `buildKcs_enter` 同样能正确读到 pid，`@page` / `@kc`
  候选可以加载。

UI 行为：
- 会话历史侧栏出现已激活的会话项；
- 「发送问题」按钮变为可点击（`canSend` 中 `activeSessionId` 已设置）；
- 输入 `@` 弹出菜单，三组（课件页码 / 知识点 / 学习笔记）都能正常列出。

## 8. 临时调试痕迹与生产部署

为了在 ECS 生产构建环境下验证（`console.warn` 已被 strip、入口 chunk 名带
hash 且 `Cache-Control: immutable`），调试期间对 ECS 容器做了以下临时改动，
**并不进入仓库**：

- `index.html` 注入 `<script>` 全局 hook `fetch`/`XMLHttpRequest`，写入
  `localStorage.__diag_log`。
- `LanguagePackQaPage-CC69ablD.js` → 复制为新名 + `python3 lp_fix.py` 注入 probe，
  并把 `a.selectedLanguagePackId`/`a.activeSessionId` 替换成 `a.$data.*`。
- `index-3CvAMJln.js` 的 lazy import 引用同步改名，并把入口 chunk 自身改名后
  `index.html` 内 `<script type="module" src="...">` 引用改名（绕过浏览器 ESM
  import cache）。

下一次 `docker compose build frontend` 重新构建时，这些 hash 文件名会被新
build 覆盖，源码层的修复（`LanguagePackQaPage.vue` 的 `setup()`）会自然
生效；ECS 上的临时 hot-patch 是过渡方案，等正式镜像 build & 部署后即可清理。

## 9. 经验

- 生产 build 把 `console.warn` strip 掉时，runtime 探针推荐用 `localStorage`
  而非 `console`，并通过 `<script>` 在 `index.html` 顶部安装。
- 浏览器 ESM 模块的 `Cache-Control: immutable` 让浏览器永远不会再次拉取
  同名文件，patch dist 时必须新文件名 + 改入口引用。
- Vue 3 `setup()` 阶段访问 `proxy` 上的 Options API data 字段会污染
  publicProxy accessCache，导致 mounted 之后所有 `this.X` 读取永远返回
  undefined；该陷阱仅出现在 setup + data 混用的组件中。

