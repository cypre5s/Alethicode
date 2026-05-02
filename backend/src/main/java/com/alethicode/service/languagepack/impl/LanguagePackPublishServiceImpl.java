package com.alethicode.service.languagepack.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.exception.BadRequestException;
import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.languagepack.LanguagePackInitExecutionService;
import com.alethicode.service.languagepack.LanguagePackInitService;
import com.alethicode.service.languagepack.LanguagePackProblemPackage;
import com.alethicode.service.languagepack.LanguagePackPublishService;
import com.alethicode.service.languagepack.ProblemPackageWriteOptions;
import com.alethicode.service.languagepack.ProblemPackageWriteResult;
import com.alethicode.service.languagepack.ProblemPackageWriteService;
import com.alethicode.service.rag.RagHealthCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@Transactional(rollbackFor = Exception.class)
public class LanguagePackPublishServiceImpl implements LanguagePackPublishService {

    private static final Logger log = LoggerFactory.getLogger(LanguagePackPublishServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;
    private final LanguagePackInitService initService;
    private final LanguagePackInitExecutionService executionService;
    private final ObjectMapper objectMapper;
    private final ProblemPackageWriteService problemPackageWriteService;
    private final AlethicodeProperties alethicodeProperties;
    private final RagHealthCheckService ragHealthCheckService;

    public LanguagePackPublishServiceImpl(JdbcTemplate jdbcTemplate,
                                          LanguagePackInitService initService,
                                          LanguagePackInitExecutionService executionService,
                                          ObjectMapper objectMapper,
                                          ProblemPackageWriteService problemPackageWriteService,
                                          AlethicodeProperties alethicodeProperties,
                                          RagHealthCheckService ragHealthCheckService) {
        this.jdbcTemplate = jdbcTemplate;
        this.initService = initService;
        this.executionService = executionService;
        this.objectMapper = objectMapper;
        this.problemPackageWriteService = problemPackageWriteService;
        this.alethicodeProperties = alethicodeProperties;
        this.ragHealthCheckService = ragHealthCheckService;
    }

    @Override
    public void publishPack(Long taskId) {
        String currentStage = jdbcTemplate.queryForObject(
                "SELECT stage FROM language_pack_init_task WHERE id = ?",
                String.class,
                taskId
        );
        if (currentStage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Init task not found");
        }
        if (!"problems_validated".equals(currentStage)) {
            throw new BadRequestException("Cannot publish in stage: " + currentStage);
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
        String packSlug = jdbcTemplate.queryForObject(
                "SELECT slug FROM language_pack WHERE id = ?",
                String.class,
                languagePackId
        );
        RagHealthCheckService.ReadinessResult readiness =
                ragHealthCheckService.assertReadyForPublish(languagePackId);
        if (!readiness.ready()) {
            String message = "RAG_NOT_READY: " + String.join("; ", readiness.blockers());
            log.warn("publishPack blocked taskId={} packId={}: {}", taskId, languagePackId, message);
            throw new BadRequestException(message);
        }

        enforceCoverageGate(taskId, packSlug);

        List<Map<String, Object>> passedCandidates = jdbcTemplate.queryForList(
                """
                SELECT id, candidate_title, candidate_body, candidate_input_description,
                       candidate_output_description, candidate_samples_json,
                       reference_solution, test_cases_json,
                       teaching_explanation, common_mistakes_json, source_pages_json, related_kc_ids_json,
                       source_example_ids_json, problem_package_json
                FROM language_pack_problem_generation_log
                WHERE init_task_id = ? AND validation_status = 'passed'
                ORDER BY id
                """,
                taskId
        );
        if (passedCandidates.isEmpty()) {
            initService.failTask(taskId, "No validated problem packages to publish");
            throw new BusinessException(ErrorCode.ERROR, "No validated problem packages to publish");
        }
        executionService.beginStep(taskId, "published", "开始发布课程内容包，共 " + passedCandidates.size() + " 个题包", 0, passedCandidates.size());

        Map<Long, Long> kcSyncMap = syncKnowledgeComponents(languagePackId);
        try {
            int publishedCount = 0;
            int totalCandidates = passedCandidates.size();
            for (int index = 0; index < totalCandidates; index++) {
                Map<String, Object> candidate = passedCandidates.get(index);
                Long logId = ((Number) candidate.get("id")).longValue();
                Integer existingMapping = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM language_pack_problem_mapping WHERE generation_log_id = ?",
                        Integer.class, logId);
                if (existingMapping != null && existingMapping > 0) {
                    publishedCount++;
                    executionService.reportProgress(taskId, "published", "发布进度 " + (index + 1) + "/" + totalCandidates + "（复用已发布题目）", index + 1, totalCandidates);
                    continue;
                }
                try {
                    LanguagePackProblemPackage problemPackage = loadProblemPackage(candidate, languagePackId, primaryLanguage);
                    enforceDeterministicDisplayId(problemPackage, logId);
                    ProblemPackageWriteResult writeResult = problemPackageWriteService.writeProblem(
                            problemPackage,
                            new ProblemPackageWriteOptions(
                                    null,
                                    true,
                                    true,
                                    true,
                                    false,
                                    "class_private",
                                    "Language Pack: " + packSlug,
                                    buildStatisticInfo(problemPackage, languagePackId),
                                    List.of(),
                                    buildProblemTags(problemPackage)
                            )
                    );
                    bindProblemToKcs(writeResult.problemId(), problemPackage.relatedKcIds(), kcSyncMap, languagePackId);
                    jdbcTemplate.update(
                            """
                            INSERT INTO language_pack_problem_mapping(language_pack_id, problem_id, generation_log_id, create_time)
                            VALUES (?, ?, ?, now())
                            """,
                            languagePackId,
                            writeResult.problemId(),
                            logId
                    );
                    publishedCount++;
                } catch (Exception exception) {
                    log.error("Failed to publish problem package {}: {}", logId, exception.getMessage());
                    jdbcTemplate.update(
                            "UPDATE language_pack_problem_generation_log SET validation_status = 'failed', validation_message = ? WHERE id = ?",
                            "Publish failed: " + exception.getMessage(),
                            logId
                    );
                }
                executionService.reportProgress(taskId, "published", "发布进度 " + (index + 1) + "/" + totalCandidates, index + 1, totalCandidates);
            }

            if (publishedCount == 0) {
                initService.failTask(taskId, "All validated problem packages failed during publishing");
                throw new BusinessException(ErrorCode.ERROR, "All validated problem packages failed during publishing");
            }

            jdbcTemplate.update(
                    "UPDATE language_pack SET problem_count = ?, status = 'published', update_time = now() WHERE id = ?",
                    publishedCount,
                    languagePackId
            );
            initService.advanceStage(taskId, "published");
            executionService.finishStep(taskId, "published", "课程内容包发布完成，共 " + publishedCount + " 道题目");
            log.info("Published {} problems for task {} (language pack {})", publishedCount, taskId, languagePackId);
        } catch (BusinessException exception) {
            initService.failTask(taskId, "Publish failed: " + exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            initService.failTask(taskId, "Publish failed: " + exception.getMessage());
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Publish failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private void enforceDeterministicDisplayId(LanguagePackProblemPackage problemPackage, Long generationLogId) {
        String displayId = problemPackage == null ? null : stringVal(problemPackage.displayId());
        if (displayId == null || displayId.isBlank()) {
            throw new IllegalStateException("generation_log_id=" + generationLogId + " missing display_id");
        }
        if (!LanguagePackDisplayIdPolicy.isValid(displayId)) {
            throw new IllegalStateException(
                    "generation_log_id=" + generationLogId + " invalid display_id: " + displayId + ", expected ^PPT\\d+-\\d+$"
            );
        }
    }

    private void enforceCoverageGate(Long taskId, String packSlug) {
        if (alethicodeProperties.getLanguagePack().getPublish().isSkipCoverageGate()) {
            return;
        }
        Map<String, Object> report = loadCoverageReport(taskId);
        List<?> missing = listVal(report.get("missing"));
        List<?> highRisk = resolveHighRiskChapters(report);
        List<?> unresolved = listVal(report.get("unresolved_review_required"));
        int baselineProblemCount = intVal(report.get("baseline_problem_count"));
        int generatedProblemCount = intVal(report.get("generated_problem_count"));
        int finalOjCandidateCount = intVal(report.get("final_oj_candidate_count"));
        boolean baselineEnabled = baselineProblemCount > 0;
        String failureReason = null;
        if (baselineEnabled) {
            if (!highRisk.isEmpty()) {
                failureReason = "Coverage gate blocked publish for " + packSlug + ": high-risk chapters remain";
            } else if (generatedProblemCount != finalOjCandidateCount) {
                failureReason = "Coverage gate blocked publish for " + packSlug + ": generated_problem_count does not match final_oj_candidate_count";
            }
        } else if (!highRisk.isEmpty()) {
            failureReason = "Coverage gate blocked publish for " + packSlug + ": high-risk chapters remain";
        } else if (!unresolved.isEmpty()) {
            failureReason = "Coverage gate blocked publish for " + packSlug + ": unresolved review-required candidates remain";
        }

        if (failureReason == null) {
            return;
        }
        initService.failTask(taskId, failureReason);
        throw new BusinessException(ErrorCode.ERROR, failureReason);
    }

    private List<Map<String, Object>> resolveHighRiskChapters(Map<String, Object> report) {
        List<Map<String, Object>> chapterStats = mapListVal(report.get("chapter_stats"));
        if (chapterStats.isEmpty()) {
            return mapListVal(report.get("high_risk_chapters"));
        }
        boolean baselineEnabled = intVal(report.get("baseline_problem_count")) > 0;
        return chapterStats.stream()
                .filter(stat -> isHighRiskChapter(stat, baselineEnabled))
                .toList();
    }

    private boolean isHighRiskChapter(Map<String, Object> stat, boolean baselineEnabled) {
        if (intVal(stat.get("chapter_page_count")) < 8) {
            return false;
        }
        if (intVal(stat.get("oj_candidate_count")) > 0) {
            return false;
        }
        if (!booleanVal(stat.get("chapter_has_task_signal"))) {
            return false;
        }
        if (intVal(stat.get("convertible_unit_count")) <= 0) {
            return false;
        }
        if (!baselineEnabled) {
            return true;
        }
        return intVal(stat.get("baseline_expected_count")) > 0;
    }

    private LanguagePackProblemPackage loadProblemPackage(Map<String, Object> candidate,
                                                          Long languagePackId,
                                                          String primaryLanguage) {
        String problemPackageJson = stringVal(candidate.get("problem_package_json"));
        if (LanguagePackProblemPackageMapper.hasMeaningfulStoredJson(problemPackageJson)) {
            return LanguagePackProblemPackageMapper.fromStoredJson(objectMapper, problemPackageJson);
        }
        return LanguagePackProblemPackageMapper.fromLegacyRow(objectMapper, candidate, languagePackId, primaryLanguage);
    }

    private Map<String, Object> buildStatisticInfo(LanguagePackProblemPackage problemPackage, Long languagePackId) {
        Map<String, Object> statisticInfo = new LinkedHashMap<>();
        statisticInfo.put("question_type", "coding");

        Map<String, Object> teaching = new LinkedHashMap<>();
        teaching.put("explanation", blankSafe(problemPackage.teachingExplanation()));
        teaching.put("common_mistakes", problemPackage.commonMistakes() == null ? List.of() : problemPackage.commonMistakes());
        teaching.put("source_pages", problemPackage.sourcePages() == null ? List.of() : problemPackage.sourcePages());
        teaching.put("source_example_ids", problemPackage.sourceExampleIds() == null ? List.of() : problemPackage.sourceExampleIds());
        teaching.put("related_kc_ids", problemPackage.relatedKcIds() == null ? List.of() : problemPackage.relatedKcIds());
        teaching.put("language_pack_id", languagePackId);
        statisticInfo.put("language_pack_teaching", teaching);
        return statisticInfo;
    }

    private List<String> buildProblemTags(LanguagePackProblemPackage problemPackage) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.add("type:coding");
        List<Long> relatedKcIds = problemPackage.relatedKcIds() == null ? List.of() : problemPackage.relatedKcIds();
        for (Long kcId : relatedKcIds) {
            String kcName = jdbcTemplate.query(
                    "SELECT name FROM language_pack_kc WHERE id = ?",
                    rs -> rs.next() ? rs.getString(1) : null,
                    kcId
            );
            if (kcName != null && !kcName.isBlank()) {
                tags.add("kc:" + kcName);
            }
        }
        return List.copyOf(tags);
    }

    private void bindProblemToKcs(Long problemId,
                                  List<Long> relatedKcIds,
                                  Map<Long, Long> kcSyncMap,
                                  Long languagePackId) {
        if (relatedKcIds == null) {
            return;
        }
        for (Long lpKcId : relatedKcIds) {
            if (lpKcId == null) {
                continue;
            }
            Long aiKcId = kcSyncMap.get(lpKcId);
            if (aiKcId == null) {
                continue;
            }
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_problem_kc_mapping(problem_id, kc_id, weight, language_pack_id)
                    VALUES (?, ?, 1.0, ?)
                    ON CONFLICT DO NOTHING
                    """,
                    problemId,
                    aiKcId,
                    languagePackId
            );
            jdbcTemplate.update(
                    "UPDATE ai_problem_kc_mapping SET language_pack_id = ? WHERE problem_id = ? AND kc_id = ?",
                    languagePackId,
                    problemId,
                    aiKcId
            );
        }
    }

    private Map<Long, Long> syncKnowledgeComponents(Long languagePackId) {
        List<Map<String, Object>> lpKcs = jdbcTemplate.queryForList(
                """
                SELECT kc.id, kc.name, kc.name_en, kc.description, kc.synced_ai_kc_id,
                       coalesce(ch.title, '') AS chapter_title
                FROM language_pack_kc kc
                LEFT JOIN language_pack_chapter ch ON ch.id = kc.chapter_id
                WHERE kc.language_pack_id = ?
                ORDER BY kc.id
                """,
                languagePackId
        );

        Map<Long, Long> syncMap = new LinkedHashMap<>();
        for (Map<String, Object> kc : lpKcs) {
            Long lpKcId = ((Number) kc.get("id")).longValue();
            Long existingSyncId = longVal(kc.get("synced_ai_kc_id"));
            String normalizedName = normalizeName((String) kc.get("name"));

            if (existingSyncId != null) {
                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM ai_knowledge_component WHERE id = ? AND language_pack_id = ?",
                        Integer.class,
                        existingSyncId,
                        languagePackId
                );
                if (exists != null && exists > 0) {
                    syncMap.put(lpKcId, existingSyncId);
                    continue;
                }
            }

            String name = (String) kc.get("name");
            String nameEn = (String) kc.get("name_en");
            String description = (String) kc.get("description");
            String chapter = (String) kc.get("chapter_title");

            Long aiKcId = jdbcTemplate.query(
                    """
                    SELECT id
                    FROM ai_knowledge_component
                    WHERE language_pack_id = ? AND name_normalized = ?
                    ORDER BY id ASC
                    LIMIT 1
                    """,
                    rs -> rs.next() ? rs.getLong(1) : null,
                    languagePackId,
                    normalizedName
            );

            if (aiKcId == null) {
                aiKcId = jdbcTemplate.queryForObject(
                        """
                        INSERT INTO ai_knowledge_component(name, name_en, chapter, description, language_pack_id, name_normalized)
                        VALUES (?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                        Long.class,
                        name,
                        nameEn != null ? nameEn : "",
                        chapter != null ? chapter : "",
                        description != null ? description : "",
                        languagePackId,
                        normalizedName
                );
            }

            jdbcTemplate.update(
                    "UPDATE language_pack_kc SET synced_ai_kc_id = ? WHERE id = ?",
                    aiKcId,
                    lpKcId
            );
            syncMap.put(lpKcId, aiKcId);
        }

        log.info("Synced {} KCs from language_pack_kc to ai_knowledge_component for pack {}", syncMap.size(), languagePackId);
        return syncMap;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
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

    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.strip()
                .toLowerCase()
                .replaceAll("[\\s_\\-]+", "_");
    }

    private String blankSafe(String value) {
        return value == null ? "" : value;
    }

    private Map<String, Object> loadCoverageReport(Long taskId) {
        String raw = jdbcTemplate.query(
                "SELECT coverage_report_json FROM language_pack_init_task WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                taskId
        );
        if (raw == null || raw.isBlank() || "{}".equals(raw.strip())) {
            throw new IllegalStateException("coverage_report_json is required before publishing");
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("coverage_report_json is invalid", exception);
        }
    }

    private List<?> listVal(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapListVal(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
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

    private boolean booleanVal(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return "true".equalsIgnoreCase(stringVal(value));
    }

    private String stringVal(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).strip();
    }
}
