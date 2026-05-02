package com.alethicode.service.languagepack.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.exception.BadRequestException;
import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.languagepack.LanguagePackInitAuditService;
import com.alethicode.service.languagepack.LanguagePackInitExecutionService;
import com.alethicode.service.languagepack.LanguagePackInitService;
import com.alethicode.service.languagepack.LanguagePackProblemPackage;
import com.alethicode.service.languagepack.ProblemGenerationService;
import com.alethicode.util.BoundedParallel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProblemGenerationServiceImpl implements ProblemGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ProblemGenerationServiceImpl.class);
    private static final int SCHEMA_MAX_ATTEMPTS = 3;
    private static final int FULL_REGEN_MAX_ATTEMPTS = 2;

    private final JdbcTemplate jdbcTemplate;
    private final LanguagePackInitService initService;
    private final LanguagePackInitExecutionService executionService;
    private final LanguagePackInitAuditService auditService;
    private final LanguagePackInitBatchRunStore batchRunStore;
    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;
    private final int problemGenerateConcurrency;
    private final ProblemJudgeMaterializationHelper materializationHelper;

    public ProblemGenerationServiceImpl(JdbcTemplate jdbcTemplate,
                                        LanguagePackInitService initService,
                                        LanguagePackInitExecutionService executionService,
                                        LanguagePackInitAuditService auditService,
                                        LanguagePackInitBatchRunStore batchRunStore,
                                        AiModelGateway aiModelGateway,
                                        ObjectMapper objectMapper,
                                        AlethicodeProperties properties,
                                        LanguagePackProblemJudgeCheckService judgeCheckService) {
        this.jdbcTemplate = jdbcTemplate;
        this.initService = initService;
        this.executionService = executionService;
        this.auditService = auditService;
        this.batchRunStore = batchRunStore;
        this.aiModelGateway = aiModelGateway;
        this.objectMapper = objectMapper;
        this.problemGenerateConcurrency = properties.getLanguagePack().getConcurrency().getProblemGenerate();
        this.materializationHelper = new ProblemJudgeMaterializationHelper(judgeCheckService, aiModelGateway);
    }

    @Override
    public void generateCandidateProblems(Long taskId) {
        String currentStage = jdbcTemplate.queryForObject(
                "SELECT stage FROM language_pack_init_task WHERE id = ?",
                String.class,
                taskId
        );
        if (currentStage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Init task not found");
        }
        if (!List.of("oj_candidates_ready", "problem_packages_ready", "failed").contains(currentStage)) {
            throw new BadRequestException("Cannot generate problems in stage: " + currentStage);
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
        String languagePackSlug = jdbcTemplate.queryForObject(
                "SELECT slug FROM language_pack WHERE id = ?",
                String.class,
                languagePackId
        );

        List<Map<String, Object>> reviewedCandidates = loadReviewedCandidates(taskId);
        List<Map<String, Object>> units = reviewedCandidates.stream()
                .filter(this::isOjConvertible)
                .toList();
        LanguagePackDisplayIdAllocator.assignDeterministicDisplayIds(units);
        if (units.isEmpty()) {
            initService.failTask(taskId, "No OJ-convertible courseware units available for problem generation");
            throw new BusinessException(ErrorCode.ERROR, "No OJ-convertible courseware units available for problem generation");
        }

        Map<Long, Map<String, Object>> kcContextById = loadKcContextById(languagePackId);
        List<Map<String, Object>> chapterInventory = loadChapterInventory(languagePackId);
        Map<String, Map<String, Object>> chapterMemoryByKey = loadChapterMemoryByKey(taskId);
        Map<String, Map<String, Object>> neighborUnitBySignature = buildNeighborUnitsBySignature(units);
        Map<String, Map<String, Object>> existingCandidateBySignature = loadExistingCandidatesBySignature(taskId);

        Long agentRunId = auditService.startAgentRun(
                taskId,
                "OjProblemPackageAgent",
                "problem_packages_ready",
                "oj-problem-package.v1",
                buildInputFingerprint(units)
        );

        executionService.beginStep(taskId, "problem_packages_ready", "开始生成练习题，共 " + units.size() + " 个单元", 0, units.size());
        java.util.concurrent.atomic.AtomicInteger completedUnits = new java.util.concurrent.atomic.AtomicInteger(0);

        try {
            record GeneratedUnit(Map<String, Object> unit, LanguagePackProblemPackage problemPackage) {}
            int totalUnits = units.size();

            List<ProblemUnitGenerationFailure> failures = java.util.Collections.synchronizedList(new ArrayList<>());
            List<JudgePausedException> paused = java.util.Collections.synchronizedList(new ArrayList<>());

            List<GeneratedUnit> generatedUnits = BoundedParallel.map(
                    units,
                    problemGenerateConcurrency,
                    unit -> {
                        try {
                            GeneratedUnit result = new GeneratedUnit(
                                    unit,
                                    generateProblemPackageForUnit(
                                            taskId, languagePackId, primaryLanguage, unit,
                                            kcContextById, chapterMemoryByKey, neighborUnitBySignature, true
                                    )
                            );
                            int done = completedUnits.incrementAndGet();
                            executionService.reportProgress(taskId, "problem_packages_ready",
                                    "题目生成进度 " + done + "/" + totalUnits, done, totalUnits);
                            return result;
                        } catch (JudgePausedException ex) {
                            paused.add(ex);
                            int done = completedUnits.incrementAndGet();
                            executionService.reportProgress(taskId, "problem_packages_ready",
                                    "判题机离线，单元 " + ex.sourceSignature() + " 暂停", done, totalUnits);
                            return null;
                        } catch (ProblemUnitGenerationFailure ex) {
                            failures.add(ex);
                            int done = completedUnits.incrementAndGet();
                            executionService.reportProgress(taskId, "problem_packages_ready",
                                    "题目生成失败 " + ex.sourceSignature(), done, totalUnits);
                            return null;
                        } catch (Exception ex) {
                            String sig = stringVal(unit.get("source_signature"));
                            failures.add(new ProblemUnitGenerationFailure(sig, ex.getClass().getSimpleName() + ": " + ex.getMessage()));
                            int done = completedUnits.incrementAndGet();
                            executionService.reportProgress(taskId, "problem_packages_ready",
                                    "题目生成异常 " + sig, done, totalUnits);
                            return null;
                        }
                    }
            );

            if (!paused.isEmpty()) {
                String detail = paused.stream()
                        .map(e -> e.sourceSignature() + ": " + e.reason())
                        .collect(java.util.stream.Collectors.joining("\n"));
                auditService.failAgentRun(agentRunId, "judge_paused: " + paused.size() + " units");
                throw new BusinessException(ErrorCode.ERROR,
                        "判题机离线，题目生成暂停: " + paused.size() + "/" + totalUnits + " 单元等重连");
            }

            if (!failures.isEmpty()) {
                String detail = failures.stream()
                        .map(f -> f.sourceSignature() + ": " + f.reason())
                        .collect(java.util.stream.Collectors.joining("\n"));
                auditService.failAgentRun(agentRunId, "unit_generation_failed: " + failures.size() + " units");
                initService.failTask(taskId,
                        "题目生成失败 (" + failures.size() + "/" + totalUnits + " 单元 3 层重试用尽):\n" + detail);
                throw new BusinessException(ErrorCode.ERROR,
                        "题目生成失败: " + failures.size() + "/" + totalUnits + " 单元未通过");
            }

            generatedUnits = generatedUnits.stream().filter(java.util.Objects::nonNull).toList();

            List<Map<String, Object>> artifactRows = new ArrayList<>();
            LinkedHashSet<String> activeSourceSignatures = new LinkedHashSet<>();
            for (GeneratedUnit generated : generatedUnits) {
                Map<String, Object> unit = generated.unit();
                String sourceSignature = stringVal(unit.get("source_signature"));
                activeSourceSignatures.add(sourceSignature);
                upsertCandidate(taskId, languagePackId, unit, generated.problemPackage(), existingCandidateBySignature.get(sourceSignature));
                artifactRows.add(LanguagePackProblemPackageMapper.toArtifactMap(
                        objectMapper,
                        generated.problemPackage(),
                        unit,
                        sourceSignature
                ));
            }

            deleteStaleCandidates(taskId, activeSourceSignatures);

            String problemPackagesJson = writeJson(Map.of("problem_packages", artifactRows));
            String packagesHash = auditService.replaceJsonArtifact(
                    taskId,
                    "problem_packages.json",
                    "problem_packages_ready",
                    problemPackagesJson
            );
            auditService.replaceMarkdownArtifact(
                    taskId,
                    "problem_packages.md",
                    "problem_packages_ready",
                    LanguagePackProblemPackageMapper.renderMarkdown(artifactRows)
            );
            Map<String, Object> coverageReport = LanguagePackCoverageBaselineSupport.buildCoverageReport(
                    objectMapper,
                    languagePackSlug,
                    artifactRows,
                    reviewedCandidates,
                    chapterInventory,
                    List.copyOf(chapterMemoryByKey.values()),
                    countReusedBatchRuns(taskId)
            );
            jdbcTemplate.update(
                    "UPDATE language_pack_init_task SET coverage_report_json = cast(? as jsonb), update_time = now() WHERE id = ?",
                    writeJson(coverageReport),
                    taskId
            );
            auditService.replaceJsonArtifact(
                    taskId,
                    "coverage_report.json",
                    "problem_packages_ready",
                    writeJson(coverageReport)
            );
            auditService.completeAgentRun(agentRunId, packagesHash);
            if ("oj_candidates_ready".equals(currentStage)) {
                initService.advanceStage(taskId, "problem_packages_ready");
            } else if ("failed".equals(currentStage)) {
                initService.restoreStage(taskId, "problem_packages_ready", "阶段已恢复：失败 → 题包就绪");
            }
            executionService.finishStep(taskId, "problem_packages_ready", "练习题生成完成，共 " + generatedUnits.size() + " 个题包");
            log.info("Generated {} problem packages for task {}", generatedUnits.size(), taskId);
        } catch (Exception exception) {
            auditService.failAgentRun(agentRunId, exception.getMessage());
            initService.failTask(taskId, "Problem package generation failed: " + exception.getMessage());
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Problem package generation failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    @Override
    public LanguagePackProblemPackage regenerateCandidateProblem(Long taskId, String sourceSignature) {
        Long languagePackId = jdbcTemplate.queryForObject(
                "SELECT language_pack_id FROM language_pack_init_task WHERE id = ?",
                Long.class,
                taskId
        );
        if (languagePackId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Init task not found");
        }
        String primaryLanguage = jdbcTemplate.queryForObject(
                "SELECT primary_language FROM language_pack WHERE id = ?",
                String.class,
                languagePackId
        );
        List<Map<String, Object>> reviewedCandidates = loadReviewedCandidates(taskId);
        LanguagePackDisplayIdAllocator.assignDeterministicDisplayIds(
                reviewedCandidates.stream().filter(this::isOjConvertible).toList()
        );
        Map<String, Object> unit = reviewedCandidates.stream()
                .filter(row -> sourceSignature.equals(stringVal(row.get("source_signature"))))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("OJ candidate not found for source_signature: " + sourceSignature));
        Map<Long, Map<String, Object>> kcContextById = loadKcContextById(languagePackId);
        Map<String, Map<String, Object>> chapterMemoryByKey = loadChapterMemoryByKey(taskId);
        Map<String, Map<String, Object>> neighborUnitBySignature = buildNeighborUnitsBySignature(reviewedCandidates);
        return generateProblemPackageForUnit(
                taskId,
                languagePackId,
                primaryLanguage,
                unit,
                kcContextById,
                chapterMemoryByKey,
                neighborUnitBySignature,
                false
        );
    }

    private List<Map<String, Object>> loadReviewedCandidates(Long taskId) {
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
            throw new IllegalStateException("oj_candidates.json artifact is required before problem generation");
        }
        try {
            Map<String, Object> root = objectMapper.readValue(contentJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            Object rows = root.get("oj_candidates");
            if (!(rows instanceof List<?> rawList)) {
                throw new IllegalStateException("oj_candidates.json artifact payload is invalid");
            }
            List<Map<String, Object>> candidates = new ArrayList<>();
            for (Object item : rawList) {
                if (!(item instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                candidates.add(normalized);
            }
            return candidates;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("oj_candidates.json artifact is invalid JSON", exception);
        }
    }

    private List<Map<String, Object>> loadChapterInventory(Long languagePackId) {
        return jdbcTemplate.queryForList(
                """
                SELECT chapter_index,
                       title AS chapter_title,
                       GREATEST(0, COALESCE(page_range_end, 0) - COALESCE(page_range_start, 0) + 1) AS chapter_page_count
                FROM language_pack_chapter
                WHERE language_pack_id = ?
                ORDER BY chapter_index
                """,
                languagePackId
        );
    }

    private Map<String, Map<String, Object>> loadChapterMemoryByKey(Long taskId) {
        String contentJson = jdbcTemplate.query(
                """
                SELECT content_json
                FROM language_pack_init_artifact
                WHERE task_id = ? AND artifact_type = 'chapter_memory.json'
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
            Object rows = root.get("chapters");
            if (!(rows instanceof List<?> rawList)) {
                return Map.of();
            }
            Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
            for (Object item : rawList) {
                Map<String, Object> row = castMap(item);
                if (row.isEmpty()) {
                    continue;
                }
                String key = chapterKey(row);
                if (key.isBlank()) {
                    continue;
                }
                indexed.put(key, row);
            }
            return indexed;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("chapter_memory.json artifact is invalid JSON", exception);
        }
    }

    private Map<String, Map<String, Object>> buildNeighborUnitsBySignature(List<Map<String, Object>> units) {
        List<Map<String, Object>> sorted = units.stream()
                .sorted(java.util.Comparator
                        .comparing((Map<String, Object> row) -> intVal(row.get("chapter_index")))
                        .thenComparing(this::firstSourcePage)
                        .thenComparing(row -> stringVal(row.get("source_signature"))))
                .toList();
        Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        for (int index = 0; index < sorted.size(); index++) {
            Map<String, Object> unit = sorted.get(index);
            Map<String, Object> context = new LinkedHashMap<>();
            if (index > 0 && chapterKey(sorted.get(index - 1)).equals(chapterKey(unit))) {
                context.put("previous", sorted.get(index - 1));
            }
            if (index + 1 < sorted.size() && chapterKey(sorted.get(index + 1)).equals(chapterKey(unit))) {
                context.put("next", sorted.get(index + 1));
            }
            indexed.put(stringVal(unit.get("source_signature")), context);
        }
        return indexed;
    }

    private Map<Long, Map<String, Object>> loadKcContextById(Long languagePackId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT k.id, k.name, k.name_en, k.description,
                       coalesce(c.title, '') AS chapter_title,
                       c.chapter_index
                FROM language_pack_kc k
                LEFT JOIN language_pack_chapter c ON c.id = k.chapter_id
                WHERE k.language_pack_id = ?
                ORDER BY c.chapter_index, k.id
                """,
                languagePackId
        );
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long kcId = longVal(row.get("id"));
            if (kcId != null) {
                result.put(kcId, row);
            }
        }
        return result;
    }

    private Map<String, Map<String, Object>> loadExistingCandidatesBySignature(Long taskId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, source_signature, problem_package_json
                FROM language_pack_problem_generation_log
                WHERE init_task_id = ?
                """,
                taskId
        );
        Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            indexed.put(stringVal(row.get("source_signature")), row);
        }
        return indexed;
    }

    private void upsertCandidate(Long taskId,
                                 Long languagePackId,
                                 Map<String, Object> unit,
                                 LanguagePackProblemPackage problemPackage,
                                 Map<String, Object> existingRow) {
        List<Long> relatedKcIds = problemPackage.relatedKcIds() == null ? List.of() : problemPackage.relatedKcIds();
        List<Long> sourceExampleIds = problemPackage.sourceExampleIds() == null ? List.of() : problemPackage.sourceExampleIds();
        List<LanguagePackProblemPackage.Sample> samples = problemPackage.samples() == null ? List.of() : problemPackage.samples();
        List<LanguagePackProblemPackage.TestCase> testCases = problemPackage.testCases() == null ? List.of() : problemPackage.testCases();

        Long primaryKcId = relatedKcIds.isEmpty() ? null : relatedKcIds.getFirst();
        Long primaryExampleId = resolvePrimaryExampleId(languagePackId, unit, sourceExampleIds);

        if (existingRow == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO language_pack_problem_generation_log(
                        init_task_id, language_pack_id, kc_id, example_id,
                        candidate_title, candidate_body, candidate_input_description, candidate_output_description,
                        candidate_samples_json, reference_solution, test_cases_json,
                        teaching_explanation, common_mistakes_json, source_pages_json, related_kc_ids_json,
                        validation_status, create_time, problem_package_json, source_example_ids_json, source_signature,
                        materialized_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', now(), ?, ?, ?, now())
                    """,
                    taskId,
                    languagePackId,
                    primaryKcId,
                    primaryExampleId,
                    problemPackage.title(),
                    problemPackage.description(),
                    problemPackage.inputDescription(),
                    problemPackage.outputDescription(),
                    writeJson(samples),
                    problemPackage.referenceSolutionCode(),
                    writeJson(testCases),
                    problemPackage.teachingExplanation(),
                    writeJson(problemPackage.commonMistakes()),
                    writeJson(problemPackage.sourcePages()),
                    writeJson(problemPackage.relatedKcIds()),
                    writeJson(problemPackage),
                    writeJson(problemPackage.sourceExampleIds()),
                    stringVal(unit.get("source_signature"))
            );
            return;
        }

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
                    problem_package_json = ?,
                    source_example_ids_json = ?,
                    validation_status = 'pending',
                    materialized_at = now()
                WHERE id = ?
                """,
                primaryKcId,
                primaryExampleId,
                problemPackage.title(),
                problemPackage.description(),
                problemPackage.inputDescription(),
                problemPackage.outputDescription(),
                writeJson(samples),
                problemPackage.referenceSolutionCode(),
                writeJson(testCases),
                problemPackage.teachingExplanation(),
                writeJson(problemPackage.commonMistakes()),
                writeJson(problemPackage.sourcePages()),
                writeJson(problemPackage.relatedKcIds()),
                writeJson(problemPackage),
                writeJson(problemPackage.sourceExampleIds()),
                longVal(existingRow.get("id"))
        );
    }

    private Long resolvePrimaryExampleId(Long languagePackId,
                                         Map<String, Object> unit,
                                         List<Long> sourceExampleIds) {
        LinkedHashSet<Long> candidates = new LinkedHashSet<>();
        candidates.addAll(sourceExampleIds);
        Long unitId = longVal(unit.get("id"));
        if (unitId != null) {
            candidates.add(unitId);
        }
        for (Long candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM language_pack_example WHERE language_pack_id = ? AND id = ?",
                    Integer.class,
                    languagePackId,
                    candidate
            );
            if (exists != null && exists > 0) {
                return candidate;
            }
        }
        return null;
    }

    private void deleteStaleCandidates(Long taskId, Set<String> activeSourceSignatures) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, source_signature FROM language_pack_problem_generation_log WHERE init_task_id = ?",
                taskId
        );
        for (Map<String, Object> row : rows) {
            String sourceSignature = stringVal(row.get("source_signature"));
            if (!activeSourceSignatures.contains(sourceSignature)) {
                jdbcTemplate.update(
                        "DELETE FROM language_pack_problem_generation_log WHERE id = ?",
                        longVal(row.get("id"))
                );
            }
        }
    }

    private void clearExistingCandidates(Long taskId) {
        jdbcTemplate.update("DELETE FROM language_pack_problem_generation_log WHERE init_task_id = ?", taskId);
    }

    private LanguagePackProblemPackage generateProblemPackageForUnit(Long taskId,
                                                                     Long languagePackId,
                                                                     String primaryLanguage,
                                                                     Map<String, Object> unit,
                                                                     Map<Long, Map<String, Object>> kcContextById,
                                                                     Map<String, Map<String, Object>> chapterMemoryByKey,
                                                                     Map<String, Map<String, Object>> neighborUnitBySignature,
                                                                     boolean allowReuse) {
        List<Long> relatedKcIds = new ArrayList<>(new LinkedHashSet<>(parseLongList(unit.get("kc_ids"))));
        if (relatedKcIds.isEmpty()) {
            throw new IllegalStateException("Courseware unit " + longVal(unit.get("id")) + " has no mapped KCs");
        }

        List<Integer> sourcePages = parseIntegerList(unit.get("source_pages"));
        if (sourcePages.isEmpty()) {
            sourcePages = deriveSourcePages(unit);
        }
        if (sourcePages.isEmpty()) {
            throw new IllegalStateException("Courseware unit " + longVal(unit.get("id")) + " has no source pages");
        }

        List<Map<String, Object>> relatedKcs = relatedKcIds.stream()
                .map(kcContextById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (relatedKcs.size() != relatedKcIds.size()) {
            throw new IllegalStateException("Courseware unit " + longVal(unit.get("id")) + " has invalid KC mappings");
        }

        String sourceSignature = stringVal(unit.get("source_signature"));
        Map<String, Object> chapterMemory = resolveChapterMemoryForUnit(unit, chapterMemoryByKey);
        Map<String, Object> neighborUnits = neighborUnitBySignature.getOrDefault(sourceSignature, Map.of());
        String inputHash = buildGenerationInputHash(unit, relatedKcs, sourcePages, chapterMemory, neighborUnits);
        Map<String, Object> reusable = null;
        if (allowReuse) {
            reusable = batchRunStore.findReusableBatch(
                    taskId,
                    "generate-problems",
                    longVal(unit.get("document_id")),
                    intVal(unit.get("chapter_index")),
                    sourcePages.getFirst(),
                    sourcePages.getLast(),
                    inputHash
            );
        }

        String generationContext = describeGenerationContext(unit, sourcePages, relatedKcs);
        log.info("Generating problem package for task {} at {}", taskId, generationContext);

        if (reusable != null) {
            Map<String, Object> reusedRow = batchRunStore.recordReuseFrom(reusable);
            return LanguagePackProblemPackageMapper.fromStoredJson(objectMapper, stringVal(reusedRow.get("output_json")));
        }

        Long batchRunId = batchRunStore.startBatchRun(
                taskId,
                "generate-problems",
                longVal(unit.get("document_id")),
                intVal(unit.get("chapter_index")),
                sourcePages.getFirst(),
                sourcePages.getLast(),
                sourcePages.size(),
                sourcePages.size(),
                inputHash
        );

        String materializeFailureContext = null;
        for (int fullAttempt = 0; fullAttempt < 1 + FULL_REGEN_MAX_ATTEMPTS; fullAttempt++) {
            try {
                LanguagePackProblemPackage problemPackage = generateOnce(
                        unit, relatedKcIds, relatedKcs, sourcePages, primaryLanguage,
                        languagePackId, chapterMemory, neighborUnits, materializeFailureContext
                );
                problemPackage = materializationHelper.materializeOutputs(problemPackage, primaryLanguage);
                batchRunStore.completeBatchRun(batchRunId, writeJson(problemPackage));
                return problemPackage;
            } catch (MaterializationFailedException ex) {
                materializeFailureContext = ex.failureSummary();
                log.warn("Layer3 full regen needed for unit {} attempt {}/{}: {}",
                        longVal(unit.get("id")), fullAttempt + 1, 1 + FULL_REGEN_MAX_ATTEMPTS,
                        ex.failureSummary().length() > 200 ? ex.failureSummary().substring(0, 200) : ex.failureSummary());
                if (fullAttempt + 1 >= 1 + FULL_REGEN_MAX_ATTEMPTS) {
                    batchRunStore.failBatchRun(batchRunId, "L3 exhausted: " + ex.failureSummary());
                    throw new ProblemUnitGenerationFailure(sourceSignature,
                            "Layer3 full regen 用尽 " + (1 + FULL_REGEN_MAX_ATTEMPTS) + " 次: " + ex.failureSummary());
                }
            } catch (LanguagePackProblemJudgeCheckService.JudgeUnavailableException ex) {
                batchRunStore.failBatchRun(batchRunId, "judge_unavailable: " + ex.getMessage());
                throw new JudgePausedException(sourceSignature, ex.getMessage());
            } catch (ProblemUnitGenerationFailure ex) {
                batchRunStore.failBatchRun(batchRunId, ex.reason());
                throw ex;
            } catch (RuntimeException ex) {
                batchRunStore.failBatchRun(batchRunId, "unexpected: " + ex.getMessage());
                throw new ProblemUnitGenerationFailure(sourceSignature,
                        "(" + ex.getClass().getSimpleName() + ") " + ex.getMessage());
            }
        }
        throw new IllegalStateException("unreachable: Layer3 outer loop exited without return/throw");
    }

    private LanguagePackProblemPackage generateOnce(Map<String, Object> unit,
                                                    List<Long> relatedKcIds,
                                                    List<Map<String, Object>> relatedKcs,
                                                    List<Integer> sourcePages,
                                                    String primaryLanguage,
                                                    Long languagePackId,
                                                    Map<String, Object> chapterMemory,
                                                    Map<String, Object> neighborUnits,
                                                    String materializeFailureContext) {
        String systemPrompt = buildSystemPrompt(primaryLanguage);
        String userPromptBase = buildUserPrompt(unit, relatedKcs, sourcePages, primaryLanguage, chapterMemory, neighborUnits);
        if (materializeFailureContext != null && !materializeFailureContext.isBlank()) {
            userPromptBase += "\n\n## 上一轮失败上下文（请重点修复 reference_solution_code）\n"
                    + materializeFailureContext
                    + "\n\n请重新生成完整题包，确保新的 reference_solution_code 能在 judge 上通过所有 testcase。";
        }

        LlmSchemaViolationException lastSchemaError = null;
        for (int attempt = 1; attempt <= SCHEMA_MAX_ATTEMPTS; attempt++) {
            String userPrompt = lastSchemaError == null
                    ? userPromptBase
                    : userPromptBase + "\n\n## 上一次 schema 违反（请按规则重新生成）\nviolation: " + lastSchemaError.violation();
            Map<String, Object> llmResult = aiModelGateway.callForJson(systemPrompt, userPrompt, "INIT_LLM_");
            try {
                return LanguagePackProblemPackageMapper.normalizeGeneratedPackage(
                        objectMapper, llmResult, unit, relatedKcIds, primaryLanguage, languagePackId);
            } catch (LlmSchemaViolationException ex) {
                log.warn("Schema violation attempt {}/{} for unit {}: {}",
                        attempt, SCHEMA_MAX_ATTEMPTS, longVal(unit.get("id")), ex.violation());
                lastSchemaError = ex;
            }
        }
        throw new ProblemUnitGenerationFailure(
                stringVal(unit.get("source_signature")),
                "Layer1 schema 违反 " + SCHEMA_MAX_ATTEMPTS + " 次: "
                        + (lastSchemaError == null ? "?" : lastSchemaError.violation()));
    }

    private String buildSystemPrompt(String language) {
        return """
                You are building beginner OJ problem packages for non-CS students learning %s.
                Convert the provided courseware unit into exactly one grounded beginner-friendly programming problem package.

                Return strict JSON:
                {
                  "problem_packages": [
                    {
                      "display_id": "optional-display-id",
                      "title": "Problem title",
                      "description": "Full problem statement in Markdown",
                      "input_description": "Input format description",
                      "output_description": "Output format description",
                      "samples": [
                        {"input": "1 2", "output": "3"}
                      ],
                      "test_cases": [
                        {"input": "1 2", "output": "3"},
                        {"input": "3 4", "output": "7"},
                        {"input": "0 0", "output": "0"}
                      ],
                      "template": {
                        "%s": "Starter template only, not the full answer"
                      },
                      "time_limit": 1000,
                      "memory_limit": 256,
                      "difficulty": "Low",
                      "source_pages": [101, 102],
                      "source_example_ids": [12345],
                      "related_kc_ids": [201, 202],
                      "teaching_explanation": "Why this problem matters for beginners",
                      "common_mistakes": ["Mistake 1", "Mistake 2"],
                      "reference_solution_code": "Complete working answer in %s"
                    }
                  ]
                }

                Rules:
                - Return exactly one problem package.
                - The package must stay grounded in the provided unit and KC list.
                - The title must preserve the core task named by source_title and must not switch to a different exercise.
                - Do not collapse different source units into the same overly generic title unless source_title already uses that exact title.
                - If the material suggests multiple possible tasks, choose the one most directly supported by source_title, normalized_body, and evidence_excerpt.
                - When adapting the material to stdin/stdout format, preserve the same core task rather than inventing a new scenario.
                - Keep the same computational goal and required output semantics from source_title/normalized_body; do not replace it with a nearby variant.
                - Copy required_display_id exactly as provided.
                - If the source material is output-only but still expresses a real computational task, convert it into a stdin/stdout OJ problem by introducing the minimal input needed to preserve the same task.
                - For fixed-bound computational tasks such as summing 1..10000 or printing a fixed-size table, parameterize the bound through stdin instead of keeping a no-input version.
                - For fixed-table exercises such as 九九乘法表, parameterize the table size through stdin so the OJ version becomes an n*n multiplication table instead of a hard-coded 9x9 printout.
                - Do not force OJ conversion for internally-terminated approximation exercises with no natural external input, such as computing π until a term threshold is reached; these should be treated as non-convertible instead of inventing fake stdin.
                - Do not return an output-only problem; every final OJ problem must consume stdin and produce stdout.
                - test_cases must contain 3 to 5 cases.
                - The first sample must match the first test case exactly.
                - Every test case must have non-empty input and output and must avoid placeholder text.
                - template must be a starter template, not the full reference solution.
                - reference_solution_code must be a complete, compilable, correct program that reads from stdin and writes to stdout.
                - reference_solution_code is the single source of truth: the system will execute it against every test case input and use the actual output to overwrite samples/test_cases output. Your output values are only initial hints.
                - reference_solution_code must produce deterministic output for the same input on every run.
                - Copy the required_source_example_ids, required_source_pages, and required_related_kc_ids exactly as provided.
                - Do not keep placeholder ids such as [1] from the JSON example.
                - source_pages must stay within the provided source pages.
                - source_example_ids must contain the provided unit id.
                - related_kc_ids must be a subset of the provided KC ids and must not be empty.
                - difficulty must be one of Low, Mid, High.
                """.formatted(language, language, language);
    }

    private String buildUserPrompt(Map<String, Object> unit,
                                   List<Map<String, Object>> relatedKcs,
                                   List<Integer> sourcePages,
                                   String primaryLanguage,
                                   Map<String, Object> chapterMemory,
                                   Map<String, Object> neighborUnits) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Target language: ").append(primaryLanguage).append("\n");
        prompt.append("Courseware unit:\n");
        prompt.append("- unit_id: ").append(unit.get("id")).append("\n");
        prompt.append("- source_title: ").append(stringVal(unit.get("source_title"))).append("\n");
        prompt.append("- chapter: ").append(stringVal(unit.get("chapter_title"))).append("\n");
        prompt.append("- unit_type: ").append(stringVal(unit.get("unit_type"))).append("\n");
        prompt.append("- document: ").append(stringVal(unit.get("document_title"))).append("\n");
        prompt.append("- source_pages: ").append(sourcePages).append("\n");
        prompt.append("- required_display_id: ").append(stringVal(unit.get("display_id"))).append("\n");
        prompt.append("- required_source_pages: ").append(sourcePages).append("\n");
        prompt.append("- required_source_example_ids: [").append(longVal(unit.get("id"))).append("]\n");
        prompt.append("- required_related_kc_ids: ").append(relatedKcs.stream()
                .map(kc -> longVal(kc.get("id")))
                .filter(java.util.Objects::nonNull)
                .toList()).append("\n");
        prompt.append("- normalized_body: ").append(stringVal(unit.get("normalized_body"))).append("\n");
        prompt.append("- input_description: ").append(stringVal(unit.get("input_description"))).append("\n");
        prompt.append("- output_description: ").append(stringVal(unit.get("output_description"))).append("\n");
        prompt.append("- evidence_excerpt: ").append(stringVal(unit.get("evidence_excerpt"))).append("\n");
        prompt.append("- raw_text:\n").append(stringVal(unit.get("raw_text"))).append("\n");
        prompt.append("\nTask grounding priority:\n");
        prompt.append("- Preserve the task named by source_title.\n");
        prompt.append("- Use normalized_body and evidence_excerpt to disambiguate the exact task.\n");
        prompt.append("- If conversion to OJ format is needed, keep the same task and only normalize the interaction style to stdin/stdout.\n");
        prompt.append("- Keep the same computational goal and output semantics; do not switch to an easier or neighboring exercise.\n");
        prompt.append("- If the material currently has no input but still represents a computable task, add the minimal stdin parameter needed to preserve the same task.\n");
        prompt.append("- Reject output-only formulations; the final OJ version must read stdin and write stdout.\n");
        prompt.append("- For fixed-bound or fixed-data tasks, parameterize the bound or data shape through stdin rather than hard-coding constants.\n");
        prompt.append("- For fixed-table tasks such as 九九乘法表, parameterize the table size through stdin so the final task becomes an n*n multiplication table.\n");
        prompt.append("- For approximation exercises with no natural external input, such as using an internal threshold to approximate π, do not invent fake stdin or rewrite the task into another exercise; those tasks should have been filtered upstream.\n");
        prompt.append("- Self-check before returning: first sample equals first test case, test case count is between 3 and 5, every test case input/output is non-empty, reference_solution_code reads stdin, writes stdout, and produces the exact output listed for every test case. The system will execute reference_solution_code against every input; if it fails or produces different output, the problem will be rejected.\n");
        prompt.append("- Copy the required_source_example_ids, required_source_pages, and required_related_kc_ids exactly as provided.\n");
        prompt.append("- Copy required_display_id exactly as provided.\n");
        prompt.append("- Do not keep placeholder ids such as [1] from the JSON example.\n");

        if (!chapterMemory.isEmpty()) {
            prompt.append("\nChapter memory:\n");
            prompt.append("- chapter_synopsis: ").append(stringVal(chapterMemory.get("chapter_synopsis"))).append("\n");
            prompt.append("- canonical_kc_count: ").append(chapterMemory.getOrDefault("canonical_kc_count", 0)).append("\n");
        }

        prompt.append("\nRelated knowledge components:\n");
        for (Map<String, Object> kc : relatedKcs) {
            prompt.append("- kc_id=").append(kc.get("id"))
                    .append(", chapter=").append(stringVal(kc.get("chapter_title")))
                    .append(", name=").append(stringVal(kc.get("name")))
                    .append(", description=").append(stringVal(kc.get("description")))
                    .append("\n");
        }

        if (!neighborUnits.isEmpty()) {
            prompt.append("\nNeighbor units:\n");
            if (neighborUnits.containsKey("previous")) {
                Map<String, Object> previous = castMap(neighborUnits.get("previous"));
                prompt.append("- previous_unit: ").append(stringVal(previous.get("source_title"))).append("\n");
            }
            if (neighborUnits.containsKey("next")) {
                Map<String, Object> next = castMap(neighborUnits.get("next"));
                prompt.append("- next_unit: ").append(stringVal(next.get("source_title"))).append("\n");
            }
        }

        prompt.append("\nReturn one grounded problem package only.\n");
        return prompt.toString();
    }

    private String describeGenerationContext(Map<String, Object> unit,
                                             List<Integer> sourcePages,
                                             List<Map<String, Object>> relatedKcs) {
        List<Long> kcIds = relatedKcs.stream()
                .map(kc -> longVal(kc.get("id")))
                .filter(java.util.Objects::nonNull)
                .toList();
        return "unit=" + longVal(unit.get("id"))
                + " [" + stringVal(unit.get("source_title")) + "]"
                + ", pages=" + sourcePages
                + ", kc_ids=" + kcIds;
    }

    private String buildInputFingerprint(List<Map<String, Object>> units) {
        StringBuilder builder = new StringBuilder();
        builder.append("units=").append(units.size()).append(';');
        for (Map<String, Object> unit : units) {
            builder.append(longVal(unit.get("id")))
                    .append(':')
                    .append(stringVal(unit.get("source_signature")))
                    .append(':')
                    .append(parseLongList(unit.get("kc_ids")))
                    .append('|');
        }
        return builder.toString();
    }

    private int firstSourcePage(Map<String, Object> unit) {
        List<Integer> sourcePages = parseIntegerList(unit.get("source_pages"));
        if (!sourcePages.isEmpty()) {
            return sourcePages.getFirst();
        }
        Integer start = intVal(unit.get("page_range_start"));
        return start == null ? 0 : start;
    }

    private Map<String, Object> resolveChapterMemoryForUnit(
            Map<String, Object> unit,
            Map<String, Map<String, Object>> chapterMemoryByKey
    ) {
        String key = chapterKey(unit);
        if (!key.isBlank() && chapterMemoryByKey.containsKey(key)) {
            return chapterMemoryByKey.get(key);
        }
        Long documentId = longVal(unit.get("document_id"));
        if (documentId == null) {
            return Map.of();
        }
        List<Map<String, Object>> rows = chapterMemoryByKey.values().stream()
                .filter(row -> documentId.equals(longVal(row.get("document_id"))))
                .toList();
        if (rows.isEmpty()) {
            return Map.of();
        }
        if (rows.size() == 1) {
            return rows.getFirst();
        }
        int firstPage = parseIntegerList(unit.get("source_pages")).stream().findFirst().orElse(0);
        return rows.stream()
                .min(java.util.Comparator.comparingInt(row -> chapterDistance(row, firstPage)))
                .orElse(Map.of());
    }

    private int chapterDistance(Map<String, Object> chapterMemory, int pageNo) {
        Integer start = intVal(chapterMemory.get("page_range_start"));
        Integer end = intVal(chapterMemory.get("page_range_end"));
        if (start == null || end == null) {
            return Integer.MAX_VALUE;
        }
        if (pageNo < start) {
            return start - pageNo;
        }
        if (pageNo > end) {
            return pageNo - end;
        }
        return 0;
    }

    private String buildGenerationInputHash(Map<String, Object> unit,
                                            List<Map<String, Object>> relatedKcs,
                                            List<Integer> sourcePages,
                                            Map<String, Object> chapterMemory,
                                            Map<String, Object> neighborUnits) {
        StringBuilder builder = new StringBuilder();
        builder.append(stringVal(unit.get("source_signature"))).append('|')
                .append(sourcePages).append('|')
                .append(stringVal(chapterMemory.get("chapter_synopsis"))).append('|');
        for (Map<String, Object> kc : relatedKcs) {
            builder.append(longVal(kc.get("id"))).append(':').append(stringVal(kc.get("name"))).append('|');
        }
        Map<String, Object> previous = castMap(neighborUnits.get("previous"));
        Map<String, Object> next = castMap(neighborUnits.get("next"));
        builder.append("prev=").append(stringVal(previous.get("source_title"))).append('|');
        builder.append("next=").append(stringVal(next.get("source_title"))).append('|');
        return builder.toString();
    }

    private List<Integer> deriveSourcePages(Map<String, Object> unit) {
        Integer start = intVal(unit.get("page_range_start"));
        Integer end = intVal(unit.get("page_range_end"));
        if (start == null) {
            return List.of();
        }
        if (end == null || end < start) {
            end = start;
        }
        List<Integer> pages = new ArrayList<>();
        for (int pageNo = start; pageNo <= end; pageNo++) {
            pages.add(pageNo);
        }
        return pages;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("json serialize failed", exception);
        }
    }

    private int countReusedBatchRuns(Long taskId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_init_batch_run WHERE task_id = ? AND status = 'reused'",
                Integer.class,
                taskId
        );
        return count == null ? 0 : count;
    }

    private String chapterKey(Map<String, Object> row) {
        Long documentId = longVal(row.get("document_id"));
        Integer chapterIndex = intVal(row.get("chapter_index"));
        if (documentId != null && chapterIndex != null && chapterIndex > 0) {
            return documentId + "::" + chapterIndex;
        }
        if (chapterIndex != null && chapterIndex > 0) {
            return "chapter::" + chapterIndex;
        }
        if (documentId != null) {
            return documentId + "::0";
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            normalized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    private String stringVal(Object obj) {
        if (obj == null) {
            return "";
        }
        return String.valueOf(obj).strip();
    }

    private Long longVal(Object obj) {
        if (obj instanceof Number number) {
            return number.longValue();
        }
        if (obj == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(obj).strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer intVal(Object obj) {
        if (obj instanceof Number number) {
            return number.intValue();
        }
        if (obj == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(obj).strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static final java.util.regex.Pattern HARD_REJECT_PATTERN = java.util.regex.Pattern.compile(
            "(turtle|tkinter|pygame|matplotlib|plt\\.|GUI|图形界面|游戏引擎|动画渲染)",
            java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private boolean isOjConvertible(Map<String, Object> row) {
        Object value = row.get("oj_convertible");
        if (value instanceof Boolean booleanValue && !booleanValue) {
            return false;
        }
        if (!"true".equalsIgnoreCase(stringVal(value)) && !(value instanceof Boolean)) {
            return false;
        }
        String body = stringVal(row.get("normalized_body"));
        String title = stringVal(row.get("title"));
        String combined = title + " " + body;
        if (HARD_REJECT_PATTERN.matcher(combined).find()) {
            log.debug("Filtered non-OJ unit '{}': requires GUI/graphics library", title);
            return false;
        }
        return true;
    }

    private List<Long> parseLongList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Long> rows = new ArrayList<>();
        for (Object item : rawList) {
            Long parsed = longVal(item);
            if (parsed != null) {
                rows.add(parsed);
            }
        }
        return List.copyOf(new LinkedHashSet<>(rows));
    }

    private List<Integer> parseIntegerList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Integer> rows = new ArrayList<>();
        for (Object item : rawList) {
            Integer parsed = intVal(item);
            if (parsed != null) {
                rows.add(parsed);
            }
        }
        return List.copyOf(new LinkedHashSet<>(rows));
    }
}
