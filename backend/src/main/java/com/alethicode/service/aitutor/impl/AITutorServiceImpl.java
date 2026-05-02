package com.alethicode.service.aitutor.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.dto.request.PreflightCheckRequest;
import com.alethicode.dto.request.TutorInferenceRequest;
import com.alethicode.dto.response.ApiResponse;

import com.alethicode.service.aitutor.language.TutorLanguageSupport;
import com.alethicode.service.aitutor.supplement.BeginnerSupplementPlannerService;
import static com.alethicode.util.ServiceParseUtils.randomId;
import static com.alethicode.util.ServiceParseUtils.stringValue;
import static com.alethicode.util.ServiceParseUtils.trimToEmpty;
import static com.alethicode.util.ServiceParseUtils.trimToNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(rollbackFor = Exception.class)
public class AITutorServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(AITutorServiceImpl.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Map<String, List<List<String>>> CALIBRATION_CONCEPT_KEYWORDS = Map.of(
            "loop", List.of(
                    List.of("init", "initial", "start", "初始化", "初始", "赋值"),
                    List.of("condition", "range", "条件", "判断", "终止", "直到"),
                    List.of("update", "increment", "decrement", "i++", "变化", "更新", "递增", "递减"),
                    List.of("body", "iterate", "repeat", "循环体", "重复执行", "每轮")
            ),
            "array", List.of(
                    List.of("index", "indices", "下标", "索引", "越界"),
                    List.of("length", "size", "len", "数组长度", "边界"),
                    List.of("if", "check", "guard", "检查", "校验", "判断"),
                    List.of("<", "<=", "n-1", "last index", "0 到", "范围")
            ),
            "recursion", List.of(
                    List.of("base case", "termination", "stop", "终止条件", "边界条件", "出口"),
                    List.of("subproblem", "smaller", "递归调用", "子问题", "参数变化", "收敛"),
                    List.of("return", "回溯", "返回", "结果合并"),
                    List.of("stack overflow", "infinite", "死循环", "栈溢出", "无限递归")
            )
    );
    private static final List<String> CALIBRATION_EXPLANATION_MARKERS = List.of(
            "因为", "所以", "如果", "那么", "先", "然后", "最后", "if", "then", "because"
    );
    private static final String REVIEW_DUE_TAXONOMY_FILTER = """
              and n.error_taxonomy in (
                  'syntax_error',
                  'runtime_error',
                  'logic_error',
                  'boundary_condition',
                  'performance',
                  'algorithm_error',
                  'input_parsing',
                  'name_or_type_error'
              )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final com.alethicode.service.ai.AiModelGateway aiModelGateway;
    private final BeginnerSupplementPlannerService beginnerSupplementPlannerService;

    @Autowired
    public AITutorServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                              com.alethicode.service.ai.AiModelGateway aiModelGateway,
                              BeginnerSupplementPlannerService beginnerSupplementPlannerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.aiModelGateway = aiModelGateway;
        this.beginnerSupplementPlannerService = beginnerSupplementPlannerService;
    }

    public AITutorServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this(jdbcTemplate, objectMapper, null, new BeginnerSupplementPlannerService(jdbcTemplate));
    }

    public AITutorServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                              com.alethicode.service.ai.AiModelGateway aiModelGateway) {
        this(jdbcTemplate, objectMapper, aiModelGateway, new BeginnerSupplementPlannerService(jdbcTemplate));
    }

    public ApiResponse<Object> inference(TutorInferenceRequest request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }

        if (request.problemId() == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目 ID 不能为空");
        }

        String taskId = randomId(32);
        String sessionId = randomId(32);

        String guidance = "请先解释你当前的思路，再逐步定位可能出错的边界条件。";
        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("content", guidance);
        responseData.put("source", "java-migrated");

        jdbcTemplate.update(
                """
                insert into ai_dialogue_session(session_id, user_id, problem_id, current_stage, is_active, context_window)
                values (?, ?, ?, 'reading', true, cast(? as jsonb))
                """,
                sessionId,
                auth.userId(),
                request.problemId(),
                "{}"
        );

        Map<String, Object> requestData = new LinkedHashMap<>();
        requestData.put("problem_id", request.problemId());
        requestData.put("submission_id", request.submissionId());
        requestData.put("language", trimToEmpty(request.language()));
        requestData.put("code_snippet", trimToEmpty(request.codeSnippet()));
        requestData.put("compiler_output", trimToEmpty(request.compilerOutput()));
        requestData.put("assignment_id", request.assignmentId());
        requestData.put("classroom_id", request.classroomId());

        jdbcTemplate.update(
                """
                insert into ai_inference_task(task_id, user_id, submission_id, session_id, status,
                                              request_data, response_data, rag_hit, rag_score)
                values (?, ?, ?, ?, 'completed', cast(? as jsonb), cast(? as jsonb), ?, ?)
                """,
                taskId,
                auth.userId(),
                trimToNull(request.submissionId()),
                sessionId,
                toJson(requestData),
                toJson(responseData),
                false,
                null
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", taskId);
        payload.put("session_id", sessionId);
        payload.put("status", "completed");
        payload.put("rag_hit", false);
        payload.put("rag_score", null);
        payload.put("guidance", guidance);
        payload.put("pedagogy", null);
        return ApiResponse.success(payload);
    }

    public ApiResponse<Object> taskStatus(String taskId, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String normalizedTaskId = trimToNull(taskId);
        if (normalizedTaskId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "task_id is required");
        }
        TaskRow task = findTask(normalizedTaskId);
        if (task == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Task not found");
        }
        if (!task.userId().equals(auth.userId()) && !auth.adminManager()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        return ApiResponse.success(task.toPayload(parseJsonMap(task.responseData())));
    }

    public ApiResponse<Object> session(String sessionId, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String normalized = trimToNull(sessionId);
        if (normalized == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "session_id is required");
        }
        SessionRow row = findSession(normalized);
        if (row == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Session not found");
        }
        if (!row.userId().equals(auth.userId()) && !auth.adminManager()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_id", row.sessionId());
        payload.put("current_stage", row.currentStage());
        payload.put("is_active", row.active());
        payload.put("context_window", parseJsonMap(row.contextWindow()));
        payload.put("create_time", formatTime(row.createTime()));
        payload.put("update_time", formatTime(row.updateTime()));
        return ApiResponse.success(payload);
    }

    public ApiResponse<Object> closeSession(String sessionId, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String normalized = trimToNull(sessionId);
        if (normalized == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "session_id is required");
        }
        SessionRow row = findSession(normalized);
        if (row == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Session not found");
        }
        if (!row.userId().equals(auth.userId()) && !auth.adminManager()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        jdbcTemplate.update(
                "update ai_dialogue_session set is_active = false, update_time = now() where session_id = ?",
                normalized
        );
        return ApiResponse.success("Session closed");
    }

    public ApiResponse<Object> skillRadar(Map<String, String> params, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long languagePackId = resolveAuthorizedLanguagePackId(params, auth);
        Long targetUserId = parseLong(params.get("user_id"));
        if (targetUserId == null) {
            targetUserId = auth.userId();
        }
        if (!targetUserId.equals(auth.userId()) && !auth.adminRole()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }

        List<Map<String, Object>> kcRows = jdbcTemplate.query(
                """
                select kc.id,
                       kc.name,
                       count(s.id) as submission_count,
                       coalesce(sum(case when s.result = 0 then 1 else 0 end), 0) as accepted_count
                from ai_knowledge_component kc
                left join ai_problem_kc_mapping m on m.kc_id = kc.id and m.language_pack_id = ?
                left join submission s on s.problem_id = m.problem_id and s.user_id = ?
                where kc.language_pack_id = ?
                group by kc.id, kc.name
                order by submission_count desc, accepted_count desc, kc.id asc
                limit 6
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", trimToEmpty(rs.getString("name")));
                    item.put("submission_count", rs.getLong("submission_count"));
                    item.put("accepted_count", rs.getLong("accepted_count"));
                    return item;
                },
                languagePackId,
                targetUserId,
                languagePackId
        );

        List<String> dimensions = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        List<Double> rdValues = new ArrayList<>();
        List<String> dataSources = new ArrayList<>();
        Map<String, Object> trends = new LinkedHashMap<>();
        for (Map<String, Object> row : kcRows) {
            String name = trimToEmpty(stringValue(row.get("name")));
            if (name.isBlank()) {
                continue;
            }
            long submissionCount = longValue(row.get("submission_count"));
            long acceptedCount = longValue(row.get("accepted_count"));
            double ratio = submissionCount == 0 ? 0.0 : acceptedCount * 1.0 / submissionCount;
            double mastery = acceptedCount > 0 ? Math.max(ratio, 0.7) : ratio;
            double confidence = Math.min(1.0, submissionCount / 5.0);
            dimensions.add(name);
            values.add(mastery);
            rdValues.add(confidence);
            dataSources.add("kc");
            trends.put(name, Map.of("label", "new", "recent_delta", 0.0));
        }

        Map<String, Object> radar = new LinkedHashMap<>();
        radar.put("dimensions", dimensions);
        radar.put("values", values);
        radar.put("rd_values", rdValues);
        radar.put("trends", trends);
        radar.put("data_sources", dataSources);
        radar.put("code_quality", Map.of());

        return ApiResponse.success(Map.of(
                "user_id", targetUserId,
                "radar_data", radar,
                "timestamp", nowIso()
        ));
    }

    public ApiResponse<Object> skillHeatmap(Map<String, String> params, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long targetUserId = parseLong(params.get("user_id"));
        if (targetUserId == null) {
            targetUserId = auth.userId();
        }
        if (!targetUserId.equals(auth.userId()) && !auth.adminRole()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }

        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select to_char(create_time at time zone 'UTC', 'YYYY-MM-DD') as day,
                       count(*) as submit_count,
                       sum(case when result = 0 then 1 else 0 end) as accepted_count
                from submission
                where user_id = ?
                group by to_char(create_time at time zone 'UTC', 'YYYY-MM-DD')
                order by day desc
                limit 366
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("date", rs.getString("day"));
                    item.put("submit_count", rs.getLong("submit_count"));
                    item.put("accepted_count", rs.getLong("accepted_count"));
                    return item;
                },
                targetUserId
        );

        Map<String, Map<String, Object>> byDay = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byDay.put(String.valueOf(row.get("date")), row);
        }

        LocalDate end = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = end.minusDays(365);
        List<String> dates = new ArrayList<>();
        List<Long> acCounts = new ArrayList<>();
        List<Integer> activityLevels = new ArrayList<>();
        long totalAc = 0;
        int activeDays = 0;
        int currentStreak = 0;
        int maxStreak = 0;
        int rollingStreak = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            String key = day.toString();
            Map<String, Object> row = byDay.get(key);
            long submitCount = row == null ? 0L : longValue(row.get("submit_count"));
            long acceptedCount = row == null ? 0L : longValue(row.get("accepted_count"));
            dates.add(key);
            acCounts.add(acceptedCount);
            activityLevels.add(activityLevel(acceptedCount));
            totalAc += acceptedCount;
            if (submitCount > 0) {
                activeDays++;
            }
            if (acceptedCount > 0) {
                rollingStreak++;
                if (rollingStreak > maxStreak) {
                    maxStreak = rollingStreak;
                }
            } else {
                rollingStreak = 0;
            }
        }
        for (int i = acCounts.size() - 1; i >= 0; i--) {
            if (acCounts.get(i) > 0) {
                currentStreak++;
            } else {
                break;
            }
        }

        return ApiResponse.success(Map.of(
                "user_id", targetUserId,
                "heatmap_data", Map.of(
                        "dates", dates,
                        "ac_counts", acCounts,
                        "activity_levels", activityLevels,
                        "total_ac", totalAc,
                        "active_days", activeDays,
                        "max_streak", maxStreak,
                        "current_streak", currentStreak
                ),
                "timestamp", nowIso()
        ));
    }

    private int activityLevel(long submitCount) {
        if (submitCount <= 0) {
            return 0;
        }
        if (submitCount == 1) {
            return 1;
        }
        if (submitCount <= 3) {
            return 2;
        }
        if (submitCount <= 5) {
            return 3;
        }
        return 4;
    }

    public ApiResponse<Object> recommendProblems(Map<String, String> params, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long targetUserId = parseLong(params.get("user_id"));
        if (targetUserId == null) {
            targetUserId = auth.userId();
        }
        if (!targetUserId.equals(auth.userId()) && !auth.adminRole()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }

        String strategy = trimToEmpty(params.getOrDefault("strategy", "balanced")).toLowerCase(Locale.ROOT);
        List<String> valid = List.of("weak", "forgotten", "balanced", "challenge", "adaptive");
        if (!valid.contains(strategy)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid strategy, must be one of: weak, forgotten, balanced, challenge, adaptive");
        }

        int count = parseInt(params.get("count"), 10);
        if (count < 1) count = 1;
        if (count > 50) count = 50;

        Long languagePackId = parseLong(params.get("language_pack_id"));

        List<Map<String, Object>> recommendations;
        if (languagePackId != null) {
            recommendations = recommendByMastery(targetUserId, languagePackId, strategy, count);
        } else {
            recommendations = recommendFallback(targetUserId, strategy, count);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_id", targetUserId);
        result.put("strategy", strategy);
        result.put("count", count);
        result.put("recommendations", recommendations);
        if (languagePackId != null) {
            Map<String, Object> supplementPlan = beginnerSupplementPlannerService.buildPlan(
                    targetUserId,
                    "warmup",
                    languagePackId,
                    null,
                    null,
                    null,
                    3
            );
            Map<String, Object> recommendedCard = extractRecommendedCard(supplementPlan);
            result.put("recommended_stage", stringValue(recommendedCard.get("education_goal")));
            result.put("recommended_question_type", stringValue(recommendedCard.get("card_type")));
            result.put("target_kcs", supplementPlan.getOrDefault("target_kcs", List.of()));
            result.put("why_this_now", stringValue(recommendedCard.get("why_this_now")));
        } else {
            result.put("recommended_stage", "apply");
            result.put("recommended_question_type", "coding_problem");
            result.put("target_kcs", List.of());
            result.put("why_this_now", "先做一题标准练习，再根据结果决定是否加练。");
        }
        result.put("timestamp", nowIso());
        return ApiResponse.success(result);
    }

    public ApiResponse<Object> supplementPlan(Map<String, Object> request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String trigger = trimToNull(stringValue(request.get("trigger")));
        Long languagePackId = parseLong(stringValue(request.get("language_pack_id")));
        if (trigger == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "trigger is required");
        }
        if (languagePackId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "language_pack_id is required");
        }
        Long problemId = parseLong(stringValue(request.get("problem_id")));
        Long submissionId = parseLong(stringValue(request.get("submission_id")));
        String errorTaxonomy = trimToNull(stringValue(request.get("error_taxonomy")));
        Integer requestedCount = parseIntObj(request.get("requested_count"), 3);
        Map<String, Object> result = beginnerSupplementPlannerService.buildPlan(
                auth.userId(),
                trigger,
                languagePackId,
                problemId,
                submissionId,
                errorTaxonomy,
                requestedCount
        );
        return ApiResponse.success(result);
    }

    private List<Map<String, Object>> recommendByMastery(Long userId, Long languagePackId, String strategy, int count) {
        String orderClause = switch (strategy) {
            case "weak"      -> "km.mastery ASC NULLS FIRST";
            case "forgotten" -> "km.last_attempt_at ASC NULLS FIRST";
            case "challenge" -> "km.mastery DESC NULLS LAST";
            case "adaptive"  -> "ABS(COALESCE(km.mastery, 0) - 0.5) ASC";
            default          -> "km.mastery ASC NULLS FIRST, km.last_attempt_at ASC NULLS FIRST";
        };
        String reasonText = switch (strategy) {
            case "weak"      -> "掌握度较低，需重点练习";
            case "forgotten" -> "长时间未练习，防止遗忘";
            case "challenge" -> "已有较好基础，挑战更高难度";
            case "adaptive"  -> "处于自适应难度区间，提升效率最高";
            default          -> "综合推荐";
        };

        return jdbcTemplate.query(
                """
                WITH ranked_kcs AS (
                    SELECT k.id AS kc_id, k.name AS kc_name, km.mastery, km.last_attempt_at
                    FROM language_pack_kc k
                    LEFT JOIN learner_kc_mastery km ON km.kc_id = k.id AND km.user_id = ?
                    WHERE k.language_pack_id = ?
                    ORDER BY """ + orderClause + """
                ),
                candidate_problems AS (
                    SELECT DISTINCT ON (p.id)
                           p.id, p._id, p.title, p.difficulty,
                           rk.kc_name, rk.mastery
                    FROM ranked_kcs rk
                    JOIN ai_problem_kc_mapping pm ON pm.kc_id = rk.kc_id
                    JOIN problem p ON p.id = pm.problem_id AND p.visible = true
                    WHERE NOT EXISTS (
                        SELECT 1 FROM submission s
                        WHERE s.user_id = ? AND s.problem_id = p.id AND s.result = 0
                    )
                )
                SELECT * FROM candidate_problems LIMIT ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("problem_id", rs.getLong("id"));
                    item.put("problem_key", rs.getString("_id"));
                    item.put("title", rs.getString("title"));
                    item.put("difficulty", rs.getString("difficulty"));
                    item.put("skill", rs.getString("kc_name"));
                    double mastery = rs.getDouble("mastery");
                    item.put("adaptive_score", rs.wasNull() ? null : Math.round(mastery * 1000.0) / 1000.0);
                    item.put("reason", reasonText + (rs.getString("kc_name") != null ? "（" + rs.getString("kc_name") + "）" : ""));
                    return item;
                },
                userId, languagePackId, userId, count);
    }

    private List<Map<String, Object>> recommendFallback(Long userId, String strategy, int count) {
        return jdbcTemplate.query(
                """
                SELECT p.id, p._id, p.title, p.difficulty
                FROM problem p
                WHERE p.visible = true
                  AND NOT EXISTS (
                    SELECT 1 FROM submission s
                    WHERE s.user_id = ? AND s.problem_id = p.id AND s.result = 0
                  )
                ORDER BY p.submission_number DESC, p.id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("problem_id", rs.getLong("id"));
                    item.put("problem_key", rs.getString("_id"));
                    item.put("title", rs.getString("title"));
                    item.put("difficulty", rs.getString("difficulty"));
                    item.put("reason", switch (strategy) {
                        case "weak" -> "针对薄弱知识点推荐";
                        case "forgotten" -> "针对遗忘风险推荐";
                        case "challenge" -> "挑战更高难度";
                        case "adaptive" -> "处于自适应难度区间";
                        default -> "平衡练习推荐";
                    });
                    return item;
                },
                userId, count);
    }

    public ApiResponse<Object> errorAttribution(Map<String, Object> request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String errorType = trimToNull(stringValue(request.get("error_type")));
        String stackTrace = trimToNull(stringValue(request.get("stack_trace")));
        if (errorType == null && stackTrace == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "error_type or stack_trace required");
        }
        String rootCause = errorType != null ? errorType : "runtime";
        if (stackTrace != null && stackTrace.toLowerCase(Locale.ROOT).contains("index")) {
            rootCause = "index_out_of_range";
        }
        return ApiResponse.success(Map.of(
                "root_cause", rootCause,
                "confidence", 0.7,
                "next_step", "请先检查边界条件与数组访问。"
        ));
    }

    public ApiResponse<Object> antiPatternAnalyze(Map<String, Object> request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String code = trimToEmpty(stringValue(request.get("code")));
        if (code.isBlank()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "code is required");
        }
        List<Map<String, Object>> antiPatterns = new ArrayList<>();
        if (code.contains("while True")) {
            antiPatterns.add(Map.of("type", "potential_infinite_loop", "message", "存在潜在无限循环"));
        }
        if (code.contains("print(") && code.contains("for")) {
            antiPatterns.add(Map.of("type", "debug_output_in_loop", "message", "循环体中存在频繁输出"));
        }
        return ApiResponse.success(Map.of("anti_patterns", antiPatterns, "total", antiPatterns.size()));
    }

    public ApiResponse<Object> evalFeedback(Map<String, Object> request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String taskId = trimToNull(stringValue(request.get("task_id")));
        if (taskId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "task_id is required");
        }
        List<Integer> executed = toIntList(request.get("executed_actions"));
        boolean practiced = toBoolean(request.get("practiced"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("updated", true);
        payload.put("execution_rate", executed.isEmpty() ? 0.0 : 1.0);
        payload.put("executed_indices", executed);
        payload.put("practiced", practiced);
        return ApiResponse.success(payload);
    }

    public ApiResponse<Object> safetyFeedback(Map<String, Object> request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String evalLogId = trimToNull(stringValue(request.get("eval_log_id")));
        String label = trimToNull(stringValue(request.get("label")));
        if (evalLogId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "eval_log_id is required");
        }
        if (label == null || !("true_leak".equals(label) || "false_positive".equals(label) || "unsure".equals(label))) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "label must be one of: true_leak, false_positive, unsure");
        }
        jdbcTemplate.update(
                """
                insert into ai_learning_event(user_id, event_type, extra_data)
                values (?, 'safety_feedback', cast(? as jsonb))
                """,
                auth.userId(),
                toJson(Map.of(
                        "eval_log_id", evalLogId,
                        "label", label,
                        "reason", trimToEmpty(stringValue(request.get("reason")))
                ))
        );
        return ApiResponse.success(Map.of("created", true));
    }

    public ApiResponse<Object> notebookList(Map<String, String> params, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String category = trimToNull(params.get("category"));
        List<Map<String, Object>> entries;
        String baseSql = """
                select n.id, n.problem_id, n.language, n.error_taxonomy, n.root_cause, n.fix_outcome,
                       n.student_reflection, n.tags::text as tags_json, n.evidence_ptr::text as evidence_json,
                       n.create_time, lpm.language_pack_id,
                       exists(select 1 from submission s
                              where s.user_id = n.user_id and s.problem_id = n.problem_id
                                and s.result = 0 and s.create_time > n.create_time) as conquered
                from ai_learner_notebook n
                left join language_pack_problem_mapping lpm on lpm.problem_id = n.problem_id
                where n.user_id = ? and n.is_deleted = false
                """;
        if (category == null) {
            entries = jdbcTemplate.query(
                    baseSql + " order by n.create_time desc limit 200",
                    (rs, rowNum) -> mapNotebookEntry(rs),
                    auth.userId()
            );
        } else {
            entries = jdbcTemplate.query(
                    baseSql + " and n.error_taxonomy = ? order by n.create_time desc limit 200",
                    (rs, rowNum) -> mapNotebookEntry(rs),
                    auth.userId(),
                    category
            );
        }
        return ApiResponse.success(Map.of("entries", entries, "total", entries.size()));
    }

    public ApiResponse<Object> notebookCreate(Map<String, Object> request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String id = randomId(32);
        Long problemId = parseLong(stringValue(request.get("problem_id")));
        String language = trimToNull(TutorLanguageSupport.normalizeLanguage(request.get("language")));
        if (language == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "请选择编程语言");
        }
        language = cut(language, 32, "");
        String errorCategory = com.alethicode.service.aitutor.contract.ErrorTaxonomy
                .normalize(stringValue(request.get("error_taxonomy")));
        String rootCause = cut(trimToEmpty(stringValue(request.get("root_cause"))), 2000, "");
        String fixOutcome = cut(trimToEmpty(stringValue(request.get("fix_outcome"))), 2000, "");
        String reflection = cut(trimToEmpty(stringValue(request.get("student_reflection"))), 2000, "");
        List<String> tags = toStringList(request.get("tags"), 20, 64);
        Map<String, Object> evidence = toStringMap(request.get("evidence_ptr"), 10, 64, 256);

        jdbcTemplate.update(
                """
                insert into ai_learner_notebook(id, user_id, problem_id, language, error_taxonomy,
                                                root_cause, fix_outcome, student_reflection, tags, evidence_ptr,
                                                is_deleted, create_time, update_time)
                values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), false, now(), now())
                """,
                id,
                auth.userId(),
                problemId,
                language,
                errorCategory,
                rootCause,
                fixOutcome,
                reflection,
                toJson(tags),
                toJson(evidence)
        );

        return ApiResponse.success(Map.of("id", id));
    }

    public ApiResponse<Object> notebookUpdate(Map<String, Object> request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String id = trimToNull(stringValue(request.get("id")));
        if (id == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "id is required");
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from ai_learner_notebook where id = ? and user_id = ? and is_deleted = false",
                Integer.class,
                id,
                auth.userId()
        );
        if (count == null || count == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Entry not found");
        }

        boolean any = false;
        if (request.containsKey("student_reflection")) {
            jdbcTemplate.update(
                    "update ai_learner_notebook set student_reflection = ?, update_time = now() where id = ? and user_id = ?",
                    cut(trimToEmpty(stringValue(request.get("student_reflection"))), 2000, ""),
                    id,
                    auth.userId()
            );
            any = true;
        }
        if (request.containsKey("fix_outcome")) {
            jdbcTemplate.update(
                    "update ai_learner_notebook set fix_outcome = ?, update_time = now() where id = ? and user_id = ?",
                    cut(trimToEmpty(stringValue(request.get("fix_outcome"))), 2000, ""),
                    id,
                    auth.userId()
            );
            any = true;
        }
        if (request.containsKey("tags")) {
            jdbcTemplate.update(
                    "update ai_learner_notebook set tags = cast(? as jsonb), update_time = now() where id = ? and user_id = ?",
                    toJson(toStringList(request.get("tags"), 20, 64)),
                    id,
                    auth.userId()
            );
            any = true;
        }
        if (!any) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "No updatable fields provided");
        }

        Map<String, Object> updated = jdbcTemplate.queryForObject(
                """
                select id, student_reflection, fix_outcome, tags::text as tags_json
                from ai_learner_notebook
                where id = ? and user_id = ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("id", rs.getString("id"));
                    payload.put("student_reflection", rs.getString("student_reflection"));
                    payload.put("fix_outcome", rs.getString("fix_outcome"));
                    payload.put("tags", toStringList(parseJsonList(rs.getString("tags_json")), 20, 64));
                    return payload;
                },
                id,
                auth.userId()
        );
        return ApiResponse.success(updated == null ? Map.of("id", id) : updated);
    }

    public ApiResponse<Object> notebookDelete(String id, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String normalized = trimToNull(id);
        if (normalized == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "id is required");
        }
        int updated = jdbcTemplate.update(
                "update ai_learner_notebook set is_deleted = true, update_time = now() where id = ? and user_id = ?",
                normalized,
                auth.userId()
        );
        if (updated == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Entry not found");
        }
        return ApiResponse.success("Deleted");
    }

    public ApiResponse<Object> notebookExport(Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        List<Map<String, Object>> entries = jdbcTemplate.query(
                """
                select id, problem_id, language, error_taxonomy, root_cause, fix_outcome,
                       student_reflection, tags::text as tags_json, create_time
                from ai_learner_notebook
                where user_id = ? and is_deleted = false
                order by create_time desc
                limit 5000
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getString("id"));
                    item.put("problem_id", rs.getObject("problem_id"));
                    item.put("language", rs.getString("language"));
                    item.put("error_taxonomy", rs.getString("error_taxonomy"));
                    item.put("root_cause", rs.getString("root_cause"));
                    item.put("fix_outcome", rs.getString("fix_outcome"));
                    item.put("student_reflection", rs.getString("student_reflection"));
                    item.put("tags", toStringList(parseJsonList(rs.getString("tags_json")), 20, 64));
                    item.put("create_time", formatTime(rs.getTimestamp("create_time")));
                    return item;
                },
                auth.userId()
        );
        return ApiResponse.success(Map.of("entries", entries, "export_time", nowIso()));
    }

    public ApiResponse<Object> notebookClassFrequency(Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        List<Map<String, Object>> frequencies = jdbcTemplate.queryForList(
                """
                SELECT aln.error_taxonomy,
                       COUNT(DISTINCT aln.user_id) AS classmate_count,
                       (SELECT COUNT(DISTINCT cm2.user_id) FROM classroom_member cm2
                        WHERE cm2.classroom_id = cm.classroom_id) AS total_classmates
                FROM ai_learner_notebook aln
                JOIN classroom_member cm ON cm.user_id = aln.user_id
                WHERE cm.classroom_id IN (SELECT classroom_id FROM classroom_member WHERE user_id = ?)
                  AND aln.error_taxonomy IS NOT NULL
                  AND aln.is_deleted = false
                GROUP BY aln.error_taxonomy, cm.classroom_id
                ORDER BY classmate_count DESC
                """,
                auth.userId()
        );
        return ApiResponse.success(frequencies);
    }

    public ApiResponse<Object> notebookGenerateReflection(Map<String, Object> request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String errorTaxonomy = trimToEmpty(stringValue(request.get("error_taxonomy")));
        String rootCause = trimToEmpty(stringValue(request.get("root_cause")));
        String fixOutcome = trimToEmpty(stringValue(request.get("fix_outcome")));

        String systemPrompt = """
                你是一位编程学习助手。学生在做 Python 编程题时犯了错误，现在需要你帮他生成一段简短的学习反思。
                要求：
                - 用第一人称（"我"）
                - 2-3句话，不超过100字
                - 包含：错误原因总结 + 下次如何避免
                - 语气友好、鼓励，适合编程初学者
                只返回 JSON: {"reflection": "反思文本"}
                """;
        String userPrompt = "错误类型: " + errorTaxonomy
                + "\n根因分析: " + rootCause
                + "\n修复结果: " + fixOutcome;

        Map<String, Object> result = aiModelGateway.callForJson(systemPrompt, userPrompt);
        String reflection = stringValue(result.get("reflection"));
        return ApiResponse.success(Map.of("reflection", reflection != null ? reflection : ""));
    }

    public ApiResponse<Object> notebookWeeklySummary(Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }

        List<Map<String, Object>> weeklyCounts = jdbcTemplate.queryForList("""
                SELECT error_taxonomy, COUNT(*) AS count
                FROM ai_learner_notebook
                WHERE user_id = ? AND is_deleted = false
                  AND create_time >= now() - interval '7 day'
                  AND error_taxonomy IS NOT NULL AND error_taxonomy <> 'unknown'
                GROUP BY error_taxonomy
                ORDER BY count DESC
                """, auth.userId());

        int totalErrors = weeklyCounts.stream().mapToInt(m -> ((Number) m.get("count")).intValue()).sum();
        int conqueredCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT n.problem_id)
                FROM ai_learner_notebook n
                WHERE n.user_id = ? AND n.is_deleted = false
                  AND n.create_time >= now() - interval '7 day'
                  AND n.problem_id IS NOT NULL
                  AND EXISTS(SELECT 1 FROM submission s
                             WHERE s.user_id = n.user_id AND s.problem_id = n.problem_id
                               AND s.result = 0 AND s.create_time > n.create_time)
                """, Integer.class, auth.userId());

        String topErrorType = weeklyCounts.isEmpty() ? null : stringValue(weeklyCounts.getFirst().get("error_taxonomy"));
        String topErrorLabel = topErrorType != null
                ? com.alethicode.service.aitutor.contract.ErrorTaxonomy.label(topErrorType)
                : null;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_errors", totalErrors);
        summary.put("conquered_count", conqueredCount);
        summary.put("top_error_type", topErrorType);
        summary.put("top_error_label", topErrorLabel);
        summary.put("breakdown", weeklyCounts);
        return ApiResponse.success(summary);
    }

    public ApiResponse<Object> frustrationAnalyze(Map<String, Object> request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long problemId = parseLong(stringValue(request.get("problem_id")));
        if (problemId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目 ID 不能为空");
        }
        Long wrongCount = jdbcTemplate.queryForObject(
                "select count(*) from submission where user_id = ? and problem_id = ? and result <> 0",
                Long.class,
                auth.userId(),
                problemId
        );
        double score = Math.min(1.0, (wrongCount == null ? 0 : wrongCount) / 3.0);

        recordLearningEvent(auth.userId(), problemId, "frustration_detected", Map.of(
                "frustration_score", score,
                "root_cause", "实现细节卡住"
        ));

        List<Map<String, Object>> recovery = jdbcTemplate.query(
                """
                select id, _id, title, difficulty
                from problem
                where visible = true and id <> ?
                order by accepted_number desc, id desc
                limit 4
                """,
                (rs, rowNum) -> Map.of(
                        "problem_id", rs.getLong("id"),
                        "problem_key", rs.getString("_id"),
                        "title", rs.getString("title"),
                        "difficulty", rs.getString("difficulty")
                ),
                problemId
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("frustration_score", score);
        payload.put("root_cause", "实现细节卡住");
        payload.put("intervention", "先简化输入并手动模拟一次执行过程。");
        payload.put("encouragement", "你已经非常接近了，先把边界样例跑通就能突破。\uD83D\uDCAA");
        payload.put("recovery_problems", recovery);
        return ApiResponse.success(payload);
    }

    public ApiResponse<Object> frustrationEvent(Map<String, Object> request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String eventType = trimToNull(stringValue(request.get("event_type")));
        List<String> validTypes = List.of("frustration_detected", "intervention_shown", "intervention_dismissed", "intervention_effective");
        if (eventType == null || !validTypes.contains(eventType)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid event_type: " + trimToEmpty(eventType));
        }
        Long problemId = parseLong(stringValue(request.get("problem_id")));
        Map<String, Object> extraData = toMap(request.get("extra_data"));
        recordLearningEvent(auth.userId(), problemId, eventType, extraData);
        return ApiResponse.success("ok");
    }

    public ApiResponse<Object> frustrationAlert(Map<String, Object> request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String classroomId = trimToNull(stringValue(request.get("classroom_id")));
        if (classroomId == null) {
            return ApiResponse.success("no_classroom");
        }
        return ApiResponse.success("ok");
    }

    public ApiResponse<Object> misconceptionsMine(Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        List<Map<String, Object>> rawMisconceptions = jdbcTemplate.query(
                """
                select id, event_type, vulnerability_tag, extra_data::text as extra_json, created_at
                from ai_learning_event
                where user_id = ?
                  and (
                    event_type in ('misconception', 'misconception_detected_ast', 'preflight_go_edit', 'preflight_force_submit')
                    or nullif(btrim(vulnerability_tag), '') is not null
                    or nullif(btrim(extra_data->>'misconception_name'), '') is not null
                    or nullif(btrim(extra_data->>'detector_name'), '') is not null
                    or nullif(btrim(extra_data->>'root_cause'), '') is not null
                  )
                order by created_at desc
                limit 50
                """,
                (rs, rowNum) -> {
                    Map<String, Object> extra = parseJsonMap(rs.getString("extra_json"));
                    String name = trimToNull(stringValue(extra.get("misconception_name")));
                    if (name == null) {
                        name = trimToNull(rs.getString("vulnerability_tag"));
                    }
                    if (name == null) {
                        name = trimToNull(stringValue(extra.get("root_cause")));
                    }
                    if (name == null) {
                        name = trimToNull(stringValue(extra.get("detector_name")));
                    }
                    if (name == null) {
                        name = trimToEmpty(rs.getString("event_type"));
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", String.valueOf(rs.getLong("id")));
                    item.put("misconception_name", trimToEmpty(name));
                    item.put("description", extra.get("description"));
                    item.put("correction_hint", extra.get("correction_hint"));
                    item.put("trigger_count", extra.getOrDefault("trigger_count", 1));
                    item.put("kc_name", extra.get("kc_name"));
                    item.put("last_triggered_at", formatTime(rs.getTimestamp("created_at")));
                    return item;
                },
                auth.userId()
        );

        Map<String, Map<String, Object>> mergedByName = new LinkedHashMap<>();
        for (Map<String, Object> one : rawMisconceptions) {
            String name = trimToNull(stringValue(one.get("misconception_name")));
            if (name == null) {
                continue;
            }
            Map<String, Object> merged = mergedByName.get(name);
            if (merged == null) {
                merged = new LinkedHashMap<>(one);
                merged.put("misconception_name", name);
                merged.put("trigger_count", parseIntObj(one.get("trigger_count"), 1));
                mergedByName.put(name, merged);
            } else {
                merged.put("trigger_count",
                        parseIntObj(merged.get("trigger_count"), 0) + parseIntObj(one.get("trigger_count"), 1));
                String existingHint = trimToNull(stringValue(merged.get("correction_hint")));
                if (existingHint == null) {
                    merged.put("correction_hint", one.get("correction_hint"));
                }
                String existingDesc = trimToNull(stringValue(merged.get("description")));
                if (existingDesc == null) {
                    merged.put("description", one.get("description"));
                }
            }
        }

        List<Map<String, Object>> misconceptions = new ArrayList<>(mergedByName.values());
        misconceptions.sort(Comparator
                .comparingInt((Map<String, Object> item) -> parseIntObj(item.get("trigger_count"), 0))
                .reversed());
        if (misconceptions.size() > 50) {
            misconceptions = new ArrayList<>(misconceptions.subList(0, 50));
        }
        return ApiResponse.success(Map.of("misconceptions", misconceptions));
    }

    public ApiResponse<Object> reviewDue(Map<String, String> params, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }

        String lpIdStr = params.get("language_pack_id");
        Long languagePackId = null;
        if (lpIdStr != null && !lpIdStr.isBlank()) {
            languagePackId = Long.valueOf(lpIdStr);
        }

        String focusSql;
        Object[] focusArgs;
        if (languagePackId != null) {
            focusSql = """
                select n.error_taxonomy,
                       count(distinct n.id) as notebook_count,
                       count(distinct s.id) as recent_wrong_count,
                       max(n.update_time) as latest_error_time
                from ai_learner_notebook n
                join language_pack_problem_mapping lpm on lpm.problem_id = n.problem_id
                left join submission s on s.user_id = n.user_id
                    and s.problem_id = n.problem_id and s.result <> 0
                    and s.create_time >= now() - interval '30 day'
                where n.user_id = ? and n.is_deleted = false
                  and lpm.language_pack_id = ?
                """
                    + REVIEW_DUE_TAXONOMY_FILTER
                    + """
                group by n.error_taxonomy
                having count(distinct n.id) > 0
                order by latest_error_time desc
                limit 9
                """;
            focusArgs = new Object[]{auth.userId(), languagePackId};
        } else {
            focusSql = """
                select n.error_taxonomy,
                       count(distinct n.id) as notebook_count,
                       count(distinct s.id) as recent_wrong_count,
                       max(n.update_time) as latest_error_time
                from ai_learner_notebook n
                left join submission s on s.user_id = n.user_id
                    and s.problem_id = n.problem_id and s.result <> 0
                    and s.create_time >= now() - interval '30 day'
                where n.user_id = ? and n.is_deleted = false
                """
                    + REVIEW_DUE_TAXONOMY_FILTER
                    + """
                group by n.error_taxonomy
                having count(distinct n.id) > 0
                order by latest_error_time desc
                limit 9
                """;
            focusArgs = new Object[]{auth.userId()};
        }

        List<Map<String, Object>> focusCards = jdbcTemplate.query(
                focusSql,
                (rs, rowNum) -> {
                    String taxonomy = rs.getString("error_taxonomy");
                    Map<String, Object> card = new LinkedHashMap<>();
                    card.put("error_taxonomy", taxonomy);
                    card.put("label", com.alethicode.service.aitutor.contract.ErrorTaxonomy.label(taxonomy));
                    card.put("notebook_count", rs.getInt("notebook_count"));
                    card.put("recent_wrong_count", rs.getInt("recent_wrong_count"));
                    card.put("latest_error_time", formatTime(rs.getTimestamp("latest_error_time")));
                    return card;
                },
                focusArgs
        );

        for (Map<String, Object> card : focusCards) {
            String taxonomy = String.valueOf(card.get("error_taxonomy"));
            Map<String, Object> activePackage = jdbcTemplate.query(
                    """
                    select id, completed_count, problem_count, mastery_reached,
                           fsrs_state, fsrs_due_at, fsrs_stability, fsrs_difficulty, fsrs_retrievability
                    from ai_error_review_package
                    where user_id = ? and error_taxonomy = ?
                    order by created_at desc limit 1
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> pkg = new LinkedHashMap<>();
                        Timestamp dueAt = rs.getTimestamp("fsrs_due_at");
                        pkg.put("package_id", rs.getString("id"));
                        pkg.put("completed_count", rs.getInt("completed_count"));
                        pkg.put("problem_count", rs.getInt("problem_count"));
                        pkg.put("mastery_reached", rs.getBoolean("mastery_reached"));
                        pkg.put("fsrs_state", rs.getString("fsrs_state"));
                        pkg.put("due_at", dueAt == null ? null : formatTime(dueAt));
                        pkg.put("is_due", dueAt == null || !dueAt.after(new Timestamp(System.currentTimeMillis())));
                        pkg.put("stability", rs.getObject("fsrs_stability"));
                        pkg.put("difficulty", rs.getObject("fsrs_difficulty"));
                        pkg.put("retrievability", rs.getObject("fsrs_retrievability"));
                        return pkg;
                    },
                    auth.userId(), taxonomy
            ).stream().findFirst().orElse(null);

            card.put("has_active_package", activePackage != null && !Boolean.TRUE.equals(activePackage.get("mastery_reached"))
                    && ((int) activePackage.getOrDefault("completed_count", 0)) < ((int) activePackage.getOrDefault("problem_count", 3)));
            card.put("last_package_mastery", activePackage != null && Boolean.TRUE.equals(activePackage.get("mastery_reached")));
            if (activePackage != null) {
                card.put("active_package_id", activePackage.get("package_id"));
                card.put("fsrs_state", activePackage.get("fsrs_state"));
                card.put("due_at", activePackage.get("due_at"));
                card.put("is_due", activePackage.get("is_due"));
                card.put("stability", activePackage.get("stability"));
                card.put("difficulty", activePackage.get("difficulty"));
                card.put("retrievability", activePackage.get("retrievability"));
            }
        }

        focusCards.sort((left, right) -> {
            String leftDue = String.valueOf(left.getOrDefault("due_at", ""));
            String rightDue = String.valueOf(right.getOrDefault("due_at", ""));
            if (leftDue.isBlank() && rightDue.isBlank()) return 0;
            if (leftDue.isBlank()) return -1;
            if (rightDue.isBlank()) return 1;
            return leftDue.compareTo(rightDue);
        });

        int totalDue = focusCards.stream().mapToInt(c -> (int) c.getOrDefault("notebook_count", 0)).sum();

        return ApiResponse.success(Map.of(
                "due_reviews", focusCards,
                "stats", Map.of(
                        "due_count", totalDue,
                        "focus_count", focusCards.size(),
                        "timestamp", nowIso()
                )
        ));
    }

    public ApiResponse<Object> preflightCheck(PreflightCheckRequest request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }

        if (request.problemId() == null || trimToNull(request.detectorName()) == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "problem_id and detector_name are required");
        }
        if (request.lineNumber() == null || request.lineNumber() < 1) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "line_number must be a positive integer");
        }

        Long triggerCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from ai_inference_task
                where user_id = ?
                  and (request_data ->> 'problem_id')::bigint = ?
                """,
                Long.class,
                auth.userId(),
                request.problemId()
        );

        boolean shouldTrigger = triggerCount == null || triggerCount < 3;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("should_trigger", shouldTrigger);
        result.put("question", shouldTrigger ? "这段代码在边界输入下会发生什么？" : null);
        result.put("hint", shouldTrigger ? "先手动跟踪一次变量变化。" : null);
        result.put("highlight_reason", trimToNull(request.detectorName()));
        result.put("alert_title", shouldTrigger ? "AI 预检查建议" : null);
        result.put("trigger_count", triggerCount == null ? 0 : triggerCount);
        result.put("kc_mastery", 0.5);
        result.put("misconception_id", null);
        result.put("latency_ms", 1);
        return ApiResponse.success(result);
    }

    public ApiResponse<Object> codeSnapshot(Map<String, Object> request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long problemId = parseLong(stringValue(request.get("problem_id")));
        String code = stringValue(request.get("code"));
        if (problemId == null || code == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "problem_id and code are required");
        }
        String trigger = trimToEmpty(stringValue(request.get("trigger")));
        if (trigger.isBlank()) {
            trigger = "interval";
        }
        int charCount = parseIntObj(request.get("char_count"), code.length());
        int lineCount = parseIntObj(request.get("line_count"), code.split("\\R", -1).length);
        int diffAdded = parseIntObj(request.get("diff_chars_added"), 0);
        int diffDeleted = parseIntObj(request.get("diff_chars_deleted"), 0);
        String sessionId = trimToNull(stringValue(request.get("session_id")));

        Long id = jdbcTemplate.queryForObject(
                """
                insert into ai_code_snapshot(user_id, problem_id, code, trigger, char_count, line_count,
                                             diff_chars_added, diff_chars_deleted, session_id, create_time)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                returning id
                """,
                Long.class,
                auth.userId(),
                problemId,
                code,
                trigger,
                charCount,
                lineCount,
                diffAdded,
                diffDeleted,
                sessionId
        );

        return ApiResponse.success(Map.of("saved", true, "snapshot_id", id == null ? 0 : id));
    }

    public ApiResponse<Object> learningEventsBatch(Map<String, Object> request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Object eventsRaw = request.get("events");
        if (!(eventsRaw instanceof List<?> events)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "events (list) is required");
        }

        List<Object[]> eventParams = new ArrayList<>();
        List<Object[]> feedbackParams = new ArrayList<>();

        for (Object eventRaw : events) {
            if (!(eventRaw instanceof Map<?, ?> mapRaw)) {
                continue;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapRaw.entrySet()) {
                event.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            Long problemId = parseLong(stringValue(event.get("problem_id")));
            String eventType = trimToNull(stringValue(event.get("event_type")));
            if (eventType == null) {
                eventType = "learning_event";
            }
            Object correctRaw = event.get("is_correct");
            Boolean isCorrect = correctRaw == null ? null : toBoolean(correctRaw);
            String vulnerabilityTag = trimToNull(stringValue(event.get("vulnerability_tag")));
            String taxonomyRaw = trimToNull(stringValue(event.get("error_taxonomy")));
            if (taxonomyRaw == null) {
                taxonomyRaw = trimToNull(stringValue(event.get("error_category")));
            }
            String errorTaxonomy = taxonomyRaw == null
                    ? null
                    : com.alethicode.service.aitutor.contract.ErrorTaxonomy.normalize(taxonomyRaw);
            String rootCause = trimToNull(stringValue(event.get("root_cause")));
            String detectorName = trimToNull(stringValue(event.get("detector_name")));

            eventParams.add(new Object[]{
                    auth.userId(), problemId, eventType,
                    toJson(event), isCorrect, vulnerabilityTag,
                    errorTaxonomy, rootCause, detectorName
            });
            collectFeedbackLabel(auth.userId(), problemId, eventType, event, feedbackParams);
        }

        int created = eventParams.size();

        String learningEventSql = """
                insert into ai_learning_event(user_id, problem_id, event_type, extra_data, is_correct, vulnerability_tag,
                                              error_taxonomy, root_cause, detector_name)
                values (?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(learningEventSql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Object[] p = eventParams.get(i);
                ps.setObject(1, p[0]);
                ps.setObject(2, p[1]);
                ps.setObject(3, p[2]);
                ps.setObject(4, p[3]);
                ps.setObject(5, p[4]);
                ps.setObject(6, p[5]);
                ps.setObject(7, p[6]);
                ps.setObject(8, p[7]);
                ps.setObject(9, p[8]);
            }

            @Override
            public int getBatchSize() {
                return eventParams.size();
            }
        });

        if (!feedbackParams.isEmpty()) {
            String feedbackLabelSql = """
                    insert into ai_feedback_label(
                        user_id, problem_id, session_id, workflow_event_id, card_type,
                        feedback_label, agent_id, source_event_type, extra_data, created_at
                    )
                    values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
                    """;
            jdbcTemplate.batchUpdate(feedbackLabelSql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Object[] p = feedbackParams.get(i);
                    ps.setObject(1, p[0]);
                    ps.setObject(2, p[1]);
                    ps.setObject(3, p[2]);
                    ps.setObject(4, p[3]);
                    ps.setObject(5, p[4]);
                    ps.setObject(6, p[5]);
                    ps.setObject(7, p[6]);
                    ps.setObject(8, p[7]);
                    ps.setObject(9, p[8]);
                }

                @Override
                public int getBatchSize() {
                    return feedbackParams.size();
                }
            });
        }

        return ApiResponse.success(Map.of("created", created));
    }

    private void collectFeedbackLabel(Long userId, Long problemId, String eventType,
                                      Map<String, Object> event, List<Object[]> feedbackParams) {
        if (!"agent_feedback".equalsIgnoreCase(trimToEmpty(eventType))) {
            return;
        }
        Map<String, Object> extraData = toMap(event.get("extra_data"));
        String feedback = trimToNull(stringValue(extraData.get("feedback")));
        String cardType = trimToNull(stringValue(extraData.get("card_type")));
        if (feedback == null || cardType == null) {
            return;
        }
        feedbackParams.add(new Object[]{
                userId,
                problemId,
                trimToEmpty(stringValue(event.get("session_id"))),
                trimToEmpty(stringValue(extraData.get("workflow_event_id"))),
                cardType,
                feedback,
                parseIntObj(extraData.get("agent_id"), 0) == 0 ? null : parseIntObj(extraData.get("agent_id"), 0),
                trimToEmpty(eventType),
                toJson(extraData)
        });
    }

    public ApiResponse<Object> calibrationStatus(Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Map<String, Object> state = loadCalibrationState(auth.userId());
        boolean calibrated = toBoolean(state.getOrDefault("calibrated", false));
        int currentIndex = parseIntObj(state.get("current_index"), 0);
        Map<String, Double> accumulated = normalizeCalibrationAccumulated(state.get("accumulated"));
        List<Map<String, Object>> questions = calibrationQuestions(auth.userId());
        if (calibrated || questions.isEmpty()) {
            return ApiResponse.success(Map.of("needs_calibration", false));
        }
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        if (currentIndex >= questions.size()) {
            currentIndex = questions.size() - 1;
        }
        Map<String, Object> currentQuestion = questions.get(currentIndex);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("needs_calibration", true);
        payload.put("total_questions", questions.size());
        payload.put("current_index", currentIndex);
        payload.put("accumulated", accumulated);
        payload.put("first_question", Map.of(
                "index", currentQuestion.get("index"),
                "prompt", currentQuestion.get("prompt"),
                "kc_group", currentQuestion.get("kc_group")
        ));
        return ApiResponse.success(payload);
    }

    public ApiResponse<Object> calibrationAnswer(Map<String, Object> request, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Integer questionIndex = parseIntObjNullable(request.get("question_index"));
        String answer = trimToNull(stringValue(request.get("answer")));
        if (questionIndex == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "question_index is required");
        }
        if (answer == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "answer is required");
        }
        Map<String, Object> state = loadCalibrationState(auth.userId());
        boolean calibrated = toBoolean(state.getOrDefault("calibrated", false));
        List<Map<String, Object>> questions = calibrationQuestions(auth.userId());
        if (questionIndex < 0 || questionIndex >= questions.size()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid question_index");
        }
        Map<String, Double> accumulated = normalizeCalibrationAccumulated(state.get("accumulated"));
        if (calibrated) {
            return ApiResponse.success(Map.of(
                    "calibration_complete", true,
                    "already_calibrated", true,
                    "calibrated_kcs", toCalibratedKcPayload(accumulated),
                    "accumulated", accumulated
            ));
        }
        int expectedIndex = parseIntObj(state.get("current_index"), 0);
        if (expectedIndex < 0) {
            expectedIndex = 0;
        }
        if (expectedIndex >= questions.size()) {
            expectedIndex = questions.size() - 1;
        }
        if (!questionIndex.equals(expectedIndex)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy(
                    "error",
                    "question_index out of sequence, expected: " + expectedIndex
            );
        }

        Map<String, Object> current = questions.get(questionIndex);
        String kcGroup = trimToEmpty(stringValue(current.get("kc_group")));
        double calibrationScore = scoreCalibrationAnswer(kcGroup, answer);
        accumulated.put(kcGroup, calibrationScore);

        int nextIndex = questionIndex + 1;
        if (nextIndex >= questions.size()) {
            jdbcTemplate.update(
                    """
                    insert into ai_calibration_state(user_id, calibrated, current_index, accumulated, updated_at)
                    values (?, true, ?, cast(? as jsonb), now())
                    on conflict (user_id) do update
                    set calibrated = excluded.calibrated,
                        current_index = excluded.current_index,
                        accumulated = excluded.accumulated,
                        updated_at = now()
                    """,
                    auth.userId(),
                    nextIndex,
                    toJson(accumulated)
            );
            return ApiResponse.success(Map.of(
                    "calibration_complete", true,
                    "calibrated_kcs", toCalibratedKcPayload(accumulated),
                    "accumulated", accumulated
            ));
        }

        Map<String, Object> next = questions.get(nextIndex);
        jdbcTemplate.update(
                """
                insert into ai_calibration_state(user_id, calibrated, current_index, accumulated, updated_at)
                values (?, false, ?, cast(? as jsonb), now())
                on conflict (user_id) do update
                set calibrated = excluded.calibrated,
                    current_index = excluded.current_index,
                    accumulated = excluded.accumulated,
                    updated_at = now()
                """,
                auth.userId(),
                nextIndex,
                toJson(accumulated)
        );
        return ApiResponse.success(Map.of(
                "calibration_complete", false,
                "accumulated", accumulated,
                "current_score", calibrationScore,
                "next_question", Map.of(
                        "index", next.get("index"),
                        "prompt", next.get("prompt"),
                        "kc_group", next.get("kc_group"),
                        "total", questions.size()
                )
        ));
    }

    public ApiResponse<Object> calibrationSkip(Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Map<String, Object> state = loadCalibrationState(auth.userId());
        if (toBoolean(state.getOrDefault("calibrated", false))) {
            return ApiResponse.success(Map.of("skipped", true, "already_calibrated", true));
        }
        jdbcTemplate.update(
                """
                insert into ai_calibration_state(user_id, calibrated, current_index, accumulated, updated_at)
                values (?, false, 0, cast(? as jsonb), now())
                on conflict (user_id) do update
                set updated_at = now()
                """,
                auth.userId(),
                "{}"
        );
        recordLearningEvent(auth.userId(), null, "calibration_skipped", Map.of());
        return ApiResponse.success(Map.of("skipped", true));
    }

    public ApiResponse<Object> knowledgeGraph(Map<String, String> params, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long languagePackId = resolveAuthorizedLanguagePackId(params, auth);
        Long userId = parseLong(params.get("user_id"));
        if (userId == null) {
            userId = auth.userId();
        }
        if (!userId.equals(auth.userId()) && !auth.adminRole()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }

        Map<Long, String> pptChapterByKc = loadPptChapterByKc();

        List<Map<String, Object>> nodes = jdbcTemplate.query(
                """
                select kc.id,
                       kc.name,
                       kc.chapter,
                       kc.description,
                       count(distinct m.problem_id) as problem_count,
                       count(s.id) as submission_count,
                       coalesce(sum(case when s.result = 0 then 1 else 0 end), 0) as accepted_count
                from ai_knowledge_component kc
                left join ai_problem_kc_mapping m on m.kc_id = kc.id and m.language_pack_id = ?
                left join submission s on s.problem_id = m.problem_id and s.user_id = ?
                where kc.language_pack_id = ?
                group by kc.id, kc.name, kc.chapter, kc.description
                order by kc.id asc
                """,
                (rs, rowNum) -> {
                    long submitCount = rs.getLong("submission_count");
                    long acceptedCount = rs.getLong("accepted_count");
                    long problemCountRow = rs.getLong("problem_count");
                    double ratio = submitCount == 0 ? 0.0 : acceptedCount * 1.0 / submitCount;
                    double mastery = acceptedCount > 0 ? Math.max(ratio, 0.7) : ratio;
                    long kcId = rs.getLong("id");
                    String rawChapter = trimToEmpty(rs.getString("chapter"));
                    String effectiveChapter = rawChapter.isBlank() ? trimToEmpty(pptChapterByKc.get(kcId)) : rawChapter;
                    String kcName = rs.getString("name");
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", kcId);
                    node.put("name", kcName);
                    node.put("kc_name", kcName);
                    node.put("chapter", effectiveChapter);
                    node.put("description", trimToEmpty(rs.getString("description")));
                    node.put("mastery", mastery);
                    node.put("problem_count", problemCountRow);
                    node.put("submission_count", submitCount);
                    node.put("active_misconceptions", List.of());
                    node.put("is_recommended_next", false);
                    node.put("recommended_review_actions",
                            com.alethicode.service.aitutor.profile.KcReviewActionBuilder.buildForWeakKc(
                                    kcName == null ? "该知识点" : kcName,
                                    mastery,
                                    submitCount,
                                    acceptedCount,
                                    problemCountRow
                            ));
                    return node;
                },
                languagePackId,
                userId,
                languagePackId
        );

        Set<Long> nodeIdSet = new LinkedHashSet<>();
        for (Map<String, Object> node : nodes) {
            nodeIdSet.add(longValue(node.get("id")));
        }
        List<Long> kcOrderByPptAppearance = loadKcOrderByPptAppearance(languagePackId, nodeIdSet);

        List<Map<String, Object>> edges = jdbcTemplate.query(
                """
                select m1.kc_id as source_id,
                       m2.kc_id as target_id,
                       count(distinct m1.problem_id) as shared_problem_count
                from ai_problem_kc_mapping m1
                join ai_problem_kc_mapping m2
                  on m1.problem_id = m2.problem_id
                 and m1.language_pack_id = m2.language_pack_id
                 and m1.kc_id < m2.kc_id
                where m1.language_pack_id = ?
                group by m1.kc_id, m2.kc_id
                order by shared_problem_count desc, source_id asc, target_id asc
                """,
                (rs, rowNum) -> {
                    long source = rs.getLong("source_id");
                    long target = rs.getLong("target_id");
                    if (!nodeIdSet.contains(source) || !nodeIdSet.contains(target)) {
                        return null;
                    }
                    long sharedCount = rs.getLong("shared_problem_count");
                    double normalizedWeight = Math.min(1.0, 0.3 + (sharedCount * 0.1));
                    Map<String, Object> edge = new LinkedHashMap<>();
                    edge.put("source", source);
                    edge.put("target", target);
                    edge.put("weight", normalizedWeight);
                    edge.put("relation", "related");
                    edge.put("is_recommended_path", false);
                    return edge;
                },
                languagePackId
        );
        edges.removeIf(edge -> edge == null);
        appendFallbackEdgesByPptOrder(edges, kcOrderByPptAppearance);

        List<Long> recommendedPath = buildRecommendedPath(nodes);
        Set<Long> recommendedSet = new LinkedHashSet<>(recommendedPath);
        for (Map<String, Object> node : nodes) {
            long id = longValue(node.get("id"));
            node.put("is_recommended_next", recommendedSet.contains(id));
        }

        Set<String> recommendedPairSet = new LinkedHashSet<>();
        for (int i = 1; i < recommendedPath.size(); i++) {
            recommendedPairSet.add(canonicalEdgeKey(recommendedPath.get(i - 1), recommendedPath.get(i)));
        }

        Set<String> edgeKeySet = new LinkedHashSet<>();
        for (Map<String, Object> edge : edges) {
            long source = longValue(edge.get("source"));
            long target = longValue(edge.get("target"));
            String key = canonicalEdgeKey(source, target);
            edgeKeySet.add(key);
            if (recommendedPairSet.contains(key)) {
                edge.put("is_recommended_path", true);
            }
        }
        for (int i = 1; i < recommendedPath.size(); i++) {
            long source = recommendedPath.get(i - 1);
            long target = recommendedPath.get(i);
            String key = canonicalEdgeKey(source, target);
            if (!edgeKeySet.contains(key)) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("source", source);
                edge.put("target", target);
                edge.put("weight", 0.6);
                edge.put("relation", "recommended");
                edge.put("is_recommended_path", true);
                edges.add(edge);
                edgeKeySet.add(key);
            }
        }

        Set<String> chapterSet = new LinkedHashSet<>();
        for (Map<String, Object> node : nodes) {
            String chapter = trimToEmpty(stringValue(node.get("chapter")));
            if (!chapter.isBlank()) {
                chapterSet.add(chapter);
            }
        }
        List<String> chapterList = new ArrayList<>(chapterSet);
        chapterList.sort(this::compareChapterKey);

        List<Map<String, Object>> chapters = new ArrayList<>();
        for (String chapter : chapterList) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chapter", chapter);
            item.put("name", formatChapterDisplayName(chapter));
            chapters.add(item);
        }

        long masteredCount = nodes.stream().filter(node -> doubleValue(node.get("mastery")) >= 0.7).count();
        long weakCount = nodes.stream().filter(node -> doubleValue(node.get("mastery")) < 0.3).count();
        Integer learningDays = jdbcTemplate.queryForObject(
                """
                select count(distinct to_char(create_time at time zone 'UTC', 'YYYY-MM-DD'))
                from submission where user_id = ?
                """,
                Integer.class,
                userId
        );

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_kcs", nodes.size());
        stats.put("mastered_count", masteredCount);
        stats.put("weak_count", weakCount);
        stats.put("active_misconception_count", 0);
        stats.put("learning_days", Math.max(learningDays == null ? 0 : learningDays, 1));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", userId);
        payload.put("nodes", nodes);
        payload.put("edges", edges);
        payload.put("chapters", chapters);
        payload.put("recommended_path", recommendedPath);
        payload.put("stats", stats);
        payload.put("generated_at", nowIso());
        return ApiResponse.success(payload);
    }

    private String canonicalEdgeKey(long source, long target) {
        if (source <= target) {
            return source + "->" + target;
        }
        return target + "->" + source;
    }

    private void appendFallbackEdgesByPptOrder(List<Map<String, Object>> edges, List<Long> kcOrderByPptAppearance) {
        if (kcOrderByPptAppearance == null || kcOrderByPptAppearance.size() < 2) {
            return;
        }
        Set<String> existingEdgeKeys = new LinkedHashSet<>();
        for (Map<String, Object> edge : edges) {
            long source = longValue(edge.get("source"));
            long target = longValue(edge.get("target"));
            existingEdgeKeys.add(canonicalEdgeKey(source, target));
        }
        for (int i = 1; i < kcOrderByPptAppearance.size(); i++) {
            long source = kcOrderByPptAppearance.get(i - 1);
            long target = kcOrderByPptAppearance.get(i);
            if (source == target) {
                continue;
            }
            String key = canonicalEdgeKey(source, target);
            if (existingEdgeKeys.contains(key)) {
                continue;
            }
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", source);
            edge.put("target", target);
            edge.put("weight", 0.3);
            edge.put("relation", "related");
            edge.put("is_recommended_path", false);
            edges.add(edge);
            existingEdgeKeys.add(key);
        }
    }

    private List<Long> loadKcOrderByPptAppearance(Long languagePackId, Set<Long> allowedNodeIds) {
        if (languagePackId == null || allowedNodeIds == null || allowedNodeIds.isEmpty()) {
            return List.of();
        }
        List<Long> ordered = jdbcTemplate.query(
                """
                select kc.synced_ai_kc_id as ai_kc_id,
                       coalesce(ch.chapter_index, 2147483647) as chapter_order,
                       coalesce(min(p.page_no), 2147483647) as page_order,
                       kc.id as kc_order
                from language_pack_kc kc
                left join language_pack_chapter ch
                       on ch.id = kc.chapter_id
                left join language_pack_kc_page_mapping m
                       on m.kc_id = kc.id
                left join language_pack_page p
                       on p.id = m.page_id
                      and p.language_pack_id = kc.language_pack_id
                where kc.language_pack_id = ?
                  and kc.synced_ai_kc_id is not null
                group by kc.synced_ai_kc_id, ch.chapter_index, kc.id
                order by chapter_order asc, page_order asc, kc_order asc
                """,
                (rs, rowNum) -> rs.getLong("ai_kc_id"),
                languagePackId
        );
        LinkedHashSet<Long> deduplicated = new LinkedHashSet<>();
        for (Long aiKcId : ordered) {
            if (aiKcId != null && allowedNodeIds.contains(aiKcId)) {
                deduplicated.add(aiKcId);
            }
        }
        for (Long kcId : allowedNodeIds) {
            deduplicated.add(kcId);
        }
        return new ArrayList<>(deduplicated);
    }

    private Map<Long, String> loadPptChapterByKc() {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select x.kc_id, x.ppt_chapter
                from (
                    select m.kc_id,
                           substring(p._id from '^(PPT\\d+)') as ppt_chapter,
                           count(*) as relation_count,
                           row_number() over (
                               partition by m.kc_id
                               order by count(*) desc, substring(p._id from '^(PPT\\d+)') asc
                           ) as rn
                    from ai_problem_kc_mapping m
                    join problem p on p.id = m.problem_id
                    where p._id is not null and p._id ~ '^PPT\\d+'
                    group by m.kc_id, substring(p._id from '^(PPT\\d+)')
                ) x
                where x.rn = 1
                """,
                (rs, rowNum) -> {
                    Map<String, Object> one = new LinkedHashMap<>();
                    one.put("kc_id", rs.getLong("kc_id"));
                    one.put("ppt_chapter", trimToEmpty(rs.getString("ppt_chapter")));
                    return one;
                }
        );
        Map<Long, String> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            long kcId = longValue(row.get("kc_id"));
            String chapter = trimToEmpty(stringValue(row.get("ppt_chapter")));
            if (!chapter.isBlank()) {
                result.put(kcId, chapter);
            }
        }
        return result;
    }

    private String formatChapterDisplayName(String chapter) {
        String normalized = trimToEmpty(chapter);
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.toUpperCase(Locale.ROOT).matches("^PPT\\d+$")) {
            return normalized.toUpperCase(Locale.ROOT);
        }
        if (normalized.startsWith("第")) {
            if (normalized.contains("章")) {
                return normalized;
            }
            return normalized + "章";
        }
        if (normalized.contains("章")) {
            return normalized;
        }
        if (normalized.matches("^\\d+$") || normalized.matches("^[一二三四五六七八九十百千万零两]+$")) {
            return "第" + normalized + "章";
        }
        return normalized;
    }

    private int compareChapterKey(String left, String right) {
        String a = trimToEmpty(left).toUpperCase(Locale.ROOT);
        String b = trimToEmpty(right).toUpperCase(Locale.ROOT);
        boolean aIsPpt = a.matches("^PPT\\d+$");
        boolean bIsPpt = b.matches("^PPT\\d+$");

        if (aIsPpt && bIsPpt) {
            Integer anObj = parseIntObjNullable(a.substring(3));
            Integer bnObj = parseIntObjNullable(b.substring(3));
            int an = anObj == null ? Integer.MAX_VALUE : anObj;
            int bn = bnObj == null ? Integer.MAX_VALUE : bnObj;
            if (an != bn) {
                return Integer.compare(an, bn);
            }
            return a.compareTo(b);
        }
        if (aIsPpt) {
            return -1;
        }
        if (bIsPpt) {
            return 1;
        }
        return a.compareTo(b);
    }

    private List<Long> buildRecommendedPath(List<Map<String, Object>> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> candidates = new ArrayList<>(nodes);
        candidates.sort(Comparator
                .comparingDouble((Map<String, Object> node) -> doubleValue(node.get("mastery")))
                .thenComparingLong(node -> longValue(node.get("id"))));

        List<Long> prioritized = new ArrayList<>();
        for (Map<String, Object> node : candidates) {
            double mastery = doubleValue(node.get("mastery"));
            if (mastery < 0.7) {
                prioritized.add(longValue(node.get("id")));
            }
            if (prioritized.size() >= 5) {
                break;
            }
        }
        if (prioritized.isEmpty()) {
            for (Map<String, Object> node : candidates) {
                prioritized.add(longValue(node.get("id")));
                if (prioritized.size() >= 3) {
                    break;
                }
            }
        }

        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            order.put(longValue(nodes.get(i).get("id")), i);
        }
        prioritized.sort(Comparator.comparingInt(id -> order.getOrDefault(id, Integer.MAX_VALUE)));
        return prioritized;
    }

    public ApiResponse<Object> knowledgeGraphSnapshot(Map<String, String> params, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long languagePackId = resolveAuthorizedLanguagePackId(params, auth);
        Long userId = parseLong(params.get("user_id"));
        if (userId == null) {
            userId = auth.userId();
        }
        if (!userId.equals(auth.userId()) && !auth.adminRole()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        String beforeDate = trimToNull(params.get("before_date"));
        if (beforeDate == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "before_date is required");
        }
        try {
            LocalDate.parse(beforeDate);
        } catch (DateTimeParseException exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid before_date format, expected YYYY-MM-DD");
        }
        // Submissions submitted strictly before the day after snapshotDate are included,
        // i.e. everything up to and including 23:59:59 on snapshotDate.
        final Long finalUserId = userId;
        Map<String, Object> masteryMap = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                select kc.id,
                       count(s.id)                                                   as submission_count,
                       coalesce(sum(case when s.result = 0 then 1 else 0 end), 0)   as accepted_count
                from ai_knowledge_component kc
                left join ai_problem_kc_mapping m on m.kc_id = kc.id and m.language_pack_id = ?
                left join submission s
                       on s.problem_id = m.problem_id
                      and s.user_id = ?
                      and s.create_time < (CAST(? AS DATE) + INTERVAL '1 day')
                where kc.language_pack_id = ?
                group by kc.id
                order by kc.id asc
                """,
                (rs) -> {
                    long submitCount = rs.getLong("submission_count");
                    long acceptedCount = rs.getLong("accepted_count");
                    double ratio = submitCount == 0 ? 0.0 : acceptedCount * 1.0 / submitCount;
                    double mastery = acceptedCount > 0 ? Math.max(ratio, 0.7) : ratio;
                    masteryMap.put(String.valueOf(rs.getLong("id")), mastery);
                },
                languagePackId, finalUserId, beforeDate, languagePackId
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mastery_map", masteryMap);
        payload.put("before_date", beforeDate);
        return ApiResponse.success(payload);
    }

    public ApiResponse<Object> kcDetail(String kcId, Map<String, String> params, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long languagePackId = resolveAuthorizedLanguagePackId(params, auth);
        Long userId = parseLong(params.get("user_id"));
        if (userId == null) {
            userId = auth.userId();
        }
        if (!userId.equals(auth.userId()) && !auth.adminRole()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        Long kcIdLong = parseLong(kcId);
        if (kcIdLong == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid kc_id");
        }

        // 1. Load KC metadata from ai_knowledge_component
        Map<String, Object> kcMeta;
        try {
            kcMeta = jdbcTemplate.queryForObject(
                    """
                    select id, name, chapter, description
                    from ai_knowledge_component
                    where id = ? and language_pack_id = ?
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", rs.getLong("id"));
                        m.put("name", trimToEmpty(rs.getString("name")));
                        m.put("chapter", trimToEmpty(rs.getString("chapter")));
                        m.put("description", trimToEmpty(rs.getString("description")));
                        return m;
                    },
                    kcIdLong,
                    languagePackId
            );
        } catch (EmptyResultDataAccessException ignored) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "KC not found");
        }

        // 2. Load related problems via ai_problem_kc_mapping with submission stats
        List<Map<String, Object>> problems = jdbcTemplate.query(
                """
                select p.id as problem_id, p._id as display_id, p.title,
                       count(s.id) as submit_count,
                       coalesce(sum(case when s.result = 0 then 1 else 0 end), 0) as accepted_count,
                       min(case when s.result = 0 then 0 else null end) as user_result
                from ai_problem_kc_mapping m
                join problem p on p.id = m.problem_id
                left join submission s on s.problem_id = p.id and s.user_id = ?
                where m.kc_id = ?
                  and m.language_pack_id = ?
                group by p.id, p._id, p.title
                order by p.id asc
                """,
                (rs, rowNum) -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("problem_id", rs.getLong("problem_id"));
                    detail.put("display_id", rs.getString("display_id"));
                    detail.put("title", rs.getString("title"));
                    long submitCount = rs.getLong("submit_count");
                    long acceptedCount = rs.getLong("accepted_count");
                    detail.put("submission_count", submitCount);
                    detail.put("accepted_count", acceptedCount);
                    // user_result: AC = passed, WA = attempted but not passed, null = not attempted
                    Object userResult = rs.getObject("user_result");
                    if (userResult != null) {
                        detail.put("user_result", "AC");
                    } else if (submitCount > 0) {
                        detail.put("user_result", "WA");
                    } else {
                        detail.put("user_result", null);
                    }
                    return detail;
                },
                userId,
                kcIdLong,
                languagePackId
        );

        // 3. Compute mastery from related problems
        long totalSubmissions = problems.stream().mapToLong(p -> longValue(p.get("submission_count"))).sum();
        long totalAccepted = problems.stream().mapToLong(p -> longValue(p.get("accepted_count"))).sum();
        double mastery = totalSubmissions == 0 ? 0.0 : totalAccepted * 1.0 / totalSubmissions;
        if (totalAccepted > 0) {
            mastery = Math.max(mastery, 0.7);
        }

        Map<String, Object> masteryData = new LinkedHashMap<>();
        masteryData.put("p_mastery", mastery);
        masteryData.put("update_count", totalSubmissions);

        // 4. Build response matching StarMapDetailPanel.vue expected structure
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kc", kcMeta);
        payload.put("mastery", masteryData);
        payload.put("problems", problems);
        payload.put("prerequisites", List.of());
        payload.put("active_misconceptions", List.of());
        payload.put("mastery_history", List.of());

        return ApiResponse.success(payload);
    }

    public ApiResponse<Object> submissionRiver(String problemId, Map<String, String> params, Authentication authentication) {
        UserAuth auth = resolveUser(authentication);
        if (!auth.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        Long userId = parseLong(params.get("user_id"));
        if (userId == null) {
            userId = auth.userId();
        }
        if (!userId.equals(auth.userId()) && !auth.adminRole()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        Long pid = parseLong(problemId);
        if (pid == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid problem_id");
        }
        List<Map<String, Object>> timeline = jdbcTemplate.query(
                """
                select id, result, language, create_time,
                       substring(coalesce(code, '') from 1 for 20000) as code_preview
                from submission
                where user_id = ? and problem_id = ?
                order by create_time asc
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("submission_id", rs.getString("id"));
                    item.put("result", rs.getInt("result"));
                    item.put("language", rs.getString("language"));
                    item.put("create_time", formatTime(rs.getTimestamp("create_time")));
                    item.put("code_preview", trimToEmpty(rs.getString("code_preview")));
                    return item;
                },
                userId,
                pid
        );
        return ApiResponse.success(Map.of(
                "user_id", userId,
                "problem_id", pid,
                "timeline", timeline
        ));
    }

    private TaskRow findTask(String taskId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select task_id, user_id, session_id, status, response_data::text as response_data,
                           rag_hit, rag_score, error_message
                    from ai_inference_task
                    where task_id = ?
                    """,
                    (rs, rowNum) -> new TaskRow(
                            rs.getString("task_id"),
                            rs.getLong("user_id"),
                            rs.getString("session_id"),
                            rs.getString("status"),
                            rs.getString("response_data"),
                            rs.getBoolean("rag_hit"),
                            rs.getObject("rag_score") == null ? null : rs.getDouble("rag_score"),
                            rs.getString("error_message")
                    ),
                    taskId
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private SessionRow findSession(String sessionId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select session_id, user_id, current_stage, is_active, context_window::text as context_json,
                           create_time, update_time
                    from ai_dialogue_session
                    where session_id = ?
                    """,
                    (rs, rowNum) -> new SessionRow(
                            rs.getString("session_id"),
                            rs.getLong("user_id"),
                            rs.getString("current_stage"),
                            rs.getBoolean("is_active"),
                            rs.getString("context_json"),
                            rs.getTimestamp("create_time"),
                            rs.getTimestamp("update_time")
                    ),
                    sessionId
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private Map<String, Object> findWorkflowSession(String sessionId, Long problemId, Long userId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select session_id, problem_id, user_id, phase, node_outputs::text as node_outputs_json
                    from ai_workflow_session
                    where session_id = ? and problem_id = ? and user_id = ? and is_active = true
                    limit 1
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("session_id", rs.getString("session_id"));
                        row.put("problem_id", rs.getLong("problem_id"));
                        row.put("user_id", rs.getLong("user_id"));
                        row.put("phase", rs.getString("phase"));
                        row.put("node_outputs", parseJsonMap(rs.getString("node_outputs_json")));
                        return row;
                    },
                    sessionId,
                    problemId,
                    userId
            );
        } catch (EmptyResultDataAccessException ignored) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "workflow session not found");
        }
    }

    private UserAuth resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return new UserAuth(false, null, false, false);
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id, admin_type from \"user\" where lower(username) = ?",
                    (rs, rowNum) -> {
                        String adminType = rs.getString("admin_type");
                        boolean adminRole = "Admin".equals(adminType) || "Teacher".equals(adminType);
                        boolean adminManager = "Admin".equals(adminType);
                        return new UserAuth(true, rs.getLong("id"), adminRole, adminManager);
                    },
                    authentication.getName().toLowerCase(Locale.ROOT)
            );
        } catch (EmptyResultDataAccessException ignored) {
            return new UserAuth(false, null, false, false);
        }
    }

    private void recordLearningEvent(Long userId, Long problemId, String eventType, Map<String, Object> extraData) {
        String taxonomyRaw = trimToNull(stringValue(extraData == null ? null : extraData.get("error_taxonomy")));
        if (taxonomyRaw == null) {
            taxonomyRaw = trimToNull(stringValue(extraData == null ? null : extraData.get("error_category")));
        }
        String errorTaxonomy = taxonomyRaw == null
                ? null
                : com.alethicode.service.aitutor.contract.ErrorTaxonomy.normalize(taxonomyRaw);
        String rootCause = trimToNull(stringValue(extraData == null ? null : extraData.get("root_cause")));
        String detectorName = trimToNull(stringValue(extraData == null ? null : extraData.get("detector_name")));
        jdbcTemplate.update(
                """
                insert into ai_learning_event(user_id, problem_id, event_type, extra_data,
                                              error_taxonomy, root_cause, detector_name)
                values (?, ?, ?, cast(? as jsonb), ?, ?, ?)
                """,
                userId,
                problemId,
                eventType,
                toJson(extraData),
                errorTaxonomy,
                rootCause,
                detectorName
        );
    }

    private Map<String, Object> mapNotebookEntry(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", rs.getString("id"));
        item.put("problem_id", rs.getObject("problem_id"));
        item.put("language_pack_id", rs.getObject("language_pack_id"));
        item.put("language", rs.getString("language"));
        item.put("error_taxonomy", rs.getString("error_taxonomy"));
        item.put("root_cause", rs.getString("root_cause"));
        item.put("fix_outcome", rs.getString("fix_outcome"));
        item.put("student_reflection", rs.getString("student_reflection"));
        item.put("tags", toStringList(parseJsonList(rs.getString("tags_json")), 20, 64));
        item.put("evidence_ptr", parseJsonMap(rs.getString("evidence_json")));
        item.put("create_time", formatTime(rs.getTimestamp("create_time")));
        try {
            item.put("conquered", rs.getBoolean("conquered"));
        } catch (java.sql.SQLException ignored) {
            item.put("conquered", false);
        }
        return item;
    }

    private Map<String, Object> loadCalibrationState(Long userId) {
        try {
            return jdbcTemplate.queryForObject(
                    "select calibrated, current_index, accumulated::text as accumulated_json from ai_calibration_state where user_id = ?",
                    (rs, rowNum) -> {
                        Map<String, Object> state = new LinkedHashMap<>();
                        state.put("calibrated", rs.getBoolean("calibrated"));
                        state.put("current_index", rs.getInt("current_index"));
                        state.put("accumulated", parseJsonMap(rs.getString("accumulated_json")));
                        return state;
                    },
                    userId
            );
        } catch (EmptyResultDataAccessException ignored) {
            return Map.of("calibrated", false, "current_index", 0, "accumulated", Map.of());
        }
    }

    private List<Map<String, Object>> calibrationQuestions(Long userId) {
        return List.of(
                Map.of("index", 0, "prompt", "请解释 for 循环的执行过程。", "kc_group", "loop"),
                Map.of("index", 1, "prompt", "你会如何识别数组越界风险？", "kc_group", "array"),
                Map.of("index", 2, "prompt", "递归终止条件为什么重要？", "kc_group", "recursion")
        );
    }

    private double scoreCalibrationAnswer(String kcGroup, String answer) {
        String normalizedAnswer = normalizeCalibrationText(answer);
        if (normalizedAnswer.isBlank()) {
            return 0.0;
        }
        double lengthScore = clamp01(normalizedAnswer.length() / 140.0);
        double keywordCoverage = scoreCalibrationKeywordCoverage(kcGroup, normalizedAnswer);
        double explanationScore = containsAnyKeyword(normalizedAnswer, CALIBRATION_EXPLANATION_MARKERS) ? 1.0 : 0.0;
        double score = 0.2 * lengthScore + 0.7 * keywordCoverage + 0.1 * explanationScore;
        if (normalizedAnswer.length() < 16) {
            score = Math.min(score, 0.35);
        }
        return roundThreeDigits(clamp01(score));
    }

    private double scoreCalibrationKeywordCoverage(String kcGroup, String normalizedAnswer) {
        List<List<String>> conceptGroups = CALIBRATION_CONCEPT_KEYWORDS.get(trimToEmpty(kcGroup));
        if (conceptGroups == null || conceptGroups.isEmpty()) {
            return clamp01(normalizedAnswer.length() / 100.0);
        }
        int matchedGroups = 0;
        for (List<String> keywordGroup : conceptGroups) {
            if (containsAnyKeyword(normalizedAnswer, keywordGroup)) {
                matchedGroups++;
            }
        }
        return conceptGroups.isEmpty() ? 0.0 : (double) matchedGroups / conceptGroups.size();
    }

    private boolean containsAnyKeyword(String normalizedText, List<String> keywords) {
        for (String keyword : keywords) {
            String normalizedKeyword = normalizeCalibrationText(keyword);
            if (!normalizedKeyword.isBlank() && normalizedText.contains(normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeCalibrationText(String raw) {
        String lower = trimToEmpty(raw).toLowerCase(Locale.ROOT);
        return lower.replaceAll("\\s+", " ").trim();
    }

    private Map<String, Double> normalizeCalibrationAccumulated(Object rawAccumulated) {
        Map<String, Object> rawMap = toMap(rawAccumulated);
        Map<String, Double> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
            String group = cut(trimToEmpty(entry.getKey()), 32, "");
            if (group.isBlank()) {
                continue;
            }
            normalized.put(group, roundThreeDigits(clamp01(doubleValue(entry.getValue()))));
        }
        return normalized;
    }

    private List<Map<String, Object>> toCalibratedKcPayload(Map<String, Double> accumulated) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (Map.Entry<String, Double> entry : accumulated.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kc_name", entry.getKey());
            item.put("p_mastery_calibrated", roundThreeDigits(clamp01(entry.getValue())));
            payload.add(item);
        }
        return payload;
    }

    private double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private double roundThreeDigits(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private Map<String, Object> castToMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private List<Object> castToList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        if (value instanceof String raw) {
            return parseJsonMap(raw);
        }
        return new LinkedHashMap<>();
    }

    private List<Object> parseJsonList(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException ignored) {
            return new ArrayList<>();
        }
    }

    private List<String> toStringList(Object value, int maxCount, int maxLength) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (result.size() >= maxCount) {
                    break;
                }
                result.add(cut(trimToEmpty(stringValue(item)), maxLength, ""));
            }
            return result;
        }
        return result;
    }

    private Map<String, Object> toStringMap(Object value, int maxCount, int keyLen, int valueLen) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (result.size() >= maxCount) {
                    break;
                }
                String key = cut(trimToEmpty(stringValue(entry.getKey())), keyLen, "");
                String v = cut(trimToEmpty(stringValue(entry.getValue())), valueLen, "");
                result.put(key, v);
            }
        }
        return result;
    }

    private List<Integer> toIntList(Object value) {
        List<Integer> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                Integer parsed = parseIntObjNullable(item);
                if (parsed != null) {
                    result.add(parsed);
                }
            }
        }
        return result;
    }

    private int parseIntObj(Object value, int fallback) {
        Integer parsed = parseIntObjNullable(value);
        return parsed == null ? fallback : parsed;
    }

    private Integer parseIntObjNullable(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            log.debug("parseIntObjNullable returned null: value={}", value, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractRecommendedCard(Map<String, Object> supplementPlan) {
        Object rawCards = supplementPlan.get("cards");
        if (!(rawCards instanceof List<?> cards) || cards.isEmpty()) {
            return Map.of();
        }
        for (Object cardObj : cards) {
            if (cardObj instanceof Map<?, ?> rawMap) {
                Map<String, Object> card = (Map<String, Object>) rawMap;
                if (!"course_example".equals(stringValue(card.get("card_type")))) {
                    return card;
                }
            }
        }
        Object first = cards.getFirst();
        if (first instanceof Map<?, ?> rawMap) {
            return (Map<String, Object>) rawMap;
        }
        return Map.of();
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(stringValue(value)) || "1".equals(stringValue(value));
    }

    private String cut(String value, int length, String fallback) {
        String normalized = value == null ? "" : value;
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized.length() > length ? normalized.substring(0, length) : normalized;
    }

    private Map<String, Object> parseJsonMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("json serialize failed", exception);
        }
    }

    private String toJsonSafe(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private String nowIso() {
        return DATE_TIME_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC));
    }

    private String formatTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(timestamp.toInstant().atOffset(ZoneOffset.UTC));
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(trimToEmpty(raw));
        } catch (Exception e) {
            log.debug("parseInt using fallback: raw={}, fallback={}", raw, fallback, e);
            return fallback;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            log.debug("longValue using default 0: value={}", value, e);
            return 0L;
        }
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            log.debug("doubleValue using default 0.0: value={}", value, e);
            return 0.0;
        }
    }

    private Long resolveAuthorizedLanguagePackId(Map<String, String> params, UserAuth auth) {
        Long languagePackId = parseLong(params.get("language_pack_id"));
        if (languagePackId == null) {
            throw new com.alethicode.exception.BadRequestException("language_pack_id is required");
        }
        if (auth.adminRole()) {
            return languagePackId;
        }
        Set<Long> accessiblePackIds = loadAccessibleLanguagePackIds(auth.userId());
        if (!accessiblePackIds.contains(languagePackId)) {
            throw new com.alethicode.exception.BusinessException(
                    com.alethicode.exception.ErrorCode.FORBIDDEN,
                    "Permission denied"
            );
        }
        return languagePackId;
    }

    private Set<Long> loadAccessibleLanguagePackIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
                """
                select distinct clp.language_pack_id
                from classroom_member cm
                join classroom_language_pack clp on clp.classroom_id = cm.classroom_id
                join classroom c on c.id = cm.classroom_id
                where cm.user_id = ?
                  and c.is_active = true
                """,
                Long.class,
                userId
        ));
    }

    private Long parseLong(String raw) {
        try {
            return Long.parseLong(trimToEmpty(raw));
        } catch (Exception e) {
            log.debug("parseLong returned null: raw={}", raw, e);
            return null;
        }
    }

    private record UserAuth(boolean authenticated, Long userId, boolean adminRole, boolean adminManager) {
    }

    private record SessionRow(
            String sessionId,
            Long userId,
            String currentStage,
            boolean active,
            String contextWindow,
            Timestamp createTime,
            Timestamp updateTime
    ) {
    }

    private record TaskRow(
            String taskId,
            Long userId,
            String sessionId,
            String status,
            String responseData,
            boolean ragHit,
            Double ragScore,
            String errorMessage
    ) {
        private Map<String, Object> toPayload(Map<String, Object> responseDataJson) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("task_id", taskId);
            payload.put("session_id", sessionId);
            payload.put("status", status == null ? "pending" : status.toLowerCase(Locale.ROOT));
            payload.put("rag_hit", ragHit);
            payload.put("rag_score", ragScore);
            payload.put("guidance", "completed".equalsIgnoreCase(status) ? responseDataJson.get("content") : null);
            payload.put("pedagogy", "completed".equalsIgnoreCase(status) ? responseDataJson.get("pedagogy") : null);
            payload.put("error", "failed".equalsIgnoreCase(status) ? errorMessage : null);
            return payload;
        }
    }
}
