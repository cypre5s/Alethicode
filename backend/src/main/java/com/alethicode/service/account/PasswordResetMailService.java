package com.alethicode.service.account;

/**
 * 重置密码邮件发送领域服务。
 *
 * <p>职责单一：根据用户信息 + 一次性 token，从 {@code sys_options.smtp_config}
 * 与 {@code sys_options.website_config} 拼出邮件并通过 {@link com.alethicode.service.system.SmtpMailService}
 * 发送。SMTP 配置缺失时 fail-fast 抛 {@link com.alethicode.exception.BadRequestException}，
 * 文案与 admin {@code testSmtp} 保持一致。</p>
 */
public interface PasswordResetMailService {

    /**
     * 发送重置密码邮件。SMTP 未配置或配置不完整时抛 {@link com.alethicode.exception.BadRequestException}。
     *
     * @param username 用于邮件称呼，不允许为 null/空
     * @param email    收件人邮箱（已规范化的小写形态），不允许为 null/空
     * @param token    一次性重置 token（32 位随机串），用于拼出 {@code /reset-password/{token}} 链接
     */
    void sendResetEmail(String username, String email, String token);
}
