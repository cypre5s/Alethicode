package com.alethicode.service.adminproblemcommand;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AdminPreflightService {

    private static final Logger log = LoggerFactory.getLogger(AdminPreflightService.class);

    private final JdbcTemplate jdbcTemplate;

    public AdminPreflightService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ApiResponse<Object> adminPreflightStats(Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.adminManager()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Map<String, PreflightDetectorMetrics> metricsByDetector = loadPreflightDetectorMetrics();
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<String, PreflightDetectorMetrics> entry : metricsByDetector.entrySet()) {
            String detector = entry.getKey();
            PreflightDetectorMetrics metrics = entry.getValue();
            long show = metrics.showCount();
            long edit = metrics.goEditCount();
            long force = metrics.forceSubmitCount();
            double helpful = computeHelpfulRate(edit, force);
            results.add(Map.of(
                    "detector", detector,
                    "show_count", show,
                    "go_edit_count", edit,
                    "force_submit_count", force,
                    "helpful_rate", helpful
            ));
        }
        results.sort(java.util.Comparator.comparingDouble(o -> ((Number) o.get("helpful_rate")).doubleValue()));
        return ApiResponse.success(Map.of("results", results));
    }

    public ApiResponse<Object> adminPreflightDiagnose(Map<String, Object> request, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.adminManager()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String detectorName = trimToNull(stringValue(request.get("detector_name")));
        if (detectorName == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "detector_name is required");
        }
        Map<String, PreflightDetectorMetrics> metricsByDetector = loadPreflightDetectorMetrics();
        PreflightDetectorMetrics metrics = metricsByDetector.getOrDefault(detectorName, new PreflightDetectorMetrics(0L, 0L, 0L));
        long show = metrics.showCount();
        long edit = metrics.goEditCount();
        long force = metrics.forceSubmitCount();
        double helpful = computeHelpfulRate(edit, force);
        Map<String, String> latestTemplate = findLatestDetectorTemplate(detectorName);
        String diagnosis = buildDetectorDiagnosis(detectorName, show, edit, force, helpful);

        return ApiResponse.success(Map.of(
                "detector_name", detectorName,
                "current_template", Map.of(
                        "question", trimToEmpty(latestTemplate.get("question")),
                        "hint", trimToEmpty(latestTemplate.get("hint"))
                ),
                "stats", Map.of(
                        "show_count", show,
                        "go_edit_count", edit,
                        "force_submit_count", force,
                        "helpful_rate", helpful
                ),
                "diagnosis", diagnosis
        ));
    }

    // ---- exclusive helpers ----

    private Map<String, PreflightDetectorMetrics> loadPreflightDetectorMetrics() {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select coalesce(extra_data->>'detector_name', '') as detector_name,
                       sum(case when event_type = 'misconception_detected_ast' then 1 else 0 end) as show_count,
                       sum(case when event_type = 'preflight_go_edit' then 1 else 0 end) as go_edit_count,
                       sum(case when event_type = 'preflight_force_submit' then 1 else 0 end) as force_submit_count
                from ai_learning_event
                where coalesce(extra_data->>'detector_name', '') <> ''
                group by coalesce(extra_data->>'detector_name', '')
                order by detector_name
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("detector_name", trimToEmpty(rs.getString("detector_name")));
                    row.put("show_count", rs.getLong("show_count"));
                    row.put("go_edit_count", rs.getLong("go_edit_count"));
                    row.put("force_submit_count", rs.getLong("force_submit_count"));
                    return row;
                }
        );
        Map<String, PreflightDetectorMetrics> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String detectorName = trimToEmpty(stringValue(row.get("detector_name")));
            if (detectorName.isBlank()) {
                continue;
            }
            result.put(detectorName, new PreflightDetectorMetrics(
                    longValue(row.get("show_count")),
                    longValue(row.get("go_edit_count")),
                    longValue(row.get("force_submit_count"))
            ));
        }
        return result;
    }

    private Map<String, String> findLatestDetectorTemplate(String detectorName) {
        Map<String, String> row = jdbcTemplate.query(
                """
                select extra_data->>'question' as question,
                       extra_data->>'hint' as hint
                from ai_learning_event
                where coalesce(extra_data->>'detector_name', '') = ?
                  and (coalesce(extra_data->>'question', '') <> '' or coalesce(extra_data->>'hint', '') <> '')
                order by created_at desc
                limit 1
                """,
                (rs, rowNum) -> {
                    Map<String, String> one = new LinkedHashMap<>();
                    one.put("question", trimToEmpty(rs.getString("question")));
                    one.put("hint", trimToEmpty(rs.getString("hint")));
                    return one;
                },
                detectorName
        ).stream().findFirst().orElse(null);
        if (row != null) {
            return row;
        }
        return Map.of("question", "", "hint", "");
    }

    private String buildDetectorDiagnosis(String detectorName, long show, long edit, long force, double helpfulRate) {
        if (show == 0L && edit == 0L && force == 0L) {
            return "检测器 " + detectorName + " 暂无真实学习事件数据。";
        }
        String level;
        if (helpfulRate >= 70.0) {
            level = "高";
        } else if (helpfulRate >= 40.0) {
            level = "中";
        } else {
            level = "低";
        }
        return String.format(
                Locale.ROOT,
                "检测器 %s 的真实事件统计：展示 %d 次，去编辑 %d 次，强制提交 %d 次，帮助率 %.1f%%，当前有效性评级为%s。",
                detectorName,
                show,
                edit,
                force,
                helpfulRate,
                level
        );
    }

    private double computeHelpfulRate(long edit, long force) {
        long total = edit + force;
        if (total <= 0L) {
            return 0.0;
        }
        return Math.round((edit * 1000.0 / total)) / 10.0;
    }

    // ---- shared utility methods ----

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

    private long longValue(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        String text = trimToNull(String.valueOf(raw));
        if (text == null) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private record UserAuth(boolean authenticated,
                            Long userId,
                            boolean admin,
                            boolean adminManager,
                            boolean teacher,
                            Set<Long> accessibleLanguagePackIds) {
    }

    private record PreflightDetectorMetrics(long showCount, long goEditCount, long forceSubmitCount) {
    }
}
