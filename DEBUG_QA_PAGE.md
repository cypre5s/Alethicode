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

## 10. 后续修复（2026-05-07 续）：PDF 预览渲染失败 + 课件问答不引用课件

> 同一天后续两个独立 bug 与本文件主题相关，记入此文方便下次定位类似问题。

### 10.1 现象

- 学生端 `language-pack-qa` 与 `/language-pack-qa/viewer` 的 PDF 预览全部红
  字「页面渲染失败」（不是「PDF 加载失败」，二者错误位置不同）。
- 同一会话里 AI 回答没有「已定位到课件页证据」卡片、没有任何课件页引用
  按钮，对外表现为「LLM 不引用课件原文」。

### 10.2 PDF.js modern build 用了 ES2025 API，老浏览器不支持

#### 根因

`pdfjs-dist@^5.6.205` 默认入口 `import * as pdfjsLib from 'pdfjs-dist'` 是
modern build。从 PDF.js 5.5.52 起 `src/display/api.js` 与 `PDFObjects` 直接
调用 ES2025 [TC39 proposal-upsert](https://github.com/tc39/proposal-upsert)
的 `Map.prototype.getOrInsertComputed` / `WeakMap.prototype.getOrInsertComputed`
（参 [mozilla/pdf.js#20680](https://github.com/mozilla/pdf.js/issues/20680)）：

| 浏览器  | 支持版本   |
| ------- | ---------- |
| Chrome  | 145+       |
| Firefox | 144+       |
| Safari  | 26.2+      |
| Edge    | 145+       |

生产环境 Cursor 内置 Electron 39（Chromium 142）等多数用户浏览器**不**支持
该 API，单页 `pdfPage.render()` 阶段抛
`TypeError: this[#ra].getOrInsertComputed is not a function`，
`PdfPageViewer.renderPage` 的 catch 写入「页面渲染失败」。

注意：错误是 `renderPage()` 抛的，**不是** `loadAndRender()`。要对应到
`PdfPageViewer.vue` 这两个 catch 的不同分支：

```js
async loadAndRender () { try { ... } catch (e) { this.error = 'PDF 加载失败' } }
async renderPage ()    { try { ... } catch (e) { this.error = '页面渲染失败' } }
```

#### 证据收集

`vite.config.mjs` 的 `terserOptions: { compress: { drop_console: true } }`
让生产 dist 完全 strip 掉 `console.error`，看不到 PDF.js 的真实异常。诊断
路径：

1. 浏览器原生 `<embed>` PDF viewer 直接打开 `/api/.../preview#page=51` →
   完整渲染第 51 页 → 证明 PDF 文件 / 后端 / cmaps 都没问题。
2. SSH 到 ECS dist：
   - 备份 `PdfPageViewer-CRVhEyEu.js` / `index-Bx3DsZ4T.js` / `index.html`。
   - 把 PdfPageViewer chunk 的 `catch(e){this.error="页面渲染失败"}` 改成
     `catch(e){this.error="页面渲染失败:"+e.message+"|"+e.name; document.title="ERR:"+e.message}`，
     并复制为 `PdfPageViewer-PATCH00x.js`。
   - 同步把 `PdfViewerPage`、`LanguagePackQaPage`、entry chunk 内对
     `PdfPageViewer-CRVhEyEu.js` 的 import 全部改名 → `PATCH00x`，每一层
     chunk 都要 rename，否则浏览器从 immutable HTTP cache 复用旧 chunk。
   - 重命名 entry chunk 自身 + 改 `index.html` 引用（`index.html` nginx
     `Cache-Control: no-cache, must-revalidate`，每次 revalidate）。
   - 临时把 nginx `expires 30d; Cache-Control "public, immutable"` 改成
     `expires off; Cache-Control "no-cache, no-store, must-revalidate"`，
     bypass 浏览器 HTTP cache。
   - 在 `/admin/_unsw.html`（PWA `navigateFallbackDenylist` 包含 `/admin/`，
     不被 SW 拦截）放一个 unregister + `caches.delete()` 的小工具，让浏览器
     脱离旧 SW 控制 + 清掉 workbox cache 后重新拉新 chunk。

`document.title` 是 cursor browser snapshot 能直读的字段（`localStorage`
要再开 DevTools 才能看），所以**优先**用 `document.title` 暴露错误，比
`localStorage` 节省一步。

最终拿到铁证：title `ERR:this[#ra].getOrInsertComputed is not a function | TypeError`。

#### 补丁

`frontend/src/components/PdfPageViewer.vue`：

```diff
-import * as pdfjsLib from 'pdfjs-dist'
-import workerUrl from 'pdfjs-dist/build/pdf.worker.mjs?url'
+import * as pdfjsLib from 'pdfjs-dist/legacy/build/pdf.mjs'
+import workerUrl from 'pdfjs-dist/legacy/build/pdf.worker.mjs?url'
```

legacy build 通过 core-js 把 `Map.prototype.getOrInsertComputed` /
`WeakMap.prototype.getOrInsertComputed` 等 ES2025 API 全部 polyfill，是
mozilla/pdf.js 官方对 broader compat 场景的推荐路径。`pdfjsAssetsPlugin`
从 `pdfjs-dist/package.json` 解析的根目录复制 `cmaps`、`standard_fonts`，
modern 与 legacy 共用同一份资源，**不需要**改 plugin。

部署：`docker compose build --no-cache frontend && up -d --force-recreate frontend`
即可，新镜像 dist 与 nginx config 全部重置，自动覆盖所有调试期 hot-patch。

#### 教训

- 升级 `pdfjs-dist` 主版本（4 → 5）必须把 `frontend/src/components/PdfPageViewer.vue`
  的 import 路径同步审一次：modern entry 的兼容性预期是「最近 1-2 个
  Chromium」，不能假设它兼容生产用户浏览器。
- 学生端任何 `<canvas>` 渲染、PDF.js / 字体相关的「无报错但白屏」
  问题，先用浏览器原生 `<embed>` / `<iframe>` 加载同一资源做交叉
  验证，能立刻区分「数据问题」与「JS 库兼容性」。
- 生产 dist 的 `drop_console: true` 会让 `console.error` 完全消失，
  调试期间用 `document.title = "ERR:..."` 暴露 catch 体内的 error
  比写 localStorage 更省一步（cursor browser snapshot 直接能看到 title）。
- 浏览器有 4 层缓存，每一层都要绕：（1）PWA workbox `runtimeCaching`
  的 `CacheFirst static-assets` →（2）workbox `navigateFallback` 缓存
  的 `index.html` →（3）浏览器 HTTP `Cache-Control: immutable` →
  （4）浏览器 module cache。Hot-patch 时要么把整个 module graph 全部
  改名（每一层 chunk 内的 import 路径都要改），要么走 `_unsw.html` 卸
  载 SW + 临时 nginx no-cache 的组合拳。

### 10.3 LightRAG 1.4.x chunk metadata 不带 `entity_id`，Java 跳过所有 chunk

#### 根因

`alethicode-rag` 升级到 LightRAG 1.4.x 后，`aquery_data()` 返回的 chunk
metadata 不再带 `entity_id` / `page_id`，只有
`file_path = "language_pack/{lpId}/p{pageNo}"`（由
`scripts/ops/rag_backfill.py` 写入 LightRAG 的 chunk source_path）。
`alethicode-rag/app/routes/query.py` 的 `_coerce_data` 已经在 chunk
metadata 上 `meta.update(_parse_courseware_path(fp))` 注入了
`language_pack_id` + `page_no`，**但 Java 端没读这两个键**：

```java
Long pageId = toLong(meta.get("entity_id"));
if (pageId == null) {
    pageId = toLong(meta.get("page_id"));
}
if (pageId == null) {
    continue;   // ← 1.4.x chunks 全部命中此分支被跳过
}
```

`RagQueryHits.chunks()` 非空但 `results` 为空 → `LanguagePackQaServiceImpl`
的「无引用」分支拼回答 → AI 输出无「已定位到课件页证据」卡片，前端引用
按钮列表也是空。

#### 证据

- `docker exec java-oj-alethicode-rag` `/v1/rag/query/courseware` 直 curl
  返回 7 条 chunk，每条 metadata 含 `language_pack_id`、`page_no`、
  `file_path`，**不含** `entity_id` / `page_id`。
- backend 日志没有任何 SQL 错误（说明根本没走到 `loadPageRow`）。
- `docker logs java-oj-backend` 只看到 RAG 请求成功，但回答里没引用。

#### 补丁

`backend/.../PageRetrievalServiceImpl.java` 抽出 `resolvePageId()`：

1. 优先按 `entity_id` / `page_id` 反查（兼容 LightRAG 1.3.x 与历史回填的 chunk）。
2. fallback 按 `(language_pack_id, page_no)` 查 `language_pack_page`：
   `SELECT id ... WHERE lp_id=? AND page_no=? ORDER BY chunk_index ASC, id ASC LIMIT 1`。
3. 兜底用 `^language_pack/(\d+)/p(\d+)$` 解析原始 `file_path` 拿 (lpId, pageNo)。
4. **必须**校验 `lpFromMeta == languagePackId`，避免跨课件包污染。

`alethicode-rag/app/routes/query.py` 此前已经在 `_coerce_data` 注入了
`language_pack_id` / `page_no`，本次修复只动 Java 一侧。

#### 教训

- LightRAG 升级是 `alethicode-rag` 与 Java backend 之间的契约变更点。
  接入点是 `RagQueryHits.chunks().get(i).metadata()` 这个 `Map<String, Object>`，
  改 schema 时**两侧必须同步**：alethicode-rag `_coerce_data` 写哪些键、
  Java `PageRetrievalServiceImpl#resolvePageId` 读哪些键，两边的契约要在
  `contracts/` 或 README 留一份。
- 任何「LLM 拿不到课件原文」的现象，先去 alethicode-rag `/v1/rag/query/courseware`
  直 curl 看 chunks 是否非空，再去 backend 看 `LanguagePackQaServiceImpl`
  日志看 hits → results 转换是否丢失，**别一上来就改 prompt 或 LLM 模型**。
- ECS 调试期间往 `PageRetrievalServiceImpl.java` 加 `__qa_debug__` log /
  `__qa_debug__` JSON 这类临时日志非常有用，但**重建镜像前**必须 cleanup，
  否则一旦 commit 进 git，noisy log 会污染生产日志。本次清理时同时删
  了 ECS 上的 `*.bak` / `*.bak.*` 备份，避免 next deploy 把临时文件打进
  镜像。

