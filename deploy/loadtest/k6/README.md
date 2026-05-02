# Alethicode k6 压测脚本

## 三个场景

| 脚本 | 场景 | 持续时间 | 关注指标 |
|------|------|---------|---------|
| `tutor-workflow-burst.js` | 单班 50 人同时开课 | ~2 min | createSession / createRun p95 |
| `submission-surge.js` | WA 提交高峰 | 3 min | ERROR_FEEDBACK 热路径 + 429 率 |
| `mixed-workload.js` | 混合长稳态 | 30 min | 内存 / 连接 / CircuitBreaker 稳定性 |

## 前置条件

1. 部署目标环境至少有一个 **真实登录 session cookie** 和 **CSRF token**
2. 已预先插入测试 problem / submission 数据（避免触发业务校验 404）
3. k6 版本 ≥ 0.48.0

## 国内用户安装 k6

```bash
# 阿里云镜像下载 (推荐)
wget https://mirrors.aliyun.com/k6/releases/v0.48.0/k6-v0.48.0-linux-amd64.tar.gz
tar xzf k6-v0.48.0-linux-amd64.tar.gz
sudo mv k6-v0.48.0-linux-amd64/k6 /usr/local/bin/

# 或使用 docker
docker run --rm -i --network host \
  -v "$PWD":/scripts -w /scripts \
  grafana/k6:0.48.0 \
  run tutor-workflow-burst.js
```

## 运行示例

```bash
# 场景 1：班级同开
k6 run \
  -e BASE_URL=https://alethicode-staging.example.cn \
  -e CSRF_TOKEN=$(cat /tmp/csrf.txt) \
  -e SESSION_COOKIE=$(cat /tmp/session.txt) \
  -e PROBLEM_IDS=1001,1002,1003 \
  deploy/loadtest/k6/tutor-workflow-burst.js

# 场景 2：WA 高峰
k6 run \
  -e BASE_URL=https://alethicode-staging.example.cn \
  -e CSRF_TOKEN=$(cat /tmp/csrf.txt) \
  -e SESSION_COOKIE=$(cat /tmp/session.txt) \
  -e SESSION_IDS=twf_abc,twf_def \
  -e SUBMISSION_IDS=sub_1,sub_2 \
  deploy/loadtest/k6/submission-surge.js

# 场景 3：30 分钟混合
k6 run --duration=30m --vus=80 \
  -e BASE_URL=... -e WS_URL=... -e CSRF_TOKEN=... -e SESSION_COOKIE=... \
  deploy/loadtest/k6/mixed-workload.js
```

## 结果观察

推荐搭配 Grafana Cloud k6 或自建 InfluxDB + Grafana。每次压测都保留：

1. k6 标准输出（含 p95 / p99 / success rate）
2. Prometheus 对应时间窗的 `http_server_requests_seconds` 面板截图
3. `ai_circuit_breaker_state` 是否开启过
4. JVM `jvm_memory_used_bytes` 是否稳定（不是单调增长）
5. Postgres `pg_stat_activity` 峰值连接数是否 < 池大小

## 压测失败的常见归因

| 症状 | 归因 |
|------|------|
| 429 率 > 5% | `tutorWorkflow` 限流过严，或 stage 流量过高 |
| p95 > threshold 且 CPU 满 | 容量不足，需扩副本或升配 |
| p95 > threshold 但 CPU 闲 | 外部依赖慢（LLM / DB），检查 circuit breaker |
| 5xx 率突增 | 检查 `fail503Redacted` 对应的 WARN 日志 + 下游真实错误 |
| JVM 内存单调上升 | runPollers 或 Caffeine TTL 问题 |
