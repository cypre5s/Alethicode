package com.alethicode.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int DEFAULT_LIMIT = 100;
    private static final int SENSITIVE_LIMIT = 10;
    private static final long WINDOW_SECONDS = 60;

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT;

    static {
        INCREMENT_SCRIPT = new DefaultRedisScript<>();
        INCREMENT_SCRIPT.setScriptText(
                "local cnt = redis.call('INCR', KEYS[1])\n" +
                "if cnt == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
                "return cnt"
        );
        INCREMENT_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;
    private final List<TrustedCidr> trustedProxies;

    public RateLimitFilter(
            StringRedisTemplate redis,
            @Value("${alethicode.security.trusted-proxies:127.0.0.1/32,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}") String trustedProxiesCsv
    ) {
        this.redis = redis;
        this.trustedProxies = parseTrustedProxies(trustedProxiesCsv);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        if (!uri.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        int limit = isSensitivePath(uri) ? SENSITIVE_LIMIT : DEFAULT_LIMIT;
        String key = "rl:" + clientIp + ":" + (isSensitivePath(uri) ? "auth" : "api");

        Long count = redis.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(WINDOW_SECONDS));

        if (count != null && count > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"rate_limit\",\"detail\":\"Too many requests\"}");
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - (count != null ? count : 0))));

        chain.doFilter(request, response);
    }

    private boolean isSensitivePath(String uri) {
        return uri.startsWith("/api/login") ||
               uri.startsWith("/api/register") ||
               uri.startsWith("/api/apply-reset-password") ||
               uri.startsWith("/api/reset-password") ||
               (uri.startsWith("/api/submission") && !uri.contains("list"));
    }

    /**
     * 解析客户端 IP：仅当请求来自 trusted proxy（即真的从 nginx 上游来）时采信 X-Real-IP。
     * 直接信 X-Real-IP 而不解析 X-Forwarded-For，因为 nginx 已经用 {@code $remote_addr}
     * 覆盖了 X-Real-IP（攻击者伪造 X-Real-IP 头会被 nginx 替换）；而 X-Forwarded-For
     * 在 nginx {@code $proxy_add_x_forwarded_for} 模式下保留了客户端伪造值，攻击者每次
     * 用不同的 XFF 即可绕过 per-IP 限流（HIGH-4 修复，2026-05-02 渗透报告）。
     *
     * <p>非 trusted 上游（直接打 backend / 测试环境）退化到 TCP 真实源地址。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String directRemote = request.getRemoteAddr();
        if (directRemote != null && isTrustedProxy(directRemote)) {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return directRemote;
    }

    private boolean isTrustedProxy(String ipText) {
        try {
            byte[] ipBytes = InetAddress.getByName(ipText).getAddress();
            for (TrustedCidr cidr : trustedProxies) {
                if (cidr.contains(ipBytes)) {
                    return true;
                }
            }
        } catch (UnknownHostException ignored) {
            return false;
        }
        return false;
    }

    private static List<TrustedCidr> parseTrustedProxies(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        List<TrustedCidr> result = new ArrayList<>();
        for (String raw : csv.split(",")) {
            String token = raw.trim();
            if (token.isEmpty()) continue;
            try {
                result.add(TrustedCidr.parse(token));
            } catch (RuntimeException ignored) {
                // 配置畸形条目跳过，避免启动失败；运维通过日志感知
            }
        }
        return Collections.unmodifiableList(result);
    }

    private record TrustedCidr(byte[] network, int prefixBits) {

        static TrustedCidr parse(String token) {
            int slash = token.indexOf('/');
            String hostPart = slash >= 0 ? token.substring(0, slash) : token;
            byte[] addr;
            try {
                addr = InetAddress.getByName(hostPart).getAddress();
            } catch (UnknownHostException ex) {
                throw new IllegalArgumentException("invalid cidr: " + token, ex);
            }
            int prefix = slash >= 0 ? Integer.parseInt(token.substring(slash + 1)) : addr.length * 8;
            return new TrustedCidr(addr, prefix);
        }

        boolean contains(byte[] candidate) {
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixBits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int remaining = prefixBits % 8;
            if (remaining == 0) {
                return true;
            }
            int mask = 0xff << (8 - remaining) & 0xff;
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
