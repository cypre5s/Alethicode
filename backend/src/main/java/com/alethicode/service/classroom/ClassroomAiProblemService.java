package com.alethicode.service.classroom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.language.AiTutorProblemLanguageNormalizer;
import com.alethicode.service.aitutor.language.TutorLanguageSupport;
import com.alethicode.service.classroom.ai.ClassroomKcResolver;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class ClassroomAiProblemService {

    private static final Logger log = LoggerFactory.getLogger(ClassroomAiProblemService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private final Path lessonRoot;
    private static final List<String> SUPPORTED_AI_LESSON_EXTENSIONS = List.of(".pdf", ".pptx");
    private static final List<String> AI_TUTOR_CODING_LANGUAGES = List.of("Python3", "C", "C++", "Java");
    private static final List<String> SUPPORTED_PREFER_STRATEGIES = List.of("lp_first", "llm_first", "lp_only", "llm_only");
    private static final String SOURCE_STRATEGY_LP_PICK = "lp_kc_pick";
    private static final String SOURCE_STRATEGY_LESSON_LLM = "lesson_llm";
    private static final String SOURCE_STRATEGY_HYBRID = "hybrid";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiModelGateway aiModelGateway;
    private final AlethicodeProperties properties;
    private final AiTutorProblemLanguageNormalizer aiTutorProblemLanguageNormalizer;
    private final com.alethicode.service.languagepack.impl.LanguagePackProblemJudgeCheckService judgeCheckService;
    private final ClassroomKcResolver classroomKcResolver;

    public ClassroomAiProblemService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                     AiModelGateway aiModelGateway, AlethicodeProperties properties,
                                     com.alethicode.service.languagepack.impl.LanguagePackProblemJudgeCheckService judgeCheckService,
                                     ClassroomKcResolver classroomKcResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.aiModelGateway = aiModelGateway;
        this.properties = properties;
        this.aiTutorProblemLanguageNormalizer = new AiTutorProblemLanguageNormalizer(objectMapper);
        this.lessonRoot = Paths.get(properties.getSystem().getClassroomLessonDir());
        this.judgeCheckService = judgeCheckService;
        this.classroomKcResolver = classroomKcResolver;
    }

    public ApiResponse<Object> aiGeneratedProblemList(String classroomId, Map<String, String> params, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isStaff(classroomId, user.userId())) {
            return ApiResponse.success(Map.of("results", List.of(), "total", 0));
        }

        int page = Math.max(parseInt(params.get("page"), 1), 1);
        int limit = Math.min(Math.max(parseInt(params.get("limit"), 20), 1), 100);
        int offset = (page - 1) * limit;
        String lessonId = trimToNull(params.get("lesson_id"));
        String questionType = trimToNull(params.get("question_type"));
        String ordering = trimToNull(params.get("ordering"));
        String orderBy = switch (ordering == null ? "" : ordering) {
            case "create_time" -> "a.create_time asc";
            case "-create_time" -> "a.create_time desc";
            case "question_type" -> "a.question_type asc, a.create_time desc";
            case "-question_type" -> "a.question_type desc, a.create_time desc";
            case "lesson" -> "a.lesson_id asc nulls last, a.create_time desc";
            case "-lesson" -> "a.lesson_id desc nulls last, a.create_time desc";
            default -> "a.create_time desc";
        };

        List<Object> whereArgs = new ArrayList<>();
        String where = " where a.classroom_id = ?";
        whereArgs.add(classroomId);
        if (lessonId != null) {
            where += " and a.lesson_id = ?";
            whereArgs.add(lessonId);
        }
        if (questionType != null) {
            where += " and a.question_type = ?";
            whereArgs.add(questionType);
        }

        Long total = jdbcTemplate.queryForObject(
                "select count(*) from ai_generated_problem a" + where,
                Long.class,
                whereArgs.toArray()
        );

        List<Object> queryArgs = new ArrayList<>(whereArgs);
        queryArgs.add(limit);
        queryArgs.add(offset);
        List<Map<String, Object>> results = jdbcTemplate.query(
                """
                select a.id, a.classroom_id, a.lesson_id, l.title as lesson_title,
                       a.source_type, a.source_pages::text as source_pages_json, a.question_type,
                       a.extracted_concepts::text as extracted_concepts_json, a.difficulty_estimation,
                       a.generated_problem_json::text as generated_problem_json,
                       a.test_data_generator_code, a.reference_solution_code,
                       a.validation_status, a.validation_log, a.test_cases_count,
                       a.is_published, a.published_problem_id, cp.problem_id as published_problem_obj_id,
                       a.target_kc_ids::text as target_kc_ids_json, a.source_strategy,
                       a.created_by_id, u.username as created_by_username, a.create_time, a.update_time
                from ai_generated_problem a
                left join classroom_lesson l on l.id = a.lesson_id
                left join classroom_problem cp on cp.id = a.published_problem_id
                left join "user" u on u.id = a.created_by_id
                """ + where + " order by " + orderBy + " limit ? offset ?",
                (rs, rowNum) -> mapAiGeneratedProblemRow(rs),
                queryArgs.toArray()
        );
        return ApiResponse.success(Map.of("results", results, "total", total == null ? 0 : total));
    }

    public ApiResponse<Object> aiGeneratedProblemCreate(String classroomId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!classroomExists(classroomId)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "班级不存在");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可生成题目");
        }
        String lessonId = trimToNull(stringValue(request.get("lesson_id")));
        if (lessonId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "lesson_id is required");
        }
        Map<String, Object> lesson = lessonRow(classroomId, lessonId);
        if (lesson == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "课件不存在");
        }

        List<Object> questionTypesObj = castList(request.get("question_types"));
        List<String> questionTypes = new ArrayList<>();
        if (questionTypesObj.isEmpty()) {
            questionTypes.add("coding");
        } else {
            for (Object qt : questionTypesObj) {
                String val = trimToNull(stringValue(qt));
                if (val != null) {
                    if (!List.of("coding", "choice", "fill_blank").contains(val)) {
                        return ApiResponse.error("不支持的题目类型: " + val, null);
                    }
                    questionTypes.add(val);
                }
            }
            if (questionTypes.isEmpty()) {
                questionTypes.add("coding");
            }
        }
        int pageStart = Math.max(1, parseIntObj(request.get("page_start"), 1));
        int pageEnd = Math.max(pageStart, parseIntObj(request.get("page_end"), pageStart));
        CoursewareGenerationContext generationContext;
        try {
            generationContext = prepareCoursewareGenerationContext(classroomId, lesson, pageStart, pageEnd);
        } catch (IllegalStateException exception) {
            return ApiResponse.error(trimToEmpty(exception.getMessage()), null);
        }

        if (request.containsKey("target_kc_names")) {
            log.warn("classroom_ai_create deprecated_field=target_kc_names classroom_id={} use target_kc_ids instead", classroomId);
        }

        Long languagePackId;
        try {
            languagePackId = classroomKcResolver.resolveLanguagePackId(classroomId);
        } catch (com.alethicode.exception.BusinessException ignored) {
            log.warn("classroom_ai_create no language_pack binding classroom_id={} fallback to lesson_llm", classroomId);
            languagePackId = null;
        }
        List<Long> targetKcIds = languagePackId == null
                ? List.of()
                : classroomKcResolver.expandKcIds(classroomId, castList(request.get("target_kc_ids")));
        Map<Long, String> kcNameMap = classroomKcResolver.loadKcNameMap(languagePackId, targetKcIds);
        List<String> targetKcNames = new ArrayList<>(kcNameMap.values());

        String preferStrategy = trimToEmpty(stringValue(request.get("prefer_strategy"))).toLowerCase(Locale.ROOT);
        if (preferStrategy.isBlank()) {
            preferStrategy = "lp_first";
        }
        if (!SUPPORTED_PREFER_STRATEGIES.contains(preferStrategy)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error",
                    "prefer_strategy 必须为 " + String.join("/", SUPPORTED_PREFER_STRATEGIES));
        }
        if (languagePackId == null || targetKcIds.isEmpty()) {
            preferStrategy = "llm_only";
        }

        String targetDifficulty = trimToNull(stringValue(request.get("target_difficulty")));
        if (targetDifficulty != null && !List.of("Low", "Mid", "High").contains(targetDifficulty)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "target_difficulty 必须为 Low/Mid/High");
        }

        Map<String, Object> counts = castMap(request.get("counts"));
        int totalRequested = 0;
        for (String questionType : questionTypes) {
            totalRequested += Math.max(1, parseIntObj(counts.get(questionType), 1));
        }

        String taskId = randomId();
        jdbcTemplate.update(
                """
                insert into ai_generation_task(id, classroom_id, lesson_id, created_by_id, status, question_types, counts,
                                               total_requested, generated_count, error_count, error_message, create_time, update_time)
                values (?, ?, ?, ?, 'running', cast(? as jsonb), cast(? as jsonb), ?, 0, 0, '', now(), now())
                """,
                taskId,
                classroomId,
                lessonId,
                user.userId(),
                toJson(questionTypes),
                toJson(counts),
                totalRequested
        );

        List<String> insertedProblemIds = new ArrayList<>();
        try {
            List<PreparedAiGeneratedProblem> preparedProblems = new ArrayList<>();
            int generatedCount = 0;
            int lpPickedTotal = 0;
            int llmPickedTotal = 0;
            int sequenceForLlm = 0;

            for (String questionType : questionTypes) {
                int wanted = Math.max(1, parseIntObj(counts.get(questionType), 1));
                int picked = 0;

                boolean canLp = languagePackId != null && !targetKcIds.isEmpty()
                        && ("lp_first".equals(preferStrategy) || "lp_only".equals(preferStrategy));
                if (canLp) {
                    Set<Long> alreadyUsed = preparedProblemIdsByType(preparedProblems, questionType);
                    List<Map<String, Object>> lpPicks = pickFromLanguagePackPool(
                            classroomId, languagePackId, targetKcIds, questionType, wanted, alreadyUsed, targetDifficulty
                    );
                    for (Map<String, Object> lpRow : lpPicks) {
                        Map<String, Object> reverseJson = reverseSerializeProblemRow(lpRow, questionType);
                        List<String> extractedConcepts = new ArrayList<>(targetKcNames);
                        preparedProblems.add(new PreparedAiGeneratedProblem(
                                randomId(),
                                questionType,
                                extractedConcepts,
                                trimToEmpty(stringValue(reverseJson.get("difficulty"))),
                                reverseJson,
                                "coding".equals(questionType) ? trimToEmpty(stringValue(reverseJson.get("reference_solution_code"))) : "",
                                buildLpPickValidationLog(lpRow, questionType, kcNameMap),
                                List.copyOf(targetKcIds),
                                SOURCE_STRATEGY_LP_PICK,
                                "passed"
                        ));
                        picked++;
                    }
                    lpPickedTotal += picked;
                }

                int remaining = wanted - picked;
                if (remaining > 0 && "lp_only".equals(preferStrategy)) {
                    log.warn("classroom_ai_create lp_only fell short classroom_id={} qt={} wanted={} picked={}",
                            classroomId, questionType, wanted, picked);
                    continue;
                }
                for (int i = 0; i < remaining; i++) {
                    sequenceForLlm++;
                    generatedCount = preparedProblems.size() + 1;
                    Map<String, Object> generatedJson = generateProblemFromCourseware(
                            questionType, generationContext, sequenceForLlm, targetKcNames, targetDifficulty
                    );
                    List<String> llmConcepts = extractConceptStrings(generatedJson.get("extracted_concepts"));
                    if (llmConcepts.isEmpty() && !targetKcNames.isEmpty()) {
                        llmConcepts = new ArrayList<>(targetKcNames);
                    }
                    String referenceSolutionCode = "coding".equals(questionType)
                            ? trimToEmpty(stringValue(generatedJson.get("reference_solution_code")))
                            : "";
                    preparedProblems.add(new PreparedAiGeneratedProblem(
                            randomId(),
                            questionType,
                            llmConcepts,
                            trimToEmpty(stringValue(generatedJson.get("difficulty"))),
                            generatedJson,
                            referenceSolutionCode,
                            buildGenerationValidationLog(generationContext, questionType, generatedCount),
                            List.copyOf(targetKcIds),
                            SOURCE_STRATEGY_LESSON_LLM,
                            "pending"
                    ));
                    llmPickedTotal++;
                }
            }

            for (PreparedAiGeneratedProblem prepared : preparedProblems) {
                jdbcTemplate.update(
                        """
                        insert into ai_generated_problem(id, classroom_id, lesson_id, source_type, source_pages, question_type,
                                                         extracted_concepts, difficulty_estimation, generated_problem_json,
                                                         test_data_generator_code, reference_solution_code,
                                                         validation_status, validation_log, test_cases_count,
                                                         is_published, published_problem_id, created_by_id,
                                                         target_kc_ids, source_strategy,
                                                         create_time, update_time)
                        values (?, ?, ?, 'lesson', cast(? as jsonb), ?, cast(? as jsonb), ?, cast(? as jsonb),
                                ?, ?, ?, ?, 0, false, null, ?, cast(? as jsonb), ?,
                                now(), now())
                        """,
                        prepared.id(),
                        classroomId,
                        lessonId,
                        toJson(List.of(pageStart, pageEnd)),
                        prepared.questionType(),
                        toJson(prepared.extractedConcepts()),
                        prepared.difficulty(),
                        toJson(prepared.generatedProblemJson()),
                        "",
                        prepared.referenceSolutionCode(),
                        prepared.validationStatus(),
                        prepared.validationLog(),
                        user.userId(),
                        toJson(prepared.targetKcIds()),
                        prepared.sourceStrategy()
                );
                insertedProblemIds.add(prepared.id());
            }

            String overallStrategy = lpPickedTotal > 0 && llmPickedTotal > 0 ? SOURCE_STRATEGY_HYBRID
                    : (lpPickedTotal > 0 ? SOURCE_STRATEGY_LP_PICK : SOURCE_STRATEGY_LESSON_LLM);

            jdbcTemplate.update(
                    """
                    update ai_generation_task
                    set status = 'completed', generated_count = ?, error_count = 0, error_message = '', update_time = now()
                    where id = ?
                    """,
                    preparedProblems.size(),
                    taskId
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("task_id", taskId);
            data.put("status", "queued");
            data.put("message", "生成任务已提交，请等待完成");
            data.put("total_requested", totalRequested);
            data.put("lp_picked", lpPickedTotal);
            data.put("llm_generated", llmPickedTotal);
            data.put("source_strategy", overallStrategy);
            data.put("language_pack_id", languagePackId);
            data.put("target_kc_ids", targetKcIds);
            return ApiResponse.success(data);
        } catch (Exception exception) {
            String errorMessage = trimToEmpty(exception.getMessage()).isBlank() ? "AI 生成失败" : trimToEmpty(exception.getMessage());
            if (!insertedProblemIds.isEmpty()) {
                String placeholders = String.join(", ", Collections.nCopies(insertedProblemIds.size(), "?"));
                List<Object> deleteArgs = new ArrayList<>();
                deleteArgs.add(classroomId);
                deleteArgs.addAll(insertedProblemIds);
                jdbcTemplate.update(
                        "delete from ai_generated_problem where classroom_id = ? and id in (" + placeholders + ")",
                        deleteArgs.toArray()
                );
            }
            jdbcTemplate.update(
                    """
                    update ai_generation_task
                    set status = 'failed', generated_count = 0, error_count = ?, error_message = ?, update_time = now()
                    where id = ?
                    """,
                    totalRequested,
                    errorMessage,
                    taskId
            );
            return ApiResponse.error(errorMessage, Map.of("task_id", taskId, "status", "failed"));
        }
    }

    public ApiResponse<Object> aiGenerationKcOptions(String classroomId, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可使用 KC 出题");
        }
        Long languagePackId = classroomKcResolver.resolveLanguagePackId(classroomId);
        List<Map<String, Object>> chapters = classroomKcResolver.listKcOptionsTree(classroomId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("classroom_id", classroomId);
        data.put("language_pack_id", languagePackId);
        data.put("chapters", chapters);
        return ApiResponse.success(data);
    }

    public ApiResponse<Object> aiGeneratedProblemRetrieve(String classroomId, String aiProblemId, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可查看");
        }
        Map<String, Object> row = findAiGeneratedProblem(classroomId, aiProblemId);
        if (row == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目不存在");
        }
        return ApiResponse.success(row);
    }

    public ApiResponse<Object> aiGeneratedProblemUpdate(String classroomId, String aiProblemId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可编辑题目");
        }
        Map<String, Object> generatedProblemPatch = castMap(request.get("generated_problem_json"));
        String generatedProblemPatchJson = request.get("generated_problem_json") == null ? null : toJson(generatedProblemPatch);
        String referenceSolutionCodePatch = trimToNull(stringValue(generatedProblemPatch.get("reference_solution_code")));

        String targetKcIdsJson = null;
        if (request.containsKey("target_kc_ids")) {
            List<Long> kcIds = classroomKcResolver.expandKcIds(classroomId, castList(request.get("target_kc_ids")));
            targetKcIdsJson = toJson(kcIds);
        }

        int updated = jdbcTemplate.update(
                """
                update ai_generated_problem
                set generated_problem_json = case
                        when ? is null then generated_problem_json
                        else coalesce(generated_problem_json, '{}'::jsonb) || cast(? as jsonb)
                    end,
                    extracted_concepts = coalesce(cast(? as jsonb), extracted_concepts),
                    reference_solution_code = coalesce(?, reference_solution_code),
                    difficulty_estimation = coalesce(?, difficulty_estimation),
                    target_kc_ids = case
                        when ? is null then target_kc_ids
                        else cast(? as jsonb)
                    end,
                    update_time = now()
                where classroom_id = ? and id = ?
                """,
                generatedProblemPatchJson,
                generatedProblemPatchJson,
                request.get("extracted_concepts") == null ? null : toJson(castList(request.get("extracted_concepts"))),
                referenceSolutionCodePatch,
                trimToNull(stringValue(request.get("difficulty_estimation"))),
                targetKcIdsJson,
                targetKcIdsJson,
                classroomId,
                aiProblemId
        );
        if (updated == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目不存在");
        }
        return aiGeneratedProblemRetrieve(classroomId, aiProblemId, authentication);
    }

    public ApiResponse<Object> aiGeneratedProblemDelete(String classroomId, String aiProblemId, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可删除题目");
        }
        int deleted = jdbcTemplate.update("delete from ai_generated_problem where classroom_id = ? and id = ?", classroomId, aiProblemId);
        if (deleted == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目不存在");
        }
        return ApiResponse.success("success");
    }

    public ApiResponse<Object> aiGeneratedProblemTaskStatus(String classroomId, String taskId, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可查看");
        }
        Map<String, Object> row = jdbcTemplate.query(
                """
                select id, status, total_requested, generated_count, error_count, error_message
                from ai_generation_task
                where id = ? and classroom_id = ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("task_id", rs.getString("id"));
                    item.put("status", rs.getString("status"));
                    item.put("total_requested", rs.getInt("total_requested"));
                    item.put("generated_count", rs.getInt("generated_count"));
                    item.put("error_count", rs.getInt("error_count"));
                    item.put("error_message", rs.getString("error_message"));
                    return item;
                },
                taskId,
                classroomId
        ).stream().findFirst().orElse(null);
        if (row == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "任务不存在");
        }
        return ApiResponse.success(row);
    }

    public ApiResponse<Object> aiGeneratedProblemPublish(String classroomId, String aiProblemId, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可发布题目");
        }
        Map<String, Object> aiProblem = findAiGeneratedProblem(classroomId, aiProblemId);
        if (aiProblem == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目不存在");
        }
        if (parseBoolean(aiProblem.get("is_published"), false)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "该题目已发布");
        }
        if (!"passed".equals(trimToEmpty(stringValue(aiProblem.get("validation_status"))))) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目未通过审核/验证，暂不可发布");
        }

        @SuppressWarnings("unchecked")
        List<Long> targetKcIds = (List<Long>) aiProblem.getOrDefault("target_kc_ids", List.of());
        String sourceStrategy = trimToEmpty(stringValue(aiProblem.get("source_strategy")));
        if (targetKcIds == null) {
            targetKcIds = List.of();
        }
        Long languagePackId = jdbcTemplate.query(
                "select language_pack_id from classroom_language_pack where classroom_id = ? limit 1",
                rs -> rs.next() ? rs.getLong("language_pack_id") : null,
                classroomId
        );
        if (languagePackId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "班级未绑定语言包，无法发布题目");
        }
        if (targetKcIds.isEmpty()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "请先标注该题目的 KC 后再发布");
        }

        Long newProblemId;
        Map<String, Object> generated = castMap(aiProblem.get("generated_problem_json_obj"));
        String questionType = trimToEmpty(stringValue(aiProblem.get("question_type")));
        String displayId;

        if (SOURCE_STRATEGY_LP_PICK.equals(sourceStrategy)) {
            Object sourceProblemIdRaw = generated.get("source_problem_id");
            if (!(sourceProblemIdRaw instanceof Number n)) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "lp_kc_pick 题缺少 source_problem_id");
            }
            newProblemId = n.longValue();
            displayId = trimToEmpty(stringValue(generated.get("source_problem_key")));
            if (displayId.isBlank()) {
                displayId = jdbcTemplate.queryForObject("select _id from problem where id = ?", String.class, newProblemId);
            }
        } else {
            displayId = "AI-" + aiProblemId.substring(0, Math.min(8, aiProblemId.length())).toUpperCase(Locale.ROOT);
            String difficulty = switch (trimToEmpty(stringValue(generated.get("difficulty")))) {
                case "Easy" -> "Low";
                case "Hard" -> "High";
                case "Medium" -> "Mid";
                default -> "Mid";
            };
            List<Object> samples = castList(generated.get("samples"));
            List<Map<String, Object>> testCases = extractIoPairs(generated.get("test_cases"), true);
            List<Map<String, Object>> testCaseScore = new ArrayList<>();
            if ("coding".equals(questionType)) {
                if (testCases.isEmpty()) {
                    throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "编程题缺少测试用例，无法发布");
                }
                for (int i = 0; i < testCases.size(); i++) {
                    Map<String, Object> score = new LinkedHashMap<>();
                    score.put("input_name", (i + 1) + ".in");
                    score.put("output_name", (i + 1) + ".out");
                    score.put("score", 0);
                    testCaseScore.add(score);
                }
            }
            String testCaseId = "ai_" + aiProblemId.substring(0, Math.min(12, aiProblemId.length())).toLowerCase(Locale.ROOT);
            if ("coding".equals(questionType)) {
                writeGeneratedCodingTestCases(testCaseId, testCases);
            }
            String referenceSolutionCode = trimToEmpty(stringValue(aiProblem.get("reference_solution_code")));
            String referenceSolutionLanguage = "";
            Map<String, String> codingTemplates = Map.of();
            if ("coding".equals(questionType)) {
                if (referenceSolutionCode.isBlank()) {
                    throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "编程题缺少参考解代码，无法发布");
                }
                referenceSolutionLanguage = resolveGeneratedReferenceSolutionLanguage(generated, referenceSolutionCode);
                codingTemplates = aiTutorProblemLanguageNormalizer.normalize(
                        "class_private",
                        "{\"question_type\":\"coding\"}",
                        "[]",
                        "{}"
                ).fullTemplates();
            }
            Map<String, Object> statisticInfo = new LinkedHashMap<>();
            if ("choice".equals(questionType) || "fill_blank".equals(questionType)) {
                Map<String, Object> objective = new LinkedHashMap<>();
                objective.put("question_type", questionType);
                objective.put("title", trimToEmpty(stringValue(generated.get("title"))));
                objective.put("description", trimToEmpty(stringValue(generated.get("description"))));
                objective.put("difficulty", trimToEmpty(stringValue(generated.get("difficulty"))));
                objective.put("explanation", trimToEmpty(stringValue(generated.get("explanation"))));
                objective.put("tags", castList(aiProblem.get("extracted_concepts_obj")));
                objective.put("options", castList(generated.get("options")));
                objective.put("answer", generated.get("answer"));
                objective.put("blanks", castList(generated.get("blanks")));
                statisticInfo.put("objective_question", objective);
            }
            newProblemId = jdbcTemplate.queryForObject(
                    """
                    insert into problem(
                        _id, title, description, input_description, output_description,
                        samples, test_case_id, test_case_score, hint,
                        languages, template, created_by_id, time_limit, memory_limit,
                        reference_solution_language, reference_solution_code,
                        visible, is_public, difficulty, source, statistic_info,
                        is_ai_generated, ai_source_classroom_id, visibility_status
                    ) values (
                        ?, ?, ?, ?, ?,
                        cast(? as jsonb), ?, cast(? as jsonb), ?,
                        cast(? as jsonb), cast(? as jsonb), ?, ?, ?,
                        ?, ?, true, false, ?, ?, cast(? as jsonb),
                        true, cast(? as bigint), 'class_private'
                    ) returning id
                    """,
                    Long.class,
                    displayId,
                    trimToEmpty(stringValue(generated.get("title"))),
                    trimToEmpty(stringValue(generated.get("description"))),
                    trimToEmpty(stringValue(generated.get("input_description"))),
                    trimToEmpty(stringValue(generated.get("output_description"))),
                    toJson(samples),
                    testCaseId,
                    toJson(testCaseScore),
                    trimToEmpty(stringValue(generated.get("hint"))),
                    toJson(AI_TUTOR_CODING_LANGUAGES),
                    toJson(codingTemplates),
                    user.userId(),
                    Math.max(1, parseIntObj(generated.get("time_limit"), 1000)),
                    Math.max(1, parseIntObj(generated.get("memory_limit"), 256)),
                    referenceSolutionLanguage,
                    referenceSolutionCode,
                    difficulty,
                    "AI-Generated from lesson",
                    toJson(statisticInfo),
                    null
            );
            if (newProblemId == null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "发布失败");
            }
            Integer mappedCount = jdbcTemplate.update(
                    """
                    insert into language_pack_problem_mapping(language_pack_id, problem_id, generation_log_id, create_time)
                    select distinct clp.language_pack_id, ?, null, now()
                    from classroom_language_pack clp
                    where clp.classroom_id = ?
                    on conflict do nothing
                    """,
                    newProblemId,
                    classroomId
            );
            if (mappedCount == null || mappedCount <= 0) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "班级未绑定语言包，无法发布题目");
            }
        }

        // 反向写 ai_problem_kc_mapping（lp_pick 已存在则 ON CONFLICT DO NOTHING）
        double weight = 1.0 / Math.max(1, targetKcIds.size());
        for (Long kcId : targetKcIds) {
            jdbcTemplate.update(
                    """
                    insert into ai_problem_kc_mapping(problem_id, kc_id, weight, language_pack_id)
                    values (?, ?, ?, ?)
                    on conflict (problem_id, kc_id) do update
                        set weight = greatest(ai_problem_kc_mapping.weight, excluded.weight),
                            language_pack_id = coalesce(ai_problem_kc_mapping.language_pack_id, excluded.language_pack_id)
                    """,
                    newProblemId, kcId, weight, languagePackId
            );
        }

        Integer maxOrder = jdbcTemplate.queryForObject(
                "select coalesce(max(display_order), -1) + 1 from classroom_problem where classroom_id = ?",
                Integer.class,
                classroomId
        );
        String existingClassroomProblemId = jdbcTemplate.query(
                "select id from classroom_problem where classroom_id = ? and problem_id = ? limit 1",
                rs -> rs.next() ? rs.getString("id") : null,
                classroomId, newProblemId
        );
        String classroomProblemId;
        if (existingClassroomProblemId == null) {
            classroomProblemId = randomId();
            jdbcTemplate.update(
                    """
                    insert into classroom_problem(id, classroom_id, problem_id, display_order, is_visible, is_private,
                                                  category, submission_count, ac_count, added_time, update_time)
                    values (?, ?, ?, ?, true, true, 'ai_generated', 0, 0, now(), now())
                    """,
                    classroomProblemId,
                    classroomId,
                    newProblemId,
                    maxOrder == null ? 0 : maxOrder
            );
            jdbcTemplate.update("update classroom set problem_count = problem_count + 1, update_time = now() where id = ?", classroomId);
        } else {
            classroomProblemId = existingClassroomProblemId;
            jdbcTemplate.update(
                    "update classroom_problem set is_visible = true, update_time = now() where id = ?",
                    classroomProblemId
            );
        }
        jdbcTemplate.update(
                "update ai_generated_problem set is_published = true, published_problem_id = ?, update_time = now() where id = ? and classroom_id = ?",
                classroomProblemId,
                aiProblemId,
                classroomId
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "发布成功");
        data.put("problem_id", newProblemId);
        data.put("display_id", displayId);
        data.put("source_strategy", sourceStrategy);
        data.put("kc_mapped", targetKcIds.size());
        return ApiResponse.success(data);
    }

    public ApiResponse<Object> aiGeneratedProblemPromote(String classroomId, String aiProblemId, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isOwner(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师可推送到主题库");
        }
        Map<String, Object> aiProblem = findAiGeneratedProblem(classroomId, aiProblemId);
        if (aiProblem == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目不存在");
        }
        if (!parseBoolean(aiProblem.get("is_published"), false) || trimToNull(stringValue(aiProblem.get("published_problem"))) == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "请先发布到班级");
        }
        Long problemId = parseLongObj(aiProblem.get("problem_id"));
        if (problemId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "关联题目不存在");
        }
        int updated = jdbcTemplate.update(
                "update problem set visibility_status = 'global_public', is_public = true, last_update_time = now() where id = ?",
                problemId
        );
        if (updated == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "关联题目不存在");
        }
        return ApiResponse.success(Map.of("message", "已推送到全局主题库", "problem_id", problemId));
    }

    public ApiResponse<Object> aiGeneratedProblemValidate(String classroomId, String aiProblemId, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可触发验证");
        }
        Map<String, Object> aiProblem = findAiGeneratedProblem(classroomId, aiProblemId);
        if (aiProblem == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目不存在");
        }
        String status = trimToEmpty(stringValue(aiProblem.get("validation_status")));
        if ("validating".equals(status)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "该题目正在验证中，请稍后");
        }
        String questionType = trimToEmpty(stringValue(aiProblem.get("question_type")));
        if (!"coding".equals(questionType)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "选择题/填空题不走沙箱验证，请使用人工审查");
        }
        Map<String, Object> problemJson = castMap(aiProblem.get("generated_problem_json_obj"));
        List<Map<String, Object>> testCases = castList(problemJson.get("test_cases")).stream()
                .map(o -> o instanceof Map<?, ?> m ? castMap(m) : Map.<String, Object>of())
                .toList();
        if (testCases.isEmpty()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "编程题缺少测试用例，无法验证");
        }
        String referenceSolution = trimToEmpty(stringValue(problemJson.get("reference_solution")));
        if (referenceSolution.isBlank()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "缺少标准答案代码，无法验证");
        }
        String language = trimToEmpty(stringValue(problemJson.get("language")));
        if (language.isBlank()) {
            language = "Python3";
        }

        jdbcTemplate.update(
                "update ai_generated_problem set validation_status = 'validating', update_time = now() where classroom_id = ? and id = ?",
                classroomId, aiProblemId
        );

        List<String> inputs = testCases.stream()
                .map(tc -> trimToEmpty(stringValue(tc.get("input"))))
                .toList();

        com.alethicode.service.languagepack.impl.JudgeCheckResult judgeResult;
        try {
            judgeResult = judgeCheckService.executeReferenceSolution(
                    referenceSolution, language, inputs, 5000, 256);
        } catch (Exception e) {
            Map<String, Object> failLog = new LinkedHashMap<>();
            failLog.put("status", "failed");
            failLog.put("type", "judge_sandbox");
            failLog.put("validated_by", user.userId());
            failLog.put("error", e.getMessage());
            jdbcTemplate.update(
                    "update ai_generated_problem set validation_status = 'failed', validation_log = ?, update_time = now() where classroom_id = ? and id = ?",
                    toJson(failLog), classroomId, aiProblemId
            );
            return ApiResponse.error("error", "验证失败：" + e.getMessage());
        }

        String validationStatus = judgeResult.allPassed() ? "passed" : "failed";
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("status", validationStatus);
        logEntry.put("type", "judge_sandbox");
        logEntry.put("validated_by", user.userId());
        logEntry.put("test_cases_count", testCases.size());
        logEntry.put("all_passed", judgeResult.allPassed());
        if (judgeResult.compileError() != null && !judgeResult.compileError().isBlank()) {
            logEntry.put("compile_error", judgeResult.compileError());
        }
        if (!judgeResult.failedIndices().isEmpty()) {
            logEntry.put("failed_indices", judgeResult.failedIndices());
        }

        jdbcTemplate.update(
                """
                update ai_generated_problem
                set validation_status = ?,
                    validation_log = ?,
                    test_cases_count = ?,
                    update_time = now()
                where classroom_id = ? and id = ?
                """,
                validationStatus,
                toJson(logEntry),
                testCases.size(),
                classroomId,
                aiProblemId
        );
        Map<String, Object> updatedRow = findAiGeneratedProblem(classroomId, aiProblemId);
        return ApiResponse.success(Map.of(
                "result", Map.of("status", validationStatus, "test_cases_count", testCases.size(), "all_passed", judgeResult.allPassed()),
                "problem", updatedRow
        ));
    }

    public ApiResponse<Object> aiGeneratedProblemReviewPass(String classroomId, String aiProblemId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可审查题目");
        }
        Map<String, Object> aiProblem = findAiGeneratedProblem(classroomId, aiProblemId);
        if (aiProblem == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目不存在");
        }
        String questionType = trimToEmpty(stringValue(aiProblem.get("question_type")));
        if ("coding".equals(questionType)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "编程题请使用沙箱验证");
        }
        Map<String, Object> reviewLog = new LinkedHashMap<>();
        reviewLog.put("status", "passed");
        reviewLog.put("type", "manual_review");
        reviewLog.put("reviewer_id", user.userId());
        reviewLog.put("notes", trimToNull(stringValue(request.get("notes"))) == null ? "人工审查通过" : trimToNull(stringValue(request.get("notes"))));
        jdbcTemplate.update(
                """
                update ai_generated_problem
                set validation_status = 'passed', test_cases_count = 0, validation_log = ?, update_time = now()
                where classroom_id = ? and id = ?
                """,
                toJson(reviewLog),
                classroomId,
                aiProblemId
        );
        return aiGeneratedProblemRetrieve(classroomId, aiProblemId, authentication);
    }

    public ApiResponse<Object> aiGeneratedProblemReviewReject(String classroomId, String aiProblemId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可审查题目");
        }
        Map<String, Object> aiProblem = findAiGeneratedProblem(classroomId, aiProblemId);
        if (aiProblem == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目不存在");
        }
        String questionType = trimToEmpty(stringValue(aiProblem.get("question_type")));
        if ("coding".equals(questionType)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "编程题请使用沙箱验证");
        }
        boolean revoked = false;
        String publishedProblemId = trimToNull(stringValue(aiProblem.get("published_problem")));
        if (parseBoolean(aiProblem.get("is_published"), false) && publishedProblemId != null) {
            jdbcTemplate.update("update classroom_problem set is_visible = false, update_time = now() where id = ?", publishedProblemId);
            jdbcTemplate.update(
                    "update ai_generated_problem set is_published = false, published_problem_id = null, update_time = now() where classroom_id = ? and id = ?",
                    classroomId,
                    aiProblemId
            );
            revoked = true;
        }
        Map<String, Object> rejectLog = new LinkedHashMap<>();
        rejectLog.put("status", "failed");
        rejectLog.put("type", "manual_review");
        rejectLog.put("reviewer_id", user.userId());
        rejectLog.put("notes", trimToNull(stringValue(request.get("notes"))) == null ? "人工审查驳回" : trimToNull(stringValue(request.get("notes"))));
        rejectLog.put("publish_revoked", revoked);
        jdbcTemplate.update(
                """
                update ai_generated_problem
                set validation_status = 'failed', validation_log = ?, update_time = now()
                where classroom_id = ? and id = ?
                """,
                toJson(rejectLog),
                classroomId,
                aiProblemId
        );
        return aiGeneratedProblemRetrieve(classroomId, aiProblemId, authentication);
    }

    public ApiResponse<Object> aiGeneratedProblemExportReviewedJson(String classroomId, Authentication authentication) {
        UserAuth user = resolveUser(authentication);
        if (!user.authenticated()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (!isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可导出");
        }
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select question_type, generated_problem_json::text as generated_problem_json,
                       extracted_concepts::text as extracted_concepts_json
                from ai_generated_problem
                where classroom_id = ? and validation_status = 'passed' and question_type in ('choice', 'fill_blank')
                order by create_time desc
                """,
                (rs, rowNum) -> {
                    Map<String, Object> generated = parseJsonMap(rs.getString("generated_problem_json"));
                    List<Object> tags = parseJsonList(rs.getString("extracted_concepts_json"));
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("question_type", rs.getString("question_type"));
                    item.put("title", trimToEmpty(stringValue(generated.get("title"))));
                    item.put("description", trimToEmpty(stringValue(generated.get("description"))));
                    item.put("difficulty", trimToEmpty(stringValue(generated.get("difficulty"))));
                    item.put("explanation", trimToEmpty(stringValue(generated.get("explanation"))));
                    item.put("tags", tags);
                    item.put("options", castList(generated.get("options")));
                    item.put("answer", generated.get("answer"));
                    item.put("blanks", castList(generated.get("blanks")));
                    return item;
                },
                classroomId
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", "1.0");
        payload.put("source", "ai_generated_reviewed_export");
        payload.put("classroom_id", classroomId);
        payload.put("exported_count", rows.size());
        payload.put("questions", rows);
        return ApiResponse.success(payload);
    }

    private Map<String, Object> findAiGeneratedProblem(String classroomId, String aiProblemId) {
        return jdbcTemplate.query(
                """
                select a.id, a.classroom_id, a.lesson_id, l.title as lesson_title,
                       a.source_type, a.source_pages::text as source_pages_json, a.question_type,
                       a.extracted_concepts::text as extracted_concepts_json, a.difficulty_estimation,
                       a.generated_problem_json::text as generated_problem_json,
                       a.test_data_generator_code, a.reference_solution_code,
                       a.validation_status, a.validation_log, a.test_cases_count,
                       a.is_published, a.published_problem_id, cp.problem_id as published_problem_obj_id,
                       a.target_kc_ids::text as target_kc_ids_json, a.source_strategy,
                       a.created_by_id, u.username as created_by_username, a.create_time, a.update_time
                from ai_generated_problem a
                left join classroom_lesson l on l.id = a.lesson_id
                left join classroom_problem cp on cp.id = a.published_problem_id
                left join "user" u on u.id = a.created_by_id
                where a.classroom_id = ? and a.id = ?
                """,
                (rs, rowNum) -> mapAiGeneratedProblemRow(rs),
                classroomId,
                aiProblemId
        ).stream().findFirst().orElse(null);
    }

    private Map<String, Object> mapAiGeneratedProblemRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> generated = parseJsonMap(rs.getString("generated_problem_json"));
        List<Object> sourcePages = parseJsonList(rs.getString("source_pages_json"));
        List<Object> tags = parseJsonList(rs.getString("extracted_concepts_json"));
        String questionType = trimToEmpty(rs.getString("question_type"));
        String validationStatus = trimToEmpty(rs.getString("validation_status"));

        Map<String, Object> createdBy = new LinkedHashMap<>();
        createdBy.put("id", rs.getLong("created_by_id"));
        createdBy.put("username", rs.getString("created_by_username"));
        createdBy.put("avatar", null);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("classroom", rs.getString("classroom_id"));
        row.put("lesson", rs.getString("lesson_id"));
        row.put("lesson_title", trimToEmpty(rs.getString("lesson_title")));
        row.put("source_type", rs.getString("source_type"));
        row.put("source_type_display", rs.getString("source_type"));
        row.put("source_pages", sourcePages);
        row.put("page_start", sourcePages.isEmpty() ? 1 : parseIntObj(sourcePages.get(0), 1));
        row.put("page_end", sourcePages.size() >= 2 ? parseIntObj(sourcePages.get(1), parseIntObj(sourcePages.get(0), 1)) : (sourcePages.isEmpty() ? 1 : parseIntObj(sourcePages.get(0), 1)));
        row.put("question_type", questionType);
        row.put("extracted_concepts", tags);
        row.put("difficulty_estimation", rs.getString("difficulty_estimation"));
        row.put("generated_problem_json", generated);
        row.put("test_data_generator_code", rs.getString("test_data_generator_code"));
        row.put("reference_solution_code", rs.getString("reference_solution_code"));
        row.put("validation_status", validationStatus);
        row.put("validation_status_display", validationStatus);
        row.put("validation_log", rs.getString("validation_log"));
        row.put("test_cases_count", rs.getInt("test_cases_count"));
        row.put("test_case_count", rs.getInt("test_cases_count"));
        row.put("is_published", rs.getBoolean("is_published"));
        row.put("published_problem", rs.getString("published_problem_id"));
        row.put("problem_id", rs.getObject("published_problem_obj_id"));
        row.put("created_by", createdBy);
        row.put("create_time", formatTime(rs.getTimestamp("create_time")));
        row.put("update_time", formatTime(rs.getTimestamp("update_time")));
        row.put("can_publish", "passed".equals(validationStatus) && !rs.getBoolean("is_published"));
        List<Object> targetKcIdsRaw = parseJsonList(rs.getString("target_kc_ids_json"));
        List<Long> targetKcIds = new ArrayList<>();
        for (Object one : targetKcIdsRaw) {
            if (one instanceof Number n) {
                targetKcIds.add(n.longValue());
            } else if (one != null) {
                try { targetKcIds.add(Long.parseLong(String.valueOf(one))); } catch (NumberFormatException ignored) {}
            }
        }
        row.put("target_kc_ids", targetKcIds);
        row.put("source_strategy", trimToEmpty(rs.getString("source_strategy")));
        row.put("title", trimToEmpty(stringValue(generated.get("title"))).isBlank() ? "AI-" + questionType : trimToEmpty(stringValue(generated.get("title"))));
        row.put("difficulty", trimToEmpty(stringValue(generated.get("difficulty"))).isBlank()
                ? trimToEmpty(rs.getString("difficulty_estimation"))
                : trimToEmpty(stringValue(generated.get("difficulty"))));
        row.put("description", trimToEmpty(stringValue(generated.get("description"))));
        row.put("status", validationStatus);
        row.put("samples", castList(generated.get("samples")));
        row.put("input_description", trimToEmpty(stringValue(generated.get("input_description"))));
        row.put("output_description", trimToEmpty(stringValue(generated.get("output_description"))));
        row.put("tags", tags);
        row.put("test_cases", castList(generated.get("test_cases")));
        row.put("options", castList(generated.get("options")));
        row.put("blanks", castList(generated.get("blanks")));
        row.put("answer", generated.get("answer"));
        row.put("explanation", trimToEmpty(stringValue(generated.get("explanation"))));
        row.put("generated_problem_json_obj", generated);
        row.put("extracted_concepts_obj", tags);
        return row;
    }

    private Map<String, Object> lessonRow(String classroomId, String lessonId) {
        return jdbcTemplate.query(
                "select id, title, lesson_type, file_path from classroom_lesson where classroom_id = ? and id = ?",
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getString("id"));
                    item.put("title", rs.getString("title"));
                    item.put("lesson_type", rs.getString("lesson_type"));
                    item.put("file_path", rs.getString("file_path"));
                    return item;
                },
                classroomId,
                lessonId
        ).stream().findFirst().orElse(null);
    }

    private CoursewareGenerationContext prepareCoursewareGenerationContext(String classroomId, Map<String, Object> lesson, int pageStart, int pageEnd) {
        String lessonId = trimToEmpty(stringValue(lesson.get("id")));
        String lessonTitle = trimToEmpty(stringValue(lesson.get("title")));
        String lessonType = trimToEmpty(stringValue(lesson.get("lesson_type")));
        String relativePath = trimToNull(stringValue(lesson.get("file_path")));
        Path lessonPath = resolveStoredLessonPath(relativePath);
        if (lessonPath == null || !Files.isRegularFile(lessonPath)) {
            throw new IllegalStateException("课件文件不存在");
        }
        String fileExtension = extension(lessonPath.getFileName().toString()).toLowerCase(Locale.ROOT);
        if ("doc".equals(lessonType) || !SUPPORTED_AI_LESSON_EXTENSIONS.contains(fileExtension)) {
            throw new IllegalStateException("不支持该课件类型进行基于页码的生成");
        }

        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(lessonPath);
        } catch (IOException exception) {
            throw new IllegalStateException("课件文件读取失败");
        }
        String fileHash = sha256Hex(fileBytes);
        List<CoursewarePage> indexedPages = ensureCoursewarePagesIndexed(classroomId, lessonId, lessonTitle, lessonType, lessonPath, fileExtension, fileHash);
        int totalPages = indexedPages.size();
        if (totalPages <= 0) {
            throw new IllegalStateException("课件内容提取失败");
        }
        if (pageStart < 1 || pageEnd < pageStart || pageEnd > totalPages) {
            throw new IllegalStateException("所选页码超出课件范围");
        }

        List<CoursewarePage> selectedPages = indexedPages.stream()
                .filter(page -> page.pageNo() >= pageStart && page.pageNo() <= pageEnd)
                .sorted(Comparator.comparingInt(CoursewarePage::pageNo))
                .toList();
        if (selectedPages.size() != (pageEnd - pageStart + 1)) {
            throw new IllegalStateException("所选页码超出课件范围");
        }
        String selectedContent = selectedPages.stream()
                .map(CoursewarePage::content)
                .map(this::trimToNull)
                .filter(value -> value != null)
                .collect(Collectors.joining("\n\n"));
        if (selectedContent.isBlank()) {
            throw new IllegalStateException("所选页码范围未提取到可用课件内容");
        }
        return new CoursewareGenerationContext(
                classroomId, lessonId, lessonTitle, lessonType, fileExtension, fileHash,
                pageStart, pageEnd, totalPages, selectedPages, selectedContent
        );
    }

    private List<CoursewarePage> ensureCoursewarePagesIndexed(String classroomId, String lessonId, String lessonTitle,
                                                              String lessonType, Path lessonPath, String fileExtension,
                                                              String fileHash) {
        List<CoursewarePage> existingPages = loadIndexedCoursewarePages(classroomId, lessonId);
        if (!existingPages.isEmpty() && fileHash.equals(existingPages.get(0).fileHash())) {
            return existingPages;
        }

        ExtractedCourseware extractedCourseware = extractCoursewarePages(lessonPath);
        if (extractedCourseware.pages().isEmpty()) {
            throw new IllegalStateException("课件内容提取失败");
        }

        jdbcTemplate.update(
                "delete from ai_courseware_chunk where metadata->>'classroom_id' = ? and metadata->>'lesson_id' = ?",
                classroomId,
                lessonId
        );
        for (CoursewarePage page : extractedCourseware.pages()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("classroom_id", classroomId);
            metadata.put("lesson_id", lessonId);
            metadata.put("page_no", page.pageNo());
            metadata.put("lesson_type", lessonType);
            metadata.put("file_hash", fileHash);
            metadata.put("file_extension", fileExtension);
            metadata.put("extraction_method", extractedCourseware.extractionMethod());
            jdbcTemplate.update(
                    """
                    insert into ai_courseware_chunk(classroom_id, lesson_id, title, content, metadata)
                    values (?, ?, ?, ?, cast(? as jsonb))
                    """,
                    null,
                    null,
                    lessonTitle + " 第" + page.pageNo() + "页",
                    trimToEmpty(page.content()),
                    toJson(metadata)
            );
        }
        jdbcTemplate.update(
                "update classroom_lesson set total_pages = ?, update_time = now() where id = ? and classroom_id = ?",
                extractedCourseware.pages().size(),
                lessonId,
                classroomId
        );
        return loadIndexedCoursewarePages(classroomId, lessonId);
    }

    private List<CoursewarePage> loadIndexedCoursewarePages(String classroomId, String lessonId) {
        return jdbcTemplate.query(
                """
                select content, metadata::text as metadata_json
                from ai_courseware_chunk
                where metadata->>'classroom_id' = ? and metadata->>'lesson_id' = ?
                order by (metadata->>'page_no')::int asc
                """,
                (rs, rowNum) -> {
                    Map<String, Object> metadata = parseJsonMap(rs.getString("metadata_json"));
                    return new CoursewarePage(
                            parseIntObj(metadata.get("page_no"), rowNum + 1),
                            trimToEmpty(rs.getString("content")),
                            trimToEmpty(stringValue(metadata.get("file_hash"))),
                            trimToEmpty(stringValue(metadata.get("extraction_method"))),
                            trimToEmpty(stringValue(metadata.get("lesson_type")))
                    );
                },
                classroomId,
                lessonId
        );
    }

    private ExtractedCourseware extractCoursewarePages(Path lessonPath) {
        Path extractorScript = resolveCoursewareExtractorScript();
        if (extractorScript == null) {
            throw new IllegalStateException("课件提取脚本不存在");
        }

        Process process;
        try {
            process = new ProcessBuilder("python3", extractorScript.toString(), lessonPath.toString())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException exception) {
            throw new IllegalStateException("课件提取环境不可用");
        }

        String output;
        try {
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("unsupported lesson file type".equals(trimToEmpty(output))
                        ? "不支持该课件类型进行基于页码的生成"
                        : "课件内容提取失败");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("课件内容提取中断");
        } catch (IOException exception) {
            throw new IllegalStateException("课件内容提取失败");
        }

        Map<String, Object> payload = parseJsonMap(output);
        String extractionMethod = trimToEmpty(stringValue(payload.get("extraction_method")));
        List<CoursewarePage> pages = new ArrayList<>();
        for (Object one : castList(payload.get("pages"))) {
            Map<String, Object> page = castMap(one);
            pages.add(new CoursewarePage(
                    Math.max(1, parseIntObj(page.get("page_no"), pages.size() + 1)),
                    trimToEmpty(stringValue(page.get("content"))),
                    "",
                    extractionMethod,
                    ""
            ));
        }
        return new ExtractedCourseware(extractionMethod, pages);
    }

    private Path resolveCoursewareExtractorScript() {
        List<Path> candidates = List.of(
                Paths.get("scripts", "extract_courseware_pages.py"),
                Paths.get("backend", "scripts", "extract_courseware_pages.py")
        );
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private Path resolveStoredLessonPath(String relativePath) {
        String normalizedPath = trimToNull(relativePath);
        if (normalizedPath == null) {
            return null;
        }
        Path path = lessonRoot.resolve(normalizedPath).normalize();
        if (!path.startsWith(lessonRoot)) {
            return null;
        }
        return path;
    }

    private Map<String, Object> generateProblemFromCourseware(String questionType, CoursewareGenerationContext context, int index,
                                                               List<String> targetKcNames, String targetDifficulty) {
        Map<String, Object> promptPayload = new LinkedHashMap<>();
        promptPayload.put("task", "generate_classroom_problem_from_courseware_pages");
        promptPayload.put("question_type", questionType);
        promptPayload.put("audience", "非计算机专业编程初学者");
        promptPayload.put("supported_coding_languages", AI_TUTOR_CODING_LANGUAGES);
        promptPayload.put("sequence", index);
        promptPayload.put("lesson", Map.of(
                "classroom_id", context.classroomId(),
                "lesson_id", context.lessonId(),
                "lesson_title", context.lessonTitle(),
                "lesson_type", context.lessonType(),
                "page_start", context.pageStart(),
                "page_end", context.pageEnd(),
                "total_pages", context.totalPages()
        ));
        promptPayload.put("courseware_pages", context.selectedPages().stream().map(page -> Map.of(
                "page_no", page.pageNo(),
                "content", trimToEmpty(page.content())
        )).toList());
        promptPayload.put("courseware_text", context.selectedContent());
        if (!targetKcNames.isEmpty()) {
            promptPayload.put("target_knowledge_concepts", targetKcNames);
        }
        if (targetDifficulty != null) {
            promptPayload.put("target_difficulty", targetDifficulty);
        }
        promptPayload.put("requirements", buildPromptRequirements(questionType));

        StringBuilder systemPromptBuilder = new StringBuilder("""
                你是一名面向非计算机专业编程初学者的教学出题老师。
                你必须严格基于给定课件页内容生成题目，不能使用课件页之外的事实，不能编造未在课件页出现的知识点。
                返回值必须是单个 JSON 对象，不能包含 Markdown、解释文字或代码块围栏。
                题目表述必须清晰自然，不能出现"这是自动生成的题目"等模板话术。
                """);
        if (!targetKcNames.isEmpty()) {
            systemPromptBuilder.append("题目必须围绕以下知识点出题：").append(String.join("、", targetKcNames)).append("。\n");
        }
        if (targetDifficulty != null) {
            String diffDesc = switch (targetDifficulty) {
                case "Low" -> "简单（适合刚接触该知识点的学生）";
                case "High" -> "较难（需要综合运用多个概念）";
                default -> "中等";
            };
            systemPromptBuilder.append("题目难度要求：").append(diffDesc).append("。\n");
        }

        Map<String, Object> raw = aiModelGateway.callForJson(systemPromptBuilder.toString(), toJson(promptPayload));
        return normalizeGeneratedProblemPayload(questionType, raw);
    }

    private List<String> buildPromptRequirements(String questionType) {
        if ("coding".equals(questionType)) {
            return List.of(
                    "题面必须是可判题的初级编程题，只能考查给定课件页中出现的知识。",
                    "必须返回 title, description, input_description, output_description, samples, test_cases, reference_solution_language, reference_solution_code, difficulty, explanation, extracted_concepts。",
                    "samples 至少 1 组，test_cases 至少 1 组，所有 input/output 都必须是字符串。",
                    "reference_solution_language 必须是 Python3/C/C++/Java 之一，reference_solution_code 必须是该语言的完整可运行参考解。"
            );
        }
        if ("choice".equals(questionType)) {
            return List.of(
                    "必须返回 title, description, options, answer, difficulty, explanation, extracted_concepts。",
                    "options 至少 4 个，格式为 [{\"label\":\"A\",\"text\":\"...\"}]。",
                    "答案必须与选项标签对应。"
            );
        }
        return List.of(
                "必须返回 title, description, blanks, answer, difficulty, explanation, extracted_concepts。",
                "blanks 为按顺序给出的填空答案数组，至少 1 个元素。",
                "题目必须能从给定课件页直接作答。"
        );
    }

    private Map<String, Object> normalizeGeneratedProblemPayload(String questionType, Map<String, Object> generated) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("title", requireGeneratedText(generated, "title"));
        normalized.put("description", requireGeneratedText(generated, "description"));
        normalized.put("difficulty", normalizeGeneratedDifficulty(stringValue(generated.get("difficulty"))));
        normalized.put("explanation", requireGeneratedText(generated, "explanation"));
        normalized.put("extracted_concepts", extractConceptStrings(generated.get("extracted_concepts")));

        if ("coding".equals(questionType)) {
            List<Map<String, Object>> samples = extractIoPairs(generated.get("samples"), true);
            List<Map<String, Object>> testCases = extractIoPairs(generated.get("test_cases"), true);
            String referenceSolutionCode = requireGeneratedText(generated, "reference_solution_code");
            String referenceSolutionLanguage = resolveGeneratedReferenceSolutionLanguage(generated, referenceSolutionCode);
            normalized.put("input_description", requireGeneratedText(generated, "input_description"));
            normalized.put("output_description", requireGeneratedText(generated, "output_description"));
            normalized.put("samples", samples);
            normalized.put("test_cases", testCases);
            normalized.put("reference_solution_language", referenceSolutionLanguage);
            normalized.put("reference_solution_code", referenceSolutionCode);
            normalized.put("hint", trimToEmpty(stringValue(generated.get("hint"))));
            normalized.put("time_limit", Math.max(1, parseIntObj(generated.get("time_limit"), 1000)));
            normalized.put("memory_limit", Math.max(1, parseIntObj(generated.get("memory_limit"), 256)));
            return normalized;
        }

        if ("choice".equals(questionType)) {
            List<Map<String, Object>> options = normalizeChoiceOptions(generated.get("options"));
            if (options.size() < 2) {
                throw new IllegalStateException("AI 生成的选择题缺少有效选项");
            }
            normalized.put("options", options);
            normalized.put("answer", requireGeneratedText(generated, "answer"));
            return normalized;
        }

        List<String> blanks = extractConceptStrings(generated.get("blanks"));
        if (blanks.isEmpty()) {
            throw new IllegalStateException("AI 生成的填空题缺少答案");
        }
        normalized.put("blanks", blanks);
        normalized.put("answer", trimToNull(stringValue(generated.get("answer"))) == null ? blanks.get(0) : trimToEmpty(stringValue(generated.get("answer"))));
        return normalized;
    }

    private String resolveGeneratedReferenceSolutionLanguage(Map<String, Object> generated, String referenceSolutionCode) {
        String normalized = normalizeAiCodingLanguage(generated.get("reference_solution_language"));
        if (!normalized.isBlank()) {
            return normalized;
        }
        String detected = detectCodingLanguageFromCode(referenceSolutionCode);
        if (!detected.isBlank()) {
            return detected;
        }
        throw new IllegalStateException("AI 生成的编程题缺少可识别的 reference_solution_language");
    }

    private String normalizeAiCodingLanguage(Object rawLanguage) {
        String normalized = TutorLanguageSupport.normalizeLanguage(rawLanguage);
        if (AI_TUTOR_CODING_LANGUAGES.contains(normalized)) {
            return normalized;
        }
        return "";
    }

    private String detectCodingLanguageFromCode(String code) {
        String normalizedCode = trimToEmpty(code);
        if (normalizedCode.isBlank()) {
            return "";
        }
        String lower = normalizedCode.toLowerCase(Locale.ROOT);
        if (lower.contains("public class ") || lower.contains("system.out.") || lower.contains("scanner ") || lower.contains("bufferedreader")) {
            return "Java";
        }
        if (lower.contains("#include")) {
            if (lower.contains("using namespace std") || lower.contains("std::") || lower.contains("cout <<") || lower.contains("cin >>") || lower.contains("vector<")) {
                return "C++";
            }
            return "C";
        }
        if (lower.contains("def ") || lower.contains("print(") || lower.contains("input(") || lower.contains("elif ")) {
            return "Python3";
        }
        if (lower.contains("cout <<") || lower.contains("cin >>")) {
            return "C++";
        }
        if (lower.contains("printf(") || lower.contains("scanf(") || lower.contains("int main(")) {
            return "C";
        }
        return "";
    }

    private List<Map<String, Object>> normalizeChoiceOptions(Object rawOptions) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        List<Object> options = castList(rawOptions);
        for (int index = 0; index < options.size(); index++) {
            Map<String, Object> option = castMap(options.get(index));
            String text = trimToNull(stringValue(option.get("text")));
            if (text == null) {
                continue;
            }
            String label = trimToNull(stringValue(option.get("label")));
            if (label == null) {
                label = String.valueOf((char) ('A' + index));
            }
            normalized.add(Map.of("label", label.toUpperCase(Locale.ROOT), "text", text));
        }
        return normalized;
    }

    private List<Map<String, Object>> extractIoPairs(Object rawPairs, boolean required) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object one : castList(rawPairs)) {
            Map<String, Object> pair = castMap(one);
            String input = trimToNull(stringValue(pair.get("input")));
            if (input == null) {
                input = trimToNull(stringValue(pair.get("stdin")));
            }
            String output = trimToNull(stringValue(pair.get("output")));
            if (output == null) {
                output = trimToNull(stringValue(pair.get("expected_output")));
            }
            if (input == null || output == null) {
                continue;
            }
            Map<String, Object> normalizedPair = new LinkedHashMap<>();
            normalizedPair.put("input", input);
            normalizedPair.put("output", output);
            normalized.add(normalizedPair);
        }
        if (required && normalized.isEmpty()) {
            throw new IllegalStateException("AI 生成的编程题缺少样例或测试用例");
        }
        return normalized;
    }

    private List<String> extractConceptStrings(Object rawValues) {
        List<String> values = new ArrayList<>();
        for (Object one : castList(rawValues)) {
            String text = trimToNull(stringValue(one));
            if (text != null) {
                values.add(text);
            }
        }
        return values;
    }

    private String buildGenerationValidationLog(CoursewareGenerationContext context, String questionType, int index) {
        List<Map<String, Object>> pageSummary = new ArrayList<>();
        for (CoursewarePage page : context.selectedPages()) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("page_no", page.pageNo());
            summary.put("excerpt", summarizePageContent(page.content()));
            pageSummary.add(summary);
        }
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("type", "courseware_generation");
        logEntry.put("question_type", questionType);
        logEntry.put("sequence", index);
        logEntry.put("lesson_id", context.lessonId());
        logEntry.put("lesson_title", context.lessonTitle());
        logEntry.put("page_start", context.pageStart());
        logEntry.put("page_end", context.pageEnd());
        logEntry.put("file_hash", context.fileHash());
        logEntry.put("pages", pageSummary);
        return toJson(logEntry);
    }

    private String summarizePageContent(String content) {
        String normalized = trimToEmpty(content).replace("\n", " ").trim();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 80);
    }

    private String requireGeneratedText(Map<String, Object> generated, String field) {
        String value = trimToNull(stringValue(generated.get(field)));
        if (value == null) {
            throw new IllegalStateException("AI 生成结果缺少字段: " + field);
        }
        return value;
    }

    private String normalizeGeneratedDifficulty(String rawDifficulty) {
        String difficulty = trimToEmpty(rawDifficulty);
        return switch (difficulty) {
            case "Easy", "Low" -> "Easy";
            case "Hard", "High" -> "Hard";
            case "Medium", "Mid" -> "Medium";
            default -> "Medium";
        };
    }

    private void writeGeneratedCodingTestCases(String testCaseId, List<Map<String, Object>> testCases) {
        Path testCaseDir = Path.of(properties.getSystem().getTestCaseDir(), testCaseId);
        try {
            Files.createDirectories(testCaseDir);
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("spj", false);
            Map<String, Object> infoCases = new LinkedHashMap<>();
            info.put("test_cases", infoCases);

            for (int index = 0; index < testCases.size(); index++) {
                Map<String, Object> testCase = testCases.get(index);
                String inputContent = ensureTrailingNewline(normalizeLineEnding(trimToEmpty(stringValue(testCase.get("input")))));
                String outputContent = ensureTrailingNewline(normalizeLineEnding(trimToEmpty(stringValue(testCase.get("output")))));
                String inputName = (index + 1) + ".in";
                String outputName = (index + 1) + ".out";
                Files.writeString(testCaseDir.resolve(inputName), inputContent, StandardCharsets.UTF_8);
                Files.writeString(testCaseDir.resolve(outputName), outputContent, StandardCharsets.UTF_8);

                Map<String, Object> infoCase = new LinkedHashMap<>();
                infoCase.put("stripped_output_md5", md5Hex(rstripWhitespace(outputContent.getBytes(StandardCharsets.UTF_8))));
                infoCase.put("input_size", inputContent.getBytes(StandardCharsets.UTF_8).length);
                infoCase.put("output_size", outputContent.getBytes(StandardCharsets.UTF_8).length);
                infoCase.put("input_name", inputName);
                infoCase.put("output_name", outputName);
                infoCases.put(String.valueOf(index + 1), infoCase);
            }

            Files.writeString(
                    testCaseDir.resolve("info"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(info),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            deleteTestCaseDirQuietly(testCaseDir);
            throw new IllegalStateException("测试用例写入失败");
        }
    }

    private void deleteTestCaseDirQuietly(Path testCaseDir) {
        try {
            if (!Files.isDirectory(testCaseDir)) {
                return;
            }
            try (var walk = Files.walk(testCaseDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        } catch (IOException ignored) {
        }
    }

    private String normalizeLineEnding(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String ensureTrailingNewline(String content) {
        if (content.isEmpty() || content.endsWith("\n")) {
            return content;
        }
        return content + "\n";
    }

    private byte[] rstripWhitespace(byte[] content) {
        int end = content.length;
        while (end > 0) {
            byte one = content[end - 1];
            if (one != '\n' && one != '\r' && one != ' ' && one != '\t') {
                break;
            }
            end--;
        }
        byte[] trimmed = new byte[end];
        System.arraycopy(content, 0, trimmed, 0, end);
        return trimmed;
    }

    private String md5Hex(byte[] content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] digest = messageDigest.digest(content);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte one : digest) {
                builder.append(String.format(Locale.ROOT, "%02x", one));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 unavailable", exception);
        }
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(content);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte one : digest) {
                builder.append(String.format(Locale.ROOT, "%02x", one));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        if (idx < 0) {
            return "";
        }
        return filename.substring(idx);
    }

    private List<Map<String, Object>> pickFromLanguagePackPool(String classroomId, Long languagePackId, List<Long> kcIds,
                                                                String questionType, int limit, Set<Long> alreadyUsed,
                                                                String targetDifficulty) {
        if (languagePackId == null || kcIds == null || kcIds.isEmpty() || limit <= 0) {
            return List.of();
        }
        double targetScore = switch (trimToEmpty(targetDifficulty)) {
            case "Low" -> 0.3;
            case "High" -> 0.75;
            case "Mid" -> 0.5;
            default -> 0.5;
        };
        String questionTypeFilter = switch (trimToEmpty(questionType)) {
            case "choice", "fill_blank" ->
                    "coalesce(p.statistic_info #>> '{objective_question,question_type}', '') = ?";
            default ->
                    "coalesce(p.statistic_info #>> '{objective_question,question_type}', 'coding') not in ('choice','fill_blank')";
        };
        String kcPlaceholders = String.join(", ", Collections.nCopies(kcIds.size(), "?"));
        String excludeClause = alreadyUsed == null || alreadyUsed.isEmpty()
                ? ""
                : " and p.id not in (" + String.join(", ", Collections.nCopies(alreadyUsed.size(), "?")) + ")";
        String sql = "select distinct on (p.id) p.id, p._id, p.title, p.description, p.input_description, p.output_description, "
                + "p.samples::text as samples_json, p.hint, p.difficulty, p.difficulty_score, "
                + "p.test_case_id, p.languages::text as languages_json, "
                + "p.reference_solution_language, p.reference_solution_code, "
                + "p.statistic_info::text as statistic_info_json, "
                + "p.time_limit, p.memory_limit "
                + "from ai_problem_kc_mapping akm "
                + "join problem p on p.id = akm.problem_id "
                + "where akm.language_pack_id = ? "
                + "and akm.kc_id in (" + kcPlaceholders + ") "
                + "and p.visible = true "
                + "and " + questionTypeFilter + " "
                + "and p.id not in (select cp.problem_id from classroom_problem cp where cp.classroom_id = ?) "
                + excludeClause
                + " order by p.id, abs(coalesce(p.difficulty_score, 0.5) - ?) asc, p.create_time desc "
                + "limit ?";
        List<Object> args = new ArrayList<>();
        args.add(languagePackId);
        args.addAll(kcIds);
        if ("choice".equals(questionType) || "fill_blank".equals(questionType)) {
            args.add(questionType);
        }
        args.add(classroomId);
        if (alreadyUsed != null && !alreadyUsed.isEmpty()) {
            args.addAll(alreadyUsed);
        }
        args.add(targetScore);
        args.add(limit);
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("problem_id", rs.getLong("id"));
            row.put("problem_key", rs.getString("_id"));
            row.put("title", rs.getString("title"));
            row.put("description", rs.getString("description"));
            row.put("input_description", rs.getString("input_description"));
            row.put("output_description", rs.getString("output_description"));
            row.put("samples", parseJsonList(rs.getString("samples_json")));
            row.put("hint", rs.getString("hint"));
            row.put("difficulty", rs.getString("difficulty"));
            Object difficultyScore = rs.getObject("difficulty_score");
            row.put("difficulty_score", difficultyScore);
            row.put("test_case_id", rs.getString("test_case_id"));
            row.put("languages", parseJsonList(rs.getString("languages_json")));
            row.put("reference_solution_language", rs.getString("reference_solution_language"));
            row.put("reference_solution_code", rs.getString("reference_solution_code"));
            row.put("statistic_info", parseJsonMap(rs.getString("statistic_info_json")));
            row.put("time_limit", rs.getInt("time_limit"));
            row.put("memory_limit", rs.getInt("memory_limit"));
            return row;
        }, args.toArray());
    }

    private Map<String, Object> reverseSerializeProblemRow(Map<String, Object> row, String questionType) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("title", trimToEmpty(stringValue(row.get("title"))));
        json.put("description", trimToEmpty(stringValue(row.get("description"))));
        String mappedDifficulty = switch (trimToEmpty(stringValue(row.get("difficulty")))) {
            case "Low", "Easy" -> "Easy";
            case "High", "Hard" -> "Hard";
            case "Mid", "Medium" -> "Medium";
            default -> "Medium";
        };
        json.put("difficulty", mappedDifficulty);
        json.put("explanation", trimToEmpty(stringValue(row.get("hint"))));
        json.put("extracted_concepts", List.of());
        json.put("source_problem_id", row.get("problem_id"));
        json.put("source_problem_key", row.get("problem_key"));

        if ("coding".equals(questionType)) {
            json.put("input_description", trimToEmpty(stringValue(row.get("input_description"))));
            json.put("output_description", trimToEmpty(stringValue(row.get("output_description"))));
            json.put("samples", castList(row.get("samples")));
            json.put("test_cases", List.of());
            json.put("reference_solution_language", trimToEmpty(stringValue(row.get("reference_solution_language"))));
            json.put("reference_solution_code", trimToEmpty(stringValue(row.get("reference_solution_code"))));
            json.put("hint", trimToEmpty(stringValue(row.get("hint"))));
            json.put("time_limit", row.get("time_limit"));
            json.put("memory_limit", row.get("memory_limit"));
            return json;
        }

        Map<String, Object> statisticInfo = castMap(row.get("statistic_info"));
        Map<String, Object> objective = castMap(statisticInfo.get("objective_question"));
        if ("choice".equals(questionType)) {
            json.put("options", castList(objective.get("options")));
            json.put("answer", objective.get("answer"));
            return json;
        }
        json.put("blanks", castList(objective.get("blanks")));
        json.put("answer", objective.get("answer"));
        return json;
    }

    private String buildLpPickValidationLog(Map<String, Object> row, String questionType, Map<Long, String> kcNameMap) {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("type", "lp_kc_pick");
        log.put("question_type", questionType);
        log.put("source_problem_id", row.get("problem_id"));
        log.put("source_problem_key", row.get("problem_key"));
        log.put("matched_kcs", new ArrayList<>(kcNameMap.values()));
        return toJson(log);
    }

    private Set<Long> preparedProblemIdsByType(List<PreparedAiGeneratedProblem> prepared, String questionType) {
        Set<Long> ids = new java.util.LinkedHashSet<>();
        for (PreparedAiGeneratedProblem one : prepared) {
            if (!questionType.equals(one.questionType())) continue;
            Object sourceId = one.generatedProblemJson().get("source_problem_id");
            if (sourceId instanceof Number n) {
                ids.add(n.longValue());
            }
        }
        return ids;
    }

    private boolean classroomExists(String classroomId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from classroom where id = ?", Integer.class, classroomId);
        return count != null && count > 0;
    }

    private boolean isStaff(String classroomId, Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from classroom_member where classroom_id = ? and user_id = ? and role in ('owner','ta')",
                Integer.class,
                classroomId,
                userId
        );
        return count != null && count > 0;
    }

    private boolean isOwner(String classroomId, Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from classroom_member where classroom_id = ? and user_id = ? and role = 'owner'",
                Integer.class,
                classroomId,
                userId
        );
        return count != null && count > 0;
    }

    private UserAuth resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return new UserAuth(false, null, false, false, null);
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id, username, admin_type from \"user\" where lower(username) = ?",
                    (rs, rowNum) -> {
                        String adminType = trimToEmpty(rs.getString("admin_type"));
                        boolean admin = "Admin".equals(adminType) || "Teacher".equals(adminType);
                        boolean adminManager = "Admin".equals(adminType);
                        return new UserAuth(true, rs.getLong("id"), admin, adminManager, rs.getString("username"));
                    },
                    authentication.getName().toLowerCase(Locale.ROOT)
            );
        } catch (EmptyResultDataAccessException ignored) {
            return new UserAuth(false, null, false, false, null);
        }
    }

    private String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String formatTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(timestamp.toInstant().atOffset(ZoneOffset.UTC));
    }

    private Map<String, Object> parseJsonMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("parseJsonMap failed, returning empty map", e);
            return Map.of();
        }
    }

    private List<Object> parseJsonList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("parseJsonList failed, returning empty list", e);
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("json serialize failed", exception);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value;
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(trimToEmpty(raw));
        } catch (Exception e) {
            log.debug("parseInt failed for raw={}, using fallback {}", raw, fallback, e);
            return fallback;
        }
    }

    private int parseIntObj(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            log.debug("parseIntObj failed for value={}, using fallback {}", value, fallback, e);
            return fallback;
        }
    }

    private Long parseLongObj(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            log.debug("parseLongObj failed for value={}", value, e);
            return null;
        }
    }

    private boolean parseBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String raw = String.valueOf(value);
        return "true".equalsIgnoreCase(raw) || "1".equals(raw) || "yes".equalsIgnoreCase(raw);
    }

    private List<Object> castList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> data = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                data.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return data;
        }
        return new LinkedHashMap<>();
    }

    private record PreparedAiGeneratedProblem(
            String id,
            String questionType,
            List<String> extractedConcepts,
            String difficulty,
            Map<String, Object> generatedProblemJson,
            String referenceSolutionCode,
            String validationLog,
            List<Long> targetKcIds,
            String sourceStrategy,
            String validationStatus
    ) {
    }

    private record CoursewarePage(
            int pageNo,
            String content,
            String fileHash,
            String extractionMethod,
            String lessonType
    ) {
    }

    private record ExtractedCourseware(String extractionMethod, List<CoursewarePage> pages) {
    }

    private record CoursewareGenerationContext(
            String classroomId,
            String lessonId,
            String lessonTitle,
            String lessonType,
            String fileExtension,
            String fileHash,
            int pageStart,
            int pageEnd,
            int totalPages,
            List<CoursewarePage> selectedPages,
            String selectedContent
    ) {
    }

    private record UserAuth(boolean authenticated, Long userId, boolean admin, boolean adminManager, String username) {
    }
}
