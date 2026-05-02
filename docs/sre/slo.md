# Alethicode SLO / SLI

> 首版：2026-04-21。季度评审一次；error budget 每月重置。

## 为什么要定 SLO

我们要把"服务健康"从"主观的 500 率"升级到可执行的量化目标：
- 每个服务有明确的**用户可感知延迟 / 错误率**
- 每月有固定的**错误预算**
- 预算告急 → 停止上线新功能，优先修复

## 服务级 SLO

### S1. Java Backend（`/api/**` 公开接口）

| SLI | Target | 窗口 |
|-----|--------|------|
| 可用性（HTTP 2xx+3xx / 总请求） | ≥ 99.5% | 30 天 |
| 延迟 p95 | ≤ 400 ms | 30 天 |
| 延迟 p99 | ≤ 1.5 s | 30 天 |

**Error budget**：30 天内可容许 0.5% 请求失败 ≈ 3.6 小时完全故障时间等价。

### S2. Tutor Workflow API（`/api/ai/tutor-workflow-sessions/**`）

| SLI | Target | 窗口 |
|-----|--------|------|
| 可用性 | ≥ 99.0% | 30 天 |
| `createRun` p95（不含 LLM 生成） | ≤ 600 ms | 30 天 |
| `runtime_event` 从 tutor-graph 产生到推到 WS 的延迟 p95 | ≤ 1.5 s | 30 天 |

### S3. tutor_graph 服务

| SLI | Target | 窗口 |
|-----|--------|------|
| `/health` 可用性 | ≥ 99.5% | 30 天 |
| LangGraph run 完成率（非 FAILED） | ≥ 95% | 30 天 |
| 节点执行 p95（不含 LLM 生成） | ≤ 800 ms | 30 天 |

### S4. LLM Provider Integration

| SLI | Target | 窗口 |
|-----|--------|------|
| `AiModelGateway.callForJson` 成功率 | ≥ 97% | 7 天 |
| `AiModelGateway.callForEmbedding` 成功率 | ≥ 99% | 7 天 |
| `AiCircuitBreaker` 开启次数 / 小时 | ≤ 2 | 7 天 |

## 告警规则（Prometheus）

参见 [`deploy/observability/prometheus/alerts.yml`](../../deploy/observability/prometheus/alerts.yml)。

告警触发 → 企业微信 / 钉钉 bot + PagerDuty（国内可替换为飞书 / 短信网关）。

## 错误预算燃烧率策略

- 1 小时内烧掉 2% 预算 → **critical** 立即呼叫 on-call
- 6 小时内烧掉 5% 预算 → **warning** 值班关注
- 3 天内烧掉 10% 预算 → **warning** 迭代规划时优先修复

参考文献：[Google SRE Book - Alerting on SLOs](https://sre.google/workbook/alerting-on-slos/)
