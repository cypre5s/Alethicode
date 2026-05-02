package com.alethicode.service.classroom;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.Locale;

import static com.alethicode.util.ServiceParseUtils.trimToEmpty;

@Component
public class ClassroomAccessHelper {

    private final JdbcTemplate jdbcTemplate;

    public ClassroomAccessHelper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record UserAuth(boolean authenticated, Long userId, boolean admin, boolean adminManager, String username) {
    }

    public UserAuth resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return new UserAuth(false, null, false, false, null);
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id, username, admin_type from \"user\" where lower(username) = ?",
                    (rs, rowNum) -> {
                        String adminType = trimToEmpty(rs.getString("admin_type"));
                        boolean admin = "Admin".equals(adminType) || "Teacher".equals(adminType);
                        boolean adminManager = "Admin".equals(adminType);
                        return new UserAuth(true, rs.getLong("id"), admin, adminManager, rs.getString("username"));
                    },
                    authentication.getName().toLowerCase(Locale.ROOT)
            );
        } catch (EmptyResultDataAccessException ignored) {
            return new UserAuth(false, null, false, false, null);
        }
    }

    public UserAuth requireAuthenticated(Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        return user;
    }

    public boolean isMember(String classroomId, Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from classroom_member where classroom_id = ? and user_id = ?",
                Integer.class, classroomId, userId);
        return count != null && count > 0;
    }

    public boolean isStaff(String classroomId, Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from classroom_member where classroom_id = ? and user_id = ? and role in ('owner','ta')",
                Integer.class, classroomId, userId);
        return count != null && count > 0;
    }

    public boolean isOwner(String classroomId, Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from classroom_member where classroom_id = ? and user_id = ? and role = 'owner'",
                Integer.class, classroomId, userId);
        return count != null && count > 0;
    }

    public boolean classroomExists(String classroomId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from classroom where id = ?", Integer.class, classroomId);
        return count != null && count > 0;
    }

    public String memberRole(String classroomId, String memberId) {
        return jdbcTemplate.query(
                "select role from classroom_member where classroom_id = ? and id = ?",
                (rs, rowNum) -> rs.getString("role"),
                classroomId, memberId
        ).stream().findFirst().orElse(null);
    }

    public String randomCode(int len) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            int idx = (int) Math.floor(Math.random() * chars.length());
            builder.append(chars.charAt(idx));
        }
        return builder.toString();
    }

    public String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes());
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception exception) {
            return raw;
        }
    }
}
