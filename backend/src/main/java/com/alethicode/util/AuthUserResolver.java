package com.alethicode.util;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * 从 Spring Security {@link Authentication} 中解析当前用户 id。
 *
 * 优先从 {@code authentication.getDetails()} 读取（由
 * {@code SessionAuthenticationFilter} 在构建 Authentication 时写入），
 * 避免每次请求都查询 user 表。
 */
public final class AuthUserResolver {

    private AuthUserResolver() {
    }

    /** @return 已登录用户 id；未登录时返回 null。 */
    public static Long currentUserIdOrNull(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof Long l) {
            return l;
        }
        if (details instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
