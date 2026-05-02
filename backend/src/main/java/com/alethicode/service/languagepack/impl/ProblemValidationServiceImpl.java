package com.alethicode.service.languagepack.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.exception.BadRequestException;
import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.languagepack.LanguagePackInitExecutionService;
import com.alethicode.service.languagepack.LanguagePackInitService;
import com.alethicode.service.languagepack.LanguagePackProblemPackage;
import com.alethicode.service.languagepack.ProblemGenerationService;
import com.alethicode.service.languagepack.ProblemValidationService;
import com.alethicode.service.languagepack.quality.LanguagePackInitQualityReportService;
import com.alethicode.service.languagepack.quality.LintViolation;
import com.alethicode.service.languagepack.quality.ReferenceLintReport;
import com.alethicode.service.languagepack.quality.ReferenceSolutionLinter;
import com.alethicode.service.languagepack.quality.ReferenceSolutionSelfValidator;
import com.alethicode.service.languagepack.quality.SamplesSynchronizer;
import com.alethicode.service.languagepack.quality.SelfValidationCaseResult;
import com.alethicode.service.languagepack.quality.SelfValidationReport;
import com.alethicode.service.languagepack.quality.TitleDedupV2Service;
import com.alethicode.service.languagepack.quality.TitleDedupV2Service.DedupAction;
import com.alethicode.service.languagepack.quality.TitleDedupV2Service.DedupCandidate;
import com.alethicode.service.languagepack.quality.TitleDedupV2Service.DedupResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProblemValidationServiceImpl implements ProblemValidationService {

    private static final Logger log = LoggerFactory.getLogger(ProblemValidationServiceImpl.class);
    private static final int MIN_TEST_CASES = 3;
    private static final int MAX_TEST_CASES = 5;
    private static final List<String> GENERIC_TITLES = List.of("练习", "示例", "作业", "代码示例", "例题", "编程题");

    private final JdbcTemplate jdbcTemplate;
    private final LanguagePackInitService initService;
    private final LanguagePackInitExecutionService executionService;
    private final ProblemGenerationService problemGenerationService;
    private final ObjectMapper objectMapper;
    private final ProblemJudgeMaterializationHelper materializationHelper;
    private final ReferenceSolutionLinter referenceSolutionLinter;
    private final ReferenceSolutionSelfValidator referenceSolutionSelfValidator;
    private final SamplesSynchronizer samplesSynchronizer;
    private final TitleDedupV2Service titleDedupV2Service;
    private final LanguagePackInitQualityReportService qualityReportService;

    public ProblemValidationServiceImpl(JdbcTemplate jdbcTemplate,
                                        LanguagePackInitService initService,
                                        LanguagePackInitExecutionService executionService,
                                        ProblemGenerationService problemGenerationService,
                                        ObjectMapper objectMapper,
                                        LanguagePackProblemJudgeCheckService judgeCheckService,
                                        AiModelGateway aiModelGateway,
                                        ReferenceSolutionLinter referenceSolutionLinter,
                                        ReferenceSolutionSelfValidator referenceSolutionSelfValidator,
                                        SamplesSynchronizer samplesSynchronizer,
                                        TitleDedupV2Service titleDedupV2Service,
                                        LanguagePackInitQualityReportService qualityReportService) {
        this.jdbcTemplate = jdbcTemplate;
        this.initService = initService;
        this.executionService = executionService;
        this.problemGenerationService = problemGenerationService;
        this.objectMapper = objectMapper;
        this.materializationHelper = new ProblemJudgeMaterializationHelper(judgeCheckService, aiModelGateway);
        this.referenceSolutionLinter = referenceSolutionLinter;
        this.referenceSolutionSelfValidator = referenceSolutionSelfValidator;
        this.samplesSynchronizer = samplesSynchronizer;
        this.titleDedupV2Service = titleDedupV2Service;
        this.qualityReportService = qualityReportService;
    }

    @Override
    public void validateCandidates(Long taskId) {
        // 题目验证不能包在单个长事务里，否则候选题行更新会借由外键锁住父任务，
        // 与 REQUIRES_NEW 的执行进度上报形成事务自锁，页面会卡在 0/N。
        String currentStage = jdbcTemplate.queryForObject(
                "SELECT stage FROM language_pack_init_task WHERE id = ?",
                String.class,
                taskId
        );
        if (currentStage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Init task not found");
        }
        if (!"problem_packages_ready".equals(currentStage) && !"problems_validated".equals(currentStage)) {
            throw new BadRequestException("Cannot validate problems in stage: " + currentStage);
        }

        Long languagePackId = jdbcTemplate.queryForObject(
                "SELECT language_pack_id FROM language_pack_init_task WHERE id = ?",
                Long.class,
                taskId
        );
        String primaryLanguage = jdbcTemplate.queryForObject(
                "SELECT primary_language FROM language_pack WHERE id = ?",
                String.class,
                languagePackId
        );

        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
                """
                SELECT id, candidate_title, candidate_body, candidate_input_description,
                       candidate_output_description, candidate_samples_json,
                       reference_solution, test_cases_json,
                       teaching_explanation, common_mistakes_json, source_pages_json, related_kc_ids_json,
                       source_example_ids_json, problem_package_json, validation_status, source_signature
                FROM language_pack_problem_generation_log
                WHERE init_task_id = ?
                ORDER BY id
                """,
                taskId
        );
        if (candidates.isEmpty()) {
            initService.failTask(taskId, "No generated problem packages to validate");
            throw new BusinessException(ErrorCode.ERROR, "No generated problem packages to validate");
        }
        executionService.beginStep(taskId, "problems_validated", "开始验证题目，共 " + candidates.size() + " 个题包", 0, candidates.size());

        Set<Long> validKcIds = jdbcTemplate.queryForList(
                "SELECT id FROM language_pack_kc WHERE language_pack_id = ?",
                Long.class,
                languagePackId
        ).stream().collect(Collectors.toSet());
        Set<Integer> validPageNos = jdbcTemplate.queryForList(
                "SELECT DISTINCT page_no FROM language_pack_page WHERE language_pack_id = ?",
                Integer.class,
                languagePackId
        ).stream().collect(Collectors.toSet());
        Set<Long> validExampleIds = jdbcTemplate.queryForList(
                "SELECT id FROM language_pack_example WHERE language_pack_id = ?",
                Long.class,
                languagePackId
        ).stream().collect(Collectors.toSet());
        Map<Long, String> sourceTitleByExampleId = jdbcTemplate.queryForList(
                "SELECT id, source_title, source_signature, page_range_start, page_range_end FROM language_pack_example WHERE language_pack_id = ?",
                languagePackId
        ).stream().collect(Collectors.toMap(
                row -> ((Number) row.get("id")).longValue(),
                row -> String.valueOf(row.get("source_title")),
                (left, right) -> left
        ));
        Map<Long, Map<String, Object>> sourceExampleById = jdbcTemplate.queryForList(
                "SELECT id, source_title, source_signature, page_range_start, page_range_end FROM language_pack_example WHERE language_pack_id = ?",
                languagePackId
        ).stream().collect(Collectors.toMap(
                row -> ((Number) row.get("id")).longValue(),
                row -> row,
                (left, right) -> left
        ));
        Set<Long> validCanonicalKcIds = loadCanonicalKcIds(taskId);
        Map<String, Map<String, Object>> reviewedCandidateBySignature = loadReviewedCandidatesBySignature(taskId);
        Instant qualityStart = Instant.now();
        LanguagePackInitQualityReportService.Aggregator aggregator =
                new LanguagePackInitQualityReportService.Aggregator();
        Map<Long, LanguagePackProblemPackage> passedPackagesByCandidateId = new LinkedHashMap<>();
        try {
            int passedCount = 0;
            int failedCount = 0;
            int totalCandidates = candidates.size();
            for (int index = 0; index < totalCandidates; index++) {
                Map<String, Object> candidate = candidates.get(index);
                Long candidateId = ((Number) candidate.get("id")).longValue();
                jdbcTemplate.update(
                        "UPDATE language_pack_problem_generation_log SET validation_status = 'validating' WHERE id = ?",
                        candidateId
                );
                aggregator.incrementTotal();

                Map<String, Object> unitContext = reviewedCandidateBySignature.getOrDefault(
                        stringVal(candidate.get("source_signature")),
                        Map.of()
                );
                Integer expectedChapterIndex = intVal(unitContext.get("chapter_index"));
                List<Long> defaultRelatedKcIds = parseLongList(unitContext.get("kc_ids"));
                LanguagePackProblemPackage problemPackage = loadProblemPackage(
                        candidate,
                        languagePackId,
                        primaryLanguage,
                        unitContext,
                        defaultRelatedKcIds
                );
                List<String> errors = validateProblemPackage(
                        problemPackage,
                        primaryLanguage,
                        languagePackId,
                        validKcIds,
                        validCanonicalKcIds,
                        validPageNos,
                        validExampleIds,
                        sourceTitleByExampleId,
                        sourceExampleById,
                        stringVal(candidate.get("source_signature")),
                        expectedChapterIndex
                );
                if (!errors.isEmpty() && !unitContext.isEmpty()) {
                    aggregator.recordRetry();
                    try {
                        problemPackage = problemGenerationService.regenerateCandidateProblem(
                                taskId,
                                stringVal(candidate.get("source_signature"))
                        );
                        problemPackage = canonicalizeProblemPackage(
                                problemPackage,
                                languagePackId,
                                primaryLanguage,
                                unitContext,
                                defaultRelatedKcIds
                        );
                        errors = validateProblemPackage(
                                problemPackage,
                                primaryLanguage,
                                languagePackId,
                                validKcIds,
                                validCanonicalKcIds,
                                validPageNos,
                                validExampleIds,
                                sourceTitleByExampleId,
                                sourceExampleById,
                                stringVal(candidate.get("source_signature")),
                                expectedChapterIndex
                        );
                    } catch (Exception exception) {
                        errors = new ArrayList<>(errors);
                        errors.add("Retry regeneration failed: " + exception.getMessage());
                    }
                }

                if (errors.isEmpty()) {
                    problemPackage = judgeRecheckAndRepair(problemPackage, primaryLanguage, errors);
                }

                SelfValidationReport selfValidationReport = null;
                if (errors.isEmpty()) {
                    selfValidationReport = runSelfValidationGate(problemPackage, errors, aggregator);
                    if (errors.isEmpty() && selfValidationReport != null && selfValidationReport.allPassed()) {
                        problemPackage = samplesSynchronizer.synchronize(problemPackage, selfValidationReport);
                    }
                }

                if (errors.isEmpty()) {
                    persistValidationResult(candidateId, problemPackage, "passed", "Problem package validation passed");
                    passedCount++;
                    aggregator.recordSelfValidated();
                    passedPackagesByCandidateId.put(candidateId, problemPackage);
                } else {
                    String message = String.join("; ", errors);
                    persistValidationResult(candidateId, problemPackage, "failed", message);
                    failedCount++;
                    aggregator.recordEscalation(buildEscalationDetail(candidateId, problemPackage, errors, selfValidationReport));
                    log.debug("Problem package {} failed validation: {}", candidateId, message);
                }
                executionService.reportProgress(
                        taskId,
                        "problems_validated",
                        "题目验证进度 " + (index + 1) + "/" + totalCandidates,
                        index + 1,
                        totalCandidates
                );
            }

            applyTitleDedupV2(passedPackagesByCandidateId);

            qualityReportService.upsert(aggregator.toRecord(
                    taskId,
                    languagePackId,
                    Duration.between(qualityStart, Instant.now())
            ));

            Integer unmaterializedCount = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*)
                    FROM language_pack_problem_generation_log
                    WHERE init_task_id = ?
                      AND validation_status = 'passed'
                      AND materialized_at IS NULL
                    """,
                    Integer.class, taskId
            );
            if (unmaterializedCount != null && unmaterializedCount > 0) {
                String message = "未物化的题包: " + unmaterializedCount + " 个，无法 advance 到 problems_validated"
                        + "（旧任务请从 problem_packages_ready 重跑）";
                log.error("Materialize gate failed for task {}: {}", taskId, message);
                initService.failTask(taskId, message);
                throw new BusinessException(ErrorCode.ERROR, message);
            }

            if ("problem_packages_ready".equals(currentStage)) {
                initService.advanceStage(taskId, "problems_validated");
            }
            executionService.finishStep(
                    taskId,
                    "problems_validated",
                    "题目验证完成，通过 " + passedCount + " 个，失败 " + failedCount + " 个"
            );
            log.info("Problem package validation completed for task {}: {} passed, {} failed", taskId, passedCount, failedCount);
        } catch (BusinessException exception) {
            initService.failTask(taskId, "Problem validation failed: " + exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            initService.failTask(taskId, "Problem validation failed: " + exception.getMessage());
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Problem validation failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private LanguagePackProblemPackage loadProblemPackage(Map<String, Object> candidate,
                                                          Long languagePackId,
                                                          String primaryLanguage,
                                                          Map<String, Object> unitContext,
                                                          List<Long> defaultRelatedKcIds) {
        String problemPackageJson = stringVal(candidate.get("problem_package_json"));
        LanguagePackProblemPackage problemPackage;
        if (LanguagePackProblemPackageMapper.hasMeaningfulStoredJson(problemPackageJson)) {
            problemPackage = LanguagePackProblemPackageMapper.fromStoredJson(objectMapper, problemPackageJson);
        } else {
            problemPackage = LanguagePackProblemPackageMapper.fromLegacyRow(objectMapper, candidate, languagePackId, primaryLanguage);
        }
        return canonicalizeProblemPackage(problemPackage, languagePackId, primaryLanguage, unitContext, defaultRelatedKcIds);
    }

    private LanguagePackProblemPackage canonicalizeProblemPackage(LanguagePackProblemPackage problemPackage,
                                                                  Long languagePackId,
                                                                  String primaryLanguage,
                                                                  Map<String, Object> unitContext,
                                                                  List<Long> defaultRelatedKcIds) {
        if (problemPackage == null || unitContext == null || unitContext.isEmpty()) {
            return problemPackage;
        }
        return LanguagePackProblemPackageMapper.canonicalizeStoredPackage(
                objectMapper,
                problemPackage,
                unitContext,
                defaultRelatedKcIds,
                primaryLanguage,
                languagePackId
        );
    }

    /**
     * 结构校验通过后，用 judge 执行标准答案复验 output。
     * 如果 output 不一致，尝试定向重生失败输入并二次复验。
     * 修改 errors 列表并返回可能更新后的题包。
     */
    private LanguagePackProblemPackage judgeRecheckAndRepair(LanguagePackProblemPackage problemPackage,
                                                             String primaryLanguage,
                                                             List<String> errors) {
        List<String> verifyErrors = materializationHelper.verifyOutputs(problemPackage, primaryLanguage);
        if (verifyErrors.isEmpty()) {
            return problemPackage;
        }

        log.info("Judge recheck found mismatches for '{}': {}", problemPackage.title(), verifyErrors);

        LanguagePackProblemPackage repaired = materializationHelper.attemptRepairForValidation(
                problemPackage, primaryLanguage, verifyErrors
        );
        if (repaired != null) {
            List<String> secondVerify = materializationHelper.verifyOutputs(repaired, primaryLanguage);
            if (secondVerify.isEmpty()) {
                return repaired;
            }
            errors.addAll(secondVerify.stream()
                    .map(e -> "Judge recheck (after repair): " + e)
                    .toList());
            return repaired;
        }

        errors.addAll(verifyErrors.stream()
                .map(e -> "Judge recheck: " + e)
                .toList());
        return problemPackage;
    }

    /**
     * Reference Solution self-validation 闸门：
     * lint 硬规则 + reference × test_cases × samples 全部 AC，否则把失败原因加入 errors
     * 并按根因（R1/R2/R3/R7 等）累加到 quality 报告。
     * 返回 self-validation 报告（无论通过与否），供 samples 同步与 escalation 详情使用。
     */
    private SelfValidationReport runSelfValidationGate(LanguagePackProblemPackage problemPackage,
                                                       List<String> errors,
                                                       LanguagePackInitQualityReportService.Aggregator aggregator) {
        SelfValidationReport report;
        try {
            report = referenceSolutionSelfValidator.validate(problemPackage);
        } catch (Exception exception) {
            errors.add("Self-validation 异常：" + exception.getMessage());
            aggregator.recordFailure("R0_self_validation_exception");
            return null;
        }
        if (report.lintReport() != null) {
            aggregator.recordLintReport(report.lintReport());
        }
        if (report.allPassed()) {
            return report;
        }
        if (report.lintBlocked()) {
            for (LintViolation violation : report.lintReport().hardViolations()) {
                errors.add("Reference lint [" + violation.ruleCode() + "]: " + violation.message());
                aggregator.recordFailure(mapLintRuleToRootCause(violation.ruleCode()));
            }
            return report;
        }
        if (report.compileFailed()) {
            errors.add("Reference solution 编译失败：" + report.compileError().orElse(""));
            aggregator.recordFailure("R1_self_validation");
            return report;
        }
        report.failureSummary().ifPresent(errors::add);
        for (SelfValidationCaseResult caseResult : report.testCaseResults()) {
            if (!caseResult.passed()) {
                aggregator.recordFailure(mapCaseStatusToRootCause(caseResult.status()));
                break;
            }
        }
        return report;
    }

    private String mapLintRuleToRootCause(String ruleCode) {
        return switch (ruleCode) {
            case "REF001" -> "R2_set_order";
            case "REF002" -> "R7_float_precision";
            case "REF003" -> "R8_random_seed";
            case "REF004" -> "R3_input_parsing";
            case "REF007" -> "R3_punctuation";
            default -> "R0_lint";
        };
    }

    private String mapCaseStatusToRootCause(String status) {
        return switch (status) {
            case SelfValidationCaseResult.STATUS_RE,
                 SelfValidationCaseResult.STATUS_TLE,
                 SelfValidationCaseResult.STATUS_OLE -> "R3_runtime";
            case SelfValidationCaseResult.STATUS_WA -> "R1_self_validation";
            default -> "R1_self_validation";
        };
    }

    private Map<String, Object> buildEscalationDetail(Long candidateId,
                                                      LanguagePackProblemPackage problemPackage,
                                                      List<String> errors,
                                                      SelfValidationReport selfValidationReport) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("candidate_id", candidateId);
        detail.put("display_id", problemPackage == null ? "" : stringVal(problemPackage.displayId()));
        detail.put("title", problemPackage == null ? "" : stringVal(problemPackage.title()));
        detail.put("errors", List.copyOf(errors));
        if (selfValidationReport != null) {
            Map<String, Object> selfValidationView = new LinkedHashMap<>();
            selfValidationView.put("all_passed", selfValidationReport.allPassed());
            selfValidationView.put("lint_blocked", selfValidationReport.lintBlocked());
            selfValidationView.put("hard_violation_codes",
                    selfValidationReport.lintReport() == null
                            ? List.of()
                            : selfValidationReport.lintReport().hardViolations().stream()
                                    .map(LintViolation::ruleCode).toList());
            selfValidationView.put("failed_case_keys",
                    selfValidationReport.testCaseResults().stream()
                            .filter(c -> !c.passed())
                            .map(SelfValidationCaseResult::caseKey).toList());
            detail.put("self_validation", selfValidationView);
        }
        return detail;
    }

    /**
     * 在所有 candidate 处理完后跑 V2 dedup：
     * - 双键完全一致 → 后写入的从 passed 状态降级为 failed（dropped_duplicate）
     * - 同 source_title 不同题面 → 自动加 V1/V2 后缀，落库到 candidate_title / problem_package_json
     */
    private void applyTitleDedupV2(Map<Long, LanguagePackProblemPackage> passedPackages) {
        if (passedPackages == null || passedPackages.isEmpty()) {
            return;
        }
        List<DedupCandidate> dedupCandidates = new ArrayList<>();
        List<Long> orderedCandidateIds = new ArrayList<>(passedPackages.keySet());
        for (Long candidateId : orderedCandidateIds) {
            LanguagePackProblemPackage pkg = passedPackages.get(candidateId);
            String sourceSignature = lookupSourceSignature(candidateId);
            int[] pageRange = lookupPageRange(pkg);
            dedupCandidates.add(new DedupCandidate(
                    stringVal(pkg.displayId()),
                    stringVal(pkg.title()),
                    stringVal(pkg.description()),
                    sourceSignature,
                    pageRange[0] == 0 ? null : pageRange[0],
                    pageRange[1] == 0 ? null : pageRange[1]
            ));
        }
        List<DedupResult> dedupResults = titleDedupV2Service.dedup(dedupCandidates);
        for (int i = 0; i < orderedCandidateIds.size() && i < dedupResults.size(); i++) {
            Long candidateId = orderedCandidateIds.get(i);
            DedupResult result = dedupResults.get(i);
            LanguagePackProblemPackage pkg = passedPackages.get(candidateId);
            if (result.action() == DedupAction.DROPPED_DUPLICATE) {
                persistValidationResult(candidateId, pkg, "failed",
                        "Dropped by TitleDedupV2: " + result.reason());
                continue;
            }
            if (result.action() == DedupAction.RENAMED_VARIANT && !result.title().equals(pkg.title())) {
                LanguagePackProblemPackage renamed = renameTitle(pkg, result.title());
                persistValidationResult(candidateId, renamed, "passed",
                        "Problem package validation passed; " + result.reason());
            }
        }
    }

    private String lookupSourceSignature(Long candidateId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT source_signature FROM language_pack_problem_generation_log WHERE id = ?",
                    String.class,
                    candidateId
            );
        } catch (Exception ignored) {
            return "";
        }
    }

    private int[] lookupPageRange(LanguagePackProblemPackage pkg) {
        List<Integer> sourcePages = pkg.sourcePages() == null ? List.of() : pkg.sourcePages();
        if (sourcePages.isEmpty()) {
            return new int[]{0, 0};
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Integer page : sourcePages) {
            if (page == null) {
                continue;
            }
            min = Math.min(min, page);
            max = Math.max(max, page);
        }
        return new int[]{min == Integer.MAX_VALUE ? 0 : min, max == Integer.MIN_VALUE ? 0 : max};
    }

    private LanguagePackProblemPackage renameTitle(LanguagePackProblemPackage pkg, String newTitle) {
        return new LanguagePackProblemPackage(
                pkg.displayId(),
                newTitle,
                pkg.description(),
                pkg.inputDescription(),
                pkg.outputDescription(),
                pkg.samples(),
                pkg.testCases(),
                pkg.template(),
                pkg.timeLimit(),
                pkg.memoryLimit(),
                pkg.difficulty(),
                pkg.sourcePages(),
                pkg.sourceExampleIds(),
                pkg.relatedKcIds(),
                pkg.teachingExplanation(),
                pkg.commonMistakes(),
                pkg.languagePackId(),
                pkg.referenceSolutionLanguage(),
                pkg.referenceSolutionCode()
        );
    }

    private void persistValidationResult(Long candidateId,
                                         LanguagePackProblemPackage problemPackage,
                                         String validationStatus,
                                         String validationMessage) {
        Long primaryKcId = firstLong(problemPackage.relatedKcIds());
        Long primaryExampleId = firstLong(problemPackage.sourceExampleIds());
        jdbcTemplate.update(
                """
                UPDATE language_pack_problem_generation_log
                SET kc_id = ?,
                    example_id = ?,
                    candidate_title = ?,
                    candidate_body = ?,
                    candidate_input_description = ?,
                    candidate_output_description = ?,
                    candidate_samples_json = ?,
                    reference_solution = ?,
                    test_cases_json = ?,
                    teaching_explanation = ?,
                    common_mistakes_json = ?,
                    source_pages_json = ?,
                    related_kc_ids_json = ?,
                    source_example_ids_json = ?,
                    problem_package_json = ?,
                    validation_status = ?,
                    validation_message = ?
                WHERE id = ?
                """,
                primaryKcId,
                primaryExampleId,
                problemPackage.title(),
                problemPackage.description(),
                problemPackage.inputDescription(),
                problemPackage.outputDescription(),
                writeJson(problemPackage.samples()),
                problemPackage.referenceSolutionCode(),
                writeJson(problemPackage.testCases()),
                problemPackage.teachingExplanation(),
                writeJson(problemPackage.commonMistakes()),
                writeJson(problemPackage.sourcePages()),
                writeJson(problemPackage.relatedKcIds()),
                writeJson(problemPackage.sourceExampleIds()),
                writeJson(problemPackage),
                validationStatus,
                validationMessage,
                candidateId
        );
    }

    private Map<String, Map<String, Object>> loadReviewedCandidatesBySignature(Long taskId) {
        String contentJson = jdbcTemplate.query(
                """
                SELECT content_json
                FROM language_pack_init_artifact
                WHERE task_id = ? AND artifact_type = 'oj_candidates.json'
                ORDER BY id DESC
                LIMIT 1
                """,
                rs -> rs.next() ? rs.getString(1) : null,
                taskId
        );
        if (contentJson == null || contentJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(contentJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            Object rows = root.get("oj_candidates");
            if (!(rows instanceof List<?> rawList)) {
                return Map.of();
            }
            Map<String, Map<String, Object>> indexed = new java.util.LinkedHashMap<>();
            for (Object item : rawList) {
                if (!(item instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Object> normalized = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                indexed.put(stringVal(normalized.get("source_signature")), normalized);
            }
            return indexed;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("oj_candidates.json artifact is invalid JSON", exception);
        }
    }

    private List<String> validateProblemPackage(LanguagePackProblemPackage problemPackage,
                                                String primaryLanguage,
                                                Long languagePackId,
                                                Set<Long> validKcIds,
                                                Set<Long> validCanonicalKcIds,
                                                Set<Integer> validPageNos,
                                                Set<Long> validExampleIds,
                                                Map<Long, String> sourceTitleByExampleId,
                                                Map<Long, Map<String, Object>> sourceExampleById,
                                                String candidateSourceSignature,
                                                Integer expectedChapterIndex) {
        List<String> errors = new ArrayList<>();
        if (problemPackage == null) {
            errors.add("Missing problem package");
            return errors;
        }

        String displayId = stringVal(problemPackage.displayId());
        if (isBlank(displayId)) {
            errors.add("Missing display_id");
        } else if (!LanguagePackDisplayIdPolicy.isValid(displayId)) {
            errors.add("display_id must match ^PPT\\d+-\\d+$");
        } else if (expectedChapterIndex != null && expectedChapterIndex > 0) {
            Integer displayChapterIndex = LanguagePackDisplayIdPolicy.parseChapterIndex(displayId);
            if (displayChapterIndex == null || !expectedChapterIndex.equals(displayChapterIndex)) {
                errors.add("display_id chapter prefix mismatch: expected PPT" + expectedChapterIndex + "-*, got " + displayId);
            }
        }

        if (isBlank(problemPackage.title())) {
            errors.add("Missing title");
        }
        if (isBlank(problemPackage.description())) {
            errors.add("Missing description");
        }
        if (isBlank(problemPackage.inputDescription())) {
            errors.add("Missing input_description");
        } else if (declaresNoInput(problemPackage.inputDescription())) {
            errors.add("Output-only problems must be parameterized to stdin/stdout");
        }
        if (isBlank(problemPackage.outputDescription())) {
            errors.add("Missing output_description");
        }
        if (problemPackage.template() == null || problemPackage.template().isEmpty()) {
            errors.add("Missing template");
        } else if (problemPackage.template().values().stream().anyMatch(this::isBlank)) {
            errors.add("Template contains blank body");
        }

        if (problemPackage.languagePackId() != null && !languagePackId.equals(problemPackage.languagePackId())) {
            errors.add("language_pack_id mismatch");
        }

        if (isBlank(problemPackage.referenceSolutionCode())) {
            errors.add("Missing reference_solution_code");
        } else if (!containsIoPattern(problemPackage.referenceSolutionCode(), primaryLanguage)) {
            errors.add("Reference solution does not appear to use stdin/stdout");
        }

        if (isBlank(problemPackage.teachingExplanation())) {
            errors.add("Missing teaching_explanation");
        }
        if (problemPackage.commonMistakes() == null || problemPackage.commonMistakes().isEmpty()) {
            errors.add("Missing common_mistakes");
        }

        List<Integer> sourcePages = problemPackage.sourcePages() == null ? List.of() : problemPackage.sourcePages();
        if (sourcePages.isEmpty()) {
            errors.add("Missing source_pages");
        } else {
            for (Integer pageNo : sourcePages) {
                if (pageNo == null || !validPageNos.contains(pageNo)) {
                    errors.add("source_pages contains out-of-pack page: " + pageNo);
                }
            }
        }

        List<Long> sourceExampleIds = problemPackage.sourceExampleIds() == null ? List.of() : problemPackage.sourceExampleIds();
        if (sourceExampleIds.isEmpty()) {
            errors.add("Missing source_example_ids");
        } else {
            for (Long exampleId : sourceExampleIds) {
                if (exampleId == null || !validExampleIds.contains(exampleId)) {
                    errors.add("source_example_ids contains invalid example_id: " + exampleId);
                    continue;
                }
                Map<String, Object> sourceExample = sourceExampleById.get(exampleId);
                if (sourceExample == null) {
                    continue;
                }
                String expectedSourceSignature = stringVal(sourceExample.get("source_signature"));
                if (!isBlank(candidateSourceSignature) && !candidateSourceSignature.equals(expectedSourceSignature)) {
                    errors.add("source_signature does not align with source example: " + exampleId);
                }
                int startPage = sourceExample.get("page_range_start") instanceof Number number ? number.intValue() : 0;
                int endPage = sourceExample.get("page_range_end") instanceof Number number ? number.intValue() : startPage;
                for (Integer pageNo : sourcePages) {
                    if (pageNo != null && (pageNo < startPage || pageNo > endPage)) {
                        errors.add("source_pages does not align with source_signature pages: " + pageNo);
                    }
                }
            }
        }
        if (isGenericTitle(problemPackage.title()) && !alignsWithAnySourceTitle(problemPackage.title(), sourceExampleIds, sourceTitleByExampleId)) {
            errors.add("Problem title is too generic and does not align with source_title");
        }

        List<Long> relatedKcIds = problemPackage.relatedKcIds() == null ? List.of() : problemPackage.relatedKcIds();
        if (relatedKcIds.isEmpty()) {
            errors.add("Missing related_kc_ids");
        } else {
            for (Long kcId : relatedKcIds) {
                if (kcId == null || !validKcIds.contains(kcId)) {
                    errors.add("related_kc_ids contains invalid kc_id: " + kcId);
                } else if (!validCanonicalKcIds.isEmpty() && !validCanonicalKcIds.contains(kcId)) {
                    errors.add("related_kc_ids contains non-canonical kc_id: " + kcId);
                }
            }
        }

        List<LanguagePackProblemPackage.TestCase> testCases = problemPackage.testCases() == null
                ? List.of()
                : problemPackage.testCases();
        if (testCases.size() < MIN_TEST_CASES) {
            errors.add("Fewer than " + MIN_TEST_CASES + " test cases (got " + testCases.size() + ")");
        }
        if (testCases.size() > MAX_TEST_CASES) {
            errors.add("More than " + MAX_TEST_CASES + " test cases (got " + testCases.size() + ")");
        }
        for (int i = 0; i < testCases.size(); i++) {
            LanguagePackProblemPackage.TestCase testCase = testCases.get(i);
            if (testCase == null || isBlank(testCase.input()) || isBlank(testCase.output())) {
                errors.add("Test case " + (i + 1) + " missing input or output");
            }
        }
        if (!testCases.isEmpty() && testCases.stream().allMatch(testCase -> testCase != null && isBlank(testCase.input()))) {
            errors.add("Output-only problems must be parameterized to stdin/stdout");
        }

        List<LanguagePackProblemPackage.Sample> samples = problemPackage.samples() == null
                ? List.of()
                : problemPackage.samples();
        if (samples.isEmpty()) {
            errors.add("Missing samples");
        }
        if (!samples.isEmpty() && !testCases.isEmpty()) {
            LanguagePackProblemPackage.Sample firstSample = samples.getFirst();
            LanguagePackProblemPackage.TestCase firstTestCase = testCases.getFirst();
            boolean sameInput = String.valueOf(firstSample.input()).equals(String.valueOf(firstTestCase.input()));
            boolean sameOutput = String.valueOf(firstSample.output()).equals(String.valueOf(firstTestCase.output()));
            if (!sameInput || !sameOutput) {
                errors.add("First sample does not match first test case");
            }
        }

        return errors;
    }

    private boolean containsIoPattern(String solution, String language) {
        String lower = solution.toLowerCase();
        return switch (language.toLowerCase()) {
            case "python3", "python" -> containsAny(lower, "input(", "sys.stdin", "stdin.readline", ".readline(")
                    && containsAny(lower, "print(", "sys.stdout", "stdout.write");
            case "java" -> containsAny(lower, "scanner", "bufferedreader", "system.in", "readline(")
                    && containsAny(lower, "system.out", "print(", "println(");
            case "c", "c++" -> containsAny(lower, "scanf", "cin", "getline", "fgets")
                    && containsAny(lower, "printf", "cout", "puts");
            default -> true;
        };
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("json serialize failed", exception);
        }
    }

    private String stringVal(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).strip();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean declaresNoInput(String inputDescription) {
        String normalizedInputDescription = stringVal(inputDescription)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
        return normalizedInputDescription.contains("无输入")
                || normalizedInputDescription.contains("无显式输入")
                || normalizedInputDescription.contains("无需输入")
                || normalizedInputDescription.contains("不需要输入")
                || normalizedInputDescription.contains("无外部输入")
                || normalizedInputDescription.contains("没有输入")
                || normalizedInputDescription.contains("noinput");
    }

    private boolean containsAny(String text, String... patterns) {
        for (String pattern : patterns) {
            if (text.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private List<Long> parseLongList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                result.add(number.longValue());
            }
        }
        return result;
    }

    private boolean isGenericTitle(String title) {
        String normalized = normalizeKey(title);
        if (normalized.isBlank()) {
            return true;
        }
        for (String genericTitle : GENERIC_TITLES) {
            String normalizedGeneric = normalizeKey(genericTitle);
            if (normalized.equals(normalizedGeneric)) {
                return true;
            }
            if (normalized.endsWith(normalizedGeneric) && normalized.length() <= normalizedGeneric.length() + 4) {
                return true;
            }
        }
        return false;
    }

    private boolean alignsWithAnySourceTitle(String problemTitle,
                                             List<Long> sourceExampleIds,
                                             Map<Long, String> sourceTitleByExampleId) {
        String normalizedProblemTitle = normalizeKey(problemTitle);
        for (Long exampleId : sourceExampleIds) {
            String sourceTitle = sourceTitleByExampleId.get(exampleId);
            String normalizedSourceTitle = normalizeKey(sourceTitle);
            if (!normalizedSourceTitle.isBlank()
                    && (normalizedProblemTitle.contains(normalizedSourceTitle)
                    || normalizedSourceTitle.contains(normalizedProblemTitle))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}：，。、“”‘’（）()\\[\\]【】《》<>\\s]+", "")
                .strip();
    }

    private Integer intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Set<Long> loadCanonicalKcIds(Long taskId) {
        String raw = jdbcTemplate.query(
                """
                SELECT content_json
                FROM language_pack_init_artifact
                WHERE task_id = ? AND artifact_type = 'kc_catalog.json'
                ORDER BY id DESC
                LIMIT 1
                """,
                rs -> rs.next() ? rs.getString(1) : null,
                taskId
        );
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            Object rows = root.get("kcs");
            if (!(rows instanceof List<?> list)) {
                return Set.of();
            }
            return list.stream()
                    .map(item -> item instanceof Map<?, ?> rawMap ? rawMap.get("canonical_kc_id") : null)
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .map(Number::longValue)
                    .collect(Collectors.toSet());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("kc_catalog.json artifact is invalid", exception);
        }
    }

    private Long firstLong(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }
}
