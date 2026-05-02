package com.alethicode.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_USERNAME_KEY = "AUTH_USERNAME";
    /**
     * Session 中缓存 authorities，避免每次请求都查表，参见 BUG #31。
     */
    public static final String AUTH_ROLES_KEY = "AUTH_ROLES";
    /**
     * Session 中缓存当前用户 id（Long），避免多个 Controller 每次请求都 SELECT user 表。
     * 登录时由 AccountServiceImpl 写入；老会话缺失时由本 filter 懒加载。
     */
    public static final String AUTH_USER_ID_KEY = "AUTH_USER_ID";

    @Nullable
    private final JdbcTemplate jdbcTemplate;

    public SessionAuthenticationFilter(@Nullable JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null || !current.isAuthenticated() || current instanceof AnonymousAuthenticationToken) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object sessionUsername = session.getAttribute(AUTH_USERNAME_KEY);
                if (sessionUsername != null) {
                    String username = String.valueOf(sessionUsername);
                    if (!username.isBlank()) {
                        List<String> authorities = readAuthoritiesFromSession(session);
                        if (authorities == null) {
                            authorities = resolveAuthorities(username);
                            session.setAttribute(AUTH_ROLES_KEY, new ArrayList<>(authorities));
                        }
                        Long userId = readUserIdFromSession(session);
                        if (userId == null) {
                            userId = resolveUserId(username);
                            if (userId != null) {
                                session.setAttribute(AUTH_USER_ID_KEY, userId);
                            }
                        }
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        username,
                                        null,
                                        AuthorityUtils.createAuthorityList(authorities.toArray(new String[0]))
                                );
                        if (userId != null) {
                            authentication.setDetails(userId);
                        }
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private Long readUserIdFromSession(HttpSession session) {
        Object cached = session.getAttribute(AUTH_USER_ID_KEY);
        if (cached instanceof Long l) {
            return l;
        }
        if (cached instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    private Long resolveUserId(String username) {
        if (jdbcTemplate == null || username == null || username.isBlank()) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id from \"user\" where lower(username) = ?",
                    Long.class,
                    username.toLowerCase(Locale.ROOT)
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> readAuthoritiesFromSession(HttpSession session) {
        Object cached = session.getAttribute(AUTH_ROLES_KEY);
        if (!(cached instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<String> roles = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry instanceof String s && !s.isBlank()) {
                roles.add(s);
            }
        }
        return roles.isEmpty() ? null : roles;
    }

    private List<String> resolveAuthorities(String username) {
        List<String> roles = new ArrayList<>();
        roles.add("ROLE_USER");
        if (jdbcTemplate == null) {
            return roles;
        }
        try {
            String adminType = jdbcTemplate.queryForObject(
                    "select admin_type from \"user\" where lower(username) = ?",
                    String.class,
                    username.toLowerCase(Locale.ROOT)
            );
            if ("Admin".equals(adminType) || "Teacher".equals(adminType)) {
                roles.add("ROLE_ADMIN");
            }
            if ("Teacher".equals(adminType)) {
                roles.add("ROLE_TEACHER");
            }
        } catch (EmptyResultDataAccessException ignored) {
            return roles;
        }
        return roles;
    }
}
