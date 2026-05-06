# 判题机能力变更摘要

本文件按 Phase 顺序记录 `services/judge-server/` 的能力扩展。每条记录给出：
新增能力、对业务端的影响、配置开关、回退方式。

业务端 Java backend 可以全程**不修改任何代码**，新字段会自动通过
`submission.statistic_info` (jsonb) 入库；新端点（`/explain` `/trace` `/metrics`）
是按需调用，不调用即不影响。

## Phase 0：源码迁入项目（2026-05-06）

- 上游 [`QingdaoU/JudgeServer@b28aa56`](https://github.com/QingdaoU/JudgeServer/commit/b28aa56) 与 [`QingdaoU/Judger@d19a6dc`](https://github.com/QingdaoU/Judger/commit/d19a6dc) fork 落库到 `services/judge-server/judge_server/` 和 `services/judge-server/judger/`。
- 新增工程化壳层：`pyproject.toml` / `Dockerfile` / `Makefile` / `README.md` / `.gitignore` / `.dockerignore` / `configs/default.toml` / `tests/`。
- `configs/default.toml` 预先登记后续 Phase 1-6 的所有配置键，但**当前一律不消费**，运行时行为与上游镜像 100% 一致。
- 新增 `deploy/docker-compose.judge-only.yml`（开发者本地按需单独拉判题机）+ `deploy/helm/alethicode/values-judge-local.yaml`（生产环境想切到本地构建镜像时的 overlay）。
- **deploy/docker-compose.yml 与 deploy/helm/alethicode/values.yaml 默认值未修改**：本地 backend 仍连上游 `registry.cn-hongkong.aliyuncs.com/oj-image/judge:1.6.1`；项目镜像 `alethicode/judge-server:dev` 仅在显式 opt-in 时启用，避免本地 30+ 分钟构建成本扩散。
- 业务端契约：零修改，回归样本集需在判题镜像内运行（host 不具备 sandbox 环境）。

| 配置开关 | 默认 | 含义 |
| --- | --- | --- |
| `judge.image.repository`（Helm） | `registry.cn-hongkong.aliyuncs.com/oj-image/judge` | 默认上游镜像，可被 `values-judge-local.yaml` 覆盖为 `alethicode/judge-server` |
| `JUDGE_SERVER_TOKEN`（env） | `alethicode_judge_local_2026` | 与上游兼容 |
| `judger_debug`（env） | `0` | 与上游兼容，`1` 时不清理工作目录便于排错 |

回退：删除 `services/judge-server/` 目录与新增的 deploy overlay；判题链路不受任何影响（默认就是上游镜像）。

## Phase 1：并发治理 + 测试点流式反馈（2026-05-06）

- 新增 `judge_server/worker_pool.py`：固定大小 worker pool + 三级优先队列（`formal > debug > trace`）+ 队列过载快速失败 `QueueFull` → 503。
- 新增 `judge_server/streaming/sse.py`：`SseStreamBridge` 把 `on_case_done` 回调适配成 SSE event generator。
- 修改 `judge_server/judge_client.py`：删除 `Pool(cpu_count())` 每请求新建模式，`run()` 改为提交到全局 worker pool；新增 `priority` 与 `on_case_done` 参数。
- 修改 `judge_server/server.py`：`/judge` 增加 `priority` / `stream` / `callback_url` 三个可选入参，三种模式严格互斥；不传时行为与 Phase 0 完全一致。
- 新增 30 个 host 单测覆盖 worker pool / streaming / server dispatch 三层。

| 配置开关 | 默认 | 含义 |
| --- | --- | --- |
| `JUDGE_MAX_WORKERS` | `psutil.cpu_count()` | worker pool 固定大小 |
| `JUDGE_MAX_QUEUE_DEPTH` | `256` | 队列深度上限 |
| `priority`（请求体） | `"formal"` | `formal` / `debug` / `trace` |
| `stream`（请求体） | `false` | `true` 时返回 SSE |
| `callback_url`（请求体） | 无 | 异步 + 推送 case/done event |

回退：还原 `judge_client.py` + `server.py` 到 Phase 0、删除 `worker_pool.py` + `streaming/`。

## Phase 2：失败信号教学化（2026-05-06）

- 新增 `judge_server/diagnosis/`：`rules.py`（Python 7 种异常 / C segfault+abort+fpe+CE / Java 3 种异常 / TLE / MLE / WA / SE，覆盖率 ~70%）+ `ai_fallback.py`（LLM 兜底，`confidence < 0.6` 或规则未命中时调用）+ `cache.py`（LRU + TTL）+ `engine.py`（规则优先 → AI 兜底 → 空诊断降级，永不 raise）。
- 新增 `judge_server/llm/client.py`：OpenAI 兼容客户端 + 令牌桶限流，三模块独立实例化。
- 修改 `judge_server/judge_client.py`：`_judge_one()` 返回前追加 `edu_diagnosis` 字段；`JudgeClient` 构造新增 `language` / `src` 参数。
- 修改 `judge_server/server.py`：`JudgeServer.judge` 把 `language` 和 `src` 传给 `JudgeClient`。
- 新增 46 个 host 单测覆盖 rules / cache / engine / ai_fallback / llm_client 五层。

| 配置开关 | 默认 | 含义 |
| --- | --- | --- |
| `ENABLE_AI_DIAGNOSIS` | `true` | 开关整个 AI 兜底诊断 |
| `AI_DIAGNOSIS_ENDPOINT` | 空 | OpenAI 兼容 base URL |
| `AI_DIAGNOSIS_MODEL` | 空 | 模型名 |
| `AI_DIAGNOSIS_API_KEY` | 空 | API Key 环境变量名 |
| `AI_DIAGNOSIS_RATE_LIMIT` | `5` | 每节点每秒最多调用次数 |
| `AI_DIAGNOSIS_CACHE_TTL` | `600` | 缓存 TTL（秒） |

回退：删除 `diagnosis/` + `llm/`、还原 `judge_client.py` 和 `server.py` 中的 diagnosis 相关改动。

## Phase 3：失败 AI 解释端点 + ACM 首错短路（2026-05-06）

- 新增 `judge_server/explain/service.py`：`POST /explain` 端点，独立 LLM 配置 + 15 分钟缓存 + 限流。不可用时返回 `status=unavailable`，不阻塞调用方。
- `/judge` 新增 `rule_type` 参数（默认 `"ACM"`）。ACM 模式下首个非 AC 测试点完成后取消后续 worker 并返回 `early_stop` 元字段；OI 模式跑完所有测试点。
- `server.py` 在启动时自动注册 `/explain` 路由（模块不可用则跳过）。
- 新增 11 个 host 单测覆盖 ExplainService / /explain 路由 / ACM rule_type 传入。

| 配置开关 | 默认 | 含义 |
| --- | --- | --- |
| `ENABLE_AI_EXPLAIN` | `true` | 开关 /explain 端点 AI 调用 |
| `AI_EXPLAIN_ENDPOINT` | 空 | OpenAI 兼容 base URL |
| `AI_EXPLAIN_MODEL` | 空 | 模型名 |
| `AI_EXPLAIN_API_KEY` | 空 | API Key |
| `AI_EXPLAIN_RATE_LIMIT` | `2` | 每节点每秒最多调用次数 |
| `rule_type`（请求体） | `"ACM"` | `"ACM"` 首错短路 / `"OI"` 全跑 |

## Phase 4：可观测性 `/metrics`（2026-05-06）

- 新增 `judge_server/metrics/exporter.py`：手工输出 Prometheus text format（不引入 `prometheus_client` 第三方库）。`GET /metrics` 暴露 worker pool / 结果分布 / AI 调用 / CPU / 内存指标。
- 新增 `MetricsCollector`：线程安全的 result/language counter。
- 新增 8 个 host 单测。

## Phase 5：运行轨迹下沉 `/trace`（2026-05-06）

- 新增 `judge_server/trace/tracer.py`：`sys.settrace` 逐行捕获 + 局部变量教学过滤 + stdout 增量捕获。`POST /trace` 端点带缓存 + `max_steps` 截断保护。仅 Python 优先。
- 新增 13 个 host 单测（`trace_python` 函数 9 个 + `/trace` 路由 4 个）。

## Phase 6：AI 安全过滤（2026-05-06）

- 新增 `judge_server/safety/screener.py`：静态正则检测 fork bomb / 敏感路径 / shell 逃逸 / ctypes + 可选 LLM 兜底。默认 `ENABLE_AI_SAFETY=false`。
- 新增 9 个 host 单测。

全量自测：117 passed / 1 skipped（sandbox 占位）。
