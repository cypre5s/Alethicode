# M0 基线扫描与只读防护报告

## 1. 源项目只读防护
- 源项目路径：`/home/cypress/Alethicode`
- 基线快照：`/home/cypress/Alethicode/.alethicode_status_baseline.txt`
- 校验脚本：`/home/cypress/Alethicode/scripts/m12/verify_alethicode_readonly.sh`
- 规则：迁移期间所有写操作仅允许在 `/home/cypress/Alethicode`。

## 2. 活跃模块清单
- account
- announcement
- conf
- problem
- submission
- ai_tutor
- classroom
- utils
- options
- judge

参考：`modules.txt`。

## 3. 路由与接口基线
- HTTP 路由抓取结果：`http-routes.txt`
- 统计总行数：163（含 `path/re_path/router.register` 声明行）
- WebSocket 路由基线：`ws-routes.txt`
- 已确认真实业务 WebSocket 通道：
  - `/ws/chat/stream/{task_id}`
  - `/ws/workflow/{session_id}`
  - `/ws/classroom/collab/{session_id}`
  - `/ws/classroom/monitor/{classroom_id}`

## 4. 前端 API 依赖点基线
- 文件：`frontend-api-refs.txt`
- 关键信号：
  - `axios.defaults.baseURL = '/api'`
  - CSRF：`csrftoken` + `X-CSRFToken`
  - 原生 `WebSocket` 直连 `/ws/*`

## 5. 模块依赖 DAG（文字版）
基于 import 统计得到核心依赖方向（见 `module-deps.txt`）：
- `utils -> account/problem/options`
- `options -> judge`
- `conf -> judge/options/problem/submission/account/utils`
- `problem -> account/options/submission/utils/ai_tutor`
- `submission -> problem/judge/account/conf/options/utils/classroom`
- `ai_tutor -> submission/problem/account/classroom/judge/utils/conf`
- `classroom -> submission/problem/account/ai_tutor/judge/utils`

迁移顺序据此保持：
1. 平台底座
2. conf/utils/options
3. problem
4. submission+judge链路
5. account
6. announcement
7. ai_tutor
8. classroom

## 6. 未验证前提（明确标注）
- 由于源项目处于脏工作区，当前只保证“相对基线不新增变更”，不对其既有未提交改动做语义判断。
- HTTP 163 为声明行统计，不等价于最终可调用 API 数量；后续将以契约测试逐条核对路径/方法/参数/响应。
