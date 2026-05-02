# 判题机监控看板前置 Todo

> 文档状态：待执行
> 更新日期：2026-03-31
> 执行顺序：`/home/cypress/Alethicode/docs/todos/todo-check-judge.md` -> `/home/cypress/Alethicode/docs/todos/todo-judge.md`
> 当前项目目录：`/home/cypress/Alethicode`
> 当前 admin 基线页面：`/home/cypress/Alethicode/frontend/src/pages/admin/views/general/JudgeServer.vue`
> 当前后端基线入口：
> - `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/controller/JudgeServerController.java`
> - `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/impl/JudgeServerServiceImpl.java`
> - `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/dto/request/JudgeServerHeartbeatRequest.java`
> 上游判题机参考源码：
> - `/home/cypress/Alethicode/.external_research/alethicode_upstream/JudgeServer`
> - `/home/cypress/Alethicode/.external_research/alethicode_upstream/Judger`
> 当前假设：这里的“admin 端口”指当前管理端 `/admin/judge-server` 页面与其后端数据接口。
> 前端硬前置条件：按照仓库约束，正式前端实现目录必须是 `frontend`；当前仓库尚无 `frontend`，因此本 Todo 的前端编码阶段必须先完成 `/home/cypress/Alethicode/docs/todos/todo-vue3.md` Phase 2，建立 `frontend` 后再实施。
> 唯一目标：在不改判题业务裁决逻辑的前提下，先把判题机监控数据链路、管理端看板、审计事件和告警基线做完整，并为后续 `/home/cypress/Alethicode/docs/todos/todo-judge.md` 提供真实、连续、可追溯的性能与安全观测面。

---

## 0. 总原则

### 0.1 硬性要求

- [ ] 本 Todo 必须整体验收通过后，才允许启动 `/home/cypress/Alethicode/docs/todos/todo-judge.md`。
- [ ] 不允许只加几个卡片数字就收工，必须形成“采集 -> 落库 -> 聚合 -> 查询 -> 图表 -> 告警 -> 审计”完整链路。
- [ ] 不允许把 `submission_id`、`user_id`、源代码摘要、IP 明细这类高基数数据直接做成时序指标标签。
- [ ] 不允许把性能、安全、可靠性拆成三套彼此孤立的数据模型，必须统一到一套判题机监控域模型。
- [ ] 不允许前端直接拼装原始 heartbeat JSON，必须由后端输出稳定、可版本化、可聚合的管理端 DTO。
- [ ] 不允许以百分数 `0~100` 作为后端长期存储单位；后端统一使用 Prometheus 风格基础单位：`seconds`、`bytes`、`ratio(0~1)`、`count`，前端再格式化显示。
- [ ] 不允许只看“当前值”，必须同时保留短周期实时趋势和跨天历史趋势。
- [ ] 不允许没有异常事件流；凡是心跳拒绝、认证失败、seccomp/系统调用异常、OOM、输出超限、清理失败，都必须进入事件列表。
- [ ] 不允许把监控做成独立旁路页面，正式入口固定为管理端现有 `/admin/judge-server`。

### 0.2 官方指标依据

本 Todo 的指标分层与命名原则基于以下官方资料整理：

- [ ] Prometheus Metric Types：<https://prometheus.io/docs/concepts/metric_types/>
- [ ] Prometheus Instrumentation Best Practices：<https://prometheus.io/docs/practices/instrumentation/>
- [ ] Prometheus Metric and Label Naming：<https://prometheus.io/docs/practices/naming/>
- [ ] Prometheus Histograms and Summaries：<https://prometheus.io/docs/practices/histograms/>
- [ ] Prometheus Node Exporter Guide：<https://prometheus.io/docs/guides/node-exporter/>
- [ ] Prometheus cAdvisor Guide：<https://prometheus.io/docs/guides/cadvisor/>
- [ ] Kubernetes System Metrics / PSI：<https://kubernetes.io/docs/concepts/cluster-administration/system-metrics/>

### 0.3 三条验收红线

- [ ] 可见性红线：任意一台判题机的 CPU、内存、磁盘、网络、并发槽位、任务队列、判题时延、错误率、安全异常，都必须能在管理端 30 秒内被看见。
- [ ] 一致性红线：管理端列表、卡片、图表、节点详情展示的同一指标，在同一时间窗口内不得互相矛盾；同源数据误差不得超过一个采样周期。
- [ ] 回溯性红线：必须至少保留 7 天可查询的判题机历史趋势，且能准确回答“什么时候开始变慢”“哪台机器先异常”“异常期间队列和资源发生了什么变化”。

### 0.4 统一时序与保留策略

- [ ] 原始节点快照采样周期固定为 `10s`。
- [ ] 管理端概览和节点详情实时轮询周期固定为 `10s`。
- [ ] 原始快照保留 `48h`。
- [ ] `1m` 粒度聚合保留 `30d`。
- [ ] 事件与告警保留 `180d`。
- [ ] 时间窗口固定支持：`15m`、`1h`、`6h`、`24h`、`7d`。

---

## 1. 当前事实与实施入口

### 1.1 当前项目真实现状

- [ ] 当前管理端判题机页只有 token 和一个简单列表，没有真正的监控看板。
- [ ] 当前后端 heartbeat 仅接收 `hostname`、`judgerVersion`、`cpuCore`、`memory`、`cpu`、`serviceUrl`，无法支持深入诊断。
- [ ] 当前数据库 `judge_server` 表只保存极少量当前值，没有时序表、事件表、聚合表。
- [ ] 当前 admin 前端有效代码位于 `frontend`；但按照仓库约束，正式前端改造目录必须为未来的 `frontend`。
- [ ] 当前前端已经具备 ECharts 能力，可复用 `@/utils/echarts` 与现有图表写法。

### 1.2 后端改造入口

- [ ] 修改现有 heartbeat 契约：
  - `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/dto/request/JudgeServerHeartbeatRequest.java`
- [ ] 修改现有 heartbeat 控制器：
  - `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/controller/JudgeServerController.java`
- [ ] 修改现有 heartbeat 服务：
  - `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/JudgeServerService.java`
  - `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/impl/JudgeServerServiceImpl.java`
- [ ] 修改现有判题机实体：
  - `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/entity/JudgeServer.java`
- [ ] 新增监控域控制器、服务、DTO、实体、Repository、迁移脚本。

### 1.3 前端改造入口

- [ ] 当前基线页面：
  - `/home/cypress/Alethicode/frontend/src/pages/admin/views/general/JudgeServer.vue`
- [ ] 当前基线 API：
  - `/home/cypress/Alethicode/frontend/src/pages/admin/api.js`
- [ ] 正式目标页面：
  - `/home/cypress/Alethicode/frontend/src/pages/admin/views/general/JudgeServer.vue`
- [ ] 正式目标 API：
  - `/home/cypress/Alethicode/frontend/src/pages/admin/api.js`
- [ ] 正式目标图表组件：
  - `frontend/src/pages/admin/views/general/judge-monitor/` 下新增看板组件目录

### 1.4 判题节点侧实施入口

- [ ] 外部判题机 / 节点代理必须扩展 heartbeat 负载，当前参考入口：
  - `/home/cypress/Alethicode/.external_research/alethicode_upstream/JudgeServer/server/utils.py`
  - `/home/cypress/Alethicode/.external_research/alethicode_upstream/JudgeServer/server/server.py`
  - `/home/cypress/Alethicode/.external_research/alethicode_upstream/JudgeServer/server/judge_client.py`
- [ ] `Judger` 内核本身不在本阶段重写，但需要把节点运行期统计、安全事件和资源数据暴露给 heartbeat 组包层。

---

## 2. 终态定义

### 2.1 管理端页面终态

- [ ] 保持路由仍为 `/admin/judge-server`，但内容升级为完整看板。
- [ ] 页面上半区展示集群级总览卡片。
- [ ] 页面中部展示集群级实时趋势图。
- [ ] 页面下半区展示节点列表表格，支持排序、状态筛选、异常优先。
- [ ] 点击任一节点，打开节点详情抽屉或详情区，展示该节点最近 `15m / 1h / 24h / 7d` 的多维趋势。
- [ ] 页面底部展示告警事件流与安全事件流。

### 2.2 后端数据流终态

- [ ] 判题节点每 `10s` 推送一次扩展 heartbeat。
- [ ] 后端先做签名/token 校验，再做字段校验，再入库当前值、原始快照、分钟聚合和事件。
- [ ] 后端统一计算集群级聚合指标，前端不得自行做跨节点聚合。
- [ ] 后端对管理端输出稳定 DTO；前端不依赖数据库字段名。

### 2.3 节点详情终态

节点详情必须至少包含以下 6 个区块：

- [ ] 资源总览：CPU、负载、内存、Swap、磁盘、网络、PSI。
- [ ] 运行总览：活跃任务、排队任务、可用槽位、编译中、运行中、SPJ 中、清理中。
- [ ] 时延分布：排队、编译、运行、总判题链路的 `p50/p95/p99`。
- [ ] 结果分布：`AC/WA/TLE/MLE/RE/CE/SYSTEM_ERROR/SPJ_ERROR` 分布趋势。
- [ ] 安全与沙箱：认证失败、seccomp/非法 syscall、OOM、输出超限、工作目录清理失败。
- [ ] 事件时间线：最近异常事件、告警状态变化、节点上下线、配置变化。

---

## 3. 指标体系

### 3.1 指标命名与标签约束

- [ ] 指标名统一采用 `judge_` 前缀。
- [ ] 指标单位统一采用基础单位：`_seconds`、`_bytes`、`_ratio`、`_total`、`_count`。
- [ ] 允许的低基数维度仅限：`node_id`、`hostname`、`stage`、`result`、`language`、`severity`。
- [ ] 严禁出现的时序标签：`submission_id`、`user_id`、`problem_id`、`classroom_id`、`code_hash`、任意原始 IP 列表。
- [ ] 明细事件中的高基数信息只能进入事件表 `details_json`，不得进入时序标签。

### 3.2 集群级指标

| 指标键 | 单位 | 含义 | 数据源 | 管理端位置 |
| --- | --- | --- | --- | --- |
| `judge_cluster_up_nodes` | count | 正常存活节点数 | 后端聚合 | 顶部卡片 |
| `judge_cluster_down_nodes` | count | 心跳超时节点数 | 后端聚合 | 顶部卡片 |
| `judge_cluster_disabled_nodes` | count | 被手动禁用节点数 | 后端聚合 | 顶部卡片 |
| `judge_cluster_running_tasks` | count | 全集群运行中任务数 | 节点 heartbeat 聚合 | 顶部卡片 |
| `judge_cluster_queued_tasks` | count | 全集群排队任务数 | 节点 heartbeat 聚合 | 顶部卡片 |
| `judge_cluster_available_slots` | count | 全集群剩余可用槽位 | 节点 heartbeat 聚合 | 顶部卡片 |
| `judge_cluster_queue_wait_duration_seconds_p95` | seconds | 全集群排队等待 `p95` | 分钟聚合 | 顶部卡片 + 趋势图 |
| `judge_cluster_end_to_end_duration_seconds_p95` | seconds | 全集群端到端判题 `p95` | 分钟聚合 | 顶部卡片 + 趋势图 |
| `judge_cluster_task_throughput_per_minute` | count/min | 每分钟完成任务数 | 分钟聚合 | 趋势图 |
| `judge_cluster_error_ratio` | ratio | `5m` 窗口非成功执行占比 | 分钟聚合 | 顶部卡片 + 趋势图 |
| `judge_cluster_security_incident_total_1h` | count | `1h` 内安全异常总数 | 事件聚合 | 顶部卡片 |
| `judge_cluster_heartbeat_lag_seconds_max` | seconds | 集群内最大心跳滞后 | 后端聚合 | 趋势图 |

### 3.3 节点主机资源指标

| 指标键 | 单位 | 含义 | 采集方式 | 是否必需 |
| --- | --- | --- | --- | --- |
| `judge_node_cpu_usage_ratio` | ratio | 节点 CPU 使用率 | `/proc/stat` 或等价采集 | 是 |
| `judge_node_cpu_load_1` | count | 1 分钟负载 | `/proc/loadavg` | 是 |
| `judge_node_cpu_load_5` | count | 5 分钟负载 | `/proc/loadavg` | 是 |
| `judge_node_cpu_load_15` | count | 15 分钟负载 | `/proc/loadavg` | 是 |
| `judge_node_cpu_iowait_ratio` | ratio | CPU 在 IO wait 的占比 | `/proc/stat` | 是 |
| `judge_node_memory_total_bytes` | bytes | 总内存 | `/proc/meminfo` | 是 |
| `judge_node_memory_available_bytes` | bytes | 可用内存 | `/proc/meminfo` | 是 |
| `judge_node_memory_usage_ratio` | ratio | 内存使用率 | 计算值 | 是 |
| `judge_node_swap_total_bytes` | bytes | 总 Swap | `/proc/meminfo` | 是 |
| `judge_node_swap_used_bytes` | bytes | 已用 Swap | 计算值 | 是 |
| `judge_node_swap_usage_ratio` | ratio | Swap 使用率 | 计算值 | 是 |
| `judge_node_filesystem_total_bytes` | bytes | 判题工作盘总容量 | `statvfs` 或等价采集 | 是 |
| `judge_node_filesystem_available_bytes` | bytes | 判题工作盘可用容量 | `statvfs` 或等价采集 | 是 |
| `judge_node_filesystem_usage_ratio` | ratio | 判题工作盘使用率 | 计算值 | 是 |
| `judge_node_filesystem_inode_usage_ratio` | ratio | inode 使用率 | `statvfs` 或等价采集 | 是 |
| `judge_node_disk_read_bytes_per_second` | bytes/s | 磁盘读吞吐 | `/proc/diskstats` 或等价采集 | 是 |
| `judge_node_disk_write_bytes_per_second` | bytes/s | 磁盘写吞吐 | `/proc/diskstats` 或等价采集 | 是 |
| `judge_node_disk_read_iops` | count/s | 磁盘读 IOPS | `/proc/diskstats` | 是 |
| `judge_node_disk_write_iops` | count/s | 磁盘写 IOPS | `/proc/diskstats` | 是 |
| `judge_node_disk_await_seconds` | seconds | 平均磁盘等待时间 | `/proc/diskstats` 计算 | 是 |
| `judge_node_network_receive_bytes_per_second` | bytes/s | 网络入流量 | `/proc/net/dev` | 是 |
| `judge_node_network_transmit_bytes_per_second` | bytes/s | 网络出流量 | `/proc/net/dev` | 是 |
| `judge_node_network_receive_drop_per_second` | count/s | 网络入丢包速率 | `/proc/net/dev` | 是 |
| `judge_node_network_transmit_drop_per_second` | count/s | 网络出丢包速率 | `/proc/net/dev` | 是 |
| `judge_node_pressure_cpu_waiting_ratio` | ratio | CPU PSI 等待占比 | `/proc/pressure/cpu` | 是，若环境不支持则本 Todo 不启动 |
| `judge_node_pressure_memory_waiting_ratio` | ratio | 内存 PSI 等待占比 | `/proc/pressure/memory` | 是，若环境不支持则本 Todo 不启动 |
| `judge_node_pressure_io_waiting_ratio` | ratio | IO PSI 等待占比 | `/proc/pressure/io` | 是，若环境不支持则本 Todo 不启动 |

### 3.4 节点运行与任务指标

| 指标键 | 单位 | 含义 | 数据源 | 管理端位置 |
| --- | --- | --- | --- | --- |
| `judge_node_running_tasks` | count | 运行中任务数 | 节点代理内存态 | 节点列表 + 详情 |
| `judge_node_queued_tasks` | count | 节点本地待处理任务数 | 节点代理内存态 | 节点列表 + 详情 |
| `judge_node_available_slots` | count | 当前空闲槽位 | 节点代理内存态 | 节点列表 + 详情 |
| `judge_node_compile_in_progress` | count | 编译阶段中的任务数 | 节点代理内存态 | 节点详情 |
| `judge_node_run_in_progress` | count | 运行阶段中的任务数 | 节点代理内存态 | 节点详情 |
| `judge_node_spj_in_progress` | count | SPJ 阶段中的任务数 | 节点代理内存态 | 节点详情 |
| `judge_node_cleanup_in_progress` | count | 清理阶段中的任务数 | 节点代理内存态 | 节点详情 |
| `judge_node_queue_wait_duration_seconds_p50` | seconds | 排队等待 `p50` | 窗口聚合 | 趋势图 |
| `judge_node_queue_wait_duration_seconds_p95` | seconds | 排队等待 `p95` | 窗口聚合 | 趋势图 |
| `judge_node_queue_wait_duration_seconds_p99` | seconds | 排队等待 `p99` | 窗口聚合 | 趋势图 |
| `judge_node_compile_duration_seconds_p50` | seconds | 编译时长 `p50` | 窗口聚合 | 趋势图 |
| `judge_node_compile_duration_seconds_p95` | seconds | 编译时长 `p95` | 窗口聚合 | 趋势图 |
| `judge_node_run_duration_seconds_p50` | seconds | 运行时长 `p50` | 窗口聚合 | 趋势图 |
| `judge_node_run_duration_seconds_p95` | seconds | 运行时长 `p95` | 窗口聚合 | 趋势图 |
| `judge_node_end_to_end_duration_seconds_p50` | seconds | 总判题时长 `p50` | 窗口聚合 | 趋势图 |
| `judge_node_end_to_end_duration_seconds_p95` | seconds | 总判题时长 `p95` | 窗口聚合 | 趋势图 |
| `judge_node_end_to_end_duration_seconds_p99` | seconds | 总判题时长 `p99` | 窗口聚合 | 趋势图 |
| `judge_node_tasks_completed_total` | total | 累计完成任务数 | 节点事件累计 | 详情统计 |
| `judge_node_tasks_completed_per_minute` | count/min | 每分钟完成任务数 | 分钟聚合 | 趋势图 |
| `judge_node_result_total{result}` | total | 各结果累计数 | 分钟聚合 | 结果分布 |
| `judge_node_language_total{language}` | total | 各语言判题数 | 分钟聚合 | 语言分布 |
| `judge_node_system_error_ratio` | ratio | 系统错误占比 | 分钟聚合 | 顶部卡片 + 告警 |
| `judge_node_timeout_ratio` | ratio | TLE 占比 | 分钟聚合 | 节点详情 |
| `judge_node_memory_peak_bytes_p95` | bytes | `5m` 窗口峰值内存 `p95` | 任务聚合 | 节点详情 |

### 3.5 容器 / cgroup / 沙箱资源指标

| 指标键 | 单位 | 含义 | 采集方式 | 是否必需 |
| --- | --- | --- | --- | --- |
| `judge_node_cgroup_cpu_usage_ratio` | ratio | 判题容器 CPU 使用率 | cgroup | 是 |
| `judge_node_cgroup_cpu_throttled_ratio` | ratio | CPU 被 throttled 的比例 | cgroup | 是 |
| `judge_node_cgroup_memory_working_set_bytes` | bytes | 判题容器 working set | cgroup | 是 |
| `judge_node_cgroup_memory_rss_bytes` | bytes | 判题容器 RSS | cgroup | 是 |
| `judge_node_cgroup_memory_cache_bytes` | bytes | 判题容器 page cache | cgroup | 是 |
| `judge_node_cgroup_pids_current` | count | 当前 PID 数 | cgroup | 是 |
| `judge_node_cgroup_pids_limit` | count | PID 限额 | cgroup | 是 |
| `judge_node_cgroup_oom_total` | total | OOM 触发累计次数 | cgroup / 事件累计 | 是 |
| `judge_node_cgroup_fs_reads_bytes_per_second` | bytes/s | 容器文件系统读速率 | cgroup / 容器统计 | 是 |
| `judge_node_cgroup_fs_writes_bytes_per_second` | bytes/s | 容器文件系统写速率 | cgroup / 容器统计 | 是 |

### 3.6 可靠性与安全指标

| 指标键 | 单位 | 含义 | 数据源 | 管理端位置 |
| --- | --- | --- | --- | --- |
| `judge_node_heartbeat_lag_seconds` | seconds | 当前心跳滞后时间 | 后端计算 | 列表 + 卡片 + 趋势 |
| `judge_node_heartbeat_reject_total` | total | heartbeat 被拒绝累计次数 | 后端鉴权 | 安全卡片 + 事件流 |
| `judge_node_auth_failure_total` | total | 节点认证失败次数 | 后端鉴权 | 安全卡片 + 事件流 |
| `judge_node_restart_total` | total | 节点代理重启次数 | 节点自报 | 节点详情 |
| `judge_node_seccomp_violation_total` | total | seccomp/非法 syscall 次数 | 节点代理/沙箱事件 | 安全卡片 |
| `judge_node_output_limit_exceeded_total` | total | 输出超限累计次数 | 节点代理 | 安全卡片 |
| `judge_node_cleanup_failure_total` | total | 工作目录清理失败次数 | 节点代理 | 事件流 |
| `judge_node_workspace_leak_count` | count | 残留工作目录数量 | 节点周期扫描 | 节点详情 |
| `judge_node_workspace_usage_bytes` | bytes | 工作目录已占空间 | 节点周期扫描 | 节点详情 |
| `judge_node_alert_open_count` | count | 当前未恢复告警数 | 后端告警引擎 | 顶部卡片 |

### 3.7 告警规则最低集

- [ ] `heartbeat_lag_seconds > 15` 且持续 `30s`：严重告警。
- [ ] `available_slots = 0` 且 `queued_tasks > 0` 且持续 `2m`：容量告警。
- [ ] `queue_wait_duration_seconds_p95 > 10` 且持续 `5m`：性能告警。
- [ ] `end_to_end_duration_seconds_p95 > 基线 * 2` 且持续 `5m`：性能告警。
- [ ] `cgroup_cpu_throttled_ratio > 0.10` 且持续 `5m`：资源限制告警。
- [ ] `memory_usage_ratio > 0.90` 或 `swap_usage_ratio > 0.20` 且持续 `5m`：内存告警。
- [ ] `filesystem_usage_ratio > 0.85` 或 `inode_usage_ratio > 0.85` 且持续 `5m`：磁盘告警。
- [ ] `seccomp_violation_total`、`auth_failure_total`、`heartbeat_reject_total` 任一增长：安全告警。
- [ ] `cleanup_failure_total` 持续增长：环境清理告警。

---

## 4. 后端实现 Phase 1：冻结指标契约与 DTO

### 阶段目标

先把 heartbeat 负载、后台查询 DTO、告警枚举和时间窗口契约完全冻结，后续实现不再边做边改。

### 执行步骤

- [ ] 定义扩展 heartbeat 顶层结构：`nodeInfo`、`hostMetrics`、`runtimeMetrics`、`taskMetrics`、`securityMetrics`、`events`。
- [ ] 明确字段单位：所有 duration 用 `seconds`，所有容量用 `bytes`，所有占比用 `ratio`。
- [ ] 明确允许的事件类型：
  - [ ] `NODE_ONLINE`
  - [ ] `NODE_OFFLINE`
  - [ ] `AUTH_FAILURE`
  - [ ] `HEARTBEAT_REJECTED`
  - [ ] `SECCOMP_VIOLATION`
  - [ ] `ILLEGAL_SYSCALL`
  - [ ] `OOM_KILLED`
  - [ ] `OUTPUT_LIMIT_EXCEEDED`
  - [ ] `WORKSPACE_CLEANUP_FAILED`
  - [ ] `TMPFS_USAGE_HIGH`
  - [ ] `DISK_USAGE_HIGH`
- [ ] 冻结后台查询 DTO：
  - [ ] `JudgeMonitorOverviewResponse`
  - [ ] `JudgeMonitorNodeListItemResponse`
  - [ ] `JudgeMonitorNodeDetailResponse`
  - [ ] `JudgeMonitorTimeseriesResponse`
  - [ ] `JudgeMonitorAlertListResponse`
  - [ ] `JudgeMonitorEventListResponse`
- [ ] 冻结时间窗口与 step 规则：`15m=10s`、`1h=30s`、`6h=1m`、`24h=5m`、`7d=1h`。
- [ ] 冻结告警级别枚举：`INFO`、`WARNING`、`CRITICAL`。

### 产出物

- [ ] Heartbeat JSON 契约文档
- [ ] 管理端响应 DTO 清单
- [ ] 告警与事件枚举表
- [ ] 采样与窗口规则表

### 严格验收标准

- [ ] 任意一个前端图表都能唯一映射到某个后端 DTO 字段，不存在前端自己“再推一遍公式”的情况。
- [ ] 任意一个后端指标字段都能明确回答“单位是什么、采样间隔是什么、来源是什么、展示在哪张图上”。
- [ ] 任意一个事件类型都能明确回答“谁产生、何时产生、显示到哪、保留多久”。

---

## 5. 后端实现 Phase 2：数据库模型与迁移

### 阶段目标

建立一套足以支撑实时值、趋势图、结果分布和事件流的监控数据模型。

### 执行步骤

- [ ] 扩展 `judge_server` 当前值表，新增最新快照所需字段：
  - [ ] `agent_version`
  - [ ] `status_reason`
  - [ ] `heartbeat_lag_seconds`
  - [ ] `available_slots`
  - [ ] `running_tasks`
  - [ ] `queued_tasks`
  - [ ] `cpu_usage_ratio`
  - [ ] `memory_usage_ratio`
  - [ ] `filesystem_usage_ratio`
  - [ ] `cgroup_cpu_throttled_ratio`
  - [ ] `queue_wait_duration_seconds_p95`
  - [ ] `end_to_end_duration_seconds_p95`
  - [ ] `security_incident_total_1h`
- [ ] 新建原始快照表 `judge_server_metric_snapshot`：
  - [ ] 主键
  - [ ] `judge_server_id`
  - [ ] `captured_at`
  - [ ] 所有主机资源指标
  - [ ] 所有节点运行指标中的当前值
  - [ ] 所有 cgroup 指标
  - [ ] `payload_version`
- [ ] 新建分钟聚合表 `judge_server_metric_rollup_minute`：
  - [ ] `judge_server_id`
  - [ ] `window_start`
  - [ ] `avg/max` 资源指标
  - [ ] `p50/p95/p99` 时延指标
  - [ ] `throughput`
  - [ ] `error_ratio`
- [ ] 新建任务分布聚合表 `judge_server_task_rollup_minute`：
  - [ ] `judge_server_id`
  - [ ] `window_start`
  - [ ] `language`
  - [ ] `result`
  - [ ] `task_count`
  - [ ] `queue_wait_p95_seconds`
  - [ ] `compile_p95_seconds`
  - [ ] `run_p95_seconds`
  - [ ] `memory_peak_p95_bytes`
- [ ] 新建事件表 `judge_server_event`：
  - [ ] `judge_server_id`
  - [ ] `event_type`
  - [ ] `severity`
  - [ ] `occurred_at`
  - [ ] `message`
  - [ ] `details_json`
  - [ ] `dedup_key`
- [ ] 新建告警状态表 `judge_server_alert_state`：
  - [ ] `judge_server_id`
  - [ ] `alert_key`
  - [ ] `severity`
  - [ ] `status`
  - [ ] `opened_at`
  - [ ] `closed_at`
  - [ ] `last_value`
- [ ] 为 `captured_at`、`window_start`、`judge_server_id + captured_at`、`severity + occurred_at` 建立索引。
- [ ] 增加数据保留清理任务：原始 `48h`、聚合 `30d`、事件 `180d`。

### 产出物

- [ ] Flyway 迁移脚本
- [ ] JPA 实体
- [ ] Repository
- [ ] 保留清理任务定义

### 严格验收标准

- [ ] 可以只依靠数据库回答“当前状态”“过去 1 小时趋势”“过去 7 天趋势”“最近异常事件”四类问题。
- [ ] 任意一个窗口查询都不会去扫全量原始表。
- [ ] `judge_server` 当前值与最近一条原始快照字段一致，不存在双写漂移。

---

## 6. 后端实现 Phase 3：判题节点扩展 heartbeat 与事件上报

### 阶段目标

让判题节点真正把详细指标推上来，而不是只报 CPU 和内存。

### 执行步骤

- [ ] 在节点代理/判题机服务中实现 `10s` 周期采样器。
- [ ] 采样器必须读取：
  - [ ] `/proc/stat`
  - [ ] `/proc/loadavg`
  - [ ] `/proc/meminfo`
  - [ ] `/proc/diskstats`
  - [ ] `/proc/net/dev`
  - [ ] `/proc/pressure/*`
  - [ ] cgroup 相关文件
  - [ ] 节点代理内存态任务池数据
  - [ ] 沙箱异常事件计数器
- [ ] 扩展 heartbeat 请求体，替换现有仅含少量字段的负载。
- [ ] 节点端必须记录分钟窗口内的任务统计：
  - [ ] 完成任务数
  - [ ] 按语言分布
  - [ ] 按结果分布
  - [ ] 排队 / 编译 / 运行 / 总时长分位数
  - [ ] 内存峰值分位数
- [ ] 节点端必须维护安全事件缓冲区，并随 heartbeat 一并发送最近窗口内新事件。
- [ ] 所有事件必须带 `event_type`、`severity`、`occurred_at`、`message`、`dedup_key`。

### 产出物

- [ ] 节点侧采样器
- [ ] 扩展 heartbeat 组包逻辑
- [ ] 事件缓存与上报逻辑
- [ ] 节点侧字段映射表

### 严格验收标准

- [ ] 在一台判题节点上做真实压测时，CPU、内存、磁盘、网络、PSI、队列、时延指标都会随 workload 实时变化。
- [ ] 节点端采样逻辑本身不会引入肉眼可见的额外抖动；采样线程或协程不得阻塞判题主流程。
- [ ] 任意一个安全事件在节点发生后，`30s` 内必须能在后端事件表中出现。

---

## 7. 后端实现 Phase 4：heartbeat 入库、聚合与告警引擎

### 阶段目标

把节点上报的详细指标转成“当前值 + 历史趋势 + 事件流 + 告警状态”四类后端资产。

### 执行步骤

- [ ] 修改 `JudgeServerController`，接收扩展 heartbeat 并做 failfast 校验。
- [ ] 修改 `JudgeServerServiceImpl`，将 heartbeat 拆分为：
  - [ ] 当前值写入 `judge_server`
  - [ ] 原始快照写入 `judge_server_metric_snapshot`
  - [ ] 事件写入 `judge_server_event`
- [ ] 新增分钟聚合作业，将 `10s` 原始快照滚成 `1m` 聚合。
- [ ] 新增任务分布聚合作业，按 `node + minute + language + result` 产出 rollup。
- [ ] 新增告警计算器，按固定阈值更新 `judge_server_alert_state`。
- [ ] 告警开闭必须写事件，形成时间线。
- [ ] 后端必须对无效 heartbeat、单位错误、缺字段、未来时间戳、过大窗口做拒绝并记安全事件。

### 产出物

- [ ] heartbeat 落库链路
- [ ] 分钟聚合作业
- [ ] 告警引擎
- [ ] 入库失败与校验失败审计日志

### 严格验收标准

- [ ] 一条 heartbeat 进入后，`judge_server` 当前值、原始快照和事件表状态必须可追踪。
- [ ] 原始快照与分钟聚合之间不存在跨分钟错桶。
- [ ] 告警开闭切换必须幂等，不允许同一阈值在一分钟内抖动生成大量重复事件。

---

## 8. 后端实现 Phase 5：Admin 查询 API

### 阶段目标

给管理端提供稳定、清晰、低耦合、可分页、可筛选的监控查询接口。

### 执行步骤

- [ ] 新增 `JudgeMonitorController` 与对应 Service。
- [ ] 固定新增 API：
  - [ ] `GET /api/admin/judge-monitor/overview`
  - [ ] `GET /api/admin/judge-monitor/nodes`
  - [ ] `GET /api/admin/judge-monitor/nodes/{nodeId}`
  - [ ] `GET /api/admin/judge-monitor/nodes/{nodeId}/timeseries`
  - [ ] `GET /api/admin/judge-monitor/nodes/{nodeId}/task-breakdown`
  - [ ] `GET /api/admin/judge-monitor/alerts`
  - [ ] `GET /api/admin/judge-monitor/events`
- [ ] `overview` 返回：
  - [ ] 顶部卡片
  - [ ] 集群趋势
  - [ ] 集群容量摘要
- [ ] `nodes` 返回：
  - [ ] 节点当前值列表
  - [ ] 支持按状态、禁用状态、异常优先、CPU、内存、队列、心跳滞后排序
- [ ] `nodes/{nodeId}` 返回：
  - [ ] 节点概况
  - [ ] 当前告警
  - [ ] 最新事件摘要
- [ ] `timeseries` 返回：
  - [ ] `range`
  - [ ] `step`
  - [ ] 多条系列 `metricKey -> points`
- [ ] `task-breakdown` 返回：
  - [ ] 语言分布
  - [ ] 结果分布
  - [ ] 时延分布
- [ ] `alerts` 支持按 `status`、`severity`、`nodeId` 筛选。
- [ ] `events` 支持按 `eventType`、`severity`、`nodeId`、时间窗口筛选。

### 产出物

- [ ] 监控查询 Controller
- [ ] 查询 Service
- [ ] 前端可消费 DTO
- [ ] API 契约文档

### 严格验收标准

- [ ] 管理端所需所有页面元素都能由上述 API 完整供给，不再复用旧 `GET /api/admin/judge-server` 作为主数据源。
- [ ] `1h` 窗口的单节点详情接口在正常数据量下响应时间必须小于 `500ms`。
- [ ] `24h` 窗口的单节点趋势接口在正常数据量下响应时间必须小于 `1.5s`。
- [ ] `7d` 窗口趋势只读聚合表，不允许回扫原始 `10s` 快照。

---

## 9. 前端实现 Phase 6：建立 `frontend` 工作区并接线 admin API

### 阶段目标

在正式前端工作区中建立 admin 监控页面的基础骨架和 API 接线。

### 执行步骤

- [ ] 先完成 `/home/cypress/Alethicode/docs/todos/todo-vue3.md` Phase 2，创建 `frontend`。
- [ ] 在 `frontend` 中复制现有 admin 路由与 view 基线。
- [ ] 在 `frontend/src/pages/admin/api.js` 新增：
  - [ ] `getJudgeMonitorOverview()`
  - [ ] `getJudgeMonitorNodes(params)`
  - [ ] `getJudgeMonitorNodeDetail(nodeId)`
  - [ ] `getJudgeMonitorNodeTimeseries(nodeId, params)`
  - [ ] `getJudgeMonitorNodeTaskBreakdown(nodeId, params)`
  - [ ] `getJudgeMonitorAlerts(params)`
  - [ ] `getJudgeMonitorEvents(params)`
- [ ] 保持路由仍为 `name: 'judge-server'`，不新增新路由。
- [ ] 把 `JudgeServer.vue` 重构为看板容器页。
- [ ] 新建组件目录：
  - [ ] `frontend/src/pages/admin/views/general/judge-monitor/JudgeOverviewCards.vue`
  - [ ] `frontend/src/pages/admin/views/general/judge-monitor/JudgeClusterCharts.vue`
  - [ ] `frontend/src/pages/admin/views/general/judge-monitor/JudgeNodeTable.vue`
  - [ ] `frontend/src/pages/admin/views/general/judge-monitor/JudgeNodeDetailDrawer.vue`
  - [ ] `frontend/src/pages/admin/views/general/judge-monitor/JudgeAlertTimeline.vue`
  - [ ] `frontend/src/pages/admin/views/general/judge-monitor/JudgeMetricChart.vue`
- [ ] 统一复用现有 `@/utils/echarts`，不引入第二套图表库。

### 产出物

- [ ] `frontend` admin 监控 API
- [ ] 看板页面骨架
- [ ] 图表基础组件
- [ ] 轮询与时间窗口状态管理

### 严格验收标准

- [ ] 前端所有监控请求都走新增 monitor API，不继续依赖旧 `getJudgeServer()` 返回的极简列表。
- [ ] 页面骨架已经能在无真实图表样式优化前稳定显示加载态、空态、错误态。
- [ ] 所有新组件命名、文件名、导入路径都满足仓库命名规范。

---

## 10. 前端实现 Phase 7：集群总览看板

### 阶段目标

让 `/admin/judge-server` 先具备“打开就能知道整个判题集群是否健康”的总览能力。

### 执行步骤

- [ ] 顶部卡片固定展示：
  - [ ] 正常节点数
  - [ ] 异常节点数
  - [ ] 禁用节点数
  - [ ] 运行中任务数
  - [ ] 排队任务数
  - [ ] 可用槽位数
  - [ ] 队列 `p95`
  - [ ] 总时延 `p95`
  - [ ] `1h` 安全事件数
- [ ] 集群趋势区固定展示 4 张核心图：
  - [ ] 集群任务吞吐趋势
  - [ ] 集群队列与可用槽位趋势
  - [ ] 集群 `queue p95 / end-to-end p95` 趋势
  - [ ] 集群异常率与安全事件趋势
- [ ] 节点列表至少包含这些列：
  - [ ] 状态
  - [ ] 主机名
  - [ ] 版本
  - [ ] CPU 使用率
  - [ ] 内存使用率
  - [ ] 磁盘使用率
  - [ ] 运行中任务
  - [ ] 排队任务
  - [ ] 可用槽位
  - [ ] 心跳滞后
  - [ ] 当前告警数
  - [ ] 最近安全事件时间
  - [ ] 禁用开关
- [ ] 节点列表默认按“异常优先 -> 心跳滞后倒序 -> 排队任务倒序”排序。
- [ ] 页面默认自动刷新 `10s`，同时显示“最后更新时间”。

### 产出物

- [ ] 集群总览页
- [ ] 实时趋势图
- [ ] 可排序节点表

### 严格验收标准

- [ ] 管理员在不打开任何节点详情的情况下，能在 `10s` 内判断出“有没有节点挂了、队列是否积压、集群是不是变慢了、安全事件是否上升”。
- [ ] 页面刷新不会导致图表实例泄漏；连续停留 `30min` 后浏览器内存增长必须可控。
- [ ] 节点列表任意一列排序都只依赖后端当前值，不允许前端拼接计算歧义值。

---

## 11. 前端实现 Phase 8：节点详情看板

### 阶段目标

让管理员能从单节点视角定位瓶颈到底在 CPU、内存、磁盘、网络、队列、判题阶段还是安全异常。

### 执行步骤

- [ ] 点击节点行打开 `JudgeNodeDetailDrawer`。
- [ ] 详情抽屉固定提供时间窗口切换：`15m`、`1h`、`6h`、`24h`、`7d`。
- [ ] 详情抽屉固定提供 6 个标签区：
  - [ ] `概况`
  - [ ] `资源`
  - [ ] `判题时延`
  - [ ] `结果分布`
  - [ ] `安全与沙箱`
  - [ ] `事件时间线`
- [ ] `概况` 区展示：
  - [ ] 节点基础信息
  - [ ] 当前告警
  - [ ] 当前任务状态
  - [ ] 最新 heartbeat 到达时间
- [ ] `资源` 区展示：
  - [ ] CPU 使用率 + Load1/5/15
  - [ ] 内存 / Swap
  - [ ] 磁盘容量 / IOPS / await
  - [ ] 网络收发 / 丢包
  - [ ] PSI 图
  - [ ] cgroup throttling / memory / pids
- [ ] `判题时延` 区展示：
  - [ ] 队列 / 编译 / 运行 / 总时长 `p50/p95/p99`
  - [ ] 不同语言的时延柱状对比
- [ ] `结果分布` 区展示：
  - [ ] 各 verdict 占比
  - [ ] 各语言任务量
  - [ ] `SYSTEM_ERROR` 与 `TLE` 趋势
- [ ] `安全与沙箱` 区展示：
  - [ ] 认证失败
  - [ ] heartbeat 拒绝
  - [ ] seccomp / 非法 syscall
  - [ ] OOM
  - [ ] 输出超限
  - [ ] 工作目录清理失败
- [ ] `事件时间线` 区展示最近事件流，支持 severity 筛选。

### 产出物

- [ ] 节点详情抽屉
- [ ] 节点多标签图表区
- [ ] 事件流组件

### 严格验收标准

- [ ] 任一节点详情都能在一个页面内回答“这台机器最近为什么慢”“它慢在哪个阶段”“它是否在资源受限或安全异常状态下运行”。
- [ ] 时间窗口切换只重新请求当前节点数据，不得整页全量刷新。
- [ ] 关闭抽屉后必须释放对应图表实例和定时器。

---

## 12. 联调 Phase 9：准确性、压测与故障注入

### 阶段目标

确认看板不是“看起来很丰富”，而是真的准确、稳定、能定位问题。

### 执行步骤

- [ ] 基于固定压测脚本制造以下场景：
  - [ ] 正常稳态
  - [ ] 比赛式突发高并发提交
  - [ ] 单节点 CPU 饱和
  - [ ] 单节点磁盘 IO 饱和
  - [ ] 单节点网络抖动
  - [ ] 人工制造队列积压
  - [ ] seccomp/非法 syscall 样例
  - [ ] fork bomb / OOM 样例
  - [ ] 输出爆量样例
  - [ ] 工作目录清理失败样例
- [ ] 校验每个场景下的图表、卡片、列表和事件流是否同步变化。
- [ ] 校验 `overview` 与 `node detail` 同时段指标是否一致。
- [ ] 校验告警开闭是否与故障发生和恢复相符。
- [ ] 校验 `7d` 聚合图是否仍能快速打开。

### 产出物

- [ ] 监控联调报告
- [ ] 故障注入报告
- [ ] 看板截图基线
- [ ] 指标字段对照表

### 严格验收标准

- [ ] 节点下线后 `30s` 内列表状态必须变异常，`1min` 内必须出现离线事件。
- [ ] 人工制造队列积压后，集群 `queued_tasks` 和 `queue_wait_duration_seconds_p95` 必须同步上升。
- [ ] 人工制造 IO 饱和后，节点 `disk_await_seconds`、`filesystem_usage_ratio` 或 `pressure_io_waiting_ratio` 至少有一项明显异常，并能与时延上升对应。
- [ ] 人工制造 seccomp/非法 syscall 后，安全事件流必须出现对应事件，且节点安全卡片计数增加。

---

## 13. 最终准入：进入 `todo_judge.md` 之前必须满足的条件

- [ ] 已经拿到不少于 `7d` 的判题机真实历史数据。
- [ ] 能准确指出当前判题链路的前三大性能瓶颈来自哪里。
- [ ] 能准确指出当前判题链路的前三大安全风险来自哪里。
- [ ] 能用看板直接观察到“队列、时延、槽位、throttling、磁盘、PSI、安全事件”的变化。
- [ ] `todo_judge.md` 中后续所有性能优化，都已经有可以直接复用的基线对照图和验收指标。

---

## 14. 本 Todo 完成后的直接收益

- [ ] 后续做调度重构前，不再靠日志猜测瓶颈。
- [ ] 后续做节点代理替换前，可以先知道真正的瓶颈是 CPU、IO、并发模型还是安全治理。
- [ ] 后续做安全加固时，能用事件流验证风险是否真的下降。
- [ ] 后续做吞吐优化时，能直接用 `queue_wait p95`、`end-to-end p95`、`available_slots`、`throttled_ratio` 做硬验收。
