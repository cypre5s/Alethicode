# ADR-0006: 韧性工程（Resilience Engineering）落地

- **Status**: Accepted
- **Date**: 2026-04-21
- **Authors**: Alethicode core team

## 背景

ADR-0001/0002/0003 打通了 AI Runtime + 合规基座后，下一个瓶颈是**系统在故障下的
行为**：LLM 超时、tutor_graph 崩溃、DB 抖动、突发班级开课。我们需要把"韧性"作为
一个显式工程维度而非散落的 try/catch。

## 决策

采用 **Resilience4j + Micrometer + Health probes + k6 + ChaosBlade** 五位一体的方案。

### 1. 四大韧性模式在每个外部依赖上完整落地

| 依赖 | CircuitBreaker | Retry | Bulkhead | TimeLimiter |
|------|---------------|-------|----------|-------------|
| tutor-graph (WebClient) | ✅ `tutorGraph` | ✅ | ✅ max=100 | ✅ 30s |
| LLM provider (AI gateway) | ✅ `llmProvider` | ✅ 2 次 | ✅ max=30 | - |
| Judge Server | ✅ `judgeServer` | ✅ | ✅ max=40 | ✅ 60s |
| Postgres (Hikari) | 连接池 | - | 连接池上限 | `statement_timeout=30s` |
| Redis (Lettuce) | - | - | - | `timeout: 500ms` |

### 2. 多 LLM provider 自动切换

`FailoverAiModelGateway` 装饰 `CachingAiModelGateway`：检测到 recoverable error 时
按 `fallbackPrefixes` 顺序切换到 `INIT_LLM_` / `BACKUP_LLM_` 等配置前缀。
Schema / guard / idempotency 错误不触发 failover（这些换 provider 也会失败）。

### 3. 多级缓存（Caffeine L1）

`MultiTierCacheConfig` 定义 5 个业务缓存：`problemAccess` / `sessionOwnership` /
`learnerState` / `courseware` / `aiProviderConfig`。
Caffeine `recordStats()` 接 Micrometer，命中率可视化。
短 TTL + null-caching 双保险缓解"缓存穿透"；`expireAfterAccess` 大于 `expireAfterWrite`
避免"缓存雪崩"。

### 4. 分层健康检查 + 外部依赖探针

- `/actuator/health/liveness` 只检查 JVM 本身
- `/actuator/health/readiness` 包含 `db` / `redis` / `tutorGraph`（后者来自新增的
  `ExternalDependencyHealthConfig` 的 `tutorGraphHealthIndicator`）
- 任一关键外部依赖 DOWN，k8s / Ingress 自动摘流

### 5. Prompt 安全

`PromptSafetyFilter.sanitize` 在所有 user-controlled 文本进入 system prompt 前：
1) 长度截断 8000 字符；2) 21 个常见 jailbreak marker 替换为 `[redacted]`；
3) 三连反引号 + XML role tag 转为无害字符。
真正的内容安全仍然依靠 `AigcComplianceService.scanForSensitiveContent` 接入阿里云 / 腾讯云。

### 6. 部署侧韧性

- K8s Deployment：`maxSurge=1 / maxUnavailable=0` 滚动；`terminationGracePeriodSeconds=45`
- `preStop` 15s LB 摘流
- `startupProbe` 90s 窗口容忍 Flyway 启动
- `PodDisruptionBudget: minAvailable=2`
- `HPA`：CPU 65% / Mem 75% 触发，单次扩容 100% 或 4 pods，缩容 300s 稳定窗口

### 7. 压测 + 混沌

- `deploy/loadtest/k6/` 三个场景（班级突发 / WA 高峰 / 30 分钟混合）
- `deploy/chaos/` 7 个 ChaosBlade 场景（tutor-graph 宕机 / 延迟、Postgres 切主、
  Redis 断连、LLM 超时、CPU 打满、节点 drain）
- 演练节奏：周 / 月 / 季 / 年

## 后果

**正面**
- 每类故障有明确的探测 → 熔断 → 降级 → 恢复路径
- 多 LLM provider 冗余；成本可控（Failover 仅触发一次重试）
- 运维有 k6 + Chaos 两套自动化验证手段
- Caffeine L1 缓存显著减少 DB 热查询

**负面 / 绑定**
- 增加 4 个依赖：`resilience4j-*` / `caffeine` / `spring-boot-starter-cache` / Logstash encoder
- K8s 部署要求 1.28+（`autoscaling/v2`、`PodDisruptionBudget v1`）
- FailoverAiModelGateway 不是默认注册 bean；集成时要显式 wire（避免干扰 admin
  validation 的确定性路径）

## 后续

- 压测 → 容量规划 → HPA 阈值校准（T + 30 天）
- 把 ADR-0005 的 Redis 共享状态并入 `MultiTierCacheConfig` 作为 L2
- 压测结果回写 `docs/sre/slo.md`（当前 SLO 是猜的，压测后有数据）
