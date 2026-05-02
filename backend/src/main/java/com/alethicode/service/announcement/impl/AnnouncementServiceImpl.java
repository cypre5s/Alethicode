package com.alethicode.service.announcement.impl;

import com.alethicode.dto.request.AnnouncementCreateRequest;
import com.alethicode.dto.request.AnnouncementEditRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.announcement.AnnouncementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(rollbackFor = Exception.class)
public class AnnouncementServiceImpl implements AnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementServiceImpl.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JdbcTemplate jdbcTemplate;

    public AnnouncementServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ApiResponse<Object> listPublic(Map<String, String> params) {
        QueryPage queryPage = page(params);
        long total = jdbcTemplate.queryForObject(
                "select count(*) from announcement where visible = true",
                Long.class
        );
        List<Map<String, Object>> results = jdbcTemplate.query(
                """
                select a.id, a.title, a.content, a.create_time, a.last_update_time,
                       a.visible, u.username as created_by
                from announcement a
                left join "user" u on u.id = a.created_by_id
                where a.visible = true
                order by a.create_time desc
                limit ? offset ?
                """,
                this::mapAnnouncement,
                queryPage.limit(),
                queryPage.offset()
        );
        return ApiResponse.success(Map.of("results", results, "total", total));
    }

    @Override
    public ApiResponse<Object> listAdmin(Map<String, String> params, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!auth.adminManager()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }

        String id = trimToNull(params.get("id"));
        if (id != null) {
            Long announcementId = parseLong(id);
            if (announcementId == null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Announcement does not exist");
            }
            Map<String, Object> one = findAnnouncement(announcementId);
            if (one == null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Announcement does not exist");
            }
            return ApiResponse.success(one);
        }

        boolean onlyVisible = "true".equalsIgnoreCase(trimToEmpty(params.get("visible")));
        QueryPage queryPage = page(params);

        String where = onlyVisible ? " where a.visible = true" : "";
        long total = jdbcTemplate.queryForObject(
                "select count(*) from announcement a" + where,
                Long.class
        );
        List<Map<String, Object>> results = jdbcTemplate.query(
                """
                select a.id, a.title, a.content, a.create_time, a.last_update_time,
                       a.visible, u.username as created_by
                from announcement a
                left join "user" u on u.id = a.created_by_id
                """ + where + " order by a.create_time desc limit ? offset ?",
                this::mapAnnouncement,
                queryPage.limit(),
                queryPage.offset()
        );

        return ApiResponse.success(Map.of("results", results, "total", total));
    }

    @Override
    public ApiResponse<Object> create(AnnouncementCreateRequest request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!auth.adminManager()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }

        String title = trimToNull(request.title());
        String content = trimToNull(request.content());
        if (title == null || content == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Parameter error");
        }

        Long id = jdbcTemplate.queryForObject(
                """
                insert into announcement(title, content, created_by_id, visible)
                values (?, ?, ?, ?)
                returning id
                """,
                Long.class,
                title,
                content,
                auth.userId(),
                request.visible() == null || request.visible()
        );
        if (id == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Announcement does not exist");
        }
        return ApiResponse.success(findAnnouncement(id));
    }

    @Override
    public ApiResponse<Object> edit(AnnouncementEditRequest request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!auth.adminManager()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }

        if (request.id() == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Announcement does not exist");
        }
        int updated = jdbcTemplate.update(
                """
                update announcement
                set title = coalesce(?, title),
                    content = coalesce(?, content),
                    visible = coalesce(?, visible),
                    last_update_time = now()
                where id = ?
                """,
                trimToNull(request.title()),
                trimToNull(request.content()),
                request.visible(),
                request.id()
        );
        if (updated == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Announcement does not exist");
        }
        return ApiResponse.success(findAnnouncement(request.id()));
    }

    @Override
    public ApiResponse<Object> delete(String id, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!auth.adminManager()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }

        Long announcementId = parseLong(id);
        if (announcementId != null) {
            jdbcTemplate.update("delete from announcement where id = ?", announcementId);
        }
        return ApiResponse.success(null);
    }

    private Map<String, Object> findAnnouncement(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select a.id, a.title, a.content, a.create_time, a.last_update_time,
                           a.visible, u.username as created_by
                    from announcement a
                    left join "user" u on u.id = a.created_by_id
                    where a.id = ?
                    """,
                    this::mapAnnouncement,
                    id
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private Map<String, Object> mapAnnouncement(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", rs.getLong("id"));
        item.put("title", rs.getString("title"));
        item.put("content", rs.getString("content"));
        item.put("visible", rs.getBoolean("visible"));
        item.put("created_by", rs.getString("created_by"));
        item.put("create_time", rs.getTimestamp("create_time") == null
                ? null
                : DATE_TIME_FORMATTER.format(rs.getTimestamp("create_time").toInstant().atOffset(ZoneOffset.UTC)));
        item.put("last_update_time", rs.getTimestamp("last_update_time") == null
                ? null
                : DATE_TIME_FORMATTER.format(rs.getTimestamp("last_update_time").toInstant().atOffset(ZoneOffset.UTC)));
        return item;
    }

    private UserAuth resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return new UserAuth(false, false, null);
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id, admin_type from \"user\" where lower(username) = ?",
                    (rs, rowNum) -> new UserAuth(
                            true,
                            "Admin".equals(rs.getString("admin_type")),
                            rs.getLong("id")
                    ),
                    authentication.getName().toLowerCase(Locale.ROOT)
            );
        } catch (EmptyResultDataAccessException ignored) {
            return new UserAuth(false, false, null);
        }
    }

    private QueryPage page(Map<String, String> params) {
        int limit = parseInt(trimToEmpty(params.get("limit")), 10);
        int offset = parseInt(trimToEmpty(params.get("offset")), 0);
        if (limit <= 0 || limit > 250) {
            limit = 10;
        }
        if (offset < 0) {
            offset = 0;
        }
        return new QueryPage(limit, offset);
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            log.debug("parseInt: invalid raw={}, using fallback {}", raw, fallback, e);
            return fallback;
        }
    }

    private Long parseLong(String raw) {
        try {
            return Long.parseLong(trimToEmpty(raw));
        } catch (Exception e) {
            log.debug("parseLong: invalid raw={}", raw, e);
            return null;
        }
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

    private record UserAuth(boolean authenticated, boolean adminManager, Long userId) {
    }

    private record QueryPage(int limit, int offset) {
    }
}
