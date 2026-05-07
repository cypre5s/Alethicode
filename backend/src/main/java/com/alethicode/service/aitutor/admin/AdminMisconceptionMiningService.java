package com.alethicode.service.aitutor.admin;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AdminMisconceptionMiningService {

    private static final Logger log = LoggerFactory.getLogger(AdminMisconceptionMiningService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JdbcTemplate jdbcTemplate;

    public AdminMisconceptionMiningService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ApiResponse<Object> adminMcMiningPending(Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.adminManager()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        List<Map<String, Object>> results = jdbcTemplate.query(
                """
                select m.id, m.name, m.description, m.correction_hint, m.evidence_count,
                       kc.name_en as kc_name_en, kc.name as kc_name, m.created_at
                from ai_misconception m
                left join ai_knowledge_component kc on kc.id = m.kc_id
                where m.source = 'mcmining' and m.status = 'pending'
                order by m.created_at desc
                limit 100
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getString("id"));
                    item.put("name", rs.getString("name"));
                    item.put("description", rs.getString("description"));
                    item.put("correction_hint", rs.getString("correction_hint"));
                    item.put("evidence_count", rs.getInt("evidence_count"));
                    item.put("suggested_kc", rs.getString("kc_name_en"));
                    item.put("kc_name", rs.getString("kc_name"));
                    item.put("created_at", formatTime(rs.getTimestamp("created_at")));
                    return item;
                }
        );
        return ApiResponse.success(Map.of("results", results, "total", results.size()));
    }

    public ApiResponse<Object> adminMcMiningApprove(Map<String, Object> request, Authentication authentication) {
        return mcStatusChange(request, authentication, "approved");
    }

    public ApiResponse<Object> adminMcMiningReject(Map<String, Object> request, Authentication authentication) {
        return mcStatusChange(request, authentication, "rejected");
    }

    public ApiResponse<Object> adminMcMiningMerge(Map<String, Object> request, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.adminManager()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String misconceptionId = trimToNull(stringValue(request.get("misconception_id")));
        String targetId = trimToNull(stringValue(request.get("target_id")));
        if (misconceptionId == null || targetId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "misconception_id and target_id are required");
        }
        Integer targetExists = jdbcTemplate.queryForObject("select count(*) from ai_misconception where id = ?", Integer.class, targetId);
        if (targetExists == null || targetExists == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Target misconception not found");
        }
        int deleted = jdbcTemplate.update("delete from ai_misconception where id = ? and status = 'pending'", misconceptionId);
        if (deleted == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Misconception not found");
        }
        Map<String, Object> target = jdbcTemplate.queryForObject(
                "select id, name from ai_misconception where id = ?",
                (rs, rowNum) -> Map.of("id", rs.getString("id"), "name", rs.getString("name"), "merged", true),
                targetId
        );
        return ApiResponse.success(target == null ? Map.of("id", targetId, "merged", true) : target);
    }

    public ApiResponse<Object> adminMcMiningDiscover(Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.adminManager()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        jdbcTemplate.update(
                """
                insert into ai_learning_event(user_id, event_type, extra_data)
                values (?, 'mcmining_discover_dispatched', cast(? as jsonb))
                """,
                user.userId(),
                "{}"
        );
        return ApiResponse.success(Map.of("dispatched", true));
    }

    private ApiResponse<Object> mcStatusChange(Map<String, Object> request, Authentication authentication, String targetStatus) {
        UserAuth user = resolveUser(authentication);
        if (!user.adminManager()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String misconceptionId = trimToNull(stringValue(request.get("misconception_id")));
        if (misconceptionId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "misconception_id is required");
        }
        int updated = jdbcTemplate.update("update ai_misconception set status = ? where id = ? and status = 'pending'", targetStatus, misconceptionId);
        if (updated == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Misconception not found");
        }
        Map<String, Object> item = jdbcTemplate.queryForObject(
                "select id, name, status from ai_misconception where id = ?",
                (rs, rowNum) -> Map.of(
                        "id", rs.getString("id"),
                        "name", rs.getString("name"),
                        "status", rs.getString("status")
                ),
                misconceptionId
        );
        return ApiResponse.success(item == null ? Map.of("id", misconceptionId, "status", targetStatus) : item);
    }

    private UserAuth resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return new UserAuth(false, null, false, false, false, Set.of());
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id, admin_type from \"user\" where lower(username) = ?",
                    (rs, rowNum) -> {
                        long userId = rs.getLong("id");
                        String adminType = rs.getString("admin_type");
                        boolean teacher = "Teacher".equals(adminType);
                        boolean admin = "Admin".equals(adminType) || teacher;
                        boolean adminManager = "Admin".equals(adminType);
                        Set<Long> accessibleLanguagePackIds = teacher ? loadTeacherLanguagePackIds(userId) : Set.of();
                        return new UserAuth(true, userId, admin, adminManager, teacher, accessibleLanguagePackIds);
                    },
                    authentication.getName().toLowerCase(Locale.ROOT)
            );
        } catch (EmptyResultDataAccessException ignored) {
            return new UserAuth(false, null, false, false, false, Set.of());
        }
    }

    private Set<Long> loadTeacherLanguagePackIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
                """
                select distinct clp.language_pack_id
                from classroom_member cm
                join classroom c on c.id = cm.classroom_id
                join classroom_language_pack clp on clp.classroom_id = cm.classroom_id
                where cm.user_id = ?
                  and c.is_active = true
                  and cm.role in ('owner', 'ta')
                """,
                Long.class,
                userId
        ));
    }

    private String formatTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(timestamp.toInstant().atOffset(ZoneOffset.UTC));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record UserAuth(boolean authenticated,
                            Long userId,
                            boolean admin,
                            boolean adminManager,
                            boolean teacher,
                            Set<Long> accessibleLanguagePackIds) {
    }
}
