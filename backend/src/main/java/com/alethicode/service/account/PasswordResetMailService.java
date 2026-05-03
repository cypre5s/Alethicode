package com.alethicode.service.account;

import org.springframework.lang.Nullable;

/**
 * 重置密码邮件发送领域服务。
 *
 * <p>职责单一：根据用户信息 + 一次性 token，从 {@code sys_options.smtp_config}
 * 拼邮件并通过 {@link com.alethicode.service.system.SmtpMailService} 发送。
 * SMTP 配置缺失时 fail-fast 抛 {@link com.alethicode.exception.BadRequestException}，
 * 文案与 admin {@code testSmtp} 保持一致。</p>
 *
 * <p>邮件链接的 base URL 解析顺序（首个非空命中即用）：
 * <ol>
 *   <li>调用方推断的 {@code requestBaseUrl}（来自当前 HTTP 请求的 X-Forwarded-Host /
 *       X-Forwarded-Proto，或 ServerName/Port），保证多环境/反代下"用户访问什么 host
 *       就发什么 host 链接"；</li>
 *   <li>{@code sys_options.website_config.website_base_url}（admin 后台显式覆盖）；</li>
 *   <li>都为空 → 抛 {@link com.alethicode.exception.BadRequestException}，failfast。</li>
 * </ol></p>
 */
public interface PasswordResetMailService {

    /**
     * 发送重置密码邮件。SMTP 未配置或配置不完整时抛 {@link com.alethicode.exception.BadRequestException}。
     *
     * @param username       用于邮件称呼，不允许为 null/空
     * @param email          收件人邮箱（已规范化的小写形态），不允许为 null/空
     * @param token          一次性重置 token（32 位随机串），用于拼出 {@code /reset-password/{token}} 链接
     * @param requestBaseUrl 当前 HTTP 请求推断出的站点 base URL（如 {@code https://oj.example.com}），
     *                       由 controller / service 层从 {@link jakarta.servlet.http.HttpServletRequest}
     *                       推断后传入；为 null/空时回退到 admin 配置
     */
    void sendResetEmail(String username, String email, String token, @Nullable String requestBaseUrl);
}
