# Alethicode 混沌演练

使用 **ChaosBlade**（阿里巴巴开源，国产化首选）实现 Alethicode 的韧性演练。目标是
主动验证 ADR-0003 里定义的容错行为在真实故障注入下能按预期工作。

## 运行环境

- Kubernetes 1.28+
- ChaosBlade Operator >= 1.7.4（阿里云 ACK 有托管 `ack-chaosblade-operator`）
- 独立 namespace `alethicode-chaos`，避免和生产 namespace 混淆

国内安装：

```bash
# helm repo 走阿里云
helm repo add chaosblade-io https://aliyuncs.chaosblade.io/helm
helm repo update
helm install chaosblade-operator chaosblade-io/chaosblade-operator \
  --namespace chaosblade --create-namespace
```

## 场景矩阵

每个场景有 3 个要素：**注入 → 预期 → 回滚**。

### S1. tutor-graph 整体宕机
- 演练：`kubectl delete pod -l app.kubernetes.io/name=tutor-graph`
- 预期：
  - `/actuator/health/readiness` 30s 内从 UP 转 DOWN（由 `tutorGraphHealthIndicator` 主导）
  - Ingress 将这个副本从 upstream 摘出
  - 现有 tutor workflow 请求落到 `fail503Redacted` → 429/503，而非 500
  - 恢复后新请求 60s 内可用（Resilience4j circuit breaker 走半开测试）
- 回滚：`kubectl rollout restart deployment/alethicode-tutor-graph`

### S2. tutor-graph 持续高延迟
- 注入：ChaosBlade `network delay`
  ```yaml
  apiVersion: chaosblade.io/v1alpha1
  kind: ChaosBlade
  metadata:
    name: tutor-graph-latency
  spec:
    experiments:
      - scope: pod
        target: network
        action: delay
        matchers:
          - name: labels
            value: ['app.kubernetes.io/name=tutor-graph']
          - name: time
            value: ['5000']    # 5s 延迟
          - name: offset
            value: ['1000']
          - name: interface
            value: ['eth0']
  ```
- 预期：
  - Java 侧 `TutorGraphClient` 的 `@CircuitBreaker(name="tutorGraph")` 在 slow-call 超过阈值后开启
  - 用户看到 "tutor-graph service temporarily unavailable"
  - `ai_circuit_breaker_state{instance="tutorGraph", state="OPEN"} == 1`
- 回滚：`kubectl delete chaosblade tutor-graph-latency`

### S3. Postgres 主实例宕机
- 注入：直接 stop 主库（阿里云 RDS 可以用"模拟主备切换"API）
- 预期：
  - HikariCP 连接池 3s 内失败
  - `/actuator/health/readiness` 失败（Spring Boot auto-config 里 DB 是 readiness 一部分）
  - Pod 被摘出 upstream；新请求 502；在飞事务超时
  - 30-60s 内主备切换完成，应用自动恢复
- 回滚：自动切主后服务恢复

### S4. Redis 断连
- 注入：ChaosBlade `network drop` 针对 Redis IP
- 预期：
  - Spring Session Redis 进入 fallback（如果配置）或 session 失效
  - Lettuce 在 2s 连接超时后抛 `RedisConnectionFailureException`
  - 不应级联打垮 HTTP 请求（除非请求本身依赖 Redis）
- 回滚：`kubectl delete chaosblade redis-disconnect`

### S5. LLM Provider API 超时
- 注入：在测试环境把 `LLM_BASE_URL` 指向一个返回 5s 延迟的 stub
- 预期：
  - Spring AI WebClient / HttpClient 超时触发
  - `AiCircuitBreaker` 5 次失败后开启
  - `FailoverAiModelGateway` 切到 `INIT_LLM_` 前缀配置的备用 provider
  - 用户看到 "AI 服务稍后重试"
- 回滚：恢复 `LLM_BASE_URL`

### S6. CPU / 内存压满
- 注入：`kubectl chaosblade burncpu --cpu-percent 95 --timeout 120`
- 预期：
  - HPA 扩容新副本
  - 请求排队但不失败
  - p99 延迟上升但 < SLO 上限 × 2
- 回滚：自动超时停止

### S7. Pod 被 Evict（节点维护）
- 注入：`kubectl drain <node> --ignore-daemonsets`
- 预期：
  - `terminationGracePeriodSeconds: 45` 让 Spring Boot `server.shutdown=graceful` 消化在飞请求
  - preStop hook 让 LB 先摘流
  - `PodDisruptionBudget` 阻止同时 evict 2+ 副本
  - 零 5xx

## 演练节奏

- **周级**：S1 / S4（开发团队自己跑）
- **月级**：S2 / S5（SRE 主导）
- **季级**：S3 / S6 / S7（正式 DR 演练，产品/教研协同）
- **年级**：端到端完整场景（全链路同时注入多点）

## 每次演练后必须产出

1. 结果报告（进 `docs/sre/chaos-reports/`，命名 `YYYY-MM-DD-scenario-X.md`）
2. 违反 SLO 或行为与预期不符的问题：开 issue + ADR
3. 修复验证：复现演练并通过
