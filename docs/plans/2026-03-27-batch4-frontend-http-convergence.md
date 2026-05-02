# 批次4：前端 HTTP 横向收敛闭环记录

## 1. 模块分析（rules: Phase 1）
- 输入：`frontend/src/pages/oj/api.js` 与 `frontend/src/pages/admin/api.js`。
- 处理流程：两处分别初始化 axios、重复注入 CSRF。
- 状态变化：仅收敛 HTTP 客户端初始化，业务 API 调用不改。
- 输出：请求路径、方法、payload、响应处理保持原行为。
- 上下游影响：
  - 上游页面调用 `@oj/api`、`@admin/api` 保持不变。
  - 下游后端接口无变化。

## 2. 映射规划（rules: Phase 2）
| 旧结构 | 新结构 |
|---|---|
| `oj/api.js` 内部 axios 初始化 | `frontend/src/api/httpClient.js` 统一初始化 + `getHttpClient()` |
| `admin/api.js` 内部 axios 初始化 | 复用 `getHttpClient()` |
| API 逻辑散落单文件 | 新增 `frontend/src/api/modules/{auth,problem,classroom,ai,submission,admin}.js` 域模块 |

## 3. 最短路径实现（rules: Phase 3）
- 新增统一 HTTP 客户端 `httpClient.js`（baseURL、CSRF header/cookie、请求拦截器一次性初始化）。
- `oj/api.js`、`admin/api.js` 改为依赖统一客户端。
- 新增域 API 模块作为收敛入口，不改既有 API 暴露契约。

## 4. 测试验证（rules: Phase 4）
- `cd frontend && npm run test -- --runInBand` -> 通过（含 `tests/unit/api.spec.js`）。
- `cd frontend && node tests/test_frontend_smoke.js` -> 通过（API 模块检查通过）。

## 5. code-reviewer 审查（rules: Phase 5）
- Security：CSRF 注入仍统一通过 `csrftoken` -> `X-CSRFToken`，未弱化。
- Performance：拦截器仅初始化一次，避免重复注册。
- Correctness：API 路径前缀仍为 `/api`，单测已覆盖。
- Maintainability：HTTP 与业务 API 分层，后续扩展成本下降。
- 阻断项：0。

## 6. 结论
- 批次4完成闭环，前端 HTTP 初始化收口完成且调用协议零变更。
