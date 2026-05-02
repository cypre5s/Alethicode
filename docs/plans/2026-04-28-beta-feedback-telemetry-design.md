# 小范围公测数据与 Bug 反馈方案（设计稿）

> 编号：ALETH-PLAN-2026-0428-BETA-FEEDBACK-TELEMETRY，v1.0  
> 状态：已落地（参见 `CHANGELOG.md` 2026-04-28 公测反馈与遥测系统部分）  
> 配套迁移：`backend/src/main/resources/db/migration/V74__beta_feedback_and_telemetry.sql`  
> 依赖前置：SMTP 465 修复（参见 `backend/src/main/java/com/alethicode/service/impl/JavaMailSmtpMailService.java`）

## 0. 背景与既定事实

**用户与公测目标：**

- 公测对象：某 Python 课程的零基础学生，约 100 人。
- SMTP 发件邮箱：`m1821726156@163.com`（授权码 `NQbKJNhj4YxVcVpV`，服务器 `smtp.163.com:465 SSL`）。
- Bug 通知收件邮箱：`1822250281@qq.com`。
- 问卷星补充入口：`https://v.wjx.cn/vm/mvsfyTf.aspx`（标题《Alethicode 平台 Python 学习体验调研》，18 题）。

**SMTP 既定事实（前置任务已完成）：**

1. 数据库 `sys_options.smtp_config` 已写入完整配置（`server / port=465 / email / password / tls=true`）。
2. `JavaMailSmtpMailService.send` 已修复：当 `port == 465 && tls=true` 走 `smtps` 协议（直接 SSL），显式 `mail.smtps.ssl.trust = <server>`、`mail.smtps.ssl.protocols = "TLSv1.2 TLSv1.3"`、connect/read/write 三超时；587 STARTTLS 路径同时补齐 `starttls.required + ssl.protocols + ssl.trust`。

**SMTP 已知限制：**

- 本地开发机外网 IP 是境外（日本 OPTAGE / 台湾 Akari Networks 等），163/QQ 邮箱对境外 IP 做 fingerprint 风控，导致 Java 21 SSL 握手在 ServerHello/EncryptedExtensions 之后被静默 reset。**本地无法直接发邮件。**
- openssl `s_client -connect smtp.163.com:465` 在同一 IP 上**可以成功握手**并交互 SMTP——证明网络层、证书均正常，区别在 Java 21 默认 ClientHello 的 cipher/extension 指纹被反垃圾系统识别。
- **生产部署到中国大陆 IP 时，163 SMTP 直接可用**，无需额外修改。
- 部署 checklist：
  1. 在中国大陆 ECS / 物理机上部署后，`curl -X POST /api/admin/smtp-test -d '{"email":"1822250281@qq.com"}'` 应在 10 秒内返回 `{"error":null,"data":"success"}`。
  2. 如果境外部署，必须改用 SMTP relay（SendGrid / Resend / 阿里云邮件推送 SDK）替换 `JavaMailSmtpMailService`，或将通知降级为站内列表查看。

## 1. 整体架构

```mermaid
flowchart TD
    student[学生浏览 OJ] --> feedbackButton[右下角反馈按钮]
    feedbackButton --> reportDialog[三步式上报弹窗]
    reportDialog --> betaApi[POST /api/beta/feedback-reports]
    reportDialog --> wjxLink[问卷星补充链接]
    betaApi --> feedbackDb[beta_feedback_report]
    betaApi --> attachDb[beta_feedback_attachment]
    betaApi --> privateShot[backend/upload/../beta-screenshots/]
    betaApi --> mailNotify[SMTP 163 -> 1822250281@qq.com]
    student --> telemetry[前端遥测]
    telemetry --> telemetryDb[beta_telemetry_event]
    admin[管理员] --> adminUI[/admin/beta-feedback]
    adminUI --> feedbackDb
```

## 2. 关键决策（Decisions）

### D1 数据表与既有事件正交

`beta_feedback_report` / `beta_feedback_attachment` / `beta_telemetry_event` 三张全新表，**不**复用 `ai_learning_event` / `ai_tutor_workflow_event` / `ai_feedback_label`。后者承载学习行为与 AI 干预语义，结构稳定且有强一致性需求；前者承载产品体验与 Bug 上报，schema 会随公测迭代变动。物理隔离避免互相污染，同时让 RAG / 学情画像等下游不会误把 Bug 反馈当成学习信号。

### D2 截图私有化存储

截图统一落到 `<alethicode.system.uploadDir>/../beta-screenshots/<yyyy-MM>/<random10>.<ext>`，**不**挂在 `/public/upload`，因此学生端的静态资源映射看不到这些文件。读取走 `/api/admin/beta/feedback-reports/{reportId}/screenshots/{attachmentId}`，由 `AdminBetaFeedbackController` 校验 admin session 后通过 `Files.readAllBytes` 流式返回。`storage_path` 写绝对路径，避免相对路径在多实例部署下歧义。

### D3 邮件 fail-soft

学生提交反馈是主流程，邮件通知是辅流程。任何 SMTP 异常都不应该阻塞学生看到「已收到」。`BetaFeedbackMailNotifier` 是独立 `@Component`，承载 `@Async` 方法以保证 Spring 代理生效（自调用绕开代理是经典坑）；catch `RuntimeException` 后把 `mail_status='failed'` 与 `mail_error=<msg 前 500 字>` 写回主表，绝不上抛。`enabled=false` 时彻底跳过，`mail_status='disabled'`——便于境外部署时整体关闭邮件而保留站内查阅链路。

### D4 隐私版本契约

`sys_options.beta_feedback_config.privacy_notice_version`（默认 `"2026-04-28-v1"`）是「服务端与客户端必须达成一致的协议版本」。客户端登录后第一次进入，比对 `localStorage.betaPrivacyVersion` 与服务端版本：不匹配则强制弹窗，按下「同意并开始使用」才写 localStorage；反之则按「暂不使用平台」走 `router.replace('logout')`。提交反馈时 `privacyNoticeVersion` 必须等于服务端版本（后端 422 fail-fast），保证学生提交时的同意态可追溯。版本字段升级 → 全员重新弹窗。

### D5 三步无术语表单

零基础学生不会回答「请描述 Bug 复现步骤、是否有 console error、相关接口返回码」。表单按 **类型 → 严重度 → 详情** 三步走，每步都用学生原话：

- 类型 7 选 1：「打不开 / 进不去」「按钮点了没反应」「页面看不懂」「题目或答案好像有错」「AI 讲得不清楚」「提交代码后结果不对」「其他」。
- 严重度 4 选 1：「完全不能用了」「还能继续但很烦」「只是有点不舒服」「我只是想提个建议」。
- 详情：自由文本（≤2000 字，counter）+ 可选截图（≤3 张，单张 ≤5 MB，PNG/JPEG/WEBP），提示语「Windows 按 Win+Shift+S，Mac 按 Command+Shift+4」。

成功页面立刻闭环——「已收到，老师会看到这条反馈」+「继续填写更详细问卷（可选）」按钮。问卷星 URL 自动追加 `?source=alethicode&report_id=<id>`，老师后续可以把站内反馈与问卷答案对齐。

### D6 遥测白名单与 fail-silent

前端遥测客户端是**严格白名单**（七元枚举：`page_view / feature_click / frontend_error / api_error / web_vital / feedback_opened / feedback_submitted`），未列入的事件 silent drop。`recordEvent` 的 payload 经 `sanitizePayload` 处理：字符串硬剪 500 字、对象走 `JSON.parse(JSON.stringify(...))` 深拷贝防循环引用、函数与 Symbol 直接丢弃。`reportApiError` 把 message 进一步剪到 200 字，避免 stack trace 泄露。所有上报失败 silent，绝不让遥测影响学生感知。

### D7 安全基线一次性硬化

公测对象 100 人共用一个教室出口 IP 是常见场景，原 `RateLimitFilter` 取 `X-Forwarded-For` 最右侧，会被「反代 trusted IP」毒化（伪造 trusted IP 在右侧 → 全班共用一个限流桶 → 整班被 429）。新逻辑：**从右往左跳过 trusted CIDR**（默认 `127.0.0.1/32, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16`），取第一个不可信 IP，等价于「我相信我的反代追加的链路，不相信公网客户端伪造的 XFF」。CSP 同时去掉 `unsafe-eval`（已确认无业务 `eval`），multipart 全局收紧到 32 MB，submission list 默认私有，prod profile 强制 HTTPS。

## 3. API 契约

### 学生侧

| 方法 | 路径 | 鉴权 | 行为 |
|---|---|---|---|
| `POST` | `/api/beta/feedback-reports` | 必须登录 | `multipart/form-data`，`data` part 是 JSON 反馈，`screenshots` part 是 0~3 个 MultipartFile。返回 `{error:null, data:{id: <bigint>}}`。校验失败 → 422，未登录 → 401。 |
| `POST` | `/api/beta/telemetry/events` | 必须登录 | `{events: [...]}` 批量遥测，silent 不弹错误，返回 `{created: n}`。 |
| `POST` | `/api/beta/telemetry/web-vitals` | 必须登录 | 单条 Web Vital，silent。 |

### 管理员侧

| 方法 | 路径 | 鉴权 | 行为 |
|---|---|---|---|
| `GET` | `/api/admin/beta/feedback-reports` | `hasRole('ADMIN')` | 分页 + 状态/严重度/类型筛选，返回 `{items, total, offset, limit}`。 |
| `GET` | `/api/admin/beta/feedback-reports/{id}` | `hasRole('ADMIN')` | 反馈详情，含附件元数据。 |
| `PATCH` | `/api/admin/beta/feedback-reports/{id}` | `hasRole('ADMIN')` | `{status: <新状态>}`。`resolved` / `wontfix` 自动写 `resolved_at = NOW()`。 |
| `GET` | `/api/admin/beta/feedback-reports/{reportId}/screenshots/{attachmentId}` | `hasRole('ADMIN')` | 流式截图，`Content-Disposition: inline; filename=<原名>`。 |

## 4. 部署与上线 Checklist

完成全部任务后，按下列顺序验收：

1. `mvn -pl backend test -Dtest='*BetaFeedback*' && cd frontend && npx jest beta-`：所有契约测试绿。
2. `./start.sh` 或 `mvn spring-boot:run`，登录 `root` / `root123456`：
   - 右下角看到反馈按钮。
   - 提交一条带截图反馈，admin 后台立即可见。
3. **生产 IP 部署后**单独跑：
   - `curl -X POST /api/admin/smtp-test -d '{"email":"1822250281@qq.com"}'` 应在 10 秒内 `{error:null, data:"success"}`，邮箱真的收到测试邮件。
   - 提交一条反馈，[1822250281@qq.com](mailto:1822250281@qq.com) 收到摘要邮件。
4. 用 1 名计算机零基础学生（或扮演者）现场试填站内反馈和问卷星，能独立完成。
5. **公测前最后一步**：导出 `beta_telemetry_event` 第一天数据，确认有 `page_view` / `feature_click` / `web_vital` 事件，采集链路活着。

## 5. 关键约束（不可违反）

- **SMTP 部署位置**：本设计的 SMTP 通知**必须**在中国大陆 IP 的服务器上跑。境外 IP 部署时把 `BetaFeedbackMailNotifier.notifyAsync` 整个跳过（`mail_status='disabled'`），改为运营每日导出 admin 列表查看。
- **截图私有性**：`beta-screenshots/` 目录不能挂在 `/public/upload`，必须只能通过 `/api/admin/beta/feedback-reports/{id}/screenshots/{attId}` 走 admin session 访问。
- **隐私采集禁区**：`betaTelemetry.recordEvent` 严禁记录代码全文、聊天对话全文、密码、token；前端错误只记摘要 message（最多 500 字），绝不放整 stack。
- **失败原则**：邮件失败不阻塞提交；遥测上报失败 silent；DB 写入失败 fail-fast 给学生明确错误。

## 6. 风险与未验证项

| 风险 | 缓解 |
|---|---|
| 生产 IP 仍被 163 风控 | 备用方案：换 SMTP relay（SendGrid / Resend / 阿里云邮件推送）替换 `JavaMailSmtpMailService`；保留 `mail_status` 字段方便切换 |
| 学生绕过隐私同意 | 后端在 `BetaFeedbackServiceImpl.createReport` 校验 `privacyNoticeVersion` 必须等于 `sys_options.beta_feedback_config.privacy_notice_version`，否则 422 |
| 截图被滥用上传敏感图 | 5 MB 上限 + admin 可手动删除 + 14 天后由后台脚本批量删除（本期不实现，下期补） |
| 问卷星问卷被关闭 | `beta_feedback_config.wjx_url` 可在后台改；前端 success view 也允许跳过 |
| 教室共用 IP 触发限流 | SEC-1 trusted-proxy CIDR 跳过逻辑保证「公网真实 IP」是限流键，不会因为反代或学生伪造 XFF 把全班绑到一桶 |

## 7. 假设

- 公测学生使用账号登录，所有反馈和遥测绑定 `user_id`；匿名页面（登录前）只采 Web Vitals 不带 `user_id`。
- `JavaMailSmtpMailService.send` 已在前置会话改完，本设计不再重写该文件。
- 不修改现有 `ai_learning_event` / `ai_tutor_workflow_event` / `ai_feedback_label`，学习行为类事件继续走原有写入路径。
- 100 人规模下 `beta_telemetry_event` 单日 ≤ 50K 行可承载（粗算 100 人 × 50 事件/天 × 10 倍冗余 = 5 万），不需要分区或归档。下次扩容到千人级时引入按月分区。
