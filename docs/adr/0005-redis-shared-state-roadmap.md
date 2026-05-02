# ADR-0005: Java 侧 tutor workflow 状态从 JVM 进程内迁移到 Redis（规划）

- **Status**: Proposed
- **Date**: 2026-04-21

## 背景

`TutorWorkflowController.activeRuns`、`TutorWorkflowWebSocketHandler.sessionMap` /
`runPollers` 目前都是 JVM 内 `ConcurrentHashMap`。tutor_graph 侧的
`_run_events` / `_active_runs` 也是模块级 dict，被硬编码 `--workers 1`。

结果：**Java 副本数 > 1 会立刻不可用**。用户 WS 连到 instance A、POST /runs 路由到
B，B 开的 poller 无法把事件推给 A 的 WS。

## 约束

- 生产扩缩容由 k8s HPA 驱动，不能依赖单副本
- 不引入 Kafka / Pulsar 级别的重型中间件（团队运维能力有限）
- 必须在境内 region；不可使用需要备案的 SaaS
- 兼容现有 Redis 部署（阿里云云数据库 Redis / Redis Cluster 5.0+）

## 选项分析

| 方案 | 优 | 劣 |
|------|----|----|
| Nginx sticky session | 改动极小 | 单 pod 满时无法弹出；滚动更新抖动；长连接占用 hot pod |
| **Redis Pub/Sub + Hash** | 运维熟悉；单 Redis 足以；与 Session-Data-Redis 复用 | 需要 serialize / deserialize runtime_event；Redis 本身是单点 |
| Kafka topics | 水平能力最强 | 新中间件；与当前 infra 不匹配 |

## 决策（待执行）

采用 **Redis Pub/Sub + 分布式登记**：

- `activeRuns` → Redis Hash `atwf:active-runs:{sessionId}` = `runId`
- `sessionMap` 只在本 JVM 保留 open 连接的 session；开 WS 时 `SUBSCRIBE atwf:events:{sessionId}`
- `runPollers` → 只有**一个** JVM 对同一 run 开 poller，用 `SET NX EX` 抢占 key `atwf:poll-leader:{runId}` 决定负责方
- poller 产出的 runtime_event 通过 `PUBLISH atwf:events:{sessionId}` 广播；所有监听该 session 的 JVM 转发给本地 WS

tutor_graph 侧暂保持单 worker；远期通过 Kafka / Redis Streams 打通，
让事件成为"发布-订阅"而不是"Java 来轮询"。

### 里程碑

1. Redis schema + 抽象服务 `SessionRuntimeRegistry`（单测可 mock）
2. 把 `activeRuns` 读写迁到 registry，保留内存回退作为一层缓存
3. WS handler 订阅 session channel，并广播到本地
4. `runPollers` 的 `SET NX` 领主选举 + fencing token
5. Nginx / k8s Ingress 开启 sticky session（保险兜底）
6. 移除本地 `sessionMap` / `runPollers` 中无意义的单机状态

## 后果

**正面**
- Java 副本数可扩，K8s HPA 可用
- 和现有 Spring Session Redis 复用同一连接池，不引入新中间件
- poller 领主选举让重复订阅天然消失（M1 的本地修复可简化）

**负面**
- Redis 成为关键依赖；需要 Redis Sentinel / Cluster HA
- 每个 runtime_event 多一跳序列化 / 发布
- 调试复杂度上升（事件来源散落多进程）

## 后续

本 ADR 状态保留为 **Proposed**；真正落地时拆成独立 PR，附带容量压测数据后
再升级为 Accepted。
