# 重置密码邮件链路补全设计

> 日期：2026-05-03
> 范围：仅后端 `applyResetPassword` 主路径补全；不涉及注册、change-email、change-password、POP3
> 决策依据：用户在 2026-05-03 的对话中确认了"最小完整 + 安全增强 + 仅复用现有 SMTP + 不引入 POP3"四个边界

## 1. 背景

`backend/src/main/java/com/alethicode/service/account/impl/AccountServiceImpl.java` 的 `applyResetPassword` 当前流程：校验 captcha → 找用户 → 写 `reset_password_token` + `reset_password_token_expire_time` → 直接 return success。**根本未调用任何邮件服务**——前端 `ApplyResetPassword.vue` 上 toast `'重置邮件已发送！'` 是空头支票，用户拿不到 token，`/reset-password/:token` 链接无来源，整条找回密码链路实质断裂。

仓库内 SMTP 出站能力（`JavaMailSmtpMailService` + `sys_options.smtp_config` + `SmtpMailService` 接口）已成熟，且已被 admin `testSmtp` 与 `BetaFeedbackMailNotifier` 复用。本次工作是把 `applyResetPassword` 接进同一条 SMTP 链路，并在此过程中修补一个明显的安全缺陷：当前在邮箱不存在时报 `User does not exist`，泄露了用户存在性，可被攻击者用作枚举接口。

仓库不存在 POP3 / IMAP 接收能力，本设计不引入。

## 2. 目标与非目标

### 目标

1. `applyResetPassword` 在用户邮箱存在时**真正**通过 SMTP 发送一封含重置链接的邮件
2. 不向客户端泄露邮箱是否存在
3. 60 秒内对同一邮箱仅允许发出 1 封重置邮件
4. SMTP 配置缺失时 fail-fast 报错，与现有 `testSmtp` 一致

### 非目标

- 不动注册流程（包括是否加邮箱 OTP）
- 不动 `change-email`、`change-password`
- 不引入 POP3
- 不改前端视图逻辑（前端已经按"已发邮件"模型在工作，后端补真发邮件即可）
- 不做"管理员未配 SMTP 时屏蔽前端入口"的 UX 改进

## 3. 行为定义

| 序 | 检查/动作 | 失败响应 |
|---|---|---|
| 1 | captcha 校验（保留现状） | 400 `Invalid captcha` |
| 2 | 已登录用户拦截（保留现状） | 400 `You have already logged in...` |
| 3 | 检查 `sys_options.smtp_config` 是否完整（server/port/email/password 全非空） | 400 `Please setup SMTP config at first`（与 `testSmtp` 文案一致；攻击者无法借此分辨用户是否存在） |
| 4 | Redis 限流：key=`password_reset_throttle:<lower(email)>`，60 秒冷却 | 400 `Reset email already sent recently, please try again later` |
| 5 | 按 lower(email) 找 user。不存在 / `is_disabled=true` | **不发邮件、不报错**，返回 success（不泄露存在性） |
| 6 | 写 `reset_password_token`（32 位随机串） + `reset_password_token_expire_time = now + 20 min` | — |
| 7 | 同步调 `SmtpMailService.send` 发邮件 | 抛 `RuntimeException` → 让前端能感知失败可重试 |
| 8 | 返回 success | — |

**单次使用 token**：现有 `resetPassword` 在重置成功后已经把 `reset_password_token` 与 `reset_password_token_expire_time` 双双置 NULL，已满足"用过即作废"。本次不改这部分。

## 4. 组件

### 4.1 `PasswordResetMailService`

接口：

```java
package com.alethicode.service.account;

public interface PasswordResetMailService {
    void sendResetEmail(String username, String email, String token);
}
```

实现 `PasswordResetMailServiceImpl`：

- 注入 `JdbcTemplate` + `ObjectMapper` + `SystemOptionService` + `SmtpMailService`
- 读 `sys_options.smtp_config`（与 `BetaFeedbackMailNotifier` 一致的方式），缺字段 → `BadRequestException("Please setup SMTP config at first")`
- baseUrl 走 `SystemOptionService.getWebsiteConfig().websiteBaseUrl()`（自带 admin 配置 → properties 的 fallback）
- 邮件 from = `sys_options.smtp_config.email`，from name = `website_name_shortcut`
- subject：`[Alethicode] 重置密码`
- 邮件正文（纯文本）：
  ```
  你好 ${username}，

  我们收到了重置 Alethicode 账号密码的请求。
  请点击下方链接，链接 20 分钟内有效：

  ${baseUrl}/reset-password/${token}

  如果你没有发起本次请求，可以忽略本邮件，账号密码不会被修改。

  —— Alethicode 团队
  ```
- 调 `SmtpMailService.send(server, port, email, password, tls, fromName, toEmail, toName, subject, content)` 发邮件，异常原样抛出（让上层决定是否回滚 token / 提示重试）

### 4.2 `PasswordResetThrottle`

接口：

```java
package com.alethicode.service.account;

public interface PasswordResetThrottle {
    /**
     * 申领一次发送窗口；返回 false 表示此 email 在冷却期内，应拒绝。
     */
    boolean tryAcquire(String email);
}
```

实现 `RedisPasswordResetThrottle`：

- 注入 `StringRedisTemplate`
- 实现：`Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(60));`
- key = `"password_reset_throttle:" + lower(email)`
- `ok == null || !ok` → 返回 false

### 4.3 `AccountServiceImpl.applyResetPassword`

改写为执行第 3 节的 8 步序列。新依赖通过构造函数注入：

```java
public AccountServiceImpl(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        AlethicodeProperties properties,
        PasswordResetMailService passwordResetMailService,
        PasswordResetThrottle passwordResetThrottle
)
```

`AccountServiceImplLogoutTest` 现有构造函数调用要补两个 mock。

## 5. 测试

### 5.1 `PasswordResetMailServiceImplTest`（新增）

| # | 用例 | 期望 |
|---|---|---|
| 1 | SMTP 完整配置 + 用户存在 | 调 `SmtpMailService.send` 一次，参数 server/port/email/password/tls 来自 sys_options，subject 含「重置密码」，content 含 `/reset-password/<token>` 与 username |
| 2 | SMTP 缺 password 字段 | 抛 `BadRequestException("Please setup SMTP config at first")` |
| 3 | SMTP 整个配置不存在（DB 无 smtp_config 行） | 抛 `BadRequestException` |

### 5.2 `RedisPasswordResetThrottleTest`（新增）

| # | 用例 | 期望 |
|---|---|---|
| 1 | 新邮箱第一次申请 | 返回 true，Redis 记录 60s TTL |
| 2 | 同邮箱 60s 内第二次 | 返回 false |
| 3 | 邮箱大小写归一化 | "Foo@bar.com" 与 "foo@bar.com" 共享同一冷却窗口 |

### 5.3 `AccountServiceImplApplyResetPasswordTest`（新增）

| # | 用例 | 期望 |
|---|---|---|
| 1 | captcha 错 | 抛 `error/Invalid captcha`，无任何 SMTP / Throttle 调用 |
| 2 | 已登录用户调用 | 抛 `error/You have already logged in...` |
| 3 | SMTP 配置缺失（PasswordResetMailService 抛 BadRequest） | 异常原样向上抛 |
| 4 | 用户不存在 | 返回 success，**未调** `passwordResetMailService.sendResetEmail`，**未写** token |
| 5 | 用户存在 | 写 token + expire（20min ± 5s 容差），调 `passwordResetMailService.sendResetEmail` 一次 |
| 6 | 60 秒内重复申请 | Throttle 返回 false → 抛 `error/Reset email already sent recently, please try again later` |

### 5.4 `AccountServiceImplLogoutTest`（已有，需补 mock）

构造函数多两个依赖，统一传 `mock(...)`。

## 6. 风险与权衡

| 风险 | 处置 |
|---|---|
| 同步发邮件造成接口慢（5-10s） | 接受。"成功"必须意味着真发了邮件，否则用户体验是假信号 |
| Redis 不可用 | `RedisConnectionFailureException` 向上抛，整条申请失败。Redis 是项目既定基础设施（已用于 spring-session），失联即系统级故障，此处不掩盖 |
| 攻击者通过限流响应推断邮箱存在 | 限流 key 用邮箱字符串，无论用户是否存在都会进入冷却，仅能推断"该邮箱字符串最近有人申请过"，不分辨真伪 |
| 攻击者通过响应时长（timing）推断邮箱存在 | 客观存在：用户存在路径走完整 SMTP 调用（5-10s），不存在路径毫秒级返回。**未做对齐**——任何 sub-second 的 sleep 都无法掩盖 SMTP 真实耗时，加固定 sleep 是安全剧场。配合限流（60s/email），攻击者每邮箱每分钟最多 1 次探测；按现实成本算（10⁷ 邮箱 ≈ 19 年），此 timing leak 不构成可用攻击面。如未来威胁模型升级，可改异步发送或在 user 不存在路径调一次"丢弃地址"的 SMTP 模拟耗时 |
| 写完 token 后 SMTP 抛错 | `@Transactional(rollbackFor = Exception.class)` 在类上：邮件抛错 → 事务回滚，token 不会落库。但 Redis 限流 key 已落（无事务）：60 秒内同邮箱无法重试。这是有意设计——避免攻击者借 SMTP 抛错绕过冷却 |

## 7. 不做事项重申

- 不引入 POP3
- 不改注册（保留 captcha-only）
- 不改前端
- 不引入新的 admin SMTP UI
- 不引入新的 DB migration（reset_password_token 字段已在 V7 就位）
