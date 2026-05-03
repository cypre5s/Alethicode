package com.alethicode.service.account;

/**
 * 重置密码邮件按邮箱维度的发送速率限制。
 *
 * <p>对外契约：每个邮箱在冷却窗口内只允许一次成功申请（窗口由实现决定，
 * 当前 {@link com.alethicode.service.account.impl.RedisPasswordResetThrottle}
 * 设为 60 秒）。无论用户是否真实存在，限流以邮箱字符串为粒度生效，
 * 避免攻击者通过响应分辨用户存在性。</p>
 */
public interface PasswordResetThrottle {

    /**
     * 申领一次发送窗口。
     *
     * @param email 邮箱（实现内部应做小写归一）
     * @return true 表示获得发送窗口；false 表示仍在冷却期内，调用方应拒绝请求
     */
    boolean tryAcquire(String email);
}
