package com.alethicode.service.languagepack.impl;

import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.languagepack.LanguagePackInitExecutionService;
import com.alethicode.service.languagepack.LanguagePackInitStageLabels;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;

@Service
public class LanguagePackInitExecutionServiceImpl implements LanguagePackInitExecutionService {

    private final JdbcTemplate jdbcTemplate;

    public LanguagePackInitExecutionServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void beginStep(Long taskId, String stepKey, String message, Integer progressCurrent, Integer progressTotal) {
        TaskExecutionRow row = lockTask(taskId);
        if ("running".equals(row.activeStatus())) {
            String runningStepLabel = LanguagePackInitStageLabels.labelZh(row.activeStepKey());
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "当前任务正在执行：" + runningStepLabel
            );
        }
        jdbcTemplate.update(
                """
                UPDATE language_pack_init_task
                SET active_step_key = ?,
                    active_status = 'running',
                    active_message = ?,
                    progress_current = ?,
                    progress_total = ?,
                    active_started_at = now(),
                    update_time = now()
                WHERE id = ?
                """,
                stepKey,
                normalizeMessage(message),
                progressCurrent,
                progressTotal,
                taskId
        );
        insertStageLog(taskId, stepKey, stepKey, normalizeMessage(message));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reportProgress(Long taskId, String stepKey, String message, Integer progressCurrent, Integer progressTotal) {
        TaskExecutionRow row = lockTask(taskId);
        if (!"running".equals(row.activeStatus()) || !stepKey.equals(row.activeStepKey())) {
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE language_pack_init_task
                SET active_message = ?,
                    progress_current = ?,
                    progress_total = ?,
                    update_time = now()
                WHERE id = ?
                """,
                normalizeMessage(message),
                progressCurrent,
                progressTotal,
                taskId
        );
        insertStageLog(taskId, stepKey, stepKey, normalizeMessage(message));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishStep(Long taskId, String stepKey, String message) {
        TaskExecutionRow row = lockTask(taskId);
        if (!"running".equals(row.activeStatus()) || !stepKey.equals(row.activeStepKey())) {
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE language_pack_init_task
                SET active_step_key = null,
                    active_status = 'idle',
                    active_message = null,
                    progress_current = null,
                    progress_total = null,
                    active_started_at = null,
                    update_time = now()
                WHERE id = ?
                """,
                taskId
        );
        if (message != null && !message.isBlank()) {
            insertStageLog(taskId, stepKey, stepKey, message.strip());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearOnFailure(Long taskId) {
        TaskExecutionRow row = lockTask(taskId);
        if ("idle".equals(row.activeStatus())
                && row.activeStepKey() == null
                && row.activeMessage() == null
                && row.progressCurrent() == null
                && row.progressTotal() == null) {
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE language_pack_init_task
                SET active_step_key = null,
                    active_status = 'idle',
                    active_message = null,
                    progress_current = null,
                    progress_total = null,
                    active_started_at = null,
                    update_time = now()
                WHERE id = ?
                """,
                taskId
        );
    }

    private TaskExecutionRow lockTask(Long taskId) {
        TaskExecutionRow row = jdbcTemplate.query(
                """
                SELECT id,
                       active_step_key,
                       active_status,
                       active_message,
                       progress_current,
                       progress_total
                FROM language_pack_init_task
                WHERE id = ?
                FOR UPDATE
                """,
                rs -> rs.next() ? mapRow(rs) : null,
                taskId
        );
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Init task not found");
        }
        return row;
    }

    private TaskExecutionRow mapRow(ResultSet rs) throws SQLException {
        Integer progressCurrent = rs.getObject("progress_current", Integer.class);
        Integer progressTotal = rs.getObject("progress_total", Integer.class);
        return new TaskExecutionRow(
                rs.getLong("id"),
                rs.getString("active_step_key"),
                normalizeStatus(rs.getString("active_status")),
                rs.getString("active_message"),
                progressCurrent,
                progressTotal
        );
    }

    private void insertStageLog(Long taskId, String fromStage, String toStage, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_stage_log(task_id, from_stage, to_stage, message, create_time)
                VALUES (?, ?, ?, ?, now())
                """,
                taskId,
                fromStage,
                toStage,
                message
        );
    }

    private String normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return "idle";
        }
        return rawStatus.strip();
    }

    private String normalizeMessage(String message) {
        return message == null ? null : message.strip();
    }

    private record TaskExecutionRow(
            Long id,
            String activeStepKey,
            String activeStatus,
            String activeMessage,
            Integer progressCurrent,
            Integer progressTotal
    ) {
    }
}
