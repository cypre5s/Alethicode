package com.alethicode.service.betafeedback.admin.impl;

import com.alethicode.exception.BadRequestException;
import com.alethicode.service.betafeedback.admin.AdminBetaFeedbackService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员侧反馈处理服务。NamedParameterJdbcTemplate 走原始 SQL 即可，
 * 业务路径短，没必要引 JPA。
 */
@Service
public class AdminBetaFeedbackServiceImpl implements AdminBetaFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(AdminBetaFeedbackServiceImpl.class);

    private static final int MAX_LIMIT = 200;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AdminBetaFeedbackServiceImpl(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> listReports(int offset, int limit, String status, String severity, String type) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", isBlank(status) ? null : status)
                .addValue("severity", isBlank(severity) ? null : severity)
                .addValue("type", isBlank(type) ? null : type)
                .addValue("limit", safeLimit)
                .addValue("offset", safeOffset);

        String filter =
                " WHERE (CAST(:status AS TEXT) IS NULL OR r.status = :status) " +
                "   AND (CAST(:severity AS TEXT) IS NULL OR r.severity = :severity) " +
                "   AND (CAST(:type AS TEXT) IS NULL OR r.type = :type) ";

        String listSql =
                "SELECT r.id, r.reporter_user_id, r.type, r.severity, r.description, r.route, " +
                "       r.problem_id, r.submission_id, r.workflow_session_id, r.status, " +
                "       r.wjx_followup_opened, r.browser_meta::text AS browser_meta_json, " +
                "       r.recent_actions::text AS recent_actions_json, r.mail_status, r.mail_error, " +
                "       r.privacy_notice_version, r.created_at, r.updated_at, r.resolved_at, " +
                "       u.username AS username, " +
                "       (SELECT COUNT(*) FROM beta_feedback_attachment a WHERE a.report_id = r.id) AS attachment_count " +
                "  FROM beta_feedback_report r " +
                "  LEFT JOIN \"user\" u ON u.id = r.reporter_user_id " +
                filter +
                " ORDER BY r.created_at DESC " +
                " LIMIT :limit OFFSET :offset";

        String countSql =
                "SELECT COUNT(*) FROM beta_feedback_report r " + filter;

        List<Map<String, Object>> rows = jdbc.queryForList(listSql, params);
        Integer total = jdbc.queryForObject(countSql, params, Integer.class);

        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            items.add(toListItem(row));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0 : total);
        result.put("offset", safeOffset);
        result.put("limit", safeLimit);
        return result;
    }

    @Override
    public Map<String, Object> getReport(long id) {
        Map<String, Object> row = loadReportRow(id);

        List<Map<String, Object>> attachmentRows = jdbc.queryForList(
                "SELECT id, file_name, content_type, size_bytes, created_at " +
                        "FROM beta_feedback_attachment WHERE report_id = :id ORDER BY id ASC",
                new MapSqlParameterSource("id", id)
        );

        Map<String, Object> result = toListItem(row);
        List<Map<String, Object>> attachments = new ArrayList<>(attachmentRows.size());
        for (Map<String, Object> att : attachmentRows) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("id", att.get("id"));
            meta.put("file_name", att.get("file_name"));
            meta.put("content_type", att.get("content_type"));
            meta.put("size_bytes", att.get("size_bytes"));
            meta.put("created_at", att.get("created_at"));
            attachments.add(meta);
        }
        result.put("attachments", attachments);
        return result;
    }

    @Override
    public void updateStatus(long id, String newStatus) {
        if (newStatus == null || !ALLOWED_STATUSES.contains(newStatus)) {
            throw new BadRequestException("invalid status");
        }
        int updated = jdbc.update(
                "UPDATE beta_feedback_report " +
                        "SET status = :status, updated_at = NOW(), " +
                        "    resolved_at = CASE WHEN :status IN ('resolved','wontfix') THEN NOW() ELSE resolved_at END " +
                        "WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("status", newStatus)
                        .addValue("id", id)
        );
        if (updated == 0) {
            throw new BadRequestException("report not found");
        }
    }

    @Override
    public ResponseEntity<byte[]> streamScreenshot(long reportId, long attachmentId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("reportId", reportId)
                .addValue("attachmentId", attachmentId);

        Map<String, Object> row;
        try {
            row = jdbc.queryForMap(
                    "SELECT file_name, content_type, storage_path " +
                            "FROM beta_feedback_attachment " +
                            "WHERE id = :attachmentId AND report_id = :reportId",
                    params
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new BadRequestException("attachment not found");
        }

        String storagePath = String.valueOf(row.get("storage_path"));
        String contentType = String.valueOf(row.get("content_type"));
        String fileName = String.valueOf(row.get("file_name"));

        Path file = Path.of(storagePath);
        if (!Files.isRegularFile(file)) {
            throw new BadRequestException("attachment file missing");
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException ex) {
            log.warn("Failed to read screenshot {} for report {}", attachmentId, reportId, ex);
            throw new IllegalStateException("failed to read attachment");
        }

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(contentType);
        } catch (RuntimeException ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ContentDisposition disposition = ContentDisposition.inline()
                .filename(fileName == null ? "screenshot" : fileName)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(disposition);
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(bytes, headers, org.springframework.http.HttpStatus.OK);
    }

    private Map<String, Object> loadReportRow(long id) {
        try {
            return jdbc.queryForMap(
                    "SELECT r.id, r.reporter_user_id, r.type, r.severity, r.description, r.route, " +
                            "       r.problem_id, r.submission_id, r.workflow_session_id, r.status, " +
                            "       r.wjx_followup_opened, r.browser_meta::text AS browser_meta_json, " +
                            "       r.recent_actions::text AS recent_actions_json, r.mail_status, r.mail_error, " +
                            "       r.privacy_notice_version, r.created_at, r.updated_at, r.resolved_at, " +
                            "       u.username AS username, " +
                            "       (SELECT COUNT(*) FROM beta_feedback_attachment a WHERE a.report_id = r.id) AS attachment_count " +
                            "  FROM beta_feedback_report r " +
                            "  LEFT JOIN \"user\" u ON u.id = r.reporter_user_id " +
                            " WHERE r.id = :id",
                    new MapSqlParameterSource("id", id)
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new BadRequestException("report not found");
        }
    }

    private Map<String, Object> toListItem(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.get("id"));
        item.put("reporter_user_id", row.get("reporter_user_id"));
        item.put("username", row.get("username"));
        item.put("type", row.get("type"));
        item.put("severity", row.get("severity"));
        item.put("description", row.get("description"));
        item.put("route", row.get("route"));
        item.put("problem_id", row.get("problem_id"));
        item.put("submission_id", row.get("submission_id"));
        item.put("workflow_session_id", row.get("workflow_session_id"));
        item.put("status", row.get("status"));
        item.put("wjx_followup_opened", row.get("wjx_followup_opened"));
        item.put("browser_meta", parseJson(row.get("browser_meta_json"), "{}"));
        item.put("recent_actions", parseJson(row.get("recent_actions_json"), "[]"));
        item.put("mail_status", row.get("mail_status"));
        item.put("mail_error", row.get("mail_error"));
        item.put("privacy_notice_version", row.get("privacy_notice_version"));
        item.put("created_at", row.get("created_at"));
        item.put("updated_at", row.get("updated_at"));
        item.put("resolved_at", row.get("resolved_at"));
        item.put("attachment_count", row.get("attachment_count"));
        return item;
    }

    private Object parseJson(Object raw, String fallback) {
        String text = raw == null ? null : String.valueOf(raw);
        if (text == null || text.isBlank()) {
            text = fallback;
        }
        try {
            return objectMapper.readValue(text, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return fallback.equals("[]") ? List.of() : Map.of();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
