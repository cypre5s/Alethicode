package com.alethicode.service.submission.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.request.CreateSubmissionRequest;
import com.alethicode.dto.request.DebugSubmissionRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.ai.AiCircuitBreaker;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.submission.SubmissionDataCollector;
import com.alethicode.service.aitutor.assessment.CodeQualityAssessmentService;
import com.alethicode.service.aitutor.assessment.GenericCodeQualityAssessmentService;
import com.alethicode.service.aitutor.assessment.LanguageRoutedCodeQualityAssessmentService;
import com.alethicode.service.aitutor.assessment.PythonCodeQualityAssessmentService;
import com.alethicode.service.aitutor.language.AiTutorProblemLanguageNormalizer;
import com.alethicode.service.submission.SubmissionEventPublisher;
import com.alethicode.service.submission.SubmissionJudgeExecutor;
import com.alethicode.service.submission.SubmissionThrottleService;
import com.alethicode.util.HashUtils;
import static com.alethicode.util.ServiceParseUtils.trimToEmpty;
import static com.alethicode.util.ServiceParseUtils.trimToNull;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SubmissionServiceImpl implements SubmissionJudgeExecutor {

    private static final Logger log = LoggerFactory.getLogger(SubmissionServiceImpl.class);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final List<Integer> RECENT_WRONG_RESULTS = List.of(-2, -1, 1, 2, 3, 4, 5);
    private static final String REJUDGE_PREVIOUS_RESULT_KEY = "rejudge_previous_result";
    private static final Pattern PREPEND_PATTERN = Pattern.compile("//PREPEND BEGIN\\n([\\s\\S]+?)//PREPEND END");
    private static final Pattern APPEND_PATTERN = Pattern.compile("//APPEND BEGIN\\n([\\s\\S]+?)//APPEND END");
    private static final Duration PING_TIMEOUT = Duration.ofMillis(1500);
    private static final Duration JUDGE_TIMEOUT = Duration.ofSeconds(30);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final int JUDGE_POOL_SIZE = 4;
    private static final int CODE_QUALITY_POOL_SIZE = 2;
    // 由实例持有，绑定 Spring 生命周期（@PreDestroy 优雅关闭），见 BUG #21。
    private final ExecutorService JUDGE_EXECUTOR = Executors.newFixedThreadPool(JUDGE_POOL_SIZE, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setName("judge-dispatch-" + thread.threadId());
        return thread;
    });
    private final ExecutorService CODE_QUALITY_EXECUTOR = Executors.newFixedThreadPool(CODE_QUALITY_POOL_SIZE, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setName("code-quality-" + thread.threadId());
        return thread;
    });

    @jakarta.annotation.PreDestroy
    void shutdownExecutors() {
        shutdownQuietly(JUDGE_EXECUTOR, "judge-dispatch");
        shutdownQuietly(CODE_QUALITY_EXECUTOR, "code-quality");
    }

    private void shutdownQuietly(ExecutorService executor, String name) {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("Executor '{}' did not terminate in 5s, forcing shutdownNow", name);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;
    private final SubmissionThrottleService submissionThrottleService;
    private final TransactionTemplate transactionTemplate;
    private final AiTutorProblemLanguageNormalizer aiTutorProblemLanguageNormalizer;
    private final SubmissionDataCollector submissionDataCollector;
    private final com.alethicode.service.aitutor.profile.LearnerMasteryServiceUnified masteryService;
    private final com.alethicode.service.submission.JudgeCompletedEventPublisher judgeCompletedEventPublisher;
    private CodeQualityAssessmentService codeQualityAssessmentService;
    private com.alethicode.service.aitutor.review.ErrorReviewPackageService errorReviewPackageService;
    private SubmissionEventPublisher submissionEventPublisher;
    private AiCircuitBreaker aiCircuitBreaker;

    public SubmissionServiceImpl(JdbcTemplate jdbcTemplate,
                                 ObjectMapper objectMapper,
                                 AlethicodeProperties properties,
                                 SubmissionThrottleService submissionThrottleService,
                                 PlatformTransactionManager transactionManager,
                                 SubmissionDataCollector submissionDataCollector,
                                 com.alethicode.service.aitutor.profile.LearnerMasteryServiceUnified masteryService,
                                 com.alethicode.service.submission.JudgeCompletedEventPublisher judgeCompletedEventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.submissionThrottleService = submissionThrottleService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.aiTutorProblemLanguageNormalizer = new AiTutorProblemLanguageNormalizer(objectMapper);
        this.submissionDataCollector = submissionDataCollector;
        this.masteryService = masteryService;
        this.judgeCompletedEventPublisher = judgeCompletedEventPublisher;
    }

    @Autowired(required = false)
    void setSubmissionEventPublisher(SubmissionEventPublisher publisher) {
        this.submissionEventPublisher = publisher;
        if (publisher != null) {
            log.info("Event-driven judge dispatch enabled");
        }
    }

    @Autowired(required = false)
    void setErrorReviewPackageService(com.alethicode.service.aitutor.review.ErrorReviewPackageService service) {
        this.errorReviewPackageService = service;
    }

    @Autowired(required = false)
    void setAiModelGateway(AiModelGateway aiModelGateway) {
        if (aiModelGateway != null) {
            this.codeQualityAssessmentService = new LanguageRoutedCodeQualityAssessmentService(
                    new PythonCodeQualityAssessmentService(aiModelGateway),
                    new GenericCodeQualityAssessmentService(aiModelGateway)
            );
        }
    }

    @Autowired
    void setAiCircuitBreaker(AiCircuitBreaker aiCircuitBreaker) {
        this.aiCircuitBreaker = aiCircuitBreaker;
    }

    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Object> createSubmission(
            CreateSubmissionRequest request,
            Authentication authentication,
            String clientIp,
            boolean apiKeyAuth
    ) {
        AuthUser user = resolveAuthUser(authentication);
        ApiResponse<Object> loginError = requireLogin(user);
        if (loginError != null) {
            return loginError;
        }
        String throttleError = submissionThrottleService.checkSubmissionThrottle(
                user.id(),
                clientIp,
                apiKeyAuth,
                readMapOption("throttling")
        );
        if (throttleError != null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", throttleError);
        }

        Long problemId = request.problemId();
        if (problemId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目 ID 不能为空");
        }

        ProblemRow problem = findProblemForSubmission(problemId, user);
        if (problem == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目不存在");
        }

        AiTutorProblemLanguageNormalizer.NormalizedProblemLanguage normalizedProblemLanguage =
                normalizeProblemLanguage(
                        problem.visibilityStatus(),
                        problem.statisticInfoJson(),
                        problem.languagesJson(),
                        problem.templateJson()
                );
        List<String> languages = normalizedProblemLanguage.languages();
        if (languages.isEmpty()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "problem languages is empty");
        }

        String selectedLanguage = trimToNull(request.language());
        if (selectedLanguage == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "请选择编程语言");
        }
        if (!languages.contains(selectedLanguage)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", selectedLanguage + " is not allowed in the problem");
        }

        ObjectiveJudgeResult objective = judgeObjective(problem.statisticInfoJson(), request.objectiveAnswer(), request.objectiveBlanks());
        String submissionId = randomString(32);

        if (objective != null) {
            Map<String, Object> info = Map.of(
                    "objective", Map.of(
                            "question_type", objective.questionType(),
                            "passed", objective.passed()
                    )
            );
            Map<String, Object> statisticInfo = new LinkedHashMap<>();
            statisticInfo.put("time_cost", 0);
            statisticInfo.put("memory_cost", 0);
            statisticInfo.put("score", objective.passed() ? 100 : 0);
            statisticInfo.put("err_info", objective.passed() ? "" : "Objective answer mismatch");
            statisticInfo.put("objective", Map.of(
                    "question_type", objective.questionType(),
                    "passed", objective.passed(),
                    "given", objective.given(),
                    "filled_blanks", objective.filledBlanks(),
                    "total_blanks", objective.totalBlanks()
            ));

            insertSubmission(
                    submissionId,
                    problem.id(),
                    user.id(),
                    user.username(),
                    trimToEmpty(request.code()),
                    objective.passed() ? 0 : -1,
                    info,
                    selectedLanguage,
                    false,
                    statisticInfo,
                    clientIp
            );
            synchronizeFirstJudgeStats(problem.id(), user.id(), problem.displayId(), objective.passed() ? 0 : -1);
            return ApiResponse.success(Map.of("submission_id", submissionId));
        }

        String code = trimToNull(request.code());
        if (code == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "代码不能为空");
        }
        if (!hasAvailableJudgeServer()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "No available judge server. Please start oj-judge service or wait for heartbeat recovery.");
        }

        Map<String, Object> info = new LinkedHashMap<>();
        if (Boolean.TRUE.equals(request.preflightOverridden())) {
            Map<String, Object> preflight = new LinkedHashMap<>();
            preflight.put("detector", trimToEmpty(request.preflightDetector()));
            preflight.put("misconception_id", request.preflightMisconceptionId());
            preflight.put("overridden", true);
            preflight.put("question", trimToEmpty(request.preflightQuestion()));
            preflight.put("line_number", request.preflightLineNumber());
            preflight.put("code_snippet", trimToEmpty(request.preflightCodeSnippet()));
            info.put("preflight", preflight);
        }

        insertSubmission(
                submissionId,
                problem.id(),
                user.id(),
                user.username(),
                code,
                6,
                info,
                selectedLanguage,
                false,
                Map.of(),
                clientIp
        );

        dispatchJudgeAfterCommit(submissionId);

        return ApiResponse.success(Map.of("submission_id", submissionId));
    }

    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Object> debugSubmission(
            DebugSubmissionRequest request,
            Authentication authentication,
            String clientIp,
            boolean apiKeyAuth
    ) {
        AuthUser user = resolveAuthUser(authentication);
        ApiResponse<Object> loginError = requireLogin(user);
        if (loginError != null) {
            return loginError;
        }
        String throttleError = submissionThrottleService.checkDebugThrottle(
                user.id(),
                clientIp,
                apiKeyAuth,
                readMapOption("throttling")
        );
        if (throttleError != null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", throttleError);
        }

        String language = trimToNull(request.language());
        if (language == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "请选择编程语言");
        }

        String code = trimToEmpty(request.code());
        String userInput = trimToEmpty(request.input());

        int timeLimit = 5;
        int memoryLimit = 256;
        Long problemId = request.problemId();
        if (problemId != null) {
            DebugProblemRow problem = findDebugProblem(problemId, user);
            if (problem == null) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目不存在");
            }
            AiTutorProblemLanguageNormalizer.NormalizedProblemLanguage normalizedProblemLanguage =
                    normalizeProblemLanguage(
                            problem.visibilityStatus(),
                            problem.statisticInfoJson(),
                            problem.languagesJson(),
                            problem.templateJson()
                    );
            List<String> languages = normalizedProblemLanguage.languages();
            if (!languages.contains(language)) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", language + " is not allowed in the problem");
            }
            code = applyProblemTemplate(writeJson(normalizedProblemLanguage.fullTemplates()), language, code);
            timeLimit = problem.timeLimit();
            memoryLimit = problem.memoryLimit();
        }

        Map<String, Object> languageConfig = resolveLanguageConfig(language);
        if (languageConfig == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Language " + language + " is not supported");
        }

        JudgeServerCandidate server = pickAvailableJudgeServer();
        if (server == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "暂无可用的评测服务器");
        }

        Path debugDir = null;
        try {
            debugDir = prepareDebugTestCaseDir(userInput);
            String testCaseId = debugDir.getFileName().toString();

            Map<String, Object> judgePayload = new LinkedHashMap<>();
            judgePayload.put("language_config", languageConfig);
            judgePayload.put("src", code);
            judgePayload.put("max_cpu_time", timeLimit);
            judgePayload.put("max_memory", 1024L * 1024L * memoryLimit);
            judgePayload.put("test_case_id", testCaseId);
            judgePayload.put("output", true);

            Map<String, Object> response = requestJudge(server.serviceUrl(), judgePayload, JUDGE_TIMEOUT);
            if (response == null || response.isEmpty()) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Failed to call judge server");
            }

            if (isTruthy(response.get("err"))) {
                Object errorData = response.get("data");
                return ApiResponse.success(Map.of(
                        "output", "",
                        "error", String.valueOf(errorData == null ? "Unknown error" : errorData),
                        "time_cost", 0,
                        "memory_cost", 0
                ));
            }

            List<Map<String, Object>> resultData = extractResultData(response.get("data"));
            if (resultData.isEmpty()) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid judge server response");
            }

            Map<String, Object> first = resultData.get(0);
            String output = trimToEmpty(asString(first.get("output")));
            int resultCode = parseInt(asString(first.get("result")), 0);
            int timeCost = parseInt(asString(first.get("cpu_time")), 0);
            int memoryCost = parseInt(asString(first.get("memory")), 0) / 1024;
            if (resultCode > 0) {
                return ApiResponse.success(Map.of(
                        "output", output,
                        "error", getDebugErrorMessage(resultCode, first),
                        "time_cost", timeCost,
                        "memory_cost", memoryCost
                ));
            }
            return ApiResponse.success(Map.of(
                    "output", output.isEmpty() ? "(程序执行完成，无输出)" : output,
                    "error", "",
                    "time_cost", timeCost,
                    "memory_cost", memoryCost
            ));
        } catch (java.net.http.HttpTimeoutException ignored) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Judge server timeout");
        } catch (ConnectException exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Failed to connect to judge server: " + exception.getMessage());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Failed to connect to judge server: " + exception.getMessage());
        } catch (Exception exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Debug failed: " + exception.getMessage());
        } finally {
            deleteDirectoryQuietly(debugDir);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Object> rejudgeSubmission(String submissionId, Authentication authentication) {
        AuthUser user = resolveAuthUser(authentication);
        if (user == null || !user.isAdminManager()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }

        String normalizedId = trimToNull(submissionId);
        if (normalizedId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "参数错误，缺少 ID");
        }

        Long count = jdbcTemplate.queryForObject(
                "select count(*) from submission where id = ?",
                Long.class,
                normalizedId
        );
        if (count == null || count == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "提交记录不存在");
        }
        Integer previousResult = findSubmissionResult(normalizedId);
        if (previousResult == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "提交记录不存在");
        }
        if (previousResult == 6 || previousResult == 7) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "提交仍在评测中");
        }

        jdbcTemplate.update(
                "update submission set statistic_info = cast(? as jsonb), result = 6 where id = ?",
                writeJson(Map.of(REJUDGE_PREVIOUS_RESULT_KEY, previousResult)),
                normalizedId
        );
        dispatchJudgeAfterCommit(normalizedId);
        return ApiResponse.success(null);
    }

    private void insertSubmission(
            String id,
            Long problemId,
            Long userId,
            String username,
            String code,
            int result,
            Map<String, Object> info,
            String language,
            boolean shared,
            Map<String, Object> statisticInfo,
            String ip
    ) {
        jdbcTemplate.update(
                """
                insert into submission(
                    id, problem_id, user_id, username, code, result,
                    info, language, shared, statistic_info, ip, create_time
                ) values (
                    ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, cast(? as jsonb), ?, now()
                )
                """,
                id,
                problemId,
                userId,
                username,
                code,
                result,
                writeJson(info),
                language,
                shared,
                writeJson(statisticInfo),
                trimToNull(ip)
        );
    }

    private ObjectiveJudgeResult judgeObjective(String statisticInfoJson, String objectiveAnswer, Object objectiveBlanks) {
        Map<String, Object> statisticInfo = parseJsonMap(statisticInfoJson);
        Object objectiveQuestion = statisticInfo.get("objective_question");
        if (!(objectiveQuestion instanceof Map<?, ?> questionMapRaw)) {
            return null;
        }
        Map<String, Object> question = castToStringObjectMap(questionMapRaw);
        String questionType = trimToNull(question.get("question_type") == null
                ? null
                : String.valueOf(question.get("question_type")));
        if (!"choice".equals(questionType) && !"fill_blank".equals(questionType)) {
            return null;
        }

        if ("choice".equals(questionType)) {
            String expected = lowerTrim(question.get("answer") == null ? "" : String.valueOf(question.get("answer")));
            String given = lowerTrim(objectiveAnswer);
            boolean passed = !expected.isEmpty() && expected.equals(given);
            return new ObjectiveJudgeResult(passed, questionType, trimToEmpty(objectiveAnswer), 0, 0);
        }

        Object blanksObj = question.get("blanks");
        List<String> expectedBlanks = new ArrayList<>();
        if (blanksObj instanceof List<?> list) {
            for (Object item : list) {
                expectedBlanks.add(trimToEmpty(item == null ? "" : String.valueOf(item)));
            }
        }

        List<String> answers = new ArrayList<>();
        if (objectiveBlanks instanceof List<?> list) {
            for (Object item : list) {
                answers.add(trimToEmpty(item == null ? "" : String.valueOf(item)));
            }
        } else if (objectiveBlanks instanceof Map<?, ?> map) {
            for (int i = 0; i < expectedBlanks.size(); i++) {
                Object one = map.get(String.valueOf(i));
                answers.add(trimToEmpty(one == null ? "" : String.valueOf(one)));
            }
        } else {
            for (int i = 0; i < expectedBlanks.size(); i++) {
                answers.add("");
            }
        }

        int filled = 0;
        for (String answer : answers) {
            if (!answer.isEmpty()) {
                filled++;
            }
        }

        boolean passed = !expectedBlanks.isEmpty();
        for (int i = 0; i < expectedBlanks.size(); i++) {
            String expected = lowerTrim(expectedBlanks.get(i));
            String given = i < answers.size() ? lowerTrim(answers.get(i)) : "";
            if (!expected.equals(given)) {
                passed = false;
                break;
            }
        }

        return new ObjectiveJudgeResult(passed, questionType, answers, expectedBlanks.size(), filled);
    }

    private SubmissionRow findSubmissionById(String submissionId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select s.id, s.problem_id, s.create_time, s.user_id, s.username, s.code, s.result,
                           s.info::text as info_json, s.language, s.shared,
                           s.statistic_info::text as statistic_info_json, s.ip,
                           p._id as problem_display_id, p.created_by_id as problem_created_by_id
                    from submission s
                    left join problem p on p.id = s.problem_id
                    where s.id = ?
                    """,
                    (rs, rowNum) -> new SubmissionRow(
                            rs.getString("id"),
                            rs.getLong("problem_id"),
                            rs.getTimestamp("create_time"),
                            rs.getLong("user_id"),
                            rs.getString("username"),
                            rs.getString("code"),
                            rs.getInt("result"),
                            rs.getString("info_json"),
                            rs.getString("language"),
                            rs.getBoolean("shared"),
                            rs.getString("statistic_info_json"),
                            rs.getString("ip"),
                            rs.getString("problem_display_id"),
                            rs.getObject("problem_created_by_id") == null ? null : rs.getLong("problem_created_by_id")
                    ),
                    submissionId
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private ProblemRow findProblemForSubmission(Long problemId, AuthUser user) {
        ProblemRow visible = jdbcTemplate.query(
                """
                select id, _id as display_id, languages::text as languages_json, template::text as template_json,
                       statistic_info::text as statistic_info_json, visibility_status
                from problem
                where id = ? and visible = true
                order by id desc
                limit 1
                """,
                (rs, rowNum) -> new ProblemRow(
                        rs.getLong("id"),
                        rs.getString("display_id"),
                        rs.getString("languages_json"),
                        rs.getString("template_json"),
                        rs.getString("statistic_info_json"),
                        rs.getString("visibility_status")
                ),
                problemId
        ).stream().findFirst().orElse(null);
        if (visible != null) {
            return visible;
        }
        if (user != null && user.isAdminRole()) {
            return jdbcTemplate.query(
                    """
                    select id, _id as display_id, languages::text as languages_json, template::text as template_json,
                           statistic_info::text as statistic_info_json, visibility_status
                    from problem
                    where id = ? and is_ai_generated = true
                    order by id desc
                    limit 1
                    """,
                    (rs, rowNum) -> new ProblemRow(
                            rs.getLong("id"),
                            rs.getString("display_id"),
                            rs.getString("languages_json"),
                            rs.getString("template_json"),
                            rs.getString("statistic_info_json"),
                            rs.getString("visibility_status")
                    ),
                    problemId
            ).stream().findFirst().orElse(null);
        }
        return jdbcTemplate.query(
                """
                select id, _id as display_id, languages::text as languages_json, template::text as template_json,
                       statistic_info::text as statistic_info_json, visibility_status
                from problem
                where id = ?
                  and is_ai_generated = true
                  and (visibility_status <> 'student_private' or created_by_id = ?)
                order by id desc
                limit 1
                """,
                (rs, rowNum) -> new ProblemRow(
                        rs.getLong("id"),
                        rs.getString("display_id"),
                        rs.getString("languages_json"),
                        rs.getString("template_json"),
                        rs.getString("statistic_info_json"),
                        rs.getString("visibility_status")
                ),
                problemId,
                user == null ? -1L : user.id()
        ).stream().findFirst().orElse(null);
    }

    private Long findVisibleOrAiProblemByDisplayId(String displayId, AuthUser user) {
        Long visible = jdbcTemplate.query(
                "select id from problem where _id = ? and visible = true order by id desc limit 1",
                (rs, rowNum) -> rs.getLong(1),
                displayId
        ).stream().findFirst().orElse(null);
        if (visible != null) {
            return visible;
        }
        if (user != null && user.isAdminRole()) {
            return jdbcTemplate.query(
                    "select id from problem where _id = ? and is_ai_generated = true order by id desc limit 1",
                    (rs, rowNum) -> rs.getLong(1),
                    displayId
            ).stream().findFirst().orElse(null);
        }
        return jdbcTemplate.query(
                """
                select id
                from problem
                where _id = ?
                  and is_ai_generated = true
                  and (visibility_status <> 'student_private' or created_by_id = ?)
                order by id desc
                limit 1
                """,
                (rs, rowNum) -> rs.getLong(1),
                displayId,
                user == null ? -1L : user.id()
        ).stream().findFirst().orElse(null);
    }

    private ProblemBaseInfo findProblemByParam(String problemIdParam) {
        if (problemIdParam.chars().allMatch(Character::isDigit)) {
            Long problemId = parseLong(problemIdParam, null);
            if (problemId == null) {
                return null;
            }
            return jdbcTemplate.query(
                    "select id, _id, title from problem where id = ? order by id desc limit 1",
                    (rs, rowNum) -> new ProblemBaseInfo(rs.getLong("id"), rs.getString("_id"), rs.getString("title")),
                    problemId
            ).stream().findFirst().orElse(null);
        }
        return jdbcTemplate.query(
                "select id, _id, title from problem where _id = ? order by id desc limit 1",
                (rs, rowNum) -> new ProblemBaseInfo(rs.getLong("id"), rs.getString("_id"), rs.getString("title")),
                problemIdParam
        ).stream().findFirst().orElse(null);
    }

    private DebugProblemRow findDebugProblem(Long problemId, AuthUser user) {
        DebugProblemRow visible = jdbcTemplate.query(
                """
                select id, languages::text as languages_json, template::text as template_json,
                       statistic_info::text as statistic_info_json, visibility_status, time_limit, memory_limit
                from problem
                where id = ? and visible = true
                order by id desc
                limit 1
                """,
                (rs, rowNum) -> new DebugProblemRow(
                        rs.getLong("id"),
                        rs.getString("languages_json"),
                        rs.getString("template_json"),
                        rs.getString("statistic_info_json"),
                        rs.getString("visibility_status"),
                        rs.getInt("time_limit"),
                        rs.getInt("memory_limit")
                ),
                problemId
        ).stream().findFirst().orElse(null);
        if (visible != null) {
            return visible;
        }
        if (user != null && user.isAdminRole()) {
            return jdbcTemplate.query(
                    """
                    select id, languages::text as languages_json, template::text as template_json,
                           statistic_info::text as statistic_info_json, visibility_status, time_limit, memory_limit
                    from problem
                    where id = ? and is_ai_generated = true
                    order by id desc
                    limit 1
                    """,
                    (rs, rowNum) -> new DebugProblemRow(
                            rs.getLong("id"),
                            rs.getString("languages_json"),
                            rs.getString("template_json"),
                            rs.getString("statistic_info_json"),
                            rs.getString("visibility_status"),
                            rs.getInt("time_limit"),
                            rs.getInt("memory_limit")
                    ),
                    problemId
            ).stream().findFirst().orElse(null);
        }
        return jdbcTemplate.query(
                """
                select id, languages::text as languages_json, template::text as template_json,
                       statistic_info::text as statistic_info_json, visibility_status, time_limit, memory_limit
                from problem
                where id = ?
                  and is_ai_generated = true
                  and (visibility_status <> 'student_private' or created_by_id = ?)
                order by id desc
                limit 1
                """,
                (rs, rowNum) -> new DebugProblemRow(
                        rs.getLong("id"),
                        rs.getString("languages_json"),
                        rs.getString("template_json"),
                        rs.getString("statistic_info_json"),
                        rs.getString("visibility_status"),
                        rs.getInt("time_limit"),
                        rs.getInt("memory_limit")
                ),
                problemId,
                user == null ? -1L : user.id()
        ).stream().findFirst().orElse(null);
    }

    private AiTutorProblemLanguageNormalizer.NormalizedProblemLanguage normalizeProblemLanguage(String visibilityStatus,
                                                                                               String statisticInfoJson,
                                                                                               String languagesJson,
                                                                                               String templateJson) {
        return aiTutorProblemLanguageNormalizer.normalize(
                visibilityStatus,
                statisticInfoJson,
                languagesJson,
                templateJson
        );
    }

    private String applyProblemTemplate(String templateJson, String language, String code) {
        Map<String, Object> templateMap = parseJsonMap(templateJson);
        Object rawTemplate = templateMap.get(language);
        if (rawTemplate == null) {
            return code;
        }
        String template = String.valueOf(rawTemplate);
        String prepend = "";
        String append = "";

        Matcher prependMatcher = PREPEND_PATTERN.matcher(template);
        if (prependMatcher.find()) {
            prepend = prependMatcher.group(1);
        }
        Matcher appendMatcher = APPEND_PATTERN.matcher(template);
        if (appendMatcher.find()) {
            append = appendMatcher.group(1);
        }
        return prepend + "\n" + code + "\n" + append;
    }

    private Path prepareDebugTestCaseDir(String userInput) throws IOException {
        Path rootDir = Path.of(properties.getSystem().getTestCaseDir());
        Files.createDirectories(rootDir);
        Path tempDir = Files.createTempDirectory(rootDir, "debug_");
        Files.writeString(tempDir.resolve("1.in"), userInput);
        Files.writeString(tempDir.resolve("1.out"), "");
        Map<String, Object> caseInfo = new LinkedHashMap<>();
        caseInfo.put("input_name", "1.in");
        caseInfo.put("output_name", "1.out");
        caseInfo.put("stripped_output_md5", null);

        Map<String, Object> testCases = new LinkedHashMap<>();
        testCases.put("1", caseInfo);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("test_cases", testCases);
        info.put("spj", null);
        Files.writeString(tempDir.resolve("info"), writeJson(info));
        return tempDir;
    }

    private JudgeServerCandidate pickAvailableJudgeServer() {
        List<JudgeServerCandidate> candidates = jdbcTemplate.query(
                """
                select hostname, service_url, ip, last_heartbeat, task_number
                from judge_server
                where is_disabled = false
                order by task_number asc, create_time asc
                """,
                (rs, rowNum) -> new JudgeServerCandidate(
                        rs.getString("hostname"),
                        rs.getString("service_url"),
                        rs.getString("ip"),
                        rs.getTimestamp("last_heartbeat")
                )
        );

        Instant threshold = Instant.now().minusSeconds(6);
        for (JudgeServerCandidate candidate : candidates) {
            if (candidate.lastHeartbeat() != null && candidate.lastHeartbeat().toInstant().isAfter(threshold)) {
                return candidate;
            }
        }
        List<java.util.concurrent.CompletableFuture<JudgeServerCandidate>> futures = new ArrayList<>();
        for (JudgeServerCandidate candidate : candidates) {
            futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                if (isServiceReachable(candidate.serviceUrl())) {
                    return candidate;
                }
                if (trimToNull(candidate.ip()) != null && isServiceReachable("http://" + candidate.ip() + ":8080")) {
                    return new JudgeServerCandidate(candidate.hostname(), "http://" + candidate.ip() + ":8080", candidate.ip(), candidate.lastHeartbeat());
                }
                return null;
            }));
        }
        try {
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                    .get(PING_TIMEOUT.toMillis() + 500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }
        for (var future : futures) {
            if (!future.isDone()) {
                continue;
            }
            try {
                JudgeServerCandidate result = future.getNow(null);
                if (result != null) {
                    return result;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean isServiceReachable(String serviceUrl) {
        String normalized = trimToNull(serviceUrl);
        if (normalized == null) {
            return false;
        }
        try {
            HttpResponse<String> response = requireAiCircuitBreaker().executeWithInstance("judgeServer", "judge ping", () -> {
                URI pingUri = normalizeBaseUri(normalized).resolve("ping");
                HttpRequest request = HttpRequest.newBuilder(pingUri)
                        .timeout(PING_TIMEOUT)
                        .GET()
                        .build();
                return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            });
            return response.statusCode() >= 200 && response.statusCode() < 500;
        } catch (Exception e) {
            log.debug("judge server ping unreachable: {}", normalized, e);
            return false;
        }
    }

    private Map<String, Object> requestJudge(String serviceUrl, Map<String, Object> payload, Duration timeout)
            throws IOException, InterruptedException {
        HttpResponse<String> response;
        try {
            response = requireAiCircuitBreaker().executeWithInstance("judgeServer", "judge execute", () -> {
                URI judgeUri = normalizeBaseUri(serviceUrl).resolve("/judge");
                HttpRequest request = HttpRequest.newBuilder(judgeUri)
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .header("X-Judge-Server-Token", HashUtils.sha256(properties.getJudgeServer().getToken()))
                        .POST(HttpRequest.BodyPublishers.ofString(writeJson(payload)))
                        .build();
                return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            });
        } catch (IOException | InterruptedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Judge request failed: " + exception.getMessage(), exception);
        }
        String raw = response.body();
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private void dispatchJudgeAsync(String submissionId) {
        if (submissionEventPublisher != null) {
            submissionEventPublisher.publishJudgeDispatch(submissionId);
        } else {
            JUDGE_EXECUTOR.submit(() -> runJudgeTask(submissionId));
        }
    }

    private AiCircuitBreaker requireAiCircuitBreaker() {
        if (aiCircuitBreaker == null) {
            throw new IllegalStateException("AiCircuitBreaker is required for judge server calls");
        }
        return aiCircuitBreaker;
    }

    @Override
    public void executeJudge(String submissionId) {
        runJudgeTask(submissionId);
    }

    private void dispatchJudgeAfterCommit(String submissionId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            dispatchJudgeAsync(submissionId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchJudgeAsync(submissionId);
            }
        });
    }

    private void dispatchCodeQualityAssessment(JudgeTaskRow task) {
        if (task == null || codeQualityAssessmentService == null) {
            return;
        }
        CODE_QUALITY_EXECUTOR.submit(() -> runCodeQualityAssessment(task));
    }

    private void runCodeQualityAssessment(JudgeTaskRow task) {
        if (task == null || codeQualityAssessmentService == null) {
            return;
        }
        try {
            Integer currentResult = jdbcTemplate.queryForObject(
                    "select result from submission where id = ?",
                    Integer.class,
                    task.id()
            );
            if (currentResult == null || currentResult != 0) {
                return;
            }

            Map<String, Object> assessment = codeQualityAssessmentService.assess(
                    trimToEmpty(task.code()),
                    task.language(),
                    buildProblemDescription(task)
            );
            Map<String, Object> statisticInfo = loadSubmissionStatisticInfo(task.id());
            statisticInfo.put("code_quality", assessment);
            statisticInfo.put("code_quality_status", "ready");
            jdbcTemplate.update(
                    "update submission set statistic_info = cast(? as jsonb) where id = ?",
                    writeJson(statisticInfo),
                    task.id()
            );
        } catch (Exception exception) {
            log.warn("code quality assessment failed for submission {}: {}", task.id(), exception.getMessage());
            Map<String, Object> statisticInfo = loadSubmissionStatisticInfo(task.id());
            statisticInfo.put("code_quality_status", "failed");
            statisticInfo.put("code_quality_error", trimToEmpty(exception.getMessage()));
            jdbcTemplate.update(
                    "update submission set statistic_info = cast(? as jsonb) where id = ?",
                    writeJson(statisticInfo),
                    task.id()
            );
        }
    }

    private void runJudgeTask(String submissionId) {
        try {
            JudgeTaskRow task = findJudgeTaskRow(submissionId);
            if (task == null) {
                return;
            }

            Map<String, Object> languageConfig = resolveLanguageConfig(task.language());
            if (languageConfig == null) {
                updateSubmissionSystemError(task, "Language " + task.language() + " is not supported");
                return;
            }
            if (trimToNull(task.testCaseId()) == null) {
                updateSubmissionSystemError(task, "No test case configured");
                return;
            }

            JudgeServerCandidate server = pickAvailableJudgeServer();
            if (server == null) {
                updateSubmissionSystemError(task, "暂无可用的评测服务器");
                return;
            }

            jdbcTemplate.update("update submission set result = 7 where id = ?", submissionId);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("language_config", languageConfig);
            payload.put("src", applyProblemTemplate(task.templateJson(), task.language(), trimToEmpty(task.code())));
            payload.put("max_cpu_time", task.timeLimit());
            payload.put("max_memory", 1024L * 1024L * task.memoryLimit());
            payload.put("test_case_id", task.testCaseId());
            payload.put("output", false);

            Map<String, Object> response = requestJudge(server.serviceUrl(), payload, JUDGE_TIMEOUT);
            if (response == null || response.isEmpty()) {
                updateSubmissionSystemError(task, "Judge server returned empty response");
                return;
            }

            if (isTruthy(response.get("err"))) {
                Map<String, Object> stat = new LinkedHashMap<>();
                stat.put("err_info", asString(response.get("data")));
                stat.put("score", 0);
                String errInfo = trimToEmpty(asString(response.get("data")));
                jdbcTemplate.update(
                        "update submission set result = -2, statistic_info = cast(? as jsonb) where id = ?",
                        writeJson(stat),
                        submissionId
                );
                synchronizeJudgeStats(task, -2);
                publishJudgeCompleted(task, -2, errInfo, response, stat);
                return;
            }

            List<Map<String, Object>> cases = extractResultData(response.get("data"));
            if (cases.isEmpty()) {
                updateSubmissionSystemError(task, "Invalid judge server response");
                return;
            }

            int maxCpuTime = 0;
            int maxMemory = 0;
            int finalResult = 0;
            String firstErrInfo = "";
            int passedTestCaseCount = 0;
            for (Map<String, Object> one : cases) {
                int result = parseInt(asString(one.get("result")), 0);
                if (finalResult == 0 && result != 0) {
                    finalResult = result;
                    firstErrInfo = trimToEmpty(asString(one.get("error")));
                }
                if (result == 0) {
                    passedTestCaseCount += 1;
                }
                maxCpuTime = Math.max(maxCpuTime, parseInt(asString(one.get("cpu_time")), 0));
                maxMemory = Math.max(maxMemory, parseInt(asString(one.get("memory")), 0));
            }

            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("time_cost", maxCpuTime);
            stat.put("memory_cost", maxMemory);
            stat.put("passed_test_case_count", passedTestCaseCount);
            stat.put("total_test_case_count", cases.size());
            if (finalResult != 0 && !firstErrInfo.isEmpty()) {
                stat.put("err_info", firstErrInfo);
            }
            if (finalResult != 0) {
                stat.put("partial_score", calculatePartialScore(passedTestCaseCount, cases.size()));
            }
            if (finalResult == 0) {
                stat.put("code_quality_status", "pending");
            }

            jdbcTemplate.update(
                    "update submission set result = ?, info = cast(? as jsonb), statistic_info = cast(? as jsonb) where id = ?",
                    finalResult,
                    writeJson(response),
                    writeJson(stat),
                    submissionId
            );
            synchronizeJudgeStats(task, finalResult);
            publishJudgeCompleted(task, finalResult, firstErrInfo, response, stat);
            if (finalResult == 0) {
                dispatchCodeQualityAssessment(task);
            }
        } catch (Exception exception) {
            JudgeTaskRow task = findJudgeTaskRow(submissionId);
            if (task == null) {
                return;
            }
            if (!isSubmissionPending(submissionId)) {
                return;
            }
            updateSubmissionSystemError(task, "Internal judge error: " + exception.getMessage());
        }
    }

    private JudgeTaskRow findJudgeTaskRow(String submissionId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select s.id, s.code, s.language, s.statistic_info::text as submission_statistic_info_json,
                           s.user_id, s.problem_id, p._id as problem_display_id,
                           p.test_case_id, p.languages::text as languages_json, p.template::text as template_json,
                           p.statistic_info::text as problem_statistic_info_json, p.visibility_status,
                           p.time_limit, p.memory_limit,
                           p.title, p.description, p.input_description, p.output_description
                    from submission s
                    join problem p on p.id = s.problem_id
                    where s.id = ?
                    """,
                    (rs, rowNum) -> {
                        AiTutorProblemLanguageNormalizer.NormalizedProblemLanguage normalizedProblemLanguage =
                                normalizeProblemLanguage(
                                        rs.getString("visibility_status"),
                                        rs.getString("problem_statistic_info_json"),
                                        rs.getString("languages_json"),
                                        rs.getString("template_json")
                                );
                        return new JudgeTaskRow(
                                rs.getString("id"),
                                rs.getString("code"),
                                rs.getString("language"),
                                rs.getString("submission_statistic_info_json"),
                                rs.getLong("user_id"),
                                rs.getLong("problem_id"),
                                rs.getString("problem_display_id"),
                                rs.getString("test_case_id"),
                                writeJson(normalizedProblemLanguage.fullTemplates()),
                                rs.getInt("time_limit"),
                                rs.getInt("memory_limit"),
                                rs.getString("title"),
                                rs.getString("description"),
                                rs.getString("input_description"),
                                rs.getString("output_description")
                        );
                    },
                    submissionId
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private void publishJudgeCompleted(JudgeTaskRow task, int finalResult, String errInfo,
                                       Map<String, Object> response, Map<String, Object> stat) {
        try {
            judgeCompletedEventPublisher.publish(new com.alethicode.service.submission.JudgeCompletedEvent(
                    task.id(), task.userId(), task.problemId(), task.problemDisplayId(),
                    finalResult, trimToEmpty(errInfo), trimToEmpty(task.code()), task.language(),
                    task.problemTitle(), task.problemDescription(), task.inputDescription(), task.outputDescription(),
                    response, stat
            ));
        } catch (Exception e) {
            log.warn("Failed to publish JudgeCompletedEvent for submission {}: {}", task.id(), e.getMessage());
        }
    }

    private void updateSubmissionSystemError(JudgeTaskRow task, String errorMessage) {
        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("err_info", trimToEmpty(errorMessage));
        stat.put("score", 0);
        jdbcTemplate.update(
                "update submission set result = 5, statistic_info = cast(? as jsonb) where id = ?",
                writeJson(stat),
                task.id()
        );
        synchronizeJudgeStats(task, 5);
        publishJudgeCompleted(task, 5, errorMessage, null, stat);
    }

    private void collectSubmissionData(JudgeTaskRow task, int result,
                                        Map<String, Object> response,
                                        Map<String, Object> statisticInfo) {
        try {
            submissionDataCollector.collect(
                    task.id(), task.userId(), task.problemId(),
                    task.problemDisplayId(), task.problemTitle(),
                    task.language(), task.code(),
                    result, response, statisticInfo, Instant.now());
        } catch (Exception e) {
            log.warn("Submission data collection failed for {}: {}", task.id(), e.getMessage());
        }
    }

    private void updateLearnerMastery(Long userId, Long problemId, boolean isCorrect) {
        try {
            List<Map<String, Object>> mappings = jdbcTemplate.queryForList("""
                SELECT lpm.language_pack_id, kc.value::bigint AS kc_id
                FROM language_pack_problem_mapping lpm
                JOIN problem p ON p.id = lpm.problem_id
                CROSS JOIN LATERAL jsonb_array_elements(
                    p.statistic_info->'language_pack_teaching'->'related_kc_ids'
                ) AS kc(value)
                WHERE lpm.problem_id = ?
                  AND p.statistic_info->'language_pack_teaching' IS NOT NULL
                """, problemId);
            for (Map<String, Object> m : mappings) {
                Long lpId = ((Number) m.get("language_pack_id")).longValue();
                Long kcId = ((Number) m.get("kc_id")).longValue();
                masteryService.updateMastery(userId, lpId, kcId, isCorrect);
            }
        } catch (Exception e) {
            log.warn("Learner mastery update failed for user={} problem={}: {}", userId, problemId, e.getMessage());
        }
    }

    private int calculatePartialScore(int passedTestCaseCount, int totalTestCaseCount) {
        if (totalTestCaseCount <= 0) {
            return 0;
        }
        return (int) Math.round((passedTestCaseCount * 100.0) / totalTestCaseCount);
    }

    private Map<String, Object> loadSubmissionStatisticInfo(String submissionId) {
        String json = jdbcTemplate.queryForObject(
                "select statistic_info::text from submission where id = ?",
                String.class,
                submissionId
        );
        return new LinkedHashMap<>(parseJsonMap(json));
    }

    private String buildProblemDescription(JudgeTaskRow task) {
        return """
                标题: %s
                题目描述: %s
                输入描述: %s
                输出描述: %s
                """.formatted(
                trimToEmpty(task.problemTitle()),
                trimToEmpty(task.problemDescription()),
                trimToEmpty(task.inputDescription()),
                trimToEmpty(task.outputDescription())
        );
    }

    private void synchronizeJudgeStats(JudgeTaskRow task, int finalResult) {
        Integer previousResult = extractRejudgePreviousResult(task.submissionStatisticInfoJson());
        if (previousResult == null) {
            synchronizeFirstJudgeStats(task.problemId(), task.userId(), task.problemDisplayId(), finalResult);
            return;
        }
        synchronizeRejudgeStats(task.problemId(), task.userId(), task.problemDisplayId(), previousResult, finalResult);
    }

    private void synchronizeFirstJudgeStats(Long problemId, Long userId, String problemDisplayId, int finalResult) {
        transactionTemplate.executeWithoutResult(status -> {
            LockedProblemStats problem = lockProblemStats(problemId);
            Map<String, Object> problemStatisticInfo = new LinkedHashMap<>(parseJsonMap(problem.statisticInfoJson()));
            incrementResultCounter(problemStatisticInfo, finalResult, 1);
            jdbcTemplate.update(
                    "update problem set submission_number = ?, accepted_number = ?, statistic_info = cast(? as jsonb) where id = ?",
                    problem.submissionNumber() + 1,
                    problem.acceptedNumber() + (finalResult == 0 ? 1 : 0),
                    writeJson(problemStatisticInfo),
                    problemId
            );

            LockedUserProfileStats profile = lockUserProfileStats(userId);
            Map<String, Object> acmProblemsStatus = new LinkedHashMap<>(parseJsonMap(profile.acmProblemsStatusJson()));
            Map<String, Object> problems = nestedStatusMap(acmProblemsStatus);
            String problemKey = String.valueOf(problemId);
            Map<String, Object> entry = asMutableStatusEntry(problems.get(problemKey));
            int acceptedNumber = profile.acceptedNumber();
            if (entry == null) {
                entry = new LinkedHashMap<>();
                entry.put("status", finalResult);
                entry.put("_id", problemDisplayId);
                problems.put(problemKey, entry);
                if (finalResult == 0) {
                    acceptedNumber += 1;
                }
            } else if (!Objects.equals(asInteger(entry.get("status")), 0)) {
                entry.put("status", finalResult);
                entry.put("_id", trimToEmpty(problemDisplayId));
                problems.put(problemKey, entry);
                if (finalResult == 0) {
                    acceptedNumber += 1;
                }
            }
            acmProblemsStatus.put("problems", problems);
            jdbcTemplate.update(
                    "update user_profile set submission_number = ?, accepted_number = ?, acm_problems_status = cast(? as jsonb) where user_id = ?",
                    profile.submissionNumber() + 1,
                    acceptedNumber,
                    writeJson(acmProblemsStatus),
                    userId
            );
        });
    }

    private void synchronizeRejudgeStats(Long problemId, Long userId, String problemDisplayId, int previousResult, int finalResult) {
        transactionTemplate.executeWithoutResult(status -> {
            LockedProblemStats problem = lockProblemStats(problemId);
            Map<String, Object> problemStatisticInfo = new LinkedHashMap<>(parseJsonMap(problem.statisticInfoJson()));
            incrementResultCounter(problemStatisticInfo, previousResult, -1);
            incrementResultCounter(problemStatisticInfo, finalResult, 1);
            int acceptedNumber = problem.acceptedNumber();
            if (previousResult != 0 && finalResult == 0) {
                acceptedNumber += 1;
            }
            jdbcTemplate.update(
                    "update problem set accepted_number = ?, statistic_info = cast(? as jsonb) where id = ?",
                    acceptedNumber,
                    writeJson(problemStatisticInfo),
                    problemId
            );

            LockedUserProfileStats profile = lockUserProfileStats(userId);
            Map<String, Object> acmProblemsStatus = new LinkedHashMap<>(parseJsonMap(profile.acmProblemsStatusJson()));
            Map<String, Object> problems = nestedStatusMap(acmProblemsStatus);
            String problemKey = String.valueOf(problemId);
            Map<String, Object> entry = asMutableStatusEntry(problems.get(problemKey));
            if (entry != null && !Objects.equals(asInteger(entry.get("status")), 0)) {
                entry.put("status", finalResult);
                entry.put("_id", trimToEmpty(problemDisplayId));
                problems.put(problemKey, entry);
                int profileAcceptedNumber = profile.acceptedNumber();
                if (finalResult == 0) {
                    profileAcceptedNumber += 1;
                }
                acmProblemsStatus.put("problems", problems);
                jdbcTemplate.update(
                        "update user_profile set accepted_number = ?, acm_problems_status = cast(? as jsonb) where user_id = ?",
                        profileAcceptedNumber,
                        writeJson(acmProblemsStatus),
                        userId
                );
            }
        });
    }

    private LockedProblemStats lockProblemStats(Long problemId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select submission_number, accepted_number, statistic_info::text as statistic_info_json
                    from problem
                    where id = ?
                    for update
                    """,
                    (rs, rowNum) -> new LockedProblemStats(
                            rs.getInt("submission_number"),
                            rs.getInt("accepted_number"),
                            rs.getString("statistic_info_json")
                    ),
                    problemId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalStateException("Problem does not exist for submission sync: " + problemId, exception);
        }
    }

    private LockedUserProfileStats lockUserProfileStats(Long userId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select submission_number, accepted_number, acm_problems_status::text as acm_problems_status_json
                    from user_profile
                    where user_id = ?
                    for update
                    """,
                    (rs, rowNum) -> new LockedUserProfileStats(
                            rs.getInt("submission_number"),
                            rs.getInt("accepted_number"),
                            rs.getString("acm_problems_status_json")
                    ),
                    userId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalStateException("User profile does not exist for submission sync: " + userId, exception);
        }
    }

    private void incrementResultCounter(Map<String, Object> statisticInfo, int resultCode, int delta) {
        String key = String.valueOf(resultCode);
        int current = asInteger(statisticInfo.get(key)) == null ? 0 : asInteger(statisticInfo.get(key));
        int next = current + delta;
        statisticInfo.put(key, Math.max(next, 0));
    }

    private Map<String, Object> nestedStatusMap(Map<String, Object> acmProblemsStatus) {
        Object rawProblems = acmProblemsStatus.get("problems");
        if (rawProblems instanceof Map<?, ?> map) {
            return new LinkedHashMap<>(castToStringObjectMap(map));
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> asMutableStatusEntry(Object rawEntry) {
        if (!(rawEntry instanceof Map<?, ?> map)) {
            return null;
        }
        return new LinkedHashMap<>(castToStringObjectMap(map));
    }

    private Integer extractRejudgePreviousResult(String statisticInfoJson) {
        Map<String, Object> statisticInfo = parseJsonMap(statisticInfoJson);
        return asInteger(statisticInfo.get(REJUDGE_PREVIOUS_RESULT_KEY));
    }

    private boolean hasReviewMarker(Map<String, Object> statisticInfo) {
        return statisticInfo.containsKey("needs_human_review") || statisticInfo.containsKey("human_review");
    }

    private Integer findSubmissionResult(String submissionId) {
        try {
            return jdbcTemplate.queryForObject(
                    "select result from submission where id = ?",
                    Integer.class,
                    submissionId
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private boolean isSubmissionPending(String submissionId) {
        Integer result = findSubmissionResult(submissionId);
        return result != null && (result == 6 || result == 7);
    }

    private void upsertNotebookForWrongSubmission(String submissionId, Integer resultCode, String errInfo) {
        if (resultCode == null || resultCode == 0 || resultCode == 6 || resultCode == 7) {
            return;
        }
        Map<String, Object> row = jdbcTemplate.query(
                """
                select s.user_id, s.problem_id, s.language, p._id as problem_display_id, p.title as problem_title
                from submission s
                join problem p on p.id = s.problem_id
                where s.id = ?
                limit 1
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("user_id", rs.getLong("user_id"));
                    item.put("problem_id", rs.getLong("problem_id"));
                    item.put("language", trimToEmpty(rs.getString("language")));
                    item.put("problem_display_id", trimToEmpty(rs.getString("problem_display_id")));
                    item.put("problem_title", trimToEmpty(rs.getString("problem_title")));
                    return item;
                },
                submissionId
        ).stream().findFirst().orElse(null);
        if (row == null) {
            return;
        }

        Long userId = ((Number) row.get("user_id")).longValue();
        Long problemId = ((Number) row.get("problem_id")).longValue();
        String language = trimToEmpty((String) row.get("language"));
        String category = notebookTaxonomyByResult(resultCode);
        String rootCause = notebookRootCause(category, errInfo);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("submission_id", submissionId);
        evidence.put("problem_display_id", row.get("problem_display_id"));
        evidence.put("problem_title", row.get("problem_title"));
        evidence.put("result_code", resultCode);
        evidence.put("captured_at", Instant.now().toString());
        if (!trimToEmpty(errInfo).isBlank()) {
            evidence.put("err_info", trimToEmpty(errInfo));
        }

        String existingId = jdbcTemplate.query(
                """
                select id
                from ai_learner_notebook
                where user_id = ?
                  and problem_id = ?
                  and error_taxonomy = ?
                  and is_deleted = false
                order by update_time desc
                limit 1
                """,
                (rs, rowNum) -> rs.getString("id"),
                userId,
                problemId,
                category
        ).stream().findFirst().orElse(null);

        if (existingId != null) {
            jdbcTemplate.update(
                    """
                    update ai_learner_notebook
                    set language = ?,
                        root_cause = ?,
                        evidence_ptr = cast(? as jsonb),
                        update_time = now()
                    where id = ?
                      and user_id = ?
                    """,
                    language,
                    rootCause,
                    writeJson(evidence),
                    existingId,
                    userId
            );
            return;
        }

        jdbcTemplate.update(
                """
                insert into ai_learner_notebook(
                    id, user_id, problem_id, language, error_taxonomy,
                    root_cause, fix_outcome, student_reflection, tags, evidence_ptr,
                    is_deleted, create_time, update_time
                ) values (
                    ?, ?, ?, ?, ?,
                    ?, '', '', cast(? as jsonb), cast(? as jsonb),
                    false, now(), now()
                )
                """,
                randomString(32),
                userId,
                problemId,
                language,
                category,
                rootCause,
                writeJson(List.of(category)),
                writeJson(evidence)
        );
    }

    private void tryRecordReviewPackageSubmission(JudgeTaskRow task, int finalResult) {
        if (errorReviewPackageService == null || task == null) {
            return;
        }
        try {
            errorReviewPackageService.recordSubmission(task.userId(), task.problemId(), finalResult == 0);
        } catch (Exception e) {
            log.warn(
                    "error review package recordSubmission failed userId={} problemId={} finalResult={}",
                    task.userId(),
                    task.problemId(),
                    finalResult,
                    e);
        }
    }

    private String notebookTaxonomyByResult(int resultCode) {
        return switch (resultCode) {
            case -2 -> com.alethicode.service.aitutor.contract.ErrorTaxonomy.SYNTAX_ERROR;
            case -1 -> com.alethicode.service.aitutor.contract.ErrorTaxonomy.LOGIC_ERROR;
            case 1, 2 -> com.alethicode.service.aitutor.contract.ErrorTaxonomy.PERFORMANCE;
            case 3 -> com.alethicode.service.aitutor.contract.ErrorTaxonomy.PERFORMANCE;
            case 4 -> com.alethicode.service.aitutor.contract.ErrorTaxonomy.RUNTIME_ERROR;
            case 5 -> com.alethicode.service.aitutor.contract.ErrorTaxonomy.UNKNOWN;
            default -> com.alethicode.service.aitutor.contract.ErrorTaxonomy.UNKNOWN;
        };
    }

    private String notebookRootCause(String taxonomy, String errInfo) {
        String normalizedErr = trimToEmpty(errInfo).trim();
        if (!normalizedErr.isEmpty()
                && normalizedErr.length() >= 2
                && !normalizedErr.matches("^[0-9]+$")) {
            return normalizedErr.length() > 500 ? normalizedErr.substring(0, 500) : normalizedErr;
        }
        return com.alethicode.service.aitutor.contract.ErrorTaxonomy.label(taxonomy);
    }

    private URI normalizeBaseUri(String serviceUrl) {
        String normalized = trimToEmpty(serviceUrl).trim();
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return URI.create(normalized);
    }

    private Map<String, Object> resolveLanguageConfig(String language) {
        Map<String, Object> option = readMapOption("languages");
        if (option != null && option.get("languages") instanceof List<?> configured) {
            for (Object item : configured) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Object name = map.get("name");
                if (name != null && language.equals(String.valueOf(name))) {
                    Object config = map.get("config");
                    if (config instanceof Map<?, ?> configMap) {
                        return castToStringObjectMap(configMap);
                    }
                }
            }
        }

        return defaultLanguageConfig(language);
    }

    private Map<String, Object> defaultLanguageConfig(String language) {
        return switch (language) {
            case "C" -> Map.of(
                    "compile", Map.of(
                            "src_name", "main.c",
                            "exe_name", "main",
                            "max_cpu_time", 3000,
                            "max_real_time", 10000,
                            "max_memory", 268435456,
                            "compile_command", "/usr/bin/gcc -DONLINE_JUDGE -O2 -w -fmax-errors=3 -std=c17 {src_path} -lm -o {exe_path}"
                    ),
                    "run", Map.of(
                            "command", "{exe_path}",
                            "seccomp_rule", "c_cpp",
                            "env", List.of("LANG=en_US.UTF-8", "LANGUAGE=en_US:en", "LC_ALL=en_US.UTF-8")
                    )
            );
            case "C++" -> Map.of(
                    "compile", Map.of(
                            "src_name", "main.cpp",
                            "exe_name", "main",
                            "max_cpu_time", 10000,
                            "max_real_time", 20000,
                            "max_memory", 1073741824,
                            "compile_command", "/usr/bin/g++ -DONLINE_JUDGE -O2 -w -fmax-errors=3 -std=c++20 {src_path} -lm -o {exe_path}"
                    ),
                    "run", Map.of(
                            "command", "{exe_path}",
                            "seccomp_rule", "c_cpp",
                            "env", List.of("LANG=en_US.UTF-8", "LANGUAGE=en_US:en", "LC_ALL=en_US.UTF-8")
                    )
            );
            case "Java" -> {
                Map<String, Object> compile = new LinkedHashMap<>();
                compile.put("src_name", "Main.java");
                compile.put("exe_name", "Main");
                compile.put("max_cpu_time", 5000);
                compile.put("max_real_time", 10000);
                compile.put("max_memory", -1);
                compile.put("compile_command", "/usr/bin/javac {src_path} -d {exe_dir}");

                Map<String, Object> run = new LinkedHashMap<>();
                run.put("command", "/usr/bin/java -cp {exe_dir} -XX:MaxRAM={max_memory}k Main");
                run.put("seccomp_rule", null);
                run.put("env", List.of("LANG=en_US.UTF-8", "LANGUAGE=en_US:en", "LC_ALL=en_US.UTF-8"));
                run.put("memory_limit_check_only", 1);

                Map<String, Object> config = new LinkedHashMap<>();
                config.put("compile", compile);
                config.put("run", run);
                yield config;
            }
            case "Python3" -> Map.of(
                    "compile", Map.of(
                            "src_name", "solution.py",
                            "exe_name", "solution.py",
                            "max_cpu_time", 3000,
                            "max_real_time", 10000,
                            "max_memory", 134217728,
                            "compile_command", "/usr/bin/python3 -m py_compile {src_path}"
                    ),
                    "run", Map.of(
                            "command", "/usr/bin/python3 -BS {exe_path}",
                            "seccomp_rule", "general",
                            // PYTHONHASHSEED=42 让 set/dict 遍历顺序 deterministic，
                            // 与 reference solution self-validation（语言包初始化质量门）保持一致环境。
                            "env", List.of(
                                    "LANG=en_US.UTF-8",
                                    "LANGUAGE=en_US:en",
                                    "LC_ALL=en_US.UTF-8",
                                    "PYTHONHASHSEED=42"
                            )
                    )
            );
            default -> null;
        };
    }

    private String getDebugErrorMessage(int resultCode, Map<String, Object> resultData) {
        String base = switch (resultCode) {
            case 1 -> "CPU Time Limit Exceeded";
            case 2 -> "Real Time Limit Exceeded";
            case 3 -> "Memory Limit Exceeded";
            case 4 -> "Runtime Error";
            case 5 -> "System Error";
            default -> "Unknown Error";
        };
        String error = trimToNull(asString(resultData.get("error")));
        return error == null ? base : base + ": " + error;
    }

    private List<Map<String, Object>> extractResultData(Object rawData) {
        if (!(rawData instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> casted = castToStringObjectMap(map);
                rows.add(casted);
            }
        }
        rows.sort(Comparator.comparingInt(one -> parseInt(asString(one.get("test_case")), 0)));
        return rows;
    }

    private boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value.toString().trim();
        return !text.isEmpty() && !"false".equalsIgnoreCase(text) && !"0".equals(text) && !"null".equalsIgnoreCase(text);
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> readMapOption(String key) {
        String rawJson;
        try {
            rawJson = jdbcTemplate.queryForObject("select value::text from sys_options where key = ?", String.class, key);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        return parseJsonMap(rawJson);
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private boolean hasAvailableJudgeServer() {
        return pickAvailableJudgeServer() != null;
    }

    private boolean canViewSubmission(SubmissionRow row, AuthUser user) {
        if (user == null) {
            return false;
        }
        if (Objects.equals(row.userId(), user.id())) {
            return true;
        }
        if (user.isAdminManager() || user.canManageAllProblem()) {
            return true;
        }
        return row.problemCreatedById() != null && Objects.equals(row.problemCreatedById(), user.id());
    }

    private AuthUser resolveAuthUser(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id, username, admin_type, problem_permission, is_disabled from \"user\" where username = ?",
                    (rs, rowNum) -> new AuthUser(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("admin_type"),
                            rs.getString("problem_permission"),
                            rs.getBoolean("is_disabled")
                    ),
                    authentication.getName()
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private ApiResponse<Object> requireLogin(AuthUser user) {
        if (user == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        if (user.disabled()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "你的账号已被禁用");
        }
        return null;
    }

    private boolean isSubmissionListShowAll() {
        try {
            String raw = jdbcTemplate.queryForObject(
                    "select value::text from sys_options where key = 'website_config'",
                    String.class
            );
            if (raw != null) {
                Map<String, Object> config = parseJsonMap(raw);
                Object flag = config.get("submission_list_show_all");
                if (flag instanceof Boolean bool) {
                    return bool;
                }
                if (flag != null) {
                    return Boolean.parseBoolean(String.valueOf(flag));
                }
            }
        } catch (EmptyResultDataAccessException ignored) {
        }
        return properties.getWebsite().isSubmissionListShowAll();
    }

    private Map<String, Object> findUserProfile(long userId) {
        try {
            String raw = jdbcTemplate.queryForObject(
                    "select acm_problems_status::text from user_profile where user_id = ?",
                    String.class,
                    userId
            );
            return raw == null ? Map.of() : parseJsonMap(raw);
        } catch (EmptyResultDataAccessException ignored) {
            return Map.of();
        }
    }

    private String writeJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    private Map<String, Object> parseJsonMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private List<String> parseStringList(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            List<Object> raw = objectMapper.readValue(rawJson, new TypeReference<>() {
            });
            List<String> result = new ArrayList<>();
            for (Object item : raw) {
                result.add(String.valueOf(item));
            }
            return result;
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private Map<String, Object> castToStringObjectMap(Map<?, ?> map) {
        Map<String, Object> casted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            casted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return casted;
    }

    private String toIso(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        Instant instant = timestamp.toInstant();
        return ISO.format(instant.atOffset(ZoneOffset.UTC));
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private Integer parseInt(String raw, Integer fallback) {
        String normalized = trimToNull(raw);
        if (normalized == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Long parseLong(String raw, Long fallback) {
        String normalized = trimToNull(raw);
        if (normalized == null) {
            return fallback;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String lowerTrim(String value) {
        return trimToEmpty(value).trim().toLowerCase(Locale.ROOT);
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return parseInt(String.valueOf(value), null);
    }

    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();
    private static final String ID_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private String randomString(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ID_CHARS.charAt(SECURE_RANDOM.nextInt(ID_CHARS.length())));
        }
        return builder.toString();
    }

    private record AuthUser(
            Long id,
            String username,
            String adminType,
            String problemPermission,
            boolean disabled
    ) {
        private boolean isAdminRole() {
            return "Admin".equals(adminType) || "Teacher".equals(adminType);
        }

        private boolean isAdminManager() {
            return "Admin".equals(adminType);
        }

        private boolean canManageAllProblem() {
            return "All".equals(problemPermission);
        }
    }

    private record ProblemRow(
            Long id,
            String displayId,
            String languagesJson,
            String templateJson,
            String statisticInfoJson,
            String visibilityStatus
    ) {
    }

    private record DebugProblemRow(
            Long id,
            String languagesJson,
            String templateJson,
            String statisticInfoJson,
            String visibilityStatus,
            int timeLimit,
            int memoryLimit
    ) {
    }

    private record JudgeServerCandidate(
            String hostname,
            String serviceUrl,
            String ip,
            Timestamp lastHeartbeat
    ) {
    }

    private record JudgeTaskRow(
            String id,
            String code,
            String language,
            String submissionStatisticInfoJson,
            Long userId,
            Long problemId,
            String problemDisplayId,
            String testCaseId,
            String templateJson,
            int timeLimit,
            int memoryLimit,
            String problemTitle,
            String problemDescription,
            String inputDescription,
            String outputDescription
    ) {
    }

    private record LockedProblemStats(
            int submissionNumber,
            int acceptedNumber,
            String statisticInfoJson
    ) {
    }

    private record LockedUserProfileStats(
            int submissionNumber,
            int acceptedNumber,
            String acmProblemsStatusJson
    ) {
    }

    private record ProblemBaseInfo(
            Long id,
            String displayId,
            String title
    ) {
    }

    private record ObjectiveJudgeResult(
            boolean passed,
            String questionType,
            Object given,
            int totalBlanks,
            int filledBlanks
    ) {
    }

    private record SubmissionRow(
            String id,
            Long problemId,
            Timestamp createTime,
            Long userId,
            String username,
            String code,
            int result,
            String infoJson,
            String language,
            boolean shared,
            String statisticInfoJson,
            String ip,
            String problemDisplayId,
            Long problemCreatedById
    ) {
    }
}
