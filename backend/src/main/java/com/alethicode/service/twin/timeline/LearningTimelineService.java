package com.alethicode.service.twin.timeline;

import com.alethicode.dto.response.twin.LearningTimelineEntry;
import com.alethicode.dto.response.twin.LearningTimelineResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LearningTimelineService {

    private static final Logger log = LoggerFactory.getLogger(LearningTimelineService.class);
    private static final int MAX_TIME_SPAN_DAYS = 365;
    private static final int ABSOLUTE_MAX_LIMIT = 1000;
    private static final Set<String> VALID_KINDS = Set.of(
            LearningTimelineEntry.KIND_SUBMISSION,
            LearningTimelineEntry.KIND_MEMORY,
            LearningTimelineEntry.KIND_AI_EVENT,
            LearningTimelineEntry.KIND_NOTEBOOK
    );

    private static final int RESULT_AC = 0;

    private final JdbcTemplate jdbcTemplate;

    public LearningTimelineService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LearningTimelineResponse query(Long userId, LocalDate from, LocalDate to,
                                          List<String> kinds, int limit) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("invalid-time-range");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("invalid-time-range");
        }
        long spanDays = ChronoUnit.DAYS.between(from, to);
        if (spanDays > MAX_TIME_SPAN_DAYS) {
            throw new IllegalArgumentException("time-span-too-large");
        }
        int effectiveLimit = Math.min(Math.max(limit, 1), ABSOLUTE_MAX_LIMIT);

        List<String> effectiveKinds = (kinds == null || kinds.isEmpty())
                ? List.copyOf(VALID_KINDS)
                : kinds.stream().filter(VALID_KINDS::contains).toList();
        if (effectiveKinds.isEmpty()) {
            return new LearningTimelineResponse(List.of(), 0, false);
        }

        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<LearningTimelineEntry> entries = queryByKinds(
                userId, fromInstant, toInstant, effectiveKinds, effectiveLimit + 1);

        boolean hasMore = entries.size() > effectiveLimit;
        if (hasMore) {
            entries = entries.subList(0, effectiveLimit);
        }

        return new LearningTimelineResponse(entries, entries.size(), hasMore);
    }

    private List<LearningTimelineEntry> queryByKinds(Long userId, Instant from, Instant to,
                                                      List<String> kinds, int limit) {
        StringBuilder sql = new StringBuilder();
        var params = new java.util.ArrayList<>();
        boolean first = true;

        if (kinds.contains(LearningTimelineEntry.KIND_SUBMISSION)) {
            sql.append("""
                SELECT s.id AS event_id, 'submission' AS event_kind, s.create_time AS event_at,
                       s.problem_id, p.title AS problem_title, s.result AS payload_int,
                       NULL AS payload_text
                FROM submission s
                LEFT JOIN problem p ON p.id = s.problem_id
                WHERE s.user_id = ? AND s.create_time >= ? AND s.create_time < ?
                """);
            params.add(userId);
            params.add(Timestamp.from(from));
            params.add(Timestamp.from(to));
            first = false;
        }

        if (kinds.contains(LearningTimelineEntry.KIND_MEMORY)) {
            if (!first) sql.append(" UNION ALL ");
            sql.append("""
                SELECT m.id::TEXT AS event_id, 'memory' AS event_kind, m.created_at AS event_at,
                       m.source_problem_id AS problem_id, p.title AS problem_title,
                       (m.confidence * 100)::INTEGER AS payload_int, m.memory_type AS payload_text
                FROM ai_learner_memory m
                LEFT JOIN problem p ON p.id = m.source_problem_id
                WHERE m.user_id = ? AND m.enabled = TRUE
                  AND m.created_at >= ? AND m.created_at < ?
                """);
            params.add(userId);
            params.add(Timestamp.from(from));
            params.add(Timestamp.from(to));
            first = false;
        }

        if (kinds.contains(LearningTimelineEntry.KIND_AI_EVENT)) {
            if (!first) sql.append(" UNION ALL ");
            sql.append("""
                SELECT e.id::TEXT AS event_id, 'ai_event' AS event_kind, e.created_at AS event_at,
                       e.problem_id, p.title AS problem_title,
                       NULL AS payload_int, e.event_type AS payload_text
                FROM ai_learning_event e
                LEFT JOIN problem p ON p.id = e.problem_id
                WHERE e.user_id = ? AND e.created_at >= ? AND e.created_at < ?
                """);
            params.add(userId);
            params.add(Timestamp.from(from));
            params.add(Timestamp.from(to));
            first = false;
        }

        if (kinds.contains(LearningTimelineEntry.KIND_NOTEBOOK)) {
            if (!first) sql.append(" UNION ALL ");
            sql.append("""
                SELECT n.id AS event_id, 'notebook' AS event_kind, n.create_time AS event_at,
                       n.problem_id, p.title AS problem_title,
                       NULL AS payload_int, n.entry_type AS payload_text
                FROM ai_learner_notebook n
                LEFT JOIN problem p ON p.id = n.problem_id
                WHERE n.user_id = ? AND n.is_deleted = FALSE
                  AND n.create_time >= ? AND n.create_time < ?
                """);
            params.add(userId);
            params.add(Timestamp.from(from));
            params.add(Timestamp.from(to));
        }

        String fullSql = "SELECT * FROM (" + sql + ") combined ORDER BY event_at DESC LIMIT ?";
        params.add(limit);

        return jdbcTemplate.query(fullSql, (rs, rowNum) -> mapRow(rs), params.toArray());
    }

    private LearningTimelineEntry mapRow(ResultSet rs) throws SQLException {
        String eventId = rs.getString("event_id");
        String eventKind = rs.getString("event_kind");
        Timestamp ts = rs.getTimestamp("event_at");
        Instant eventAt = ts != null ? ts.toInstant() : null;
        long problemIdRaw = rs.getLong("problem_id");
        Long problemId = rs.wasNull() ? null : problemIdRaw;
        String problemTitle = rs.getString("problem_title");
        int payloadIntRaw = rs.getInt("payload_int");
        Integer payloadInt = rs.wasNull() ? null : payloadIntRaw;
        String payloadText = rs.getString("payload_text");

        String summary = buildSummary(eventKind, problemTitle, payloadInt, payloadText);
        Map<String, Object> meta = buildMeta(eventKind, payloadInt, payloadText);

        return new LearningTimelineEntry(
                eventId, eventKind, eventAt, problemId, problemTitle,
                summary, false, meta
        );
    }

    private String buildSummary(String kind, String title, Integer payloadInt, String payloadText) {
        String safeTitle = title != null ? title : "未知题目";
        return switch (kind) {
            case LearningTimelineEntry.KIND_SUBMISSION -> {
                if (payloadInt != null && payloadInt == RESULT_AC) {
                    yield "AC 了「" + safeTitle + "」";
                }
                yield "提交了「" + safeTitle + "」";
            }
            case LearningTimelineEntry.KIND_MEMORY -> {
                String memType = payloadText != null ? payloadText : "记忆";
                yield "记录了一条" + memType + "（" + safeTitle + "）";
            }
            case LearningTimelineEntry.KIND_AI_EVENT -> {
                String eventType = payloadText != null ? payloadText : "学习";
                yield eventType + "（" + safeTitle + "）";
            }
            case LearningTimelineEntry.KIND_NOTEBOOK -> "记了一条笔记「" + safeTitle + "」";
            default -> "学习事件";
        };
    }

    private Map<String, Object> buildMeta(String kind, Integer payloadInt, String payloadText) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (payloadInt != null) {
            meta.put("payload_int", payloadInt);
        }
        if (payloadText != null) {
            meta.put("payload_text", payloadText);
        }
        if (LearningTimelineEntry.KIND_SUBMISSION.equals(kind) && payloadInt != null) {
            meta.put("is_ac", payloadInt == RESULT_AC);
        }
        return meta;
    }
}
