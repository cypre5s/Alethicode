package com.alethicode.service.languagepack.impl;

import com.alethicode.dto.request.CreateLanguagePackInitTaskRequest;
import com.alethicode.dto.response.LanguagePackInitTaskResponse;
import com.alethicode.dto.response.LanguagePackInitTaskResponse.LanguagePackSummary;
import com.alethicode.exception.BadRequestException;
import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.languagepack.LanguagePackInitExecutionService;
import com.alethicode.service.languagepack.LanguagePackInitService;
import com.alethicode.service.languagepack.LanguagePackInitStageLabels;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(rollbackFor = Exception.class)
public class LanguagePackInitServiceImpl implements LanguagePackInitService {

    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "created", Set.of("normalizing", "failed"),
            "normalizing", Set.of("parsing", "failed"),
            "parsing", Set.of("kc_ready", "failed"),
            "kc_ready", Set.of("segments_ready", "failed"),
            "segments_ready", Set.of("units_ready", "failed"),
            "units_ready", Set.of("oj_candidates_ready", "failed"),
            "oj_candidates_ready", Set.of("problem_packages_ready", "failed"),
            "problem_packages_ready", Set.of("problems_validated", "failed"),
            "problems_validated", Set.of("published", "failed")
    );

    private final JdbcTemplate jdbcTemplate;
    private final LanguagePackInitExecutionService executionService;

    public LanguagePackInitServiceImpl(JdbcTemplate jdbcTemplate,
                                       LanguagePackInitExecutionService executionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.executionService = executionService;
    }

    @Override
    public LanguagePackInitTaskResponse createTask(CreateLanguagePackInitTaskRequest request, Long creatorId) {
        Integer existingVersion = jdbcTemplate.query(
                "SELECT MAX(version) FROM language_pack WHERE slug = ?",
                rs -> rs.next() ? rs.getObject(1, Integer.class) : null,
                request.slug()
        );
        int nextVersion = existingVersion == null ? 1 : existingVersion + 1;

        KeyHolder packKeyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO language_pack(slug, version, name, primary_language, status, creator_id, create_time, update_time)
                    VALUES (?, ?, ?, ?, 'draft', ?, now(), now())
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, request.slug());
            ps.setInt(2, nextVersion);
            ps.setString(3, request.name());
            ps.setString(4, request.primaryLanguage());
            if (creatorId != null) {
                ps.setLong(5, creatorId);
            } else {
                ps.setNull(5, java.sql.Types.BIGINT);
            }
            return ps;
        }, packKeyHolder);
        Long languagePackId = ((Number) packKeyHolder.getKeys().get("id")).longValue();

        boolean enableObjective = request.enableObjectiveQuestions() != null && request.enableObjectiveQuestions();

        KeyHolder taskKeyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO language_pack_init_task(language_pack_id, stage,
                                                       enable_objective_questions, create_time, update_time)
                    VALUES (?, 'created', ?, now(), now())
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, languagePackId);
            ps.setBoolean(2, enableObjective);
            return ps;
        }, taskKeyHolder);
        Long taskId = ((Number) taskKeyHolder.getKeys().get("id")).longValue();

        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_stage_log(task_id, from_stage, to_stage, message, create_time)
                VALUES (?, '', 'created', ?, now())
                """,
                taskId, LanguagePackInitStageLabels.formatTaskCreated()
        );

        return getTask(taskId);
    }

    @Override
    @Transactional(readOnly = true)
    public LanguagePackInitTaskResponse getTask(Long taskId) {
        List<LanguagePackInitTaskResponse> results = jdbcTemplate.query(
                """
                SELECT t.id, t.language_pack_id, t.stage,
                       t.active_step_key, t.active_status, t.active_message,
                       t.progress_current, t.progress_total,
                       t.enable_objective_questions, t.failure_reason, t.create_time, t.update_time,
                       lp.id AS lp_id, lp.slug, lp.version, lp.name, lp.primary_language, lp.status,
                       lp.document_count, lp.page_count, lp.chapter_count, lp.kc_count,
                       lp.example_count, lp.problem_count, lp.creator_id
                FROM language_pack_init_task t
                JOIN language_pack lp ON lp.id = t.language_pack_id
                WHERE t.id = ?
                """,
                (rs, rowNum) -> mapTaskRow(rs),
                taskId
        );
        if (results.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Init task not found");
        }
        return results.getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LanguagePackInitTaskResponse> listTasks() {
        return jdbcTemplate.query(
                """
                SELECT t.id, t.language_pack_id, t.stage,
                       t.active_step_key, t.active_status, t.active_message,
                       t.progress_current, t.progress_total,
                       t.enable_objective_questions, t.failure_reason, t.create_time, t.update_time,
                       lp.id AS lp_id, lp.slug, lp.version, lp.name, lp.primary_language, lp.status,
                       lp.document_count, lp.page_count, lp.chapter_count, lp.kc_count,
                       lp.example_count, lp.problem_count, lp.creator_id
                FROM language_pack_init_task t
                JOIN language_pack lp ON lp.id = t.language_pack_id
                ORDER BY t.create_time DESC
                """,
                (rs, rowNum) -> mapTaskRow(rs)
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void advanceStage(Long taskId, String targetStage) {
        String currentStage = jdbcTemplate.queryForObject(
                "SELECT stage FROM language_pack_init_task WHERE id = ?",
                String.class,
                taskId
        );
        if (currentStage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Init task not found");
        }

        Set<String> allowed = VALID_TRANSITIONS.get(currentStage);
        if (allowed == null || !allowed.contains(targetStage)) {
            throw new BadRequestException(
                    "Invalid stage transition: " + currentStage + " -> " + targetStage
            );
        }

        jdbcTemplate.update(
                "UPDATE language_pack_init_task SET stage = ?, update_time = now() WHERE id = ?",
                targetStage, taskId
        );

        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_stage_log(task_id, from_stage, to_stage, message, create_time)
                VALUES (?, ?, ?, ?, now())
                """,
                taskId, currentStage, targetStage,
                LanguagePackInitStageLabels.formatAdvance(currentStage, targetStage)
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failTask(Long taskId, String reason) {
        Map<String, Object> taskRow = jdbcTemplate.queryForMap(
                """
                SELECT stage, active_step_key, active_status
                FROM language_pack_init_task
                WHERE id = ?
                """,
                taskId
        );
        String currentStage = (String) taskRow.get("stage");
        if ("failed".equals(currentStage) || "published".equals(currentStage)) {
            return;
        }
        String activeStepKey = (String) taskRow.get("active_step_key");
        String activeStatus = (String) taskRow.get("active_status");
        String logFromStage = "running".equals(activeStatus) && activeStepKey != null && !activeStepKey.isBlank()
                ? activeStepKey
                : currentStage;

        executionService.clearOnFailure(taskId);

        jdbcTemplate.update(
                "UPDATE language_pack_init_task SET stage = 'failed', failure_reason = ?, update_time = now() WHERE id = ?",
                reason, taskId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_stage_log(task_id, from_stage, to_stage, message, create_time)
                VALUES (?, ?, 'failed', ?, now())
                """,
                taskId, logFromStage, reason
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreStage(Long taskId, String targetStage, String message) {
        String currentStage = jdbcTemplate.queryForObject(
                "SELECT stage FROM language_pack_init_task WHERE id = ?",
                String.class,
                taskId
        );
        if (currentStage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Init task not found");
        }
        if ("published".equals(currentStage)) {
            throw new BadRequestException("Cannot restore a published task");
        }
        jdbcTemplate.update(
                "UPDATE language_pack_init_task SET stage = ?, failure_reason = null, update_time = now() WHERE id = ?",
                targetStage,
                taskId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_stage_log(task_id, from_stage, to_stage, message, create_time)
                VALUES (?, ?, ?, ?, now())
                """,
                taskId,
                currentStage,
                targetStage,
                message == null || message.isBlank()
                        ? LanguagePackInitStageLabels.formatRestoreDefault(currentStage, targetStage)
                        : message
        );
    }

    private LanguagePackInitTaskResponse mapTaskRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        long creatorIdRaw = rs.getLong("creator_id");
        Long creatorId = rs.wasNull() ? null : creatorIdRaw;
        return new LanguagePackInitTaskResponse(
                rs.getLong("id"),
                rs.getLong("language_pack_id"),
                rs.getString("stage"),
                rs.getString("active_step_key"),
                rs.getString("active_status"),
                rs.getString("active_message"),
                rs.getObject("progress_current", Integer.class),
                rs.getObject("progress_total", Integer.class),
                rs.getBoolean("enable_objective_questions"),
                rs.getString("failure_reason"),
                toInstant(rs.getTimestamp("create_time")),
                toInstant(rs.getTimestamp("update_time")),
                new LanguagePackSummary(
                        rs.getLong("lp_id"),
                        rs.getString("slug"),
                        rs.getInt("version"),
                        rs.getString("name"),
                        rs.getString("primary_language"),
                        rs.getString("status"),
                        rs.getInt("document_count"),
                        rs.getInt("page_count"),
                        rs.getInt("chapter_count"),
                        rs.getInt("kc_count"),
                        rs.getInt("example_count"),
                        rs.getInt("problem_count"),
                        creatorId
                )
        );
    }

    private Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
