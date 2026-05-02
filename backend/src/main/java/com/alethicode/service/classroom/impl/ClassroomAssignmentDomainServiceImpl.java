package com.alethicode.service.classroom.impl;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.aitutor.events.ClassroomAssignmentEventSubscriber;
import com.alethicode.service.aitutor.events.LearningEventPublisher;
import com.alethicode.service.classroom.ClassroomAccessHelper;
import com.alethicode.service.classroom.ClassroomAccessHelper.UserAuth;
import com.alethicode.service.classroom.ClassroomAssignmentDomainService;
import com.alethicode.service.classroom.ai.ClassroomAssignmentSmartComposer;
import com.alethicode.service.classroom.ai.ClassroomKcResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.alethicode.util.ServiceParseUtils.*;

@Service
@Transactional(rollbackFor = Exception.class)
public class ClassroomAssignmentDomainServiceImpl implements ClassroomAssignmentDomainService {

    private static final Logger log = LoggerFactory.getLogger(ClassroomAssignmentDomainServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ClassroomAccessHelper access;
    private final ClassroomKcResolver classroomKcResolver;
    private final ClassroomAssignmentSmartComposer smartComposer;
    private final LearningEventPublisher learningEventPublisher;
    private final ClassroomAssignmentEventSubscriber classroomAssignmentEventSubscriber;

    public ClassroomAssignmentDomainServiceImpl(JdbcTemplate jdbcTemplate,
                                                ObjectMapper objectMapper,
                                                ClassroomAccessHelper access,
                                                ClassroomKcResolver classroomKcResolver,
                                                ClassroomAssignmentSmartComposer smartComposer,
                                                LearningEventPublisher learningEventPublisher,
                                                @Lazy ClassroomAssignmentEventSubscriber classroomAssignmentEventSubscriber) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.access = access;
        this.classroomKcResolver = classroomKcResolver;
        this.smartComposer = smartComposer;
        this.learningEventPublisher = learningEventPublisher;
        this.classroomAssignmentEventSubscriber = classroomAssignmentEventSubscriber;
    }

    @Override
    public ApiResponse<Object> assignmentList(String classroomId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isMember(classroomId, user.userId())) {
            return ApiResponse.success(Map.of("results", List.of()));
        }
        boolean staff = access.isStaff(classroomId, user.userId());
        String visibility = staff ? "" : " and is_public = true and start_time <= now()";
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, title, description, start_time, end_time,
                       allow_late_submission, late_penalty, is_public,
                       anti_cheating_enabled, allow_ai_tutor, create_time, update_time,
                       compose_strategy, target_kc_ids::text as target_kc_ids_json
                from classroom_assignment
                where classroom_id = ?
                """ + visibility + " order by create_time desc",
                (rs, rowNum) -> mapAssignmentRow(rs, classroomId),
                classroomId
        );
        return ApiResponse.success(Map.of("results", rows));
    }

    @Override
    public ApiResponse<Object> assignmentRetrieve(String classroomId, String assignmentId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isMember(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        Map<String, Object> item = assignmentDetail(classroomId, assignmentId);
        if (item == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Assignment not found");
        }
        return ApiResponse.success(item);
    }

    @Override
    public ApiResponse<Object> assignmentCreate(String classroomId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        String title = trimToNull(stringValue(request.get("title")));
        if (title == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "title is required");
        }

        String composeStrategy = trimToEmpty(stringValue(request.get("compose_strategy"))).toLowerCase(java.util.Locale.ROOT);
        if (composeStrategy.isBlank()) {
            composeStrategy = "manual";
        }
        if (!"manual".equals(composeStrategy) && !"smart_kc".equals(composeStrategy)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "compose_strategy 必须为 manual 或 smart_kc");
        }

        List<Long> targetKcIds = List.of();
        List<Object> sectionsRequest = castList(request.get("sections"));
        if ("smart_kc".equals(composeStrategy)) {
            targetKcIds = classroomKcResolver.expandKcIds(classroomId, castList(request.get("target_kc_ids")));
            Integer perStudentBudget = parseIntObjNullable(request.get("per_student_budget"));
            Integer totalProblemBudget = parseIntObjNullable(request.get("total_problem_budget"));
            Map<String, Object> compose = smartComposer.composeForClassroom(
                    classroomId, targetKcIds, perStudentBudget, totalProblemBudget
            );
            sectionsRequest = composeSmartSectionsToAssignmentSectionRequest(classroomId, compose);
            if (sectionsRequest.isEmpty()) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "智能组卷未选出题，请放宽 KC 或扩大预算");
            }
            if (targetKcIds.isEmpty()) {
                List<?> derivedKcIds = (List<?>) compose.getOrDefault("kc_ids", List.of());
                targetKcIds = new ArrayList<>();
                for (Object id : derivedKcIds) {
                    if (id instanceof Number n) targetKcIds.add(n.longValue());
                }
            }
        }

        String assignmentId = randomId();
        String start = trimToNull(stringValue(request.get("start_time")));
        String end = trimToNull(stringValue(request.get("end_time")));
        if (start == null) start = nowIso();
        if (end == null) end = nowPlusHours(24);

        jdbcTemplate.update(
                """
                insert into classroom_assignment(id, classroom_id, creator_id, title, description,
                                                 start_time, end_time, allow_late_submission, late_penalty,
                                                 is_public, anti_cheating_enabled, allow_ai_tutor,
                                                 allowed_groups,
                                                 compose_strategy, target_kc_ids,
                                                 create_time, update_time)
                values (?, ?, ?, ?, ?, cast(? as timestamptz), cast(? as timestamptz), ?, ?, ?, ?, ?, cast(? as jsonb),
                        ?, cast(? as jsonb),
                        now(), now())
                """,
                assignmentId, classroomId, user.userId(), title,
                trimToEmpty(stringValue(request.get("description"))),
                start, end,
                parseBoolean(request.get("allow_late_submission"), false),
                parseDouble(request.get("late_penalty"), 0.8),
                parseBoolean(request.get("is_public"), true),
                parseBoolean(request.get("anti_cheating_enabled"), false),
                parseBoolean(request.get("allow_ai_tutor"), true),
                toJson(objectMapper, List.of()),
                composeStrategy,
                toJson(objectMapper, targetKcIds)
        );

        for (Object sectionObj : sectionsRequest) {
            Map<String, Object> section = castMap(sectionObj);
            String sectionId = randomId();
            jdbcTemplate.update(
                    """
                    insert into classroom_assignment_section(id, assignment_id, title, description, sort_order)
                    values (?, ?, ?, ?, ?)
                    """,
                    sectionId, assignmentId,
                    trimToEmpty(stringValue(section.get("title"))),
                    trimToEmpty(stringValue(section.get("description"))),
                    parseIntObj(section.get("order"), 0)
            );
            for (Object problemObj : castList(section.get("problems"))) {
                Map<String, Object> p = castMap(problemObj);
                String classroomProblemId = trimToNull(stringValue(p.get("problem_id")));
                if (classroomProblemId == null) continue;
                jdbcTemplate.update(
                        """
                        insert into classroom_assignment_problem(id, section_id, classroom_problem_id, score, sort_order)
                        values (?, ?, ?, ?, ?)
                        """,
                        randomId(), sectionId, classroomProblemId,
                        parseDouble(p.get("score"), 10),
                        parseIntObj(p.get("order"), 0)
                );
            }
        }
        return ApiResponse.success(assignmentDetail(classroomId, assignmentId));
    }

    @Override
    public ApiResponse<Object> assignmentProblemEnter(String classroomId, String assignmentId, String classroomProblemId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isMember(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        Map<String, Object> assignment = jdbcTemplate.query(
                """
                select a.id, a.start_time, a.end_time, a.allow_late_submission,
                       a.anti_cheating_enabled, a.allow_ai_tutor,
                       cp.problem_id, cp.classroom_id as cp_classroom_id
                from classroom_assignment a
                join classroom_assignment_section sec on sec.assignment_id = a.id
                join classroom_assignment_problem ap on ap.section_id = sec.id
                join classroom_problem cp on cp.id = ap.classroom_problem_id
                where a.id = ? and a.classroom_id = ? and ap.classroom_problem_id = ?
                limit 1
                """,
                rs -> {
                    if (!rs.next()) return null;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("start_time", rs.getTimestamp("start_time"));
                    row.put("end_time", rs.getTimestamp("end_time"));
                    row.put("allow_late_submission", rs.getBoolean("allow_late_submission"));
                    row.put("anti_cheating_enabled", rs.getBoolean("anti_cheating_enabled"));
                    row.put("allow_ai_tutor", rs.getBoolean("allow_ai_tutor"));
                    row.put("problem_id", rs.getLong("problem_id"));
                    return row;
                },
                assignmentId, classroomId, classroomProblemId
        );
        if (assignment == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Assignment problem not found");
        }
        Instant now = Instant.now();
        Timestamp start = (Timestamp) assignment.get("start_time");
        Timestamp end = (Timestamp) assignment.get("end_time");
        boolean inWindow = (start == null || !now.isBefore(start.toInstant()))
                && (end == null || !now.isAfter(end.toInstant())
                    || Boolean.TRUE.equals(assignment.get("allow_late_submission")));
        Map<String, Object> tutorContext = new LinkedHashMap<>();
        tutorContext.put("source", "classroom_assignment");
        tutorContext.put("classroom_id", classroomId);
        tutorContext.put("assignment_id", assignmentId);
        tutorContext.put("classroom_problem_id", classroomProblemId);
        tutorContext.put("problem_id", assignment.get("problem_id"));
        tutorContext.put("anti_cheating", Boolean.TRUE.equals(assignment.get("anti_cheating_enabled")));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("assignment_id", assignment.get("id"));
        response.put("classroom_id", classroomId);
        response.put("classroom_problem_id", classroomProblemId);
        response.put("problem_id", assignment.get("problem_id"));
        response.put("allow_ai_tutor", Boolean.TRUE.equals(assignment.get("allow_ai_tutor")));
        response.put("anti_cheating_enabled", Boolean.TRUE.equals(assignment.get("anti_cheating_enabled")));
        response.put("in_window", inWindow);
        response.put("tutor_context", tutorContext);
        return ApiResponse.success(response);
    }

    @Override
    public ApiResponse<Object> assignmentPreviewSmartCompose(String classroomId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        List<Long> targetKcIds = classroomKcResolver.expandKcIds(classroomId, castList(request.get("target_kc_ids")));
        Integer perStudentBudget = parseIntObjNullable(request.get("per_student_budget"));
        Integer totalProblemBudget = parseIntObjNullable(request.get("total_problem_budget"));
        Map<String, Object> result = smartComposer.composeForClassroom(classroomId, targetKcIds, perStudentBudget, totalProblemBudget);
        return ApiResponse.success(result);
    }

    private List<Object> composeSmartSectionsToAssignmentSectionRequest(String classroomId, Map<String, Object> compose) {
        List<Object> sections = new ArrayList<>();
        Object rawSectionsObj = compose.get("sections");
        if (!(rawSectionsObj instanceof List<?> rawSections)) {
            return sections;
        }
        int order = 0;
        for (Object rawSection : rawSections) {
            if (!(rawSection instanceof Map<?, ?> sectionRaw)) continue;
            Map<String, Object> sectionMap = castMap(sectionRaw);
            Object rawProblemsObj = sectionMap.get("problems");
            List<?> rawProblems = rawProblemsObj instanceof List<?> list ? list : List.of();
            List<Long> problemIds = rawProblems.stream()
                    .filter(p -> p instanceof Map<?, ?>)
                    .map(p -> ((Map<?, ?>) p).get("problem_id"))
                    .filter(p -> p instanceof Number)
                    .map(p -> ((Number) p).longValue())
                    .collect(Collectors.toList());
            if (problemIds.isEmpty()) continue;
            List<String> classroomProblemIds = smartComposer.resolveClassroomProblemIdsByProblemId(classroomId, problemIds);
            if (classroomProblemIds.isEmpty()) {
                log.warn("smart compose section dropped because problems not in classroom_problem classroom={} kc_id={}",
                        classroomId, sectionMap.get("kc_id"));
                continue;
            }
            Map<String, Object> section = new LinkedHashMap<>();
            String title = sectionMap.get("title") == null ? "智能组卷" : String.valueOf(sectionMap.get("title"));
            section.put("title", title);
            section.put("description", "");
            section.put("order", order++);
            List<Map<String, Object>> problems = new ArrayList<>();
            int problemOrder = 0;
            for (String cpId : classroomProblemIds) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("problem_id", cpId);
                p.put("score", 10);
                p.put("order", problemOrder++);
                problems.add(p);
            }
            section.put("problems", problems);
            sections.add(section);
        }
        return sections;
    }

    @Override
    public ApiResponse<Object> assignmentUpdate(String classroomId, String assignmentId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        int updated = jdbcTemplate.update(
                """
                update classroom_assignment
                set title = coalesce(?, title),
                    description = coalesce(?, description),
                    start_time = coalesce(cast(? as timestamptz), start_time),
                    end_time = coalesce(cast(? as timestamptz), end_time),
                    allow_late_submission = coalesce(?, allow_late_submission),
                    late_penalty = coalesce(?, late_penalty),
                    is_public = coalesce(?, is_public),
                    anti_cheating_enabled = coalesce(?, anti_cheating_enabled),
                    allow_ai_tutor = coalesce(?, allow_ai_tutor),
                    update_time = now()
                where classroom_id = ? and id = ?
                """,
                trimToNull(stringValue(request.get("title"))),
                trimToNull(stringValue(request.get("description"))),
                trimToNull(stringValue(request.get("start_time"))),
                trimToNull(stringValue(request.get("end_time"))),
                parseBooleanObj(request.get("allow_late_submission")),
                parseDoubleObj(request.get("late_penalty")),
                parseBooleanObj(request.get("is_public")),
                parseBooleanObj(request.get("anti_cheating_enabled")),
                parseBooleanObj(request.get("allow_ai_tutor")),
                classroomId, assignmentId
        );
        if (updated == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Assignment not found");
        }
        return ApiResponse.success(assignmentDetail(classroomId, assignmentId));
    }

    @Override
    public ApiResponse<Object> assignmentDelete(String classroomId, String assignmentId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        jdbcTemplate.update("delete from classroom_assignment where classroom_id = ? and id = ?", classroomId, assignmentId);
        return ApiResponse.success("success");
    }

    @Override
    public ApiResponse<Object> assignmentSubmit(String classroomId, String assignmentId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isMember(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }

        Map<String, Object> assignment = jdbcTemplate.query(
                "select id, start_time, end_time, allow_late_submission, late_penalty from classroom_assignment where classroom_id = ? and id = ?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("start_time", rs.getTimestamp("start_time"));
                    row.put("end_time", rs.getTimestamp("end_time"));
                    row.put("allow_late_submission", rs.getBoolean("allow_late_submission"));
                    row.put("late_penalty", rs.getDouble("late_penalty"));
                    return row;
                },
                classroomId, assignmentId
        ).stream().findFirst().orElse(null);
        if (assignment == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Assignment not found");
        }

        Instant now = Instant.now();
        Timestamp start = (Timestamp) assignment.get("start_time");
        Timestamp end = (Timestamp) assignment.get("end_time");
        if (start != null && now.isBefore(start.toInstant())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "作业尚未开始");
        }
        boolean late = end != null && now.isAfter(end.toInstant());
        if (late && !Boolean.TRUE.equals(assignment.get("allow_late_submission"))) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "作业已截止，当前不允许补交");
        }

        String submissionId = jdbcTemplate.query(
                "select id from classroom_assignment_submission where assignment_id = ? and user_id = ?",
                (rs, rowNum) -> rs.getString("id"),
                assignmentId, user.userId()
        ).stream().findFirst().orElse(null);
        if (submissionId == null) {
            submissionId = randomId();
            jdbcTemplate.update(
                    """
                    insert into classroom_assignment_submission(id, assignment_id, user_id, is_late, total_score, is_graded, submit_time)
                    values (?, ?, ?, ?, 0, false, now())
                    """,
                    submissionId, assignmentId, user.userId(), late);
        } else {
            jdbcTemplate.update("update classroom_assignment_submission set is_late = ?, submit_time = now() where id = ?", late, submissionId);
        }

        Map<String, Object> answers = castMap(request.get("answers"));
        Map<String, Object> fillAnswers = castMap(request.get("fill_answers"));

        List<Map<String, Object>> assignmentProblems = jdbcTemplate.query(
                """
                select ap.id as assignment_problem_id, ap.score, cp.problem_id,
                       p.statistic_info::text as statistic_info_json
                from classroom_assignment_problem ap
                join classroom_assignment_section s on s.id = ap.section_id
                join classroom_problem cp on cp.id = ap.classroom_problem_id
                join problem p on p.id = cp.problem_id
                where s.assignment_id = ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> statInfo = parseJsonMap(objectMapper, rs.getString("statistic_info_json"));
                    Map<String, Object> oq = castMap(statInfo.get("objective_question"));
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("assignment_problem_id", rs.getString("assignment_problem_id"));
                    item.put("score", rs.getDouble("score"));
                    item.put("problem_id", rs.getLong("problem_id"));
                    item.put("objective_question", oq);
                    return item;
                },
                assignmentId
        );

        Map<Long, String> codingStatuses = loadCodingStatuses(user.userId(), assignmentProblems);

        double totalScore = 0.0;
        for (Map<String, Object> ap : assignmentProblems) {
            String apId = stringValue(ap.get("assignment_problem_id"));
            Map<String, Object> oq = castMap(ap.get("objective_question"));
            String questionType = trimToNull(stringValue(oq.get("question_type")));
            double score = parseDouble(ap.get("score"), 0);

            String detailId = jdbcTemplate.query(
                    "select id from classroom_assignment_problem_submission where submission_id = ? and assignment_problem_id = ?",
                    (rs, rowNum) -> rs.getString("id"),
                    submissionId, apId
            ).stream().findFirst().orElse(null);
            if (detailId == null) {
                detailId = randomId();
                jdbcTemplate.update(
                        """
                        insert into classroom_assignment_problem_submission(id, submission_id, assignment_problem_id, create_time)
                        values (?, ?, ?, now())
                        """,
                        detailId, submissionId, apId);
            }

            String answer = "";
            String judgeStatus = "Pending";
            double judgeScore = 0;

            if ("choice".equals(questionType)) {
                String given = stringValue(answers.get(apId));
                String expected = trimToEmpty(stringValue(oq.get("answer")));
                boolean ok = given != null && !expected.isBlank() && expected.equalsIgnoreCase(given.trim());
                answer = given == null ? "" : given;
                judgeStatus = ok ? "AC" : "WA";
                judgeScore = ok ? score : 0;
            } else if ("fill_blank".equals(questionType)) {
                List<Object> blanks = castList(oq.get("blanks"));
                List<String> provided = new ArrayList<>();
                boolean ok = !blanks.isEmpty();
                for (int i = 0; i < blanks.size(); i++) {
                    String key = apId + "_" + i;
                    String value = trimToEmpty(stringValue(fillAnswers.get(key)));
                    provided.add(value);
                    if (!value.equalsIgnoreCase(trimToEmpty(stringValue(blanks.get(i))))) {
                        ok = false;
                    }
                }
                answer = toJson(objectMapper, provided);
                judgeStatus = ok ? "AC" : "WA";
                judgeScore = ok ? score : 0;
            } else {
                Long problemId = parseLongObj(ap.get("problem_id"));
                String status = problemId == null ? null : codingStatuses.get(problemId);
                if ("AC".equals(status)) {
                    judgeStatus = "AC";
                    judgeScore = score;
                } else if ("attempted".equals(status)) {
                    judgeStatus = "attempted";
                    judgeScore = 0;
                }
            }

            jdbcTemplate.update(
                    """
                    update classroom_assignment_problem_submission
                    set answer = ?, judge_score = ?, judge_status = ?, status = ?
                    where id = ?
                    """,
                    answer, judgeScore, judgeStatus, judgeStatus, detailId);
            totalScore += judgeScore;

            Long problemId = parseLongObj(ap.get("problem_id"));
            if (problemId != null && ("AC".equals(judgeStatus) || "WA".equals(judgeStatus))) {
                boolean isCorrect = "AC".equals(judgeStatus);
                String errorTaxonomy = isCorrect ? null : trimToNull(stringValue(ap.get("error_taxonomy")));
                Long languagePackId = jdbcTemplate.query(
                        "select language_pack_id from classroom_language_pack where classroom_id = ? limit 1",
                        rs -> rs.next() ? rs.getLong("language_pack_id") : null,
                        classroomId
                );
                try {
                    classroomAssignmentEventSubscriber.onAssignmentSubmissionGraded(
                            user.userId(), assignmentId, problemId, isCorrect, errorTaxonomy, languagePackId, detailId);
                } catch (RuntimeException exception) {
                    log.warn("classroom_assignment in-process subscriber failed user={} problem={}: {}",
                            user.userId(), problemId, exception.getMessage());
                }
                try {
                    learningEventPublisher.publishAssignmentSubmissionGraded(
                            user.userId(), assignmentId, problemId, isCorrect, errorTaxonomy, languagePackId, detailId);
                } catch (RuntimeException exception) {
                    log.warn("classroom_assignment event publish failed user={} problem={}: {}",
                            user.userId(), problemId, exception.getMessage());
                }
            }
        }

        if (late) {
            totalScore = totalScore * parseDoubleObj(assignment.get("late_penalty"));
        }
        totalScore = Math.round(totalScore * 100.0) / 100.0;

        jdbcTemplate.update(
                "update classroom_assignment_submission set total_score = ?, is_graded = false, submit_time = now() where id = ?",
                totalScore, submissionId);

        return ApiResponse.success(Map.of("message", "提交成功", "total_score", totalScore, "is_late", late));
    }

    @Override
    public ApiResponse<Object> assignmentSubmissions(String classroomId, String assignmentId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        if (classroomId == null || classroomId.isBlank()) {
            throw new IllegalStateException("classroom_id is required");
        }
        List<Map<String, Object>> results = jdbcTemplate.query(
                """
                select s.id, s.user_id, u.username, s.submit_time, s.is_late, s.total_score, s.is_graded
                from classroom_assignment_submission s
                join "user" u on u.id = s.user_id
                join classroom_assignment a on a.id = s.assignment_id
                where s.assignment_id = ? and a.classroom_id = ?
                order by s.submit_time desc
                """,
                (rs, rowNum) -> {
                    String submissionId = rs.getString("id");
                    Long submissionUserId = rs.getLong("user_id");
                    List<Map<String, Object>> details = jdbcTemplate.query(
                            """
                            select d.id, d.assignment_problem_id, d.answer, d.code,
                                   d.judge_score, d.judge_status, d.ta_score, d.ta_comment,
                                   d.ta_graded_by_id, d.status,
                                   d.error_taxonomy, d.review_package_id,
                                   cp.problem_id as classroom_problem_target_id
                            from classroom_assignment_problem_submission d
                            join classroom_assignment_problem ap on ap.id = d.assignment_problem_id
                            join classroom_problem cp on cp.id = ap.classroom_problem_id
                            where d.submission_id = ?
                            """,
                            (drs, drow) -> {
                                Map<String, Object> detail = new LinkedHashMap<>();
                                detail.put("id", drs.getString("id"));
                                detail.put("problem_id", drs.getString("assignment_problem_id"));
                                detail.put("answer", drs.getString("answer"));
                                detail.put("code", drs.getString("code"));
                                detail.put("judge_score", drs.getObject("judge_score"));
                                detail.put("judge_status", drs.getString("judge_status"));
                                detail.put("ta_score", drs.getObject("ta_score"));
                                detail.put("ta_comment", drs.getString("ta_comment"));
                                detail.put("ta_graded_by", drs.getObject("ta_graded_by_id"));
                                detail.put("status", drs.getString("status"));
                                detail.put("error_taxonomy", drs.getString("error_taxonomy"));
                                Long problemId = drs.getLong("classroom_problem_target_id");
                                detail.put("recent_misconceptions", loadRecentMisconceptions(submissionUserId, problemId, 5));
                                String packageId = drs.getString("review_package_id");
                                detail.put("linked_review_package", packageId == null ? null : loadReviewPackageSummary(packageId));
                                return detail;
                            },
                            submissionId
                    );
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", submissionId);
                    row.put("user_id", submissionUserId);
                    row.put("username", rs.getString("username"));
                    row.put("submit_time", formatTime(rs.getTimestamp("submit_time")));
                    row.put("is_late", rs.getBoolean("is_late"));
                    row.put("total_score", rs.getDouble("total_score"));
                    row.put("is_graded", rs.getBoolean("is_graded"));
                    row.put("details", details);
                    return row;
                },
                assignmentId, classroomId
        );
        return ApiResponse.success(Map.of("results", results));
    }

    private List<Map<String, Object>> loadRecentMisconceptions(Long userId, Long problemId, int limit) {
        if (userId == null || problemId == null) return List.of();
        return jdbcTemplate.query(
                """
                select n.error_taxonomy as taxonomy,
                       count(*) as count,
                       max(n.create_time) as last_at
                from ai_learner_notebook n
                join ai_problem_kc_mapping m1 on m1.problem_id = n.problem_id
                join ai_problem_kc_mapping m2 on m2.problem_id = ?
                where n.user_id = ?
                  and n.is_deleted = false
                  and n.error_taxonomy is not null
                  and n.error_taxonomy <> ''
                  and m1.kc_id = m2.kc_id
                group by n.error_taxonomy
                order by count desc, last_at desc
                limit ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    String taxonomy = rs.getString("taxonomy");
                    row.put("taxonomy", taxonomy);
                    row.put("label", taxonomy);
                    row.put("count", rs.getInt("count"));
                    row.put("last_at", formatTime(rs.getTimestamp("last_at")));
                    return row;
                },
                problemId, userId, limit
        );
    }

    private Map<String, Object> loadReviewPackageSummary(String packageId) {
        return jdbcTemplate.query(
                """
                select id, error_taxonomy, mastery_reached, fsrs_due_at
                from ai_error_review_package
                where id = ?
                """,
                rs -> {
                    if (!rs.next()) return null;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("error_taxonomy", rs.getString("error_taxonomy"));
                    row.put("mastery_reached", rs.getBoolean("mastery_reached"));
                    row.put("due_at", formatTime(rs.getTimestamp("fsrs_due_at")));
                    return row;
                },
                packageId
        );
    }

    @Override
    public ApiResponse<Object> assignmentGrade(String classroomId, String assignmentId, String submissionDetailId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        Double taScore = parseDoubleObj(request.get("ta_score"));
        if (taScore == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "ta_score is required");
        }

        String submissionId = jdbcTemplate.query(
                """
                select d.submission_id
                from classroom_assignment_problem_submission d
                join classroom_assignment_submission s on s.id = d.submission_id
                where d.id = ? and s.assignment_id = ?
                """,
                (rs, rowNum) -> rs.getString("submission_id"),
                submissionDetailId, assignmentId
        ).stream().findFirst().orElse(null);
        if (submissionId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "提交记录不存在");
        }

        jdbcTemplate.update(
                """
                update classroom_assignment_problem_submission
                set ta_score = ?, ta_comment = ?, ta_graded_by_id = ?, ta_graded_at = now(), status = 'graded'
                where id = ?
                """,
                taScore, trimToEmpty(stringValue(request.get("ta_comment"))), user.userId(), submissionDetailId);

        Double total = jdbcTemplate.queryForObject(
                """
                select coalesce(sum(coalesce(ta_score, judge_score, score, 0)), 0)
                from classroom_assignment_problem_submission
                where submission_id = ?
                """,
                Double.class, submissionId);
        if (total == null) total = 0.0;
        jdbcTemplate.update("update classroom_assignment_submission set total_score = ?, is_graded = true where id = ?", total, submissionId);
        return ApiResponse.success(Map.of("message", "评分成功", "total_score", total));
    }

    @Override
    public ApiResponse<Object> assignmentStats(String classroomId, String assignmentId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }

        int totalStudents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom_member WHERE classroom_id = ? AND role = 'student'",
                Integer.class, classroomId);

        Map<String, Object> overview = jdbcTemplate.queryForMap("""
            SELECT COUNT(*) AS submitted_count,
                   COALESCE(AVG(s.total_score), 0) AS avg_score,
                   COALESCE(MAX(s.total_score), 0) AS max_score,
                   COALESCE(MIN(s.total_score), 0) AS min_score,
                   COUNT(CASE WHEN s.is_late THEN 1 END) AS late_count,
                   COUNT(CASE WHEN NOT s.is_graded THEN 1 END) AS ungraded_count
            FROM classroom_assignment_submission s
            WHERE s.assignment_id = ?
            """, assignmentId);

        double fullScore = jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(ap.score), 0)
            FROM classroom_assignment_problem ap
            JOIN classroom_assignment_section sec ON sec.id = ap.section_id
            WHERE sec.assignment_id = ?
            """, Double.class, assignmentId);

        List<Map<String, Object>> problemStats = jdbcTemplate.queryForList("""
            SELECT sec.title AS section_title,
                   COALESCE(p.title, cp_p.title, 'unknown') AS problem_title,
                   ap.score AS full_score,
                   COUNT(ps.id) AS attempt_count,
                   COUNT(CASE WHEN ps.judge_status = 'AC' THEN 1 END) AS correct_count,
                   CASE WHEN COUNT(ps.id) > 0
                        THEN ROUND(AVG(COALESCE(ps.ta_score, ps.judge_score, ps.score, 0) / NULLIF(ap.score, 0)) * 100) / 100.0
                        ELSE 0 END AS avg_score_rate
            FROM classroom_assignment_problem ap
            JOIN classroom_assignment_section sec ON sec.id = ap.section_id
            JOIN classroom_problem cp ON cp.id = ap.classroom_problem_id
            LEFT JOIN problem cp_p ON cp_p.id = cp.problem_id
            LEFT JOIN classroom_assignment_problem_submission ps ON ps.assignment_problem_id = ap.id
            WHERE sec.assignment_id = ?
            GROUP BY sec.title, sec.sort_order, ap.sort_order, ap.id, problem_title, ap.score
            ORDER BY sec.sort_order, ap.sort_order
            """, assignmentId);

        List<Map<String, Object>> scoreDistribution = jdbcTemplate.queryForList("""
            SELECT bucket.range_label AS range,
                   COUNT(s.id) AS count
            FROM (VALUES ('0-59', 0, 59.999), ('60-69', 60, 69.999), ('70-79', 70, 79.999), ('80-89', 80, 89.999), ('90-100', 90, 100)) AS bucket(range_label, lo, hi)
            LEFT JOIN classroom_assignment_submission s
                   ON s.assignment_id = ?
                  AND s.total_score >= bucket.lo
                  AND s.total_score <= bucket.hi
            GROUP BY bucket.range_label, bucket.lo
            ORDER BY bucket.lo
            """, assignmentId);

        List<Map<String, Object>> submissions = jdbcTemplate.queryForList("""
            SELECT u.username, s.submit_time, s.total_score, s.is_late, s.is_graded
            FROM classroom_assignment_submission s
            JOIN "user" u ON u.id = s.user_id
            WHERE s.assignment_id = ?
            ORDER BY s.total_score DESC, s.submit_time
            """, assignmentId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_students", totalStudents);
        result.put("submitted_count", ((Number) overview.get("submitted_count")).intValue());
        result.put("avg_score", Math.round(((Number) overview.get("avg_score")).doubleValue() * 10.0) / 10.0);
        result.put("max_score", ((Number) overview.get("max_score")).doubleValue());
        result.put("min_score", ((Number) overview.get("min_score")).doubleValue());
        result.put("full_score", fullScore);
        result.put("late_count", ((Number) overview.get("late_count")).intValue());
        result.put("ungraded_count", ((Number) overview.get("ungraded_count")).intValue());
        result.put("problem_stats", problemStats);
        result.put("score_distribution", scoreDistribution);
        result.put("submissions", submissions);
        return ApiResponse.success(result);
    }

    private Map<Long, String> loadCodingStatuses(Long userId, List<Map<String, Object>> assignmentProblems) {
        List<Long> codingProblemIds = new ArrayList<>();
        for (Map<String, Object> ap : assignmentProblems) {
            Map<String, Object> oq = castMap(ap.get("objective_question"));
            String qt = trimToNull(stringValue(oq.get("question_type")));
            if (qt == null || "coding".equals(qt)) {
                Long pid = parseLongObj(ap.get("problem_id"));
                if (pid != null) codingProblemIds.add(pid);
            }
        }
        if (codingProblemIds.isEmpty()) return Map.of();

        String in = String.join(",", codingProblemIds.stream().map(String::valueOf).toList());
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "select problem_id, result from submission where user_id = ? and problem_id in (" + in + ")",
                (rs, rowNum) -> Map.of("problem_id", rs.getLong("problem_id"), "result", rs.getInt("result")),
                userId);
        Map<Long, String> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long pid = parseLongObj(row.get("problem_id"));
            Integer verdict = parseIntObjNullable(row.get("result"));
            if (pid == null || verdict == null) continue;
            if ("AC".equals(result.get(pid))) continue;
            result.put(pid, verdict == 0 ? "AC" : "attempted");
        }
        return result;
    }

    private Map<String, Object> assignmentDetail(String classroomId, String assignmentId) {
        Map<String, Object> base = jdbcTemplate.query(
                """
                select id, title, description, start_time, end_time,
                       allow_late_submission, late_penalty, is_public,
                       anti_cheating_enabled, allow_ai_tutor, create_time, update_time,
                       compose_strategy, target_kc_ids::text as target_kc_ids_json
                from classroom_assignment
                where classroom_id = ? and id = ?
                """,
                (rs, rowNum) -> mapAssignmentRow(rs, classroomId),
                classroomId, assignmentId
        ).stream().findFirst().orElse(null);
        if (base == null) return null;

        List<Map<String, Object>> sections = jdbcTemplate.query(
                """
                select id, title, description, sort_order
                from classroom_assignment_section
                where assignment_id = ?
                order by sort_order asc, id asc
                """,
                (rs, rowNum) -> {
                    String sectionId = rs.getString("id");
                    List<Map<String, Object>> problems = jdbcTemplate.query(
                            """
                            select ap.id, ap.score, ap.sort_order,
                                   cp.id as classroom_problem_id, cp.problem_id,
                                   p._id as problem_display_id, p.title, p.difficulty,
                                   p.submission_number, p.accepted_number
                            from classroom_assignment_problem ap
                            join classroom_problem cp on cp.id = ap.classroom_problem_id
                            join problem p on p.id = cp.problem_id
                            where ap.section_id = ?
                            order by ap.sort_order asc, ap.id asc
                            """,
                            (prs, prow) -> {
                                Map<String, Object> problem = new LinkedHashMap<>();
                                problem.put("id", prs.getString("id"));
                                problem.put("score", prs.getDouble("score"));
                                problem.put("order", prs.getInt("sort_order"));
                                Map<String, Object> p = new LinkedHashMap<>();
                                p.put("id", prs.getString("classroom_problem_id"));
                                p.put("classroom", classroomId);
                                p.put("problem_id", prs.getLong("problem_id"));
                                p.put("problem_display_id", prs.getString("problem_display_id"));
                                p.put("_id", prs.getString("problem_display_id"));
                                p.put("title", prs.getString("title"));
                                p.put("difficulty", prs.getString("difficulty"));
                                p.put("submission_number", prs.getLong("submission_number"));
                                p.put("accepted_number", prs.getLong("accepted_number"));
                                p.put("ac_rate", acRate(prs.getLong("submission_number"), prs.getLong("accepted_number")));
                                problem.put("problem", p);
                                return problem;
                            },
                            sectionId
                    );
                    Map<String, Object> section = new LinkedHashMap<>();
                    section.put("id", sectionId);
                    section.put("title", rs.getString("title"));
                    section.put("description", rs.getString("description"));
                    section.put("order", rs.getInt("sort_order"));
                    section.put("problems", problems);
                    return section;
                },
                assignmentId
        );
        base.put("sections", sections);
        return base;
    }

    private Map<String, Object> mapAssignmentRow(java.sql.ResultSet rs, String classroomId) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("title", rs.getString("title"));
        row.put("description", rs.getString("description"));
        row.put("start_time", formatTime(rs.getTimestamp("start_time")));
        row.put("end_time", formatTime(rs.getTimestamp("end_time")));
        row.put("allow_late_submission", rs.getBoolean("allow_late_submission"));
        row.put("late_penalty", rs.getDouble("late_penalty"));
        row.put("is_public", rs.getBoolean("is_public"));
        row.put("anti_cheating_enabled", rs.getBoolean("anti_cheating_enabled"));
        row.put("allow_ai_tutor", rs.getBoolean("allow_ai_tutor"));
        row.put("create_time", formatTime(rs.getTimestamp("create_time")));
        row.put("update_time", formatTime(rs.getTimestamp("update_time")));
        try {
            row.put("compose_strategy", rs.getString("compose_strategy"));
        } catch (java.sql.SQLException ignored) {
            // 老 select 不带该列时跳过
        }
        try {
            String targetKcJson = rs.getString("target_kc_ids_json");
            if (targetKcJson != null) {
                row.put("target_kc_ids", parseJsonList(objectMapper, targetKcJson));
            }
        } catch (java.sql.SQLException ignored) {
        }
        Map<String, Object> creator = new LinkedHashMap<>();
        creator.put("id", 0);
        creator.put("username", "");
        creator.put("avatar", null);
        row.put("creator", creator);
        row.put("sections", List.of());
        row.put("status", assignmentStatus(rs.getTimestamp("start_time"), rs.getTimestamp("end_time")));
        return row;
    }

    private String assignmentStatus(Timestamp start, Timestamp end) {
        Instant now = Instant.now();
        if (start != null && now.isBefore(start.toInstant())) return "scheduled";
        if (end != null && now.isAfter(end.toInstant())) return "ended";
        return "ongoing";
    }
}
