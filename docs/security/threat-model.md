# Alethicode 威胁模型 (STRIDE)

> 首版：2026-04-21；每次关键架构调整或 ADR 新增时更新。

本文件使用 Microsoft STRIDE 框架对 Alethicode 主要数据流做威胁建模，配合
[ADR-0003](../adr/0003-ai-runtime-integration-handoff.md) 和
[ADR-0004](../adr/0004-mainland-china-compliance.md) 提到的控制措施一起阅读。

## 数据流视图

```
              +----------------------+
              |  Student / Teacher   |
              | Browser (Vue + WS)   |
              +----------+-----------+
                         | HTTPS + CSRF token + session cookie
                         v
              +----------------------+      +-----------------+
              |  Java Backend        |<-----|  Judge Server   |
              |  (Spring Boot 3.5)   |      | (Docker)        |
              +----------+-----------+      +-----------------+
          REST/WS |      | Internal HTTP + X-Internal-Service-Key
                  |      v
                  |   +----------------------+
                  |   |  tutor_graph         |
                  |   |  (FastAPI + LangGraph)|
                  |   +----------+-----------+
                  |              |
                  +------+-------+
                         |
                         v
                  +----------------------+
                  | PostgreSQL (pgvector)|
                  |  + LangGraph state   |
                  |  + Alethicode main   |
                  +----------------------+
                         |
                         v
                  +----------------------+
                  | LLM provider         |
                  | (MiniMax / 通义 / ...)|
                  +----------------------+
```

## 资产分级

| 资产 | 分级 | 说明 |
|------|------|------|
| 学生账号 + 密码 hash | PII 敏感 | 登录、找回 |
| 学生代码 / 提交 | PII 一般 | 私有题 / 班级作业属个人数据 |
| 学情画像（mastery / memory） | PII 敏感 | 可推断学习能力 |
| AI Tutor 会话记录 | PII 一般 | 与学生学习行为关联 |
| INTERNAL_SERVICE_KEY | 机密 | 泄露即 internal API 开洞 |
| LLM API key | 机密 | 泄露即账单耗尽 |
| CSRF token / session cookie | 机密 | 会话劫持 |
| 讲义 PPT / 语言包 | 一般 | 教师侧敏感 |

## STRIDE 分析

### S — Spoofing（身份伪造）

| 威胁 | 控制 |
|------|------|
| 伪造 student session cookie | `SessionAuthenticationFilter` + Redis Session store + `SameSite=Lax` |
| 伪造 WebSocket Origin 绕 CSRF | `WebSocketOriginConfigurer` 白名单 + `ClassroomHandshakeInterceptor` 登录校验 |
| 伪造 `X-Internal-Service-Key` | `MessageDigest.isEqual` 常量时间比较；长度 ≥ 24；`InternalServiceKeyValidator` 阻止弱默认 |
| 伪造 Authentication principal | `TutorWorkflowController.extractUserId` 按 `Authentication.getDetails()` 优先，不相信任意 Map principal |

### T — Tampering（数据篡改）

| 威胁 | 控制 |
|------|------|
| 修改他人 tutor workflow session | `TutorWorkflowAuthorizer` + `isSessionOwnedByUser` + WS ownership |
| 修改他人提交的判题结果 | 主要靠现有 `SubmissionServiceImpl` 校验；`assertSubmissionBelongsTo` 在 tutor workflow 入口二次校验 |
| 篡改 `ai_tutor_side_effect_log` 幂等 | `request_hash` 校验，不同 hash 返回 409 |
| 请求体过大导致堆攻击 | 256 KiB 上限 + 403 |

### R — Repudiation（抵赖）

| 威胁 | 控制 |
|------|------|
| 学生否认 AI 生成内容是自己要求的 | `aigc_audit_log`（6 个月）+ `content_tagged` 字段 |
| 管理员否认访问过学生 PII | `pii_access_log`（5 年）只 append |
| 学生否认请求删除 | `pii_deletion_request` 记录 requested_at + reason |

### I — Information Disclosure（信息泄露）

| 威胁 | 控制 |
|------|------|
| 错误响应泄露 stack trace / 内部路径 | `TutorWorkflowController.fail503Redacted` + `GlobalRestExceptionHandler` 统一脱敏；`InternalAITutorToolController` 通用异常回 "internal error" |
| 日志泄露 prompt / completion | `application.yml: spring.ai.chat.observations.log-prompt=false`；`AigcComplianceService` 仅存 hash + 500 字符 preview |
| 跨用户 WS 订阅 | `afterConnectionEstablished` 的 ownership check |
| LLM 提示注入泄露系统 prompt | 节点级系统 prompt 模板 + 输入 sanitize（待接入内容安全） |

### D — Denial of Service

| 威胁 | 控制 |
|------|------|
| 单用户刷 `/api/ai/tutor-workflow-sessions/*` | Resilience4j `tutorWorkflow` 限流 20 req/s/instance，超出 429 + Retry-After |
| 请求巨大 body | 256 KiB 上限 |
| tutor-graph hang 导致 Java 资源枯竭 | WebClient `connectTimeout=5s`; 调用层 `.block(Duration.ofSeconds(10/30))` |
| 同 run 多次 subscribe 刷 CPU | `runPollers` 主动 interrupt 旧 poller |
| AI run 无穷等待 | `MAX_RUN_DURATION=10min` + Python `INTERRUPT_TIMEOUT_SECONDS=1800` |

### E — Elevation of Privilege

| 威胁 | 控制 |
|------|------|
| 普通学生访问 admin API | Spring Security `@PreAuthorize`（现有） |
| 借 internal API 绕过学生 ownership | `InternalAITutorToolServiceImpl` 二次校验 `subUserId == userId` & `subProblemId == problemId` |
| 教师访问他班学生数据 | `ClassroomAuthorization*Service`（现有），WS 新增 ownership |

## 未解项 / 后续

- [ ] 内容安全接入（`AigcComplianceService.scanForSensitiveContent`）
- [ ] Java ↔ tutor_graph mTLS（目前依赖内网 + service key）
- [ ] Redis 共享状态 ADR-0005 落地，防止单副本 HPA 故障
- [ ] LLMOps 层打通 prompt 版本化 + eval pipeline
- [ ] 红队渗透测试（建议季度一次）
