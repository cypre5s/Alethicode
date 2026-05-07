package com.alethicode.service.languagepack.quality;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 持久化并查询 {@code language_pack_init_quality_report}。
 *
 * 聚合粒度是一条 init task 一行；重新运行质量门禁会覆盖旧行，避免报告漂移。
 */
@Service
public class LanguagePackInitQualityReportService {

    private static final Logger log = LoggerFactory.getLogger(LanguagePackInitQualityReportService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public LanguagePackInitQualityReportService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void upsert(QualityReportRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        jdbcTemplate.update(
                "DELETE FROM language_pack_init_quality_report WHERE init_task_id = ?",
                record.initTaskId()
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_quality_report (
                    init_task_id, language_pack_id,
                    total_packages, self_validated_count, failed_count,
                    retried_count, escalated_count,
                    failure_breakdown, lint_summary, escalated_packages, duration_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?)
                """,
                record.initTaskId(),
                record.languagePackId(),
                record.totalPackages(),
                record.selfValidatedCount(),
                record.failedCount(),
                record.retriedCount(),
                record.escalatedCount(),
                writeJson(record.failureBreakdown()),
                writeJson(record.lintSummary()),
                writeJson(record.escalatedPackages()),
                record.duration().toMillis()
        );
    }

    public Optional<QualityReportRecord> findByTaskId(Long taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        try {
            QualityReportRecord row = jdbcTemplate.queryForObject(
                    """
                    SELECT init_task_id, language_pack_id,
                           total_packages, self_validated_count, failed_count,
                           retried_count, escalated_count,
                           failure_breakdown, lint_summary, escalated_packages,
                           duration_ms, create_time
                    FROM language_pack_init_quality_report
                    WHERE init_task_id = ?
                    """,
                    (rs, rowNum) -> mapRow(rs),
                    taskId
            );
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public List<QualityReportRecord> findRecentByLanguagePack(Long languagePackId, int limit) {
        if (languagePackId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT init_task_id, language_pack_id,
                       total_packages, self_validated_count, failed_count,
                       retried_count, escalated_count,
                       failure_breakdown, lint_summary, escalated_packages,
                       duration_ms, create_time
                FROM language_pack_init_quality_report
                WHERE language_pack_id = ?
                ORDER BY create_time DESC
                LIMIT ?
                """,
                (rs, rowNum) -> mapRow(rs),
                languagePackId,
                Math.max(1, Math.min(limit, 100))
        );
    }

    private QualityReportRecord mapRow(ResultSet rs) throws SQLException {
        Long initTaskId = rs.getLong("init_task_id");
        Long languagePackId = rs.getLong("language_pack_id");
        Map<String, Integer> failureBreakdown = readJsonMapInt(rs.getString("failure_breakdown"));
        Map<String, Object> lintSummary = readJsonMapObj(rs.getString("lint_summary"));
        List<Map<String, Object>> escalatedPackages = readJsonList(rs.getString("escalated_packages"));
        Timestamp createTime = rs.getTimestamp("create_time");
        return new QualityReportRecord(
                initTaskId,
                languagePackId,
                rs.getInt("total_packages"),
                rs.getInt("self_validated_count"),
                rs.getInt("failed_count"),
                rs.getInt("retried_count"),
                rs.getInt("escalated_count"),
                failureBreakdown,
                lintSummary,
                escalatedPackages,
                Duration.ofMillis(rs.getLong("duration_ms")),
                createTime == null ? null : createTime.toInstant()
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize quality report JSON", e);
        }
    }

    private Map<String, Integer> readJsonMapInt(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Integer> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getValue() instanceof Number n) {
                    out.put(entry.getKey(), n.intValue());
                }
            }
            return out;
        } catch (JsonProcessingException e) {
            log.warn("quality report failure_breakdown JSON parse failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> readJsonMapObj(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("quality report lint_summary JSON parse failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private List<Map<String, Object>> readJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("quality report escalated_packages JSON parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    public record QualityReportRecord(
            Long initTaskId,
            Long languagePackId,
            int totalPackages,
            int selfValidatedCount,
            int failedCount,
            int retriedCount,
            int escalatedCount,
            Map<String, Integer> failureBreakdown,
            Map<String, Object> lintSummary,
            List<Map<String, Object>> escalatedPackages,
            Duration duration,
            Instant createTime
    ) {

        public static QualityReportRecord empty(Long taskId, Long languagePackId) {
            return new QualityReportRecord(
                    taskId,
                    languagePackId,
                    0, 0, 0, 0, 0,
                    Map.of(),
                    Map.of(),
                    List.of(),
                    Duration.ZERO,
                    null
            );
        }
    }

    /**
     * ProblemValidationServiceImpl 处理候选题时使用的内存聚合器。
     */
    public static final class Aggregator {
        private int totalPackages = 0;
        private int selfValidatedCount = 0;
        private int failedCount = 0;
        private int retriedCount = 0;
        private int escalatedCount = 0;
        private final Map<String, Integer> failureBreakdown = new LinkedHashMap<>();
        private final Map<String, Map<String, Integer>> lintSummary = new LinkedHashMap<>();
        private final List<Map<String, Object>> escalatedPackages = new ArrayList<>();

        public void incrementTotal() {
            totalPackages++;
        }

        public void recordSelfValidated() {
            selfValidatedCount++;
        }

        public void recordFailure(String rootCauseKey) {
            failedCount++;
            failureBreakdown.merge(rootCauseKey, 1, Integer::sum);
        }

        public void recordRetry() {
            retriedCount++;
        }

        public void recordEscalation(Map<String, Object> escalationDetail) {
            escalatedCount++;
            escalatedPackages.add(escalationDetail);
        }

        public void recordLintReport(ReferenceLintReport report) {
            if (report == null) {
                return;
            }
            for (LintViolation v : report.hardViolations()) {
                lintSummary.computeIfAbsent("hard_violations", k -> new LinkedHashMap<>())
                        .merge(v.ruleCode(), 1, Integer::sum);
            }
            for (LintViolation v : report.softViolations()) {
                lintSummary.computeIfAbsent("soft_violations", k -> new LinkedHashMap<>())
                        .merge(v.ruleCode(), 1, Integer::sum);
            }
        }

        public QualityReportRecord toRecord(Long taskId, Long languagePackId, Duration duration) {
            Map<String, Object> lintSummaryView = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Integer>> entry : lintSummary.entrySet()) {
                lintSummaryView.put(entry.getKey(), Map.copyOf(entry.getValue()));
            }
            return new QualityReportRecord(
                    taskId,
                    languagePackId,
                    totalPackages,
                    selfValidatedCount,
                    failedCount,
                    retriedCount,
                    escalatedCount,
                    Map.copyOf(failureBreakdown),
                    Map.copyOf(lintSummaryView),
                    List.copyOf(escalatedPackages),
                    duration,
                    null
            );
        }
    }
}
