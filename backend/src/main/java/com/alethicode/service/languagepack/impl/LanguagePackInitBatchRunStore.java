package com.alethicode.service.languagepack.impl;

import com.alethicode.util.HashUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LanguagePackInitBatchRunStore {

    private final JdbcTemplate jdbcTemplate;

    public LanguagePackInitBatchRunStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long startBatchRun(Long taskId,
                              String stageName,
                              Long documentId,
                              int chapterIndex,
                              int batchStartPage,
                              int batchEndPage,
                              int requestedWindowSize,
                              int effectiveWindowSize,
                              String inputHash) {
        Integer nextAttempt = jdbcTemplate.query(
                """
                SELECT max(attempt_no)
                FROM language_pack_init_batch_run
                WHERE task_id = ?
                  AND stage_name = ?
                  AND coalesce(document_id, 0) = coalesce(?, 0)
                  AND chapter_index = ?
                  AND batch_start_page = ?
                  AND batch_end_page = ?
                """,
                rs -> rs.next() ? rs.getObject(1, Integer.class) : null,
                taskId,
                stageName,
                documentId,
                chapterIndex,
                batchStartPage,
                batchEndPage
        );
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_batch_run(
                    task_id, stage_name, document_id, chapter_index,
                    batch_start_page, batch_end_page,
                    requested_window_size, effective_window_size,
                    status, attempt_no, input_hash, output_hash,
                    failure_reason, output_json, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'running', ?, ?, '', '', '{}', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                stageName,
                documentId,
                chapterIndex,
                batchStartPage,
                batchEndPage,
                requestedWindowSize,
                effectiveWindowSize,
                nextAttempt == null ? 1 : nextAttempt + 1,
                safeHash(inputHash)
        );
    }

    public void completeBatchRun(Long runId, String outputJson) {
        String normalized = normalizeJson(outputJson);
        jdbcTemplate.update(
                """
                UPDATE language_pack_init_batch_run
                SET status = 'completed',
                    output_json = ?,
                    output_hash = ?,
                    failure_reason = '',
                    update_time = now()
                WHERE id = ?
                """,
                normalized,
                HashUtils.sha256(normalized),
                runId
        );
    }

    public void splitBatchRun(Long runId, String failureReason) {
        jdbcTemplate.update(
                """
                UPDATE language_pack_init_batch_run
                SET status = 'split',
                    failure_reason = ?,
                    update_time = now()
                WHERE id = ?
                """,
                blankSafe(failureReason),
                runId
        );
    }

    public void failBatchRun(Long runId, String failureReason) {
        jdbcTemplate.update(
                """
                UPDATE language_pack_init_batch_run
                SET status = 'failed',
                    failure_reason = ?,
                    update_time = now()
                WHERE id = ?
                """,
                blankSafe(failureReason),
                runId
        );
    }

    public Map<String, Object> recordReuseFrom(Map<String, Object> sourceRow) {
        Long reusedId = jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_batch_run(
                    task_id, stage_name, document_id, chapter_index,
                    batch_start_page, batch_end_page,
                    requested_window_size, effective_window_size,
                    status, attempt_no, input_hash, output_hash,
                    failure_reason, output_json, create_time, update_time
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'reused', ?, ?, ?, '', ?, now(), now())
                RETURNING id
                """,
                Long.class,
                longVal(sourceRow.get("task_id")),
                stringVal(sourceRow.get("stage_name")),
                longVal(sourceRow.get("document_id")),
                intVal(sourceRow.get("chapter_index")),
                intVal(sourceRow.get("batch_start_page")),
                intVal(sourceRow.get("batch_end_page")),
                intVal(sourceRow.get("requested_window_size")),
                intVal(sourceRow.get("effective_window_size")),
                intVal(sourceRow.get("attempt_no")) + 1,
                stringVal(sourceRow.get("input_hash")),
                stringVal(sourceRow.get("output_hash")),
                normalizeJson(sourceRow.get("output_json"))
        );
        return findById(reusedId);
    }

    public Map<String, Object> findReusableBatch(Long taskId,
                                                 String stageName,
                                                 Long documentId,
                                                 int chapterIndex,
                                                 int batchStartPage,
                                                 int batchEndPage,
                                                 String inputHash) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT *
                FROM language_pack_init_batch_run
                WHERE task_id = ?
                  AND stage_name = ?
                  AND coalesce(document_id, 0) = coalesce(?, 0)
                  AND chapter_index = ?
                  AND batch_start_page = ?
                  AND batch_end_page = ?
                  AND input_hash = ?
                  AND status IN ('completed', 'reused')
                ORDER BY id DESC
                LIMIT 1
                """,
                taskId,
                stageName,
                documentId,
                chapterIndex,
                batchStartPage,
                batchEndPage,
                safeHash(inputHash)
        );
        if (!rows.isEmpty()) {
            return rows.getFirst();
        }
        List<Map<String, Object>> crossTaskRows = jdbcTemplate.queryForList(
                """
                SELECT *
                FROM language_pack_init_batch_run
                WHERE stage_name = ?
                  AND input_hash = ?
                  AND status = 'completed'
                  AND output_json IS NOT NULL
                ORDER BY id DESC
                LIMIT 1
                """,
                stageName,
                safeHash(inputHash)
        );
        if (!crossTaskRows.isEmpty()) {
            return crossTaskRows.getFirst();
        }
        return null;
    }

    public Map<String, Object> findSplitBatch(Long taskId,
                                              String stageName,
                                              Long documentId,
                                              int chapterIndex,
                                              int batchStartPage,
                                              int batchEndPage,
                                              String inputHash) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT *
                FROM language_pack_init_batch_run
                WHERE task_id = ?
                  AND stage_name = ?
                  AND coalesce(document_id, 0) = coalesce(?, 0)
                  AND chapter_index = ?
                  AND batch_start_page = ?
                  AND batch_end_page = ?
                  AND input_hash = ?
                  AND status = 'split'
                ORDER BY id DESC
                LIMIT 1
                """,
                taskId,
                stageName,
                documentId,
                chapterIndex,
                batchStartPage,
                batchEndPage,
                safeHash(inputHash)
        );
        if (rows.isEmpty()) {
            return null;
        }
        return rows.getFirst();
    }

    public int countByStatus(Long taskId, String stageName, String status) {
        Integer value = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM language_pack_init_batch_run
                WHERE task_id = ? AND stage_name = ? AND status = ?
                """,
                Integer.class,
                taskId,
                stageName,
                status
        );
        return value == null ? 0 : value;
    }

    private Map<String, Object> findById(Long runId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM language_pack_init_batch_run WHERE id = ?",
                runId
        );
        if (rows.isEmpty()) {
            throw new IllegalStateException("Batch run not found: " + runId);
        }
        return rows.getFirst();
    }

    private String normalizeJson(Object value) {
        if (value == null) {
            return "{}";
        }
        String text = String.valueOf(value).strip();
        return text.isBlank() ? "{}" : text;
    }

    private String safeHash(String raw) {
        return HashUtils.sha256(raw == null ? "" : raw);
    }

    private String stringVal(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).strip();
    }

    private Long longVal(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String blankSafe(String value) {
        return value == null ? "" : value;
    }
}
