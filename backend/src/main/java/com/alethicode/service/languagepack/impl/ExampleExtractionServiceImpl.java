package com.alethicode.service.languagepack.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.exception.BadRequestException;
import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.languagepack.ExampleExtractionService;
import com.alethicode.service.languagepack.LanguagePackInitAuditService;
import com.alethicode.service.languagepack.LanguagePackInitExecutionService;
import com.alethicode.service.languagepack.LanguagePackInitService;
import com.alethicode.util.BoundedParallel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ExampleExtractionServiceImpl implements ExampleExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExampleExtractionServiceImpl.class);

    private static final int SEGMENT_WINDOW_SIZE = 4;
    private static final int SEGMENT_WINDOW_OVERLAP = 1;

    private static final List<String> TASK_SIGNAL_KEYWORDS = List.of(
            "练习", "编程", "作业", "示例", "实例", "输出", "计算", "统计", "判断",
            "管理", "系统", "生成", "密码", "登录", "成绩", "面积", "求和", "阶乘",
            "祝福语", "图案", "词频", "订单", "通讯录", "字典", "列表", "车辆", "宠物"
    );
    private static final List<String> STRONG_OJ_KEYWORDS = List.of(
            "图案", "祝福语", "菜单", "通讯录", "用户管理", "列表", "字典", "统计",
            "成绩", "车辆", "订单", "登录", "密码", "乘法表", "词频", "宠物", "阶乘",
            "求和", "面积", "年历", "凯撒密码", "BMI", "输出", "生成", "判断"
    );
    private static final List<String> PARAMETERIZABLE_OUTPUT_KEYWORDS = List.of(
            "计算", "求和", "统计", "判断", "筛选", "密码", "加密", "车辆", "成绩",
            "圆", "面积", "周长", "π", "词频", "集合", "通讯录", "快递", "登录",
            "数字", "星期", "乘法表", "三角形", "自然数", "偶数"
    );
    private static final List<String> NON_STDIN_STDOUT_KEYWORDS = List.of(
            "生日歌", "歌词", "歌曲", "周杰伦", "单曲循环", "绘制", "绘图", "图形", "画布", "多态",
            "创建实例", "创建类", "创建属性", "实例方法", "魔术方法", "__init__", "全局变量", "引用自定义模块"
    );
    private static final List<String> CONCEPT_ONLY_KEYWORDS = List.of(
            "概念", "定义", "简介", "说明", "语法", "原理", "创立者", "特点", "编码",
            "unicode", "数据类型", "变量命名", "保留字", "运算符", "流程图"
    );
    private static final List<String> GENERIC_TITLE_KEYWORDS = List.of(
            "练习", "示例", "作业", "代码示例", "程序设计实例", "例题", "编程题"
    );

    private final JdbcTemplate jdbcTemplate;
    private final LanguagePackInitService initService;
    private final LanguagePackInitExecutionService executionService;
    private final LanguagePackInitAuditService auditService;
    private final LanguagePackInitBatchRunStore batchRunStore;
    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;
    private final int unitExtractConcurrency;

    public ExampleExtractionServiceImpl(JdbcTemplate jdbcTemplate,
                                        LanguagePackInitService initService,
                                        LanguagePackInitExecutionService executionService,
                                        LanguagePackInitAuditService auditService,
                                        LanguagePackInitBatchRunStore batchRunStore,
                                        AiModelGateway aiModelGateway,
                                        ObjectMapper objectMapper,
                                        AlethicodeProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.initService = initService;
        this.executionService = executionService;
        this.auditService = auditService;
        this.batchRunStore = batchRunStore;
        this.aiModelGateway = aiModelGateway;
        this.objectMapper = objectMapper;
        this.unitExtractConcurrency = properties.getLanguagePack().getConcurrency().getUnitExtract();
    }

    @Override
    public void extractExamples(Long taskId) {
        String currentStage = jdbcTemplate.queryForObject(
                "SELECT stage FROM language_pack_init_task WHERE id = ?",
                String.class,
                taskId
        );
        if (currentStage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Init task not found");
        }
        if (!List.of("kc_ready", "segments_ready", "units_ready", "oj_candidates_ready", "failed").contains(currentStage)) {
            throw new BadRequestException("Cannot extract examples in stage: " + currentStage);
        }

        Long languagePackId = jdbcTemplate.queryForObject(
                "SELECT language_pack_id FROM language_pack_init_task WHERE id = ?",
                Long.class,
                taskId
        );

        List<Map<String, Object>> pages = jdbcTemplate.queryForList(
                """
                SELECT p.id, p.document_id, p.page_no, p.page_title, p.page_text, d.original_filename
                FROM language_pack_page p
                JOIN language_pack_document d ON d.id = p.document_id
                WHERE p.language_pack_id = ?
                ORDER BY d.sort_order, d.id, p.page_no
                """,
                languagePackId
        );
        Map<Long, Map<Long, Map<String, Object>>> kcsByPageId = loadKcsByPageId(languagePackId);
        if (pages.isEmpty() || kcsByPageId.isEmpty()) {
            initService.failTask(taskId, "No pages or KCs available for example extraction");
            throw new BusinessException(ErrorCode.ERROR, "No pages or KCs available for example extraction");
        }

        executionService.beginStep(taskId, "oj_candidates_ready", "开始抽取例题", null, null);
        try {
            Map<Long, List<Map<String, Object>>> pagesByDocument = groupPagesByDocument(pages);
            Map<Integer, Map<String, Object>> chapterStatsByIndex = buildChapterStats(pages, kcsByPageId);
            String stageCursor = currentStage;

            List<Map<String, Object>> segments = runSegmentationAgent(taskId, stageCursor, pagesByDocument, kcsByPageId, chapterStatsByIndex);
            executionService.reportProgress(taskId, "oj_candidates_ready", "课件分段完成，共 " + segments.size() + " 个分段", null, null);
            stageCursor = advanceExtractionStage(taskId, stageCursor, "kc_ready", "segments_ready");
            Map<String, Map<String, Object>> chapterMemoryByKey = loadChapterMemoryByKey(taskId);
            Map<String, List<Map<String, Object>>> canonicalKcsByChapter = loadCanonicalKcsByChapter(taskId);
            Map<String, Map<String, Object>> neighborContextBySegment = buildNeighborContextBySegment(segments);

            List<Map<String, Object>> extractedUnits = runUnitExtractionAgent(
                    taskId,
                    stageCursor,
                    segments,
                    pagesByDocument,
                    kcsByPageId,
                    chapterMemoryByKey,
                    canonicalKcsByChapter,
                    neighborContextBySegment
            );
            List<Map<String, Object>> mergedUnits = mergeUnits(extractedUnits);
            if (mergedUnits.isEmpty()) {
                initService.failTask(taskId, "No courseware units extracted from segments");
                throw new BusinessException(ErrorCode.ERROR, "No courseware units extracted from segments");
            }
            executionService.reportProgress(taskId, "oj_candidates_ready", "教学单元抽取完成，共 " + mergedUnits.size() + " 个单元", null, null);
            clearExistingExamples(languagePackId);
            List<Map<String, Object>> persistedUnits = persistUnits(taskId, languagePackId, mergedUnits);
            jdbcTemplate.update(
                    "UPDATE language_pack SET example_count = ?, update_time = now() WHERE id = ?",
                    persistedUnits.size(),
                    languagePackId
            );
            replaceJsonArtifact(taskId, "courseware_units.json", "units_ready", Map.of("courseware_units", persistedUnits));
            stageCursor = advanceExtractionStage(taskId, stageCursor, "segments_ready", "units_ready");

            List<Map<String, Object>> judgedCandidates = runCandidateJudgementAgent(taskId, stageCursor, persistedUnits);
            applyCandidateFlags(judgedCandidates);
            replaceJsonArtifact(taskId, "oj_candidates.json", "oj_candidates_ready", Map.of("oj_candidates", judgedCandidates));
            executionService.reportProgress(taskId, "oj_candidates_ready", "候选例题筛选完成，共 " + judgedCandidates.size() + " 条候选", null, null);

            Map<String, Object> escalationReview = runEscalationReviewAgent(taskId, stageCursor, judgedCandidates, chapterStatsByIndex);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> reviewedCandidates = (List<Map<String, Object>>) escalationReview.getOrDefault("oj_candidates", List.of());
            applyCandidateFlags(reviewedCandidates);
            replaceJsonArtifact(taskId, "oj_candidates.json", "oj_candidates_ready", Map.of("oj_candidates", reviewedCandidates));
            replaceJsonArtifact(taskId, "escalation_review.json", "oj_candidates_ready", escalationReview);
            stageCursor = advanceExtractionStage(taskId, stageCursor, "units_ready", "oj_candidates_ready");

            int ojCandidateCount = (int) reviewedCandidates.stream()
                    .filter(this::isOjConvertible)
                    .count();
            if ("failed".equals(currentStage)) {
                initService.restoreStage(taskId, "oj_candidates_ready", "Stage restored after resumable example extraction");
            }
            log.info(
                    "Example extraction pipeline completed for task {}: segments={}, units={}, oj_candidates={}",
                    taskId,
                    segments.size(),
                    persistedUnits.size(),
                    ojCandidateCount
            );
            executionService.finishStep(taskId, "oj_candidates_ready", "例题抽取完成，共 " + ojCandidateCount + " 条 OJ 候选");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            initService.failTask(taskId, "Example extraction failed: " + exception.getMessage());
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Example extraction failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private List<Map<String, Object>> runSegmentationAgent(Long taskId,
                                                           String sourceStage,
                                                           Map<Long, List<Map<String, Object>>> pagesByDocument,
                                                           Map<Long, Map<Long, Map<String, Object>>> kcsByPageId,
                                                           Map<Integer, Map<String, Object>> chapterStatsByIndex) {
        Long runId = auditService.startAgentRun(
                taskId,
                "CoursewareSegmentationAgent",
                sourceStage,
                "courseware-segmentation.v1",
                buildPageFingerprint(pagesByDocument)
        );
        try {
            List<Map<String, Object>> segments = buildSegments(pagesByDocument, kcsByPageId, chapterStatsByIndex);
            if (segments.isEmpty()) {
                throw new IllegalStateException("No courseware segments generated");
            }
            String hash = replaceJsonArtifact(taskId, "courseware_segments.json", "segments_ready", Map.of("courseware_segments", segments));
            auditService.completeAgentRun(runId, hash);
            return segments;
        } catch (Exception exception) {
            auditService.failAgentRun(runId, exception.getMessage());
            initService.failTask(taskId, "Courseware segmentation failed: " + exception.getMessage());
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Courseware segmentation failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private List<Map<String, Object>> runUnitExtractionAgent(Long taskId,
                                                             String sourceStage,
                                                             List<Map<String, Object>> segments,
                                                             Map<Long, List<Map<String, Object>>> pagesByDocument,
                                                             Map<Long, Map<Long, Map<String, Object>>> kcsByPageId,
                                                             Map<String, Map<String, Object>> chapterMemoryByKey,
                                                             Map<String, List<Map<String, Object>>> canonicalKcsByChapter,
                                                             Map<String, Map<String, Object>> neighborContextBySegment) {
        Long runId = auditService.startAgentRun(
                taskId,
                "CoursewareUnitExtractionAgent",
                sourceStage,
                "courseware-unit-extraction.v3",
                writeJson(Map.of("courseware_segments", segments))
        );
        try {
            List<List<Map<String, Object>>> perSegmentResults = BoundedParallel.map(
                    segments,
                    unitExtractConcurrency,
                    segment -> {
                        List<Map<String, Object>> segmentPages = materializeSegmentPages(segment, pagesByDocument);
                        if (segmentPages.isEmpty()) {
                            return List.<Map<String, Object>>of();
                        }
                        List<Map<String, Object>> segmentKcs = resolveBatchKcs(segmentPages, kcsByPageId);
                        if (segmentKcs.isEmpty()) {
                            return List.<Map<String, Object>>of();
                        }
                        Map<String, Object> chapterMemory = resolveChapterMemoryForSegment(segment, chapterMemoryByKey);
                        List<Map<String, Object>> canonicalKcs = resolveCanonicalKcsForSegment(segment, canonicalKcsByChapter);
                        Map<String, Object> neighborContext = neighborContextBySegment.getOrDefault(segmentKey(segment), Map.of());
                        String inputHash = buildUnitExtractionInputHash(segment, segmentKcs, chapterMemory, canonicalKcs, neighborContext);
                        int startPage = intVal(segment.get("page_range_start"));
                        int endPage = intVal(segment.get("page_range_end"));

                        Map<String, Object> reusable = batchRunStore.findReusableBatch(
                                taskId,
                                "extract-examples",
                                longVal(segment.get("document_id")),
                                intVal(segment.get("chapter_index")),
                                startPage,
                                endPage,
                                inputHash
                        );
                        if (reusable != null) {
                            Map<String, Object> reusedRow = batchRunStore.recordReuseFrom(reusable);
                            return parseStoredUnits(reusedRow.get("output_json"));
                        }

                        Long batchRunId = batchRunStore.startBatchRun(
                                taskId,
                                "extract-examples",
                                longVal(segment.get("document_id")),
                                intVal(segment.get("chapter_index")),
                                startPage,
                                endPage,
                                endPage - startPage + 1,
                                endPage - startPage + 1,
                                inputHash
                        );
                        try {
                            Map<String, Object> llmResult = aiModelGateway.callForJsonCached(
                                    inputHash,
                                    buildUnitExtractionSystemPrompt(),
                                    buildUnitExtractionUserPrompt(segment, segmentPages, segmentKcs, chapterMemory, canonicalKcs, neighborContext),
                                    "INIT_LLM_"
                            );
                            List<Map<String, Object>> normalizedUnits = normalizeSegmentUnits(segment, llmResult, segmentKcs);
                            batchRunStore.completeBatchRun(batchRunId, writeJson(Map.of("units", normalizedUnits)));
                            return normalizedUnits;
                        } catch (Exception exception) {
                            batchRunStore.failBatchRun(batchRunId, exception.getMessage());
                            throw exception;
                        }
                    }
            );
            List<Map<String, Object>> units = new ArrayList<>();
            for (List<Map<String, Object>> segmentUnits : perSegmentResults) {
                units.addAll(segmentUnits);
            }
            if (units.isEmpty()) {
                throw new IllegalStateException("No courseware units extracted");
            }
            auditService.completeAgentRun(runId, writeJson(Map.of("unit_count", units.size())));
            return units;
        } catch (Exception exception) {
            auditService.failAgentRun(runId, exception.getMessage());
            initService.failTask(taskId, "Courseware unit extraction failed: " + exception.getMessage());
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Courseware unit extraction failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private List<Map<String, Object>> runCandidateJudgementAgent(Long taskId,
                                                                 String sourceStage,
                                                                 List<Map<String, Object>> persistedUnits) {
        Long runId = auditService.startAgentRun(
                taskId,
                "OjCandidateJudgementAgent",
                sourceStage,
                "oj-candidate-judgement.v1",
                writeJson(Map.of("courseware_units", persistedUnits))
        );
        try {
            List<Map<String, Object>> judged = new ArrayList<>();
            for (Map<String, Object> unit : persistedUnits) {
                OjJudgement judgement = judgeUnit(unit);
                Map<String, Object> row = new LinkedHashMap<>(unit);
                row.put("oj_convertible", judgement.ojConvertible());
                row.put("stdin_stdout_convertible", judgement.stdinStdoutConvertible());
                row.put("oj_block_reason", judgement.ojBlockReason());
                row.put("review_required", judgement.reviewRequired());
                row.put("review_reason", judgement.reviewReason());
                row.put("task_signal_score", judgement.taskSignalScore());
                judged.add(row);
            }
            auditService.completeAgentRun(runId, writeJson(Map.of("oj_candidate_count", countOjCandidates(judged))));
            return judged;
        } catch (Exception exception) {
            auditService.failAgentRun(runId, exception.getMessage());
            initService.failTask(taskId, "OJ candidate judgement failed: " + exception.getMessage());
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "OJ candidate judgement failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private Map<String, Object> runEscalationReviewAgent(Long taskId,
                                                         String sourceStage,
                                                         List<Map<String, Object>> judgedCandidates,
                                                         Map<Integer, Map<String, Object>> chapterStatsByIndex) {
        Long runId = auditService.startAgentRun(
                taskId,
                "EscalationReviewAgent",
                sourceStage,
                "escalation-review.v1",
                writeJson(Map.of("oj_candidates", judgedCandidates))
        );
        try {
            List<Map<String, Object>> reviewed = new ArrayList<>();
            Map<String, Map<String, Object>> chapterStats = buildCandidateChapterStats(judgedCandidates, chapterStatsByIndex);
            Set<String> highRiskChapterKeys = chapterStats.values().stream()
                    .filter(this::isHighRiskChapter)
                    .map(this::chapterKey)
                    .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

            for (Map<String, Object> candidate : judgedCandidates) {
                Map<String, Object> reviewedRow = new LinkedHashMap<>(candidate);
                boolean reviewRequired = booleanVal(candidate.get("review_required"));
                boolean chapterHighRisk = highRiskChapterKeys.contains(chapterKey(candidate));
                if (reviewRequired || chapterHighRisk) {
                    boolean promote = shouldPromoteDuringEscalation(candidate);
                    reviewedRow.put("reviewed_by_escalation", true);
                    reviewedRow.put("review_decision", promote ? "promoted_to_oj" : "kept_blocked");
                    if (promote) {
                        reviewedRow.put("oj_convertible", true);
                        reviewedRow.put("oj_block_reason", "");
                    }
                } else {
                    reviewedRow.put("reviewed_by_escalation", false);
                    reviewedRow.put("review_decision", "not_required");
                }
                reviewed.add(reviewedRow);
            }

            List<Map<String, Object>> highRiskChapters = buildHighRiskChapterList(reviewed, chapterStatsByIndex);
            List<Map<String, Object>> unresolved = reviewed.stream()
                    .filter(row -> booleanVal(row.get("review_required")) && !booleanVal(row.get("oj_convertible")))
                    .map(this::toCandidateSummary)
                    .toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("oj_candidates", reviewed);
            result.put("high_risk_chapters", highRiskChapters);
            result.put("unresolved_review_required", unresolved);
            auditService.completeAgentRun(runId, writeJson(result));
            return result;
        } catch (Exception exception) {
            auditService.failAgentRun(runId, exception.getMessage());
            initService.failTask(taskId, "Escalation review failed: " + exception.getMessage());
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Escalation review failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private List<Map<String, Object>> buildSegments(Map<Long, List<Map<String, Object>>> pagesByDocument,
                                                    Map<Long, Map<Long, Map<String, Object>>> kcsByPageId,
                                                    Map<Integer, Map<String, Object>> chapterStatsByIndex) {
        List<Map<String, Object>> rawSegments = new ArrayList<>();
        for (List<Map<String, Object>> documentPages : pagesByDocument.values()) {
            for (List<Map<String, Object>> window : partitionPagesWithOverlap(documentPages, SEGMENT_WINDOW_SIZE, SEGMENT_WINDOW_OVERLAP)) {
                rawSegments.add(buildSegment(window, kcsByPageId, chapterStatsByIndex));
            }
        }
        return mergeSegments(rawSegments);
    }

    private Map<String, Object> buildSegment(List<Map<String, Object>> window,
                                             Map<Long, Map<Long, Map<String, Object>>> kcsByPageId,
                                             Map<Integer, Map<String, Object>> chapterStatsByIndex) {
        List<Integer> pageNumbers = window.stream()
                .map(page -> ((Number) page.get("page_no")).intValue())
                .toList();
        Map<String, Object> primaryChapter = resolvePrimaryChapter(window, kcsByPageId, chapterStatsByIndex);
        String anchor = deriveSegmentAnchor(window);
        int taskSignalScore = computeTaskSignalScore(anchor + "\n" + describeWindowText(window));

        Map<String, Object> segment = new LinkedHashMap<>();
        segment.put("document_id", longVal(window.getFirst().get("document_id")));
        segment.put("document_title", stringVal(window.getFirst().get("original_filename")));
        segment.put("page_range_start", pageNumbers.getFirst());
        segment.put("page_range_end", pageNumbers.getLast());
        segment.put("source_pages", pageNumbers);
        segment.put("chapter_title", stringVal(primaryChapter.get("chapter_title")));
        segment.put("chapter_index", intVal(primaryChapter.get("chapter_index")));
        segment.put("chapter_page_count", intVal(primaryChapter.get("chapter_page_count")));
        segment.put("segment_anchor", anchor);
        segment.put("segment_type", taskSignalScore > 0 ? "task_like" : "concept");
        segment.put("task_signal_score", taskSignalScore);
        return segment;
    }

    private List<Map<String, Object>> mergeSegments(List<Map<String, Object>> rawSegments) {
        List<Map<String, Object>> sorted = rawSegments.stream()
                .sorted(Comparator
                        .comparing((Map<String, Object> row) -> longVal(row.get("document_id")))
                        .thenComparing(row -> intVal(row.get("chapter_index")))
                        .thenComparing(row -> normalizeKey(stringVal(row.get("segment_anchor"))))
                        .thenComparing(row -> intVal(row.get("page_range_start"))))
                .toList();
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> segment : sorted) {
            if (merged.isEmpty()) {
                merged.add(new LinkedHashMap<>(segment));
                continue;
            }
            Map<String, Object> previous = merged.getLast();
            if (canMergeSegment(previous, segment)) {
                mergeSegmentInto(previous, segment);
            } else {
                merged.add(new LinkedHashMap<>(segment));
            }
        }
        return merged;
    }

    private boolean canMergeSegment(Map<String, Object> previous, Map<String, Object> current) {
        if (!longVal(previous.get("document_id")).equals(longVal(current.get("document_id")))) {
            return false;
        }
        if (!normalizeKey(stringVal(previous.get("segment_anchor"))).equals(normalizeKey(stringVal(current.get("segment_anchor"))))) {
            return false;
        }
        if (normalizeKey(stringVal(previous.get("segment_anchor"))).isBlank()) {
            return false;
        }
        return pageRangeTouches(previous, current);
    }

    private void mergeSegmentInto(Map<String, Object> previous, Map<String, Object> current) {
        previous.put("page_range_start", Math.min(intVal(previous.get("page_range_start")), intVal(current.get("page_range_start"))));
        previous.put("page_range_end", Math.max(intVal(previous.get("page_range_end")), intVal(current.get("page_range_end"))));
        previous.put("source_pages", unionIntegerLists(previous.get("source_pages"), current.get("source_pages")));
        previous.put("task_signal_score", Math.max(intVal(previous.get("task_signal_score")), intVal(current.get("task_signal_score"))));
    }

    private List<Map<String, Object>> normalizeSegmentUnits(Map<String, Object> segment,
                                                            Map<String, Object> llmResult,
                                                            List<Map<String, Object>> segmentKcs) {
        List<Map<String, Object>> units = parseObjectList(llmResult.get("units"));
        if (units.isEmpty()) {
            units = parseObjectList(llmResult.get("examples"));
        }

        Set<Long> validKcIds = segmentKcs.stream()
                .map(kc -> longVal(kc.get("id")))
                .filter(java.util.Objects::nonNull)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> unit : units) {
            String rawText = stringVal(unit.get("raw_text"));
            String evidenceExcerpt = stringVal(unit.get("evidence_excerpt"));
            Integer pageStart = nullableIntVal(unit.get("page_range_start"));
            if (rawText.isBlank() || evidenceExcerpt.isBlank() || pageStart == null) {
                continue;
            }
            List<Long> kcIds = parseLongList(unit.get("kc_ids")).stream()
                    .filter(validKcIds::contains)
                    .distinct()
                    .toList();
            if (kcIds.isEmpty()) {
                continue;
            }
            Integer pageRangeEnd = nullableIntVal(unit.get("page_range_end"));
            int pageEnd = pageRangeEnd == null || pageRangeEnd < pageStart ? pageStart : pageRangeEnd;

            Map<String, Object> normalizedRow = new LinkedHashMap<>();
            normalizedRow.put("document_id", longVal(segment.get("document_id")));
            normalizedRow.put("document_title", stringVal(segment.get("document_title")));
            normalizedRow.put("chapter_title", stringVal(segment.get("chapter_title")));
            normalizedRow.put("chapter_index", intVal(segment.get("chapter_index")));
            normalizedRow.put("chapter_page_count", intVal(segment.get("chapter_page_count")));
            normalizedRow.put("raw_text", rawText);
            normalizedRow.put("normalized_body", stringVal(unit.get("normalized_body")));
            normalizedRow.put("input_description", stringVal(unit.get("input_description")));
            normalizedRow.put("output_description", stringVal(unit.get("output_description")));
            normalizedRow.put("evidence_excerpt", evidenceExcerpt);
            normalizedRow.put("page_range_start", pageStart);
            normalizedRow.put("page_range_end", pageEnd);
            normalizedRow.put("source_title", deriveSourceTitle(unit));
            normalizedRow.put("unit_type", normalizeUnitType(unit, rawText));
            normalizedRow.put("source_pages", buildSourcePages(pageStart, pageEnd));
            normalizedRow.put("kc_ids", kcIds);
            normalizedRow.put("task_signal_score", Math.max(intVal(segment.get("task_signal_score")), computeTaskSignalScore(rawText + "\n" + evidenceExcerpt)));
            normalized.add(normalizedRow);
        }
        return normalized;
    }

    private List<Map<String, Object>> mergeUnits(List<Map<String, Object>> extractedUnits) {
        List<Map<String, Object>> sorted = extractedUnits.stream()
                .sorted(Comparator
                        .comparing((Map<String, Object> row) -> longVal(row.get("document_id")))
                        .thenComparing(row -> intVal(row.get("chapter_index")))
                        .thenComparing(row -> normalizeKey(stringVal(row.get("source_title"))))
                        .thenComparing(row -> stringVal(row.get("unit_type")))
                        .thenComparing(row -> intVal(row.get("page_range_start"))))
                .toList();
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> unit : sorted) {
            if (merged.isEmpty()) {
                merged.add(new LinkedHashMap<>(unit));
                continue;
            }
            Map<String, Object> previous = merged.getLast();
            if (canMergeUnit(previous, unit)) {
                mergeUnitInto(previous, unit);
            } else {
                merged.add(new LinkedHashMap<>(unit));
            }
        }
        return merged;
    }

    private boolean canMergeUnit(Map<String, Object> previous, Map<String, Object> current) {
        if (!longVal(previous.get("document_id")).equals(longVal(current.get("document_id")))) {
            return false;
        }
        if (intVal(previous.get("chapter_index")) != intVal(current.get("chapter_index"))) {
            return false;
        }
        if (!stringVal(previous.get("unit_type")).equals(stringVal(current.get("unit_type")))) {
            return false;
        }
        return normalizeKey(stringVal(previous.get("source_title"))).equals(normalizeKey(stringVal(current.get("source_title"))))
                && pageRangeTouches(previous, current);
    }

    private void mergeUnitInto(Map<String, Object> previous, Map<String, Object> current) {
        previous.put("page_range_start", Math.min(intVal(previous.get("page_range_start")), intVal(current.get("page_range_start"))));
        previous.put("page_range_end", Math.max(intVal(previous.get("page_range_end")), intVal(current.get("page_range_end"))));
        previous.put("source_pages", unionIntegerLists(previous.get("source_pages"), current.get("source_pages")));
        previous.put("kc_ids", unionLongLists(previous.get("kc_ids"), current.get("kc_ids")));
        previous.put("raw_text", preferLongerText(previous.get("raw_text"), current.get("raw_text")));
        previous.put("normalized_body", preferLongerText(previous.get("normalized_body"), current.get("normalized_body")));
        previous.put("input_description", preferLongerText(previous.get("input_description"), current.get("input_description")));
        previous.put("output_description", preferLongerText(previous.get("output_description"), current.get("output_description")));
        previous.put("evidence_excerpt", preferLongerText(previous.get("evidence_excerpt"), current.get("evidence_excerpt")));
        previous.put("task_signal_score", Math.max(intVal(previous.get("task_signal_score")), intVal(current.get("task_signal_score"))));
    }

    private List<Map<String, Object>> persistUnits(Long taskId, Long languagePackId, List<Map<String, Object>> units) {
        List<Map<String, Object>> persisted = new ArrayList<>();
        for (Map<String, Object> unit : units) {
            String sourceTitle = stringVal(unit.get("source_title"));
            String unitType = stringVal(unit.get("unit_type"));
            String sourceSignature = buildSourceSignature(
                    intVal(unit.get("chapter_index")),
                    sourceTitle,
                    unitType,
                    intVal(unit.get("page_range_start")),
                    intVal(unit.get("page_range_end"))
            );
            Long exampleId = insertExample(
                    taskId,
                    languagePackId,
                    longVal(unit.get("document_id")),
                    unit,
                    sourceTitle,
                    unitType,
                    false,
                    "awaiting_oj_review",
                    sourceSignature
            );
            for (Long kcId : parseLongList(unit.get("kc_ids"))) {
                jdbcTemplate.update(
                        "INSERT INTO language_pack_example_kc_mapping(example_id, kc_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                        exampleId,
                        kcId
                );
            }

            Map<String, Object> row = new LinkedHashMap<>(unit);
            row.put("id", exampleId);
            row.put("oj_convertible", false);
            row.put("oj_block_reason", "awaiting_oj_review");
            row.put("source_signature", sourceSignature);
            persisted.add(row);
        }
        return persisted;
    }

    private void applyCandidateFlags(List<Map<String, Object>> candidates) {
        for (Map<String, Object> row : candidates) {
            Long exampleId = longVal(row.get("id"));
            if (exampleId == null) {
                continue;
            }
            jdbcTemplate.update(
                    """
                    UPDATE language_pack_example
                    SET oj_convertible = ?,
                        oj_block_reason = ?,
                        source_signature = ?
                    WHERE id = ?
                    """,
                    booleanVal(row.get("oj_convertible")),
                    stringVal(row.get("oj_block_reason")),
                    stringVal(row.get("source_signature")),
                    exampleId
            );
        }
    }

    private void clearExistingExamples(Long languagePackId) {
        jdbcTemplate.update(
                """
                DELETE FROM language_pack_example_kc_mapping
                WHERE example_id IN (SELECT id FROM language_pack_example WHERE language_pack_id = ?)
                """,
                languagePackId
        );
        jdbcTemplate.update("DELETE FROM language_pack_example WHERE language_pack_id = ?", languagePackId);
    }

    private Long insertExample(Long taskId,
                               Long languagePackId,
                               Long documentId,
                               Map<String, Object> unit,
                               String sourceTitle,
                               String unitType,
                               boolean ojConvertible,
                               String ojBlockReason,
                               String sourceSignature) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO language_pack_example(language_pack_id, init_task_id, document_id, raw_text, normalized_body,
                        input_description, output_description, evidence_excerpt,
                        page_range_start, page_range_end, source_title, unit_type,
                        oj_convertible, oj_block_reason, source_signature, create_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, languagePackId);
            ps.setLong(2, taskId);
            ps.setObject(3, documentId);
            ps.setString(4, stringVal(unit.get("raw_text")));
            ps.setString(5, stringVal(unit.get("normalized_body")));
            ps.setString(6, stringVal(unit.get("input_description")));
            ps.setString(7, stringVal(unit.get("output_description")));
            ps.setString(8, stringVal(unit.get("evidence_excerpt")));
            ps.setObject(9, intVal(unit.get("page_range_start")));
            ps.setObject(10, intVal(unit.get("page_range_end")));
            ps.setString(11, sourceTitle);
            ps.setString(12, unitType);
            ps.setBoolean(13, ojConvertible);
            ps.setString(14, ojBlockReason);
            ps.setString(15, sourceSignature);
            return ps;
        }, keyHolder);
        return ((Number) keyHolder.getKeys().get("id")).longValue();
    }

    private String buildUnitExtractionSystemPrompt() {
        return """
                You are a programming education analyst for non-CS beginners.
                Given one courseware segment and its related knowledge components, extract ONLY teaching units that can each stand alone as an independent OJ programming problem.

                Return exactly one JSON object and nothing else.
                No markdown, no code fence, no commentary.

                Output schema (top-level must contain only "units"):
                {
                  "units": [
                    {
                      "raw_text": "Original courseware text for the unit",
                      "normalized_body": "Cleaned-up unit body",
                      "input_description": "Expected input if the unit implies one",
                      "output_description": "Expected output if the unit implies one",
                      "evidence_excerpt": "Direct quote proving the unit exists",
                      "page_range_start": 12,
                      "page_range_end": 13,
                      "kc_ids": [10, 11],
                      "source_title": "4.18 九九乘法表",
                      "unit_type": "exercise"
                    }
                  ]
                }

                Hard constraints:
                - If no valid unit exists, return {"units": []}.
                - The top-level key must be exactly "units"; do not add any extra top-level keys.
                - Each unit object must contain exactly these keys:
                  raw_text, normalized_body, input_description, output_description, evidence_excerpt,
                  page_range_start, page_range_end, kc_ids, source_title, unit_type.
                - unit_type must be one of: code_snippet, worked_example, exercise, assignment, demo.
                - page_range_start/page_range_end must be integers and must stay within the provided segment pages.
                - kc_ids must be a non-empty integer array and only use IDs from the provided KC list.
                - Any double quote inside string values must be JSON-escaped as \\\".
                - Never output trailing commas.

                Extraction rules (precision-first — only OJ-worthy units):
                - Each unit MUST have a clear, self-contained programming task goal that a student could implement and submit.
                - A valid unit has: a describable input, a verifiable output, and enough specification to write test cases.
                - If a segment contains multiple independent exercises or examples, return each one separately.
                - Keep the visible numbered title when present, such as "4.18 九九乘法表".
                - Do not drop text-output, business-simulation, or data-structure tasks just because they look simple.

                What to EXCLUDE (critical — do not extract these):
                - Pure concept explanation pages or syntax reference pages with no task goal.
                - Single API demonstrations or code snippets that merely show how a function works.
                - Lecture slides that only define terms, show flowcharts, or list bullet points.
                - Multiple-choice questions, fill-in-the-blank questions, or true/false questions.
                - Code fragments embedded in explanatory text that are not self-contained tasks.
                - Table-of-contents pages and pure diagram pages.
                """;
    }

    private String buildUnitExtractionUserPrompt(Map<String, Object> segment,
                                                 List<Map<String, Object>> segmentPages,
                                                 List<Map<String, Object>> segmentKcs,
                                                 Map<String, Object> chapterMemory,
                                                 List<Map<String, Object>> canonicalKcs,
                                                 Map<String, Object> neighborContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Segment:\n");
        prompt.append("- document: ").append(stringVal(segment.get("document_title"))).append("\n");
        prompt.append("- chapter: ").append(stringVal(segment.get("chapter_title"))).append("\n");
        prompt.append("- segment_anchor: ").append(stringVal(segment.get("segment_anchor"))).append("\n");
        prompt.append("- source_pages: ").append(segment.get("source_pages")).append("\n");
        if (!chapterMemory.isEmpty()) {
            prompt.append("\nChapter memory:\n");
            prompt.append("- chapter_synopsis: ").append(stringVal(chapterMemory.get("chapter_synopsis"))).append("\n");
            prompt.append("- canonical_kc_count: ").append(chapterMemory.getOrDefault("canonical_kc_count", 0)).append("\n");
        }
        if (!canonicalKcs.isEmpty()) {
            prompt.append("\nCanonical KCs in this chapter:\n");
            for (Map<String, Object> kc : canonicalKcs) {
                prompt.append("- canonical_kc_id=").append(kc.getOrDefault("canonical_kc_id", ""))
                        .append(", canonical_name=").append(stringVal(kc.get("canonical_name")))
                        .append(", aliases=").append(kc.getOrDefault("aliases", List.of()))
                        .append("\n");
            }
        }
        if (!neighborContext.isEmpty()) {
            prompt.append("\nNeighbor segments:\n");
            Map<String, Object> previous = castMap(neighborContext.get("previous"));
            Map<String, Object> next = castMap(neighborContext.get("next"));
            if (!previous.isEmpty()) {
                prompt.append("- previous_segment_anchor: ").append(stringVal(previous.get("segment_anchor"))).append("\n");
            }
            if (!next.isEmpty()) {
                prompt.append("- next_segment_anchor: ").append(stringVal(next.get("segment_anchor"))).append("\n");
            }
        }
        prompt.append("\nAvailable KCs:\n");
        for (Map<String, Object> kc : segmentKcs) {
            prompt.append("- ").append(stringVal(kc.get("name")))
                    .append(" (id=").append(kc.get("id")).append(")\n");
        }
        prompt.append("\nSegment pages:\n");
        for (Map<String, Object> page : segmentPages) {
            prompt.append("\n--- Page ").append(page.get("page_no")).append(" ---\n");
            if (!stringVal(page.get("page_title")).isBlank()) {
                prompt.append("Title: ").append(stringVal(page.get("page_title"))).append("\n");
            }
            prompt.append(stringVal(page.get("page_text"))).append("\n");
        }
        prompt.append("\nStrict output reminder:\n");
        prompt.append("- Output a single JSON object only, with top-level key \"units\".\n");
        prompt.append("- Do not output markdown/code fence/analysis text.\n");
        prompt.append("- Keep exactly required unit keys and keep kc_ids/page_range valid.\n");
        prompt.append("- Escape any inner double quote in string values as \\\\\".\n");
        return prompt.toString();
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

    private Integer nullableIntVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException exception) {
            return null;
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

    private List<Map<String, Object>> parseObjectList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            rows.add(normalized);
        }
        return rows;
    }

    private List<Long> parseLongList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Long> values = new ArrayList<>();
        for (Object item : rawList) {
            Long parsed = longVal(item);
            if (parsed != null) {
                values.add(parsed);
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("json serialize failed", exception);
        }
    }

    private String replaceJsonArtifact(Long taskId, String artifactType, String sourceStage, Object payload) {
        return auditService.replaceJsonArtifact(taskId, artifactType, sourceStage, writeJson(payload));
    }

    private String buildPageFingerprint(Map<Long, List<Map<String, Object>>> pagesByDocument) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<Long, List<Map<String, Object>>> entry : pagesByDocument.entrySet()) {
            builder.append(entry.getKey()).append(':');
            for (Map<String, Object> page : entry.getValue()) {
                builder.append(page.get("page_no")).append('|');
            }
            builder.append(';');
        }
        return builder.toString();
    }

    private Map<String, Map<String, Object>> loadChapterMemoryByKey(Long taskId) {
        String contentJson = loadArtifactJson(taskId, "chapter_memory.json");
        if (contentJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(contentJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            List<Map<String, Object>> rows = parseObjectList(root.get("chapters"));
            Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
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

    private Map<String, List<Map<String, Object>>> loadCanonicalKcsByChapter(Long taskId) {
        String contentJson = loadArtifactJson(taskId, "kc_catalog.json");
        if (contentJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(contentJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            List<Map<String, Object>> rows = parseObjectList(root.get("kcs"));
            Map<String, List<Map<String, Object>>> indexed = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String key = chapterKey(row);
                if (key.isBlank()) {
                    continue;
                }
                indexed.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
            }
            return indexed;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("kc_catalog.json artifact is invalid JSON", exception);
        }
    }

    private Map<String, Map<String, Object>> buildNeighborContextBySegment(List<Map<String, Object>> segments) {
        List<Map<String, Object>> sorted = segments.stream()
                .sorted(Comparator
                        .comparing((Map<String, Object> row) -> longVal(row.get("document_id")))
                        .thenComparing(row -> intVal(row.get("page_range_start"))))
                .toList();
        Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        for (int index = 0; index < sorted.size(); index++) {
            Map<String, Object> segment = sorted.get(index);
            Map<String, Object> context = new LinkedHashMap<>();
            if (index > 0 && sameSegmentScope(sorted.get(index - 1), segment)) {
                context.put("previous", sorted.get(index - 1));
            }
            if (index + 1 < sorted.size() && sameSegmentScope(sorted.get(index + 1), segment)) {
                context.put("next", sorted.get(index + 1));
            }
            indexed.put(segmentKey(segment), context);
        }
        return indexed;
    }

    private boolean sameSegmentScope(Map<String, Object> left, Map<String, Object> right) {
        return longVal(left.get("document_id")).equals(longVal(right.get("document_id")))
                && intVal(left.get("chapter_index")) == intVal(right.get("chapter_index"));
    }

    private String buildUnitExtractionInputHash(Map<String, Object> segment,
                                                List<Map<String, Object>> segmentKcs,
                                                Map<String, Object> chapterMemory,
                                                List<Map<String, Object>> canonicalKcs,
                                                Map<String, Object> neighborContext) {
        StringBuilder builder = new StringBuilder();
        builder.append(segmentKey(segment)).append('|')
                .append(segment.get("source_pages")).append('|')
                .append(stringVal(chapterMemory.get("chapter_synopsis"))).append('|');
        for (Map<String, Object> kc : segmentKcs) {
            builder.append(longVal(kc.get("id"))).append(':').append(stringVal(kc.get("name"))).append('|');
        }
        for (Map<String, Object> kc : canonicalKcs) {
            builder.append(stringVal(kc.get("canonical_name"))).append('|');
        }
        Map<String, Object> previous = castMap(neighborContext.get("previous"));
        Map<String, Object> next = castMap(neighborContext.get("next"));
        builder.append("prev=").append(stringVal(previous.get("segment_anchor"))).append('|');
        builder.append("next=").append(stringVal(next.get("segment_anchor"))).append('|');
        return builder.toString();
    }

    private List<Map<String, Object>> parseStoredUnits(Object outputJson) {
        try {
            Map<String, Object> root = objectMapper.readValue(stringVal(outputJson), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            return parseObjectList(root.get("units"));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored extract-examples batch output is invalid JSON", exception);
        }
    }

    private String loadArtifactJson(Long taskId, String artifactType) {
        String contentJson = jdbcTemplate.query(
                """
                SELECT content_json
                FROM language_pack_init_artifact
                WHERE task_id = ? AND artifact_type = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                rs -> rs.next() ? rs.getString(1) : null,
                taskId,
                artifactType
        );
        return contentJson == null ? "" : contentJson;
    }

    private String segmentKey(Map<String, Object> segment) {
        return longVal(segment.get("document_id"))
                + "::" + intVal(segment.get("chapter_index"))
                + "::" + intVal(segment.get("page_range_start"))
                + "::" + intVal(segment.get("page_range_end"));
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

    private Map<Long, Map<Long, Map<String, Object>>> loadKcsByPageId(Long languagePackId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT m.page_id,
                       kc.id,
                       kc.name,
                       coalesce(ch.title, '') AS chapter_title,
                       coalesce(ch.chapter_index, 0) AS chapter_index
                FROM language_pack_kc_page_mapping m
                JOIN language_pack_kc kc ON kc.id = m.kc_id
                LEFT JOIN language_pack_chapter ch ON ch.id = kc.chapter_id
                WHERE kc.language_pack_id = ?
                ORDER BY ch.chapter_index, kc.id, m.page_id
                """,
                languagePackId
        );
        Map<Long, Map<Long, Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long pageId = longVal(row.get("page_id"));
            Long kcId = longVal(row.get("id"));
            if (pageId == null || kcId == null) {
                continue;
            }
            Map<String, Object> kcRow = new LinkedHashMap<>();
            kcRow.put("id", kcId);
            kcRow.put("name", stringVal(row.get("name")));
            kcRow.put("chapter_title", stringVal(row.get("chapter_title")));
            kcRow.put("chapter_index", intVal(row.get("chapter_index")));
            grouped.computeIfAbsent(pageId, ignored -> new LinkedHashMap<>()).putIfAbsent(kcId, kcRow);
        }
        return grouped;
    }

    private Map<Long, List<Map<String, Object>>> groupPagesByDocument(List<Map<String, Object>> pages) {
        Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> page : pages) {
            Long documentId = longVal(page.get("document_id"));
            if (documentId == null) {
                continue;
            }
            grouped.computeIfAbsent(documentId, ignored -> new ArrayList<>()).add(page);
        }
        return grouped;
    }

    private List<List<Map<String, Object>>> partitionPagesWithOverlap(List<Map<String, Object>> pages, int windowSize, int overlap) {
        if (pages.isEmpty()) {
            return List.of();
        }
        int step = Math.max(1, windowSize - overlap);
        List<List<Map<String, Object>>> windows = new ArrayList<>();
        for (int start = 0; start < pages.size(); start += step) {
            int end = Math.min(start + windowSize, pages.size());
            windows.add(new ArrayList<>(pages.subList(start, end)));
            if (end == pages.size()) {
                break;
            }
        }
        return windows;
    }

    private List<Map<String, Object>> materializeSegmentPages(Map<String, Object> segment,
                                                              Map<Long, List<Map<String, Object>>> pagesByDocument) {
        Long documentId = longVal(segment.get("document_id"));
        List<Integer> sourcePages = parseIntegerList(segment.get("source_pages"));
        if (documentId == null || sourcePages.isEmpty()) {
            return List.of();
        }
        Set<Integer> allowedPages = new LinkedHashSet<>(sourcePages);
        return pagesByDocument.getOrDefault(documentId, List.of()).stream()
                .filter(page -> allowedPages.contains(intVal(page.get("page_no"))))
                .toList();
    }

    private List<Integer> parseIntegerList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Integer> values = new ArrayList<>();
        for (Object item : rawList) {
            Integer parsed = null;
            if (item instanceof Number number) {
                parsed = number.intValue();
            } else if (item != null) {
                try {
                    parsed = Integer.parseInt(String.valueOf(item).strip());
                } catch (NumberFormatException ignored) {
                    parsed = null;
                }
            }
            if (parsed != null) {
                values.add(parsed);
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private List<Map<String, Object>> resolveBatchKcs(List<Map<String, Object>> batch,
                                                      Map<Long, Map<Long, Map<String, Object>>> kcsByPageId) {
        LinkedHashMap<Long, Map<String, Object>> resolved = new LinkedHashMap<>();
        for (Map<String, Object> page : batch) {
            Long pageId = longVal(page.get("id"));
            if (pageId == null) {
                continue;
            }
            Map<Long, Map<String, Object>> pageKcs = kcsByPageId.get(pageId);
            if (pageKcs == null) {
                continue;
            }
            resolved.putAll(pageKcs);
        }
        return List.copyOf(resolved.values());
    }

    private Map<Integer, Map<String, Object>> buildChapterStats(List<Map<String, Object>> pages,
                                                                Map<Long, Map<Long, Map<String, Object>>> kcsByPageId) {
        Map<Integer, Map<String, Object>> stats = new LinkedHashMap<>();
        for (Map<String, Object> page : pages) {
            Map<String, Object> primaryChapter = resolvePrimaryChapter(List.of(page), kcsByPageId, stats);
            int chapterIndex = intVal(primaryChapter.get("chapter_index"));
            if (chapterIndex == 0) {
                continue;
            }
            Map<String, Object> stat = stats.computeIfAbsent(chapterIndex, ignored -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("chapter_index", chapterIndex);
                row.put("chapter_title", stringVal(primaryChapter.get("chapter_title")));
                row.put("chapter_page_count", 0);
                return row;
            });
            stat.put("chapter_page_count", intVal(stat.get("chapter_page_count")) + 1);
        }
        return stats;
    }

    private Map<String, Object> resolvePrimaryChapter(List<Map<String, Object>> pages,
                                                      Map<Long, Map<Long, Map<String, Object>>> kcsByPageId,
                                                      Map<Integer, Map<String, Object>> chapterStatsByIndex) {
        Map<Integer, Integer> chapterCounts = new LinkedHashMap<>();
        Map<Integer, String> chapterTitles = new LinkedHashMap<>();
        for (Map<String, Object> page : pages) {
            Long pageId = longVal(page.get("id"));
            if (pageId == null) {
                continue;
            }
            Map<Long, Map<String, Object>> pageKcs = kcsByPageId.get(pageId);
            if (pageKcs == null) {
                continue;
            }
            for (Map<String, Object> kc : pageKcs.values()) {
                int chapterIndex = intVal(kc.get("chapter_index"));
                if (chapterIndex == 0) {
                    continue;
                }
                chapterCounts.merge(chapterIndex, 1, Integer::sum);
                chapterTitles.putIfAbsent(chapterIndex, stringVal(kc.get("chapter_title")));
            }
        }
        if (chapterCounts.isEmpty()) {
            return Map.of("chapter_index", 0, "chapter_title", "", "chapter_page_count", 0);
        }
        int bestChapter = chapterCounts.entrySet().stream()
                .max(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(entry -> -entry.getKey()))
                .map(Map.Entry::getKey)
                .orElse(0);
        Map<String, Object> stats = chapterStatsByIndex.get(bestChapter);
        return Map.of(
                "chapter_index", bestChapter,
                "chapter_title", chapterTitles.getOrDefault(bestChapter, ""),
                "chapter_page_count", stats == null ? 0 : intVal(stats.get("chapter_page_count"))
        );
    }

    private String deriveSegmentAnchor(List<Map<String, Object>> window) {
        for (Map<String, Object> page : window) {
            String candidate = firstTaskLine(stringVal(page.get("page_title")));
            if (!candidate.isBlank()) {
                return candidate;
            }
            candidate = firstTaskLine(stringVal(page.get("page_text")));
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private String firstTaskLine(String rawText) {
        if (rawText.isBlank()) {
            return "";
        }
        for (String line : rawText.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isBlank()) {
                continue;
            }
            if (hasTaskSignal(trimmed)) {
                return trimmed;
            }
        }
        return "";
    }

    private String describeWindowText(List<Map<String, Object>> window) {
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> page : window) {
            builder.append(stringVal(page.get("page_title"))).append('\n');
            builder.append(stringVal(page.get("page_text"))).append('\n');
        }
        return builder.toString();
    }

    private boolean hasTaskSignal(String rawText) {
        return computeTaskSignalScore(rawText) > 0;
    }

    private int computeTaskSignalScore(String rawText) {
        int score = 0;
        String text = stringVal(rawText);
        for (String keyword : TASK_SIGNAL_KEYWORDS) {
            if (text.contains(keyword)) {
                score++;
            }
        }
        return score;
    }

    private boolean pageRangeTouches(Map<String, Object> left, Map<String, Object> right) {
        int leftEnd = intVal(left.get("page_range_end"));
        int rightStart = intVal(right.get("page_range_start"));
        return rightStart <= leftEnd + 1;
    }

    private String deriveSourceTitle(Map<String, Object> unit) {
        String sourceTitle = stringVal(unit.get("source_title"));
        String rawText = stringVal(unit.get("raw_text"));
        String firstLine = rawText.contains("\n") ? rawText.substring(0, rawText.indexOf('\n')) : rawText;
        if (sourceTitle.isBlank()) {
            return firstLine.isBlank() ? "未命名教学单元" : firstLine;
        }
        if (firstLine.isBlank()) {
            return sourceTitle;
        }

        String sourceTitleCore = normalizeTitleCore(sourceTitle);
        String firstLineCore = normalizeTitleCore(firstLine);
        boolean sameTaskTitle = !sourceTitleCore.isBlank()
                && !firstLineCore.isBlank()
                && (sourceTitleCore.equals(firstLineCore)
                || sourceTitleCore.contains(firstLineCore)
                || firstLineCore.contains(sourceTitleCore));
        if (sameTaskTitle) {
            return sourceTitle;
        }
        if (hasTaskSignal(firstLine)) {
            return firstLine;
        }
        return sourceTitle;
    }

    private String normalizeTitleCore(String title) {
        String normalized = normalizeKey(title);
        if (normalized.isBlank()) {
            return "";
        }
        normalized = normalized.replaceFirst("^第?[0-9一二三四五六七八九十百千]+章", "");
        normalized = normalized.replaceFirst("^[0-9]+(\\.[0-9]+)*", "");

        List<String> prefixes = List.of("举例", "示例", "练习", "例题", "问题解析", "程序设计实例", "案例");
        boolean changed = true;
        while (changed && !normalized.isBlank()) {
            changed = false;
            for (String prefix : prefixes) {
                String normalizedPrefix = normalizeKey(prefix);
                if (!normalizedPrefix.isBlank() && normalized.startsWith(normalizedPrefix)) {
                    normalized = normalized.substring(normalizedPrefix.length());
                    changed = true;
                    break;
                }
            }
        }
        return normalized.replaceFirst("^[：:，,。\\-—_]+", "");
    }

    private String normalizeUnitType(Map<String, Object> unit, String rawText) {
        String requested = stringVal(unit.get("unit_type")).toLowerCase(Locale.ROOT);
        if (List.of("code_snippet", "worked_example", "exercise", "assignment", "demo").contains(requested)) {
            return requested;
        }
        if (rawText.contains("上机作业")) {
            return "assignment";
        }
        if (rawText.contains("练习")) {
            return "exercise";
        }
        if (rawText.contains("示例") || rawText.contains("举例") || rawText.contains("程序设计实例")) {
            return "worked_example";
        }
        return "demo";
    }

    private List<Integer> buildSourcePages(int start, int end) {
        List<Integer> pages = new ArrayList<>();
        int actualEnd = Math.max(start, end);
        for (int pageNo = start; pageNo <= actualEnd; pageNo++) {
            pages.add(pageNo);
        }
        return pages;
    }

    private List<Integer> unionIntegerLists(Object left, Object right) {
        LinkedHashSet<Integer> merged = new LinkedHashSet<>(parseIntegerList(left));
        merged.addAll(parseIntegerList(right));
        return List.copyOf(merged);
    }

    private List<Long> unionLongLists(Object left, Object right) {
        LinkedHashSet<Long> merged = new LinkedHashSet<>(parseLongList(left));
        merged.addAll(parseLongList(right));
        return List.copyOf(merged);
    }

    private String preferLongerText(Object left, Object right) {
        String leftText = stringVal(left);
        String rightText = stringVal(right);
        return rightText.length() > leftText.length() ? rightText : leftText;
    }

    private OjJudgement judgeUnit(Map<String, Object> unit) {
        String sourceTitle = stringVal(unit.get("source_title"));
        String rawText = stringVal(unit.get("raw_text"));
        String evidenceExcerpt = stringVal(unit.get("evidence_excerpt"));
        String inputDescription = stringVal(unit.get("input_description"));
        String outputDescription = stringVal(unit.get("output_description"));
        String unitType = stringVal(unit.get("unit_type"));
        String combined = String.join("\n", sourceTitle, rawText, evidenceExcerpt, inputDescription, outputDescription);

        int taskSignalScore = Math.max(intVal(unit.get("task_signal_score")), computeTaskSignalScore(combined));
        boolean noInputDeclared = declaresNoInput(inputDescription) || declaresNoInput(combined);
        boolean hasExplicitInputSignal = (!inputDescription.isBlank() && !declaresNoInput(inputDescription))
                || (combined.contains("输入") && !declaresNoInput(combined));
        boolean explicitIo = !inputDescription.isBlank() || !outputDescription.isBlank()
                || combined.contains("输入")
                || combined.contains("输出")
                || combined.contains("打印")
                || combined.contains("显示");
        boolean strongOjCategory = containsAny(combined, STRONG_OJ_KEYWORDS);
        boolean conceptOnly = containsAny(combined, CONCEPT_ONLY_KEYWORDS) && !explicitIo && taskSignalScore == 0;
        boolean genericTitle = isGenericTitle(sourceTitle);
        boolean hardNonConvertible = containsAny(combined, NON_STDIN_STDOUT_KEYWORDS);
        boolean parameterizableOutputOnly = noInputDeclared
                && !hardNonConvertible
                && containsAny(combined, PARAMETERIZABLE_OUTPUT_KEYWORDS);

        boolean stdinStdoutConvertible = !conceptOnly
                && !("demo".equals(unitType) && taskSignalScore == 0)
                && !hardNonConvertible
                && (hasExplicitInputSignal
                || parameterizableOutputOnly
                || (!noInputDeclared && (explicitIo || strongOjCategory || taskSignalScore >= 2)));

        boolean ojConvertible = stdinStdoutConvertible && ("assignment".equals(unitType)
                || "exercise".equals(unitType)
                || "worked_example".equals(unitType)
                || explicitIo
                || strongOjCategory
                || taskSignalScore >= 2);

        if (conceptOnly && !strongOjCategory) {
            ojConvertible = false;
        }

        boolean reviewRequired = !ojConvertible
                && stdinStdoutConvertible
                && (taskSignalScore > 0 || strongOjCategory || genericTitle);
        String reviewReason = "";
        if (reviewRequired) {
            if (strongOjCategory) {
                reviewReason = "strong_oj_category";
            } else if (genericTitle) {
                reviewReason = "generic_title_requires_review";
            } else {
                reviewReason = "task_signal_requires_review";
            }
        }
        String ojBlockReason;
        if (ojConvertible) {
            ojBlockReason = "";
        } else if (conceptOnly) {
            ojBlockReason = "concept_only";
        } else if ("demo".equals(unitType) && taskSignalScore == 0) {
            ojBlockReason = "api_demo_without_task";
        } else if (!stdinStdoutConvertible && (taskSignalScore > 0 || explicitIo || strongOjCategory)) {
            ojBlockReason = "not_stdin_stdout_convertible";
        } else {
            ojBlockReason = "insufficient_task_goal";
        }
        return new OjJudgement(ojConvertible, stdinStdoutConvertible, ojBlockReason, reviewRequired, reviewReason, taskSignalScore);
    }

    private boolean shouldPromoteDuringEscalation(Map<String, Object> candidate) {
        if ("not_stdin_stdout_convertible".equals(stringVal(candidate.get("oj_block_reason")))) {
            return false;
        }
        String combined = String.join(
                "\n",
                stringVal(candidate.get("source_title")),
                stringVal(candidate.get("raw_text")),
                stringVal(candidate.get("evidence_excerpt")),
                stringVal(candidate.get("input_description")),
                stringVal(candidate.get("output_description"))
        );
        return containsAny(combined, STRONG_OJ_KEYWORDS)
                || computeTaskSignalScore(combined) >= 2
                || (isGenericTitle(stringVal(candidate.get("source_title"))) && hasTaskSignal(combined));
    }

    private boolean containsAny(String rawText, List<String> keywords) {
        String text = stringVal(rawText);
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean declaresNoInput(String rawText) {
        String normalized = normalizeKey(rawText);
        return normalized.contains("无输入")
                || normalized.contains("无显式输入")
                || normalized.contains("无需输入")
                || normalized.contains("不需要输入")
                || normalized.contains("无外部输入")
                || normalized.contains("没有输入")
                || normalized.contains("noinput");
    }

    private boolean isGenericTitle(String title) {
        String normalized = normalizeKey(title);
        if (normalized.isBlank()) {
            return true;
        }
        for (String keyword : GENERIC_TITLE_KEYWORDS) {
            String normalizedKeyword = normalizeKey(keyword);
            if (normalized.equals(normalizedKeyword)) {
                return true;
            }
            if (normalized.endsWith(normalizedKeyword) && normalized.length() <= normalizedKeyword.length() + 4) {
                return true;
            }
        }
        return false;
    }

    private int countOjCandidates(List<Map<String, Object>> rows) {
        return (int) rows.stream().filter(this::isOjConvertible).count();
    }

    private boolean isOjConvertible(Map<String, Object> row) {
        return booleanVal(row.get("oj_convertible"));
    }

    private Map<String, Map<String, Object>> buildCandidateChapterStats(List<Map<String, Object>> candidates,
                                                                        Map<Integer, Map<String, Object>> chapterStatsByIndex) {
        Map<String, Map<String, Object>> stats = new LinkedHashMap<>();
        for (Map<String, Object> chapterStat : chapterStatsByIndex.values()) {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("chapter_title", stringVal(chapterStat.get("chapter_title")));
            created.put("chapter_index", intVal(chapterStat.get("chapter_index")));
            created.put("chapter_page_count", intVal(chapterStat.get("chapter_page_count")));
            created.put("unit_count", 0);
            created.put("oj_candidate_count", 0);
            created.put("convertible_unit_count", 0);
            created.put("non_convertible_unit_count", 0);
            created.put("chapter_has_task_signal", false);
            created.put("blocked_by_reason", new LinkedHashMap<String, Integer>());
            stats.put(chapterKey(created), created);
        }
        for (Map<String, Object> row : candidates) {
            String chapterKey = chapterKey(row);
            Map<String, Object> stat = stats.computeIfAbsent(chapterKey, ignored -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("chapter_title", stringVal(row.get("chapter_title")));
                created.put("chapter_index", intVal(row.get("chapter_index")));
                created.put("chapter_page_count", intVal(row.get("chapter_page_count")));
                created.put("unit_count", 0);
                created.put("oj_candidate_count", 0);
                created.put("convertible_unit_count", 0);
                created.put("non_convertible_unit_count", 0);
                created.put("chapter_has_task_signal", false);
                created.put("blocked_by_reason", new LinkedHashMap<String, Integer>());
                return created;
            });
            stat.put("unit_count", intVal(stat.get("unit_count")) + 1);
            stat.put("chapter_has_task_signal", booleanVal(stat.get("chapter_has_task_signal")) || intVal(row.get("task_signal_score")) > 0);
            if (booleanVal(row.get("oj_convertible"))) {
                stat.put("oj_candidate_count", intVal(stat.get("oj_candidate_count")) + 1);
            }
            if (booleanVal(row.get("stdin_stdout_convertible")) || booleanVal(row.get("oj_convertible"))) {
                stat.put("convertible_unit_count", intVal(stat.get("convertible_unit_count")) + 1);
            } else {
                stat.put("non_convertible_unit_count", intVal(stat.get("non_convertible_unit_count")) + 1);
            }
            if (!booleanVal(row.get("oj_convertible"))) {
                @SuppressWarnings("unchecked")
                Map<String, Integer> blockedByReason = (Map<String, Integer>) stat.get("blocked_by_reason");
                blockedByReason.merge(stringVal(row.get("oj_block_reason")), 1, Integer::sum);
            }
        }
        return stats;
    }

    private List<Map<String, Object>> buildHighRiskChapterList(List<Map<String, Object>> candidates,
                                                               Map<Integer, Map<String, Object>> chapterStatsByIndex) {
        return buildCandidateChapterStats(candidates, chapterStatsByIndex).values().stream()
                .filter(this::isHighRiskChapter)
                .map(stat -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("chapter_title", stringVal(stat.get("chapter_title")));
                    row.put("chapter_index", intVal(stat.get("chapter_index")));
                    row.put("chapter_page_count", intVal(stat.get("chapter_page_count")));
                    row.put("oj_candidate_count", intVal(stat.get("oj_candidate_count")));
                    row.put("unit_count", intVal(stat.get("unit_count")));
                    row.put("reason", "chapter_has_many_pages_but_no_oj_candidate");
                    return row;
                })
                .toList();
    }

    private boolean isHighRiskChapter(Map<String, Object> stat) {
        return intVal(stat.get("chapter_page_count")) >= 8
                && intVal(stat.get("oj_candidate_count")) == 0
                && booleanVal(stat.get("chapter_has_task_signal"))
                && intVal(stat.get("convertible_unit_count")) > 0;
    }

    private Map<String, Object> toCandidateSummary(Map<String, Object> row) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", longVal(row.get("id")));
        summary.put("chapter_title", stringVal(row.get("chapter_title")));
        summary.put("chapter_index", intVal(row.get("chapter_index")));
        summary.put("source_title", stringVal(row.get("source_title")));
        summary.put("page_range_start", intVal(row.get("page_range_start")));
        summary.put("page_range_end", intVal(row.get("page_range_end")));
        summary.put("oj_block_reason", stringVal(row.get("oj_block_reason")));
        summary.put("review_reason", stringVal(row.get("review_reason")));
        return summary;
    }

    private Map<String, Object> resolveChapterMemoryForSegment(
            Map<String, Object> segment,
            Map<String, Map<String, Object>> chapterMemoryByKey
    ) {
        String segmentChapterKey = chapterKey(segment);
        if (!segmentChapterKey.isBlank() && chapterMemoryByKey.containsKey(segmentChapterKey)) {
            return chapterMemoryByKey.get(segmentChapterKey);
        }
        Long documentId = longVal(segment.get("document_id"));
        if (documentId == null) {
            return Map.of();
        }
        List<Map<String, Object>> sameDocumentRows = chapterMemoryByKey.values().stream()
                .filter(row -> documentId.equals(longVal(row.get("document_id"))))
                .toList();
        if (sameDocumentRows.isEmpty()) {
            return Map.of();
        }
        if (sameDocumentRows.size() == 1) {
            return sameDocumentRows.getFirst();
        }
        int pageStart = intVal(segment.get("page_range_start"));
        int pageEnd = intVal(segment.get("page_range_end"));
        return sameDocumentRows.stream()
                .min(Comparator.comparingInt(row -> chapterDistance(row, pageStart, pageEnd)))
                .orElse(Map.of());
    }

    private List<Map<String, Object>> resolveCanonicalKcsForSegment(
            Map<String, Object> segment,
            Map<String, List<Map<String, Object>>> canonicalKcsByChapter
    ) {
        String segmentChapterKey = chapterKey(segment);
        if (!segmentChapterKey.isBlank() && canonicalKcsByChapter.containsKey(segmentChapterKey)) {
            return canonicalKcsByChapter.get(segmentChapterKey);
        }
        Long documentId = longVal(segment.get("document_id"));
        if (documentId == null) {
            return List.of();
        }
        LinkedHashSet<Map<String, Object>> merged = new LinkedHashSet<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : canonicalKcsByChapter.entrySet()) {
            for (Map<String, Object> row : entry.getValue()) {
                if (documentId.equals(longVal(row.get("document_id")))) {
                    merged.add(row);
                }
            }
        }
        return List.copyOf(merged);
    }

    private int chapterDistance(Map<String, Object> chapterMemory, int pageStart, int pageEnd) {
        int chapterStart = intVal(chapterMemory.get("page_range_start"));
        int chapterEnd = intVal(chapterMemory.get("page_range_end"));
        if (pageEnd < chapterStart) {
            return chapterStart - pageEnd;
        }
        if (pageStart > chapterEnd) {
            return pageStart - chapterEnd;
        }
        return 0;
    }

    private String chapterKey(Map<String, Object> row) {
        Long documentId = longVal(row.get("document_id"));
        int chapterIndex = intVal(row.get("chapter_index"));
        if (documentId != null && chapterIndex > 0) {
            return documentId + "::" + chapterIndex;
        }
        if (chapterIndex > 0) {
            return "chapter::" + chapterIndex;
        }
        if (documentId != null) {
            return documentId + "::0";
        }
        return "";
    }

    private String advanceExtractionStage(Long taskId, String currentStage, String expectedCurrent, String targetStage) {
        if (expectedCurrent.equals(currentStage)) {
            initService.advanceStage(taskId, targetStage);
            return targetStage;
        }
        return currentStage;
    }

    private String buildSourceSignature(int chapterIndex,
                                        String sourceTitle,
                                        String unitType,
                                        int pageRangeStart,
                                        int pageRangeEnd) {
        return "chapter:" + chapterIndex
                + "|title:" + sourceTitle
                + "|pages:" + pageRangeStart + "-" + pageRangeEnd
                + "|type:" + unitType;
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

    private record OjJudgement(
            boolean ojConvertible,
            boolean stdinStdoutConvertible,
            String ojBlockReason,
            boolean reviewRequired,
            String reviewReason,
            int taskSignalScore
    ) {
    }
}
