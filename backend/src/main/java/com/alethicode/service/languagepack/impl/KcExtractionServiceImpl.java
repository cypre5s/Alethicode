package com.alethicode.service.languagepack.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.exception.BadRequestException;
import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.languagepack.KcExtractionService;
import com.alethicode.service.languagepack.LanguagePackInitAuditService;
import com.alethicode.service.languagepack.LanguagePackInitExecutionService;
import com.alethicode.service.languagepack.LanguagePackInitService;
import com.alethicode.util.BoundedParallel;
import com.alethicode.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
public class KcExtractionServiceImpl implements KcExtractionService {

    private static final Logger log = LoggerFactory.getLogger(KcExtractionServiceImpl.class);

    private static final int ROOT_WINDOW_SIZE = 16;
    private static final int WINDOW_OVERLAP = 2;
    private static final String STAGE_NAME = "extract-kcs";

    private final JdbcTemplate jdbcTemplate;
    private final LanguagePackInitService initService;
    private final LanguagePackInitExecutionService executionService;
    private final LanguagePackInitAuditService auditService;
    private final LanguagePackInitBatchRunStore batchRunStore;
    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;
    private final int kcExtractConcurrency;

    public KcExtractionServiceImpl(JdbcTemplate jdbcTemplate,
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
        this.kcExtractConcurrency = properties.getLanguagePack().getConcurrency().getKcExtract();
    }

    @Override
    public void extractChaptersAndKcs(Long taskId) {
        String currentStage = jdbcTemplate.queryForObject(
                "SELECT stage FROM language_pack_init_task WHERE id = ?",
                String.class,
                taskId
        );
        if (currentStage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Init task not found");
        }
        if (!List.of("parsing", "kc_ready", "failed").contains(currentStage)) {
            throw new BadRequestException("Cannot extract KCs in stage: " + currentStage);
        }
        if ("failed".equals(currentStage) && !hasResumableKcContext(taskId)) {
            throw new BadRequestException("Cannot resume KC extraction in stage: failed");
        }

        Long languagePackId = jdbcTemplate.queryForObject(
                "SELECT language_pack_id FROM language_pack_init_task WHERE id = ?",
                Long.class,
                taskId
        );

        List<Map<String, Object>> pages = jdbcTemplate.queryForList(
                """
                SELECT p.id, p.document_id, p.page_no, p.page_text, p.text_hash, d.original_filename
                FROM language_pack_page p
                JOIN language_pack_document d ON d.id = p.document_id
                WHERE p.language_pack_id = ?
                ORDER BY d.sort_order, d.id, p.page_no
                """,
                languagePackId
        );
        if (pages.isEmpty()) {
            initService.failTask(taskId, "No pages available for KC extraction");
            throw new BusinessException(ErrorCode.ERROR, "No pages available for KC extraction");
        }

        List<DocumentChapter> documentChapters = materializeDocumentChapters(pages);
        executionService.beginStep(
                taskId,
                "kc_ready",
                "开始抽取知识点，共 " + documentChapters.size() + " 个章节，" + pages.size() + " 页",
                0,
                documentChapters.size()
        );
        Long batchRunId = auditService.startAgentRun(
                taskId,
                "KcBatchExtractionAgent",
                currentStage,
                "kc-batch-extraction.v3",
                buildPageFingerprint(documentChapters)
        );

        int totalChapters = documentChapters.size();
        java.util.concurrent.atomic.AtomicInteger completedChapters = new java.util.concurrent.atomic.AtomicInteger(0);

        List<Map<String, Object>> batchResults;
        try {
            List<List<Map<String, Object>>> perChapterResults = BoundedParallel.map(
                    documentChapters,
                    kcExtractConcurrency,
                    chapter -> {
                        List<List<Map<String, Object>>> windows = partitionPagesWithOverlap(chapter.pages(), ROOT_WINDOW_SIZE, WINDOW_OVERLAP);
                        List<List<Map<String, Object>>> windowResults = BoundedParallel.map(
                                windows,
                                Math.max(2, kcExtractConcurrency),
                                window -> processBatch(taskId, chapter, window, ROOT_WINDOW_SIZE)
                        );
                        List<Map<String, Object>> chapterBatches = new ArrayList<>();
                        for (List<Map<String, Object>> windowResult : windowResults) {
                            chapterBatches.addAll(windowResult);
                        }
                        int done = completedChapters.incrementAndGet();
                        executionService.reportProgress(taskId, "kc_ready", "知识点抽取进度 " + done + "/" + totalChapters, done, totalChapters);
                        return chapterBatches;
                    }
            );
            batchResults = new ArrayList<>();
            for (List<Map<String, Object>> chapterBatches : perChapterResults) {
                batchResults.addAll(chapterBatches);
            }
            if (batchResults.isEmpty()) {
                throw new IllegalStateException("No valid KC batches extracted from course content");
            }
            String batchHash = auditService.replaceJsonArtifact(
                    taskId,
                    "kc_batch_results.json",
                    "kc_ready",
                    writeJson(Map.of("batches", batchResults))
            );
            auditService.completeAgentRun(batchRunId, batchHash);
        } catch (Exception exception) {
            auditService.failAgentRun(batchRunId, exception.getMessage());
            initService.failTask(taskId, "KC batch extraction failed: " + exception.getMessage());
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "KC batch extraction failed: " + exception.getMessage(),
                    exception
            );
        }

        Long reconciliationRunId = auditService.startAgentRun(
                taskId,
                "KcReconciliationAgent",
                currentStage,
                "kc-reconciliation.v1",
                writeJson(Map.of("batches", batchResults))
        );
        try {
            ReconciliationResult reconciliation = reconcileChapters(batchResults, documentChapters);
            if (reconciliation.catalogRows().isEmpty()) {
                throw new IllegalStateException("No canonical KCs extracted from batch results");
            }

            clearExistingKnowledgeExtraction(languagePackId);
            Map<Integer, Long> chapterIdByIndex = insertChapters(taskId, languagePackId, documentChapters);
            List<Map<String, Object>> storedCatalogRows = insertCanonicalKcs(languagePackId, taskId, reconciliation.catalogRows(), chapterIdByIndex, documentChapters);

            long distinctKcCount = storedCatalogRows.stream()
                    .map(row -> longVal(row.get("canonical_kc_id")))
                    .distinct()
                    .count();
            jdbcTemplate.update(
                    "UPDATE language_pack SET chapter_count = ?, kc_count = ?, update_time = now() WHERE id = ?",
                    documentChapters.size(),
                    distinctKcCount,
                    languagePackId
            );

            String chapterMemoryHash = auditService.replaceJsonArtifact(
                    taskId,
                    "chapter_memory.json",
                    "kc_ready",
                    writeJson(Map.of("chapters", reconciliation.chapterMemoryRows()))
            );
            String catalogHash = auditService.replaceJsonArtifact(
                    taskId,
                    "kc_catalog.json",
                    "kc_ready",
                    writeJson(Map.of("kcs", storedCatalogRows))
            );
            auditService.completeAgentRun(reconciliationRunId, HashUtils.sha256(chapterMemoryHash + ":" + catalogHash));

            if ("parsing".equals(currentStage)) {
                initService.advanceStage(taskId, "kc_ready");
            } else if ("failed".equals(currentStage)) {
                initService.restoreStage(taskId, "kc_ready", "Stage restored after resumable KC extraction");
            }

            log.info(
                    "KC extraction completed for task {}: chapters={}, kcs={}, reused_batches={}",
                    taskId,
                    documentChapters.size(),
                    distinctKcCount,
                    batchRunStore.countByStatus(taskId, STAGE_NAME, "reused")
            );
            executionService.finishStep(taskId, "kc_ready", "知识点抽取完成，共 " + distinctKcCount + " 个知识点");
        } catch (Exception exception) {
            auditService.failAgentRun(reconciliationRunId, exception.getMessage());
            initService.failTask(taskId, "KC reconciliation failed: " + exception.getMessage());
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "KC reconciliation failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private boolean hasResumableKcContext(Long taskId) {
        Integer pageCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM language_pack_page
                WHERE language_pack_id = (SELECT language_pack_id FROM language_pack_init_task WHERE id = ?)
                """,
                Integer.class,
                taskId
        );
        if (pageCount == null || pageCount == 0) {
            return false;
        }
        Integer batchCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_init_batch_run WHERE task_id = ? AND stage_name = ?",
                Integer.class,
                taskId,
                STAGE_NAME
        );
        return batchCount != null && batchCount > 0;
    }

    private List<DocumentChapter> materializeDocumentChapters(List<Map<String, Object>> pages) {
        Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> page : pages) {
            Long documentId = longVal(page.get("document_id"));
            if (documentId == null) {
                continue;
            }
            grouped.computeIfAbsent(documentId, ignored -> new ArrayList<>()).add(page);
        }

        List<DocumentChapter> chapters = new ArrayList<>();
        int chapterIndex = 0;
        for (Map.Entry<Long, List<Map<String, Object>>> entry : grouped.entrySet()) {
            List<Map<String, Object>> documentPages = entry.getValue();
            if (documentPages.isEmpty()) {
                continue;
            }
            chapterIndex++;
            String filename = stringVal(documentPages.getFirst().get("original_filename"));
            chapters.add(new DocumentChapter(
                    entry.getKey(),
                    filename,
                    stripExtension(filename),
                    chapterIndex,
                    documentPages
            ));
        }
        return chapters;
    }

    private List<Map<String, Object>> processBatch(Long taskId,
                                                   DocumentChapter chapter,
                                                   List<Map<String, Object>> batchPages,
                                                   int requestedWindowSize) {
        if (batchPages.isEmpty()) {
            return List.of();
        }
        int batchStartPage = intVal(batchPages.getFirst().get("page_no"));
        int batchEndPage = intVal(batchPages.getLast().get("page_no"));
        String inputHash = buildBatchInputHash(chapter, batchPages, requestedWindowSize);

        Map<String, Object> reusable = batchRunStore.findReusableBatch(
                taskId,
                STAGE_NAME,
                chapter.documentId(),
                chapter.chapterIndex(),
                batchStartPage,
                batchEndPage,
                inputHash
        );
        if (reusable != null) {
            Map<String, Object> reusedRow = batchRunStore.recordReuseFrom(reusable);
            return List.of(parseBatchArtifact(reusedRow, chapter));
        }

        Map<String, Object> splitRow = batchRunStore.findSplitBatch(
                taskId,
                STAGE_NAME,
                chapter.documentId(),
                chapter.chapterIndex(),
                batchStartPage,
                batchEndPage,
                inputHash
        );
        if (splitRow != null) {
            return processChildren(taskId, chapter, batchPages);
        }

        Long runId = batchRunStore.startBatchRun(
                taskId,
                STAGE_NAME,
                chapter.documentId(),
                chapter.chapterIndex(),
                batchStartPage,
                batchEndPage,
                requestedWindowSize,
                batchPages.size(),
                inputHash
        );
        try {
            String batchContext = describeBatchContext(chapter.filename(), batchPages);
            log.info("Extracting KCs for task {} at {}", taskId, batchContext);
            Map<String, Object> llmResult = aiModelGateway.callForJsonCached(
                    inputHash, buildSystemPrompt(), buildUserPrompt(chapter, batchPages), "INIT_LLM_");
            Map<String, Object> artifact = buildBatchArtifact(runId, chapter, batchPages, requestedWindowSize, inputHash, llmResult);
            batchRunStore.completeBatchRun(runId, writeJson(artifact));
            return List.of(artifact);
        } catch (Exception exception) {
            if (shouldSplitBatch(exception, batchPages.size())) {
                batchRunStore.splitBatchRun(runId, exception.getMessage());
                return processChildren(taskId, chapter, batchPages);
            }
            batchRunStore.failBatchRun(runId, exception.getMessage());
            String batchContext = describeBatchContext(chapter.filename(), batchPages);
            log.error("LLM KC extraction failed for task {} at {}: {}", taskId, batchContext, exception.getMessage());
            initService.failTask(taskId, "LLM KC extraction failed at " + batchContext + ": " + exception.getMessage());
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "LLM KC extraction failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private List<Map<String, Object>> processChildren(Long taskId,
                                                      DocumentChapter chapter,
                                                      List<Map<String, Object>> batchPages) {
        List<List<Map<String, Object>>> children = splitBatchPages(batchPages);
        if (children.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "KC extraction batch cannot be split further");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        int nextRequestedWindow = Math.max(1, batchPages.size() / 2);
        for (List<Map<String, Object>> child : children) {
            results.addAll(processBatch(taskId, chapter, child, nextRequestedWindow));
        }
        return results;
    }

    private Map<String, Object> buildBatchArtifact(Long runId,
                                                   DocumentChapter chapter,
                                                   List<Map<String, Object>> batchPages,
                                                   int requestedWindowSize,
                                                   String inputHash,
                                                   Map<String, Object> llmResult) {
        List<Map<String, Object>> rawKcs = normalizeBatchKcs(llmResult, batchPages);
        if (rawKcs.isEmpty()) {
            throw new IllegalStateException("No valid KCs extracted from batch");
        }
        LinkedHashSet<String> localAliases = new LinkedHashSet<>();
        for (Map<String, Object> kc : rawKcs) {
            localAliases.addAll(parseStringList(kc.get("aliases")));
        }

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("batch_id", runId);
        artifact.put("document_id", chapter.documentId());
        artifact.put("document_title", chapter.filename());
        artifact.put("chapter_index", chapter.chapterIndex());
        artifact.put("chapter_title", chapter.chapterTitle());
        artifact.put("page_range_start", intVal(batchPages.getFirst().get("page_no")));
        artifact.put("page_range_end", intVal(batchPages.getLast().get("page_no")));
        artifact.put("page_range", List.of(intVal(batchPages.getFirst().get("page_no")), intVal(batchPages.getLast().get("page_no"))));
        artifact.put("requested_window_size", requestedWindowSize);
        artifact.put("effective_window_size", batchPages.size());
        artifact.put("input_hash", HashUtils.sha256(inputHash));
        artifact.put("raw_kcs", rawKcs);
        artifact.put("local_aliases", List.copyOf(localAliases));
        artifact.put("evidence_excerpt", buildBatchEvidenceExcerpt(batchPages));
        return artifact;
    }

    private Map<String, Object> parseBatchArtifact(Map<String, Object> row, DocumentChapter chapter) {
        String outputJson = stringVal(row.get("output_json"));
        if (outputJson.isBlank()) {
            throw new IllegalStateException("Reusable KC batch has no output_json");
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(outputJson, new TypeReference<>() {});
            parsed.put("batch_id", longVal(row.get("id")));
            parsed.put("document_id", chapter.documentId());
            parsed.put("document_title", chapter.filename());
            parsed.put("chapter_index", chapter.chapterIndex());
            parsed.put("chapter_title", chapter.chapterTitle());
            return parsed;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Reusable KC batch output_json is invalid", exception);
        }
    }

    private List<Map<String, Object>> normalizeBatchKcs(Map<String, Object> llmResult, List<Map<String, Object>> batchPages) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawKcs = (List<Map<String, Object>>) llmResult.get("kcs");
        if (rawKcs == null || rawKcs.isEmpty()) {
            return List.of();
        }
        Set<Integer> allowedPages = batchPages.stream()
                .map(page -> intVal(page.get("page_no")))
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> rawKc : rawKcs) {
            String name = stringVal(rawKc.get("name"));
            if (name.isBlank()) {
                continue;
            }
            List<Integer> reportedPages = parseIntegerList(rawKc.get("pages")).stream()
                    .distinct()
                    .toList();
            List<Integer> pages = reportedPages.stream()
                    .filter(allowedPages::contains)
                    .distinct()
                    .toList();
            if (pages.isEmpty()) {
                pages = reportedPages.isEmpty() ? List.copyOf(allowedPages) : reportedPages;
            }
            String evidenceExcerpt = buildEvidenceExcerptForPages(batchPages, pages);
            if (evidenceExcerpt.isBlank()) {
                evidenceExcerpt = buildBatchEvidenceExcerpt(batchPages);
            }
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            aliases.add(name);
            String nameEn = stringVal(rawKc.get("name_en"));
            if (!nameEn.isBlank()) {
                aliases.add(nameEn);
            }
            Map<String, Object> normalizedRow = new LinkedHashMap<>();
            normalizedRow.put("name", name);
            normalizedRow.put("name_en", nameEn);
            normalizedRow.put("description", stringVal(rawKc.get("description")));
            normalizedRow.put("pages", pages);
            normalizedRow.put("aliases", List.copyOf(aliases));
            normalizedRow.put("evidence_excerpt", evidenceExcerpt);
            normalized.add(normalizedRow);
        }
        return normalized;
    }

    private ReconciliationResult reconcileChapters(List<Map<String, Object>> batchResults,
                                                   List<DocumentChapter> documentChapters) {
        List<Map<String, Object>> chapterMemoryRows = new ArrayList<>();
        List<Map<String, Object>> perDocumentCatalog = new ArrayList<>();

        Map<Long, List<Map<String, Object>>> batchRowsByDocument = new LinkedHashMap<>();
        for (Map<String, Object> batchRow : batchResults) {
            Long documentId = longVal(batchRow.get("document_id"));
            if (documentId == null) {
                continue;
            }
            batchRowsByDocument.computeIfAbsent(documentId, ignored -> new ArrayList<>()).add(batchRow);
        }

        for (DocumentChapter chapter : documentChapters) {
            List<Map<String, Object>> chapterBatches = batchRowsByDocument.getOrDefault(chapter.documentId(), List.of());
            List<Map<String, Object>> chapterCatalog = reconcileSingleChapter(chapter, chapterBatches);
            int aliasMergeCount = 0;
            int crossBatchMergedCount = 0;
            int conflictCount = 0;
            for (Map<String, Object> row : chapterCatalog) {
                int aliasCount = parseStringList(row.get("aliases")).size();
                aliasMergeCount += Math.max(0, aliasCount - 1);
                int localBatchCount = parseLongList(row.get("local_batch_ids")).size();
                if (localBatchCount > 1) {
                    crossBatchMergedCount++;
                }
                if (aliasCount > 1) {
                    conflictCount++;
                }
            }
            Map<String, Object> chapterMemory = new LinkedHashMap<>();
            chapterMemory.put("document_id", chapter.documentId());
            chapterMemory.put("chapter_index", chapter.chapterIndex());
            chapterMemory.put("chapter_title", chapter.chapterTitle());
            chapterMemory.put("chapter_page_count", chapter.pages().size());
            chapterMemory.put("page_range_start", intVal(chapter.pages().getFirst().get("page_no")));
            chapterMemory.put("page_range_end", intVal(chapter.pages().getLast().get("page_no")));
            chapterMemory.put("chapter_synopsis", buildChapterSynopsis(chapterCatalog));
            chapterMemory.put("canonical_kc_count", chapterCatalog.size());
            chapterMemory.put("conflict_count", conflictCount);
            chapterMemory.put("safe_window_size", estimateSafeWindowSize(chapterBatches));
            chapterMemory.put("kc_alias_merge_count", aliasMergeCount);
            chapterMemory.put("cross_batch_merged_kc_count", crossBatchMergedCount);
            chapterMemory.put("canonical_kcs", chapterCatalog.stream()
                    .map(row -> Map.of(
                            "canonical_name", stringVal(row.get("canonical_name")),
                            "aliases", parseStringList(row.get("aliases")),
                            "page_numbers", parseIntegerList(row.get("page_numbers"))
                    ))
                    .toList());
            chapterMemoryRows.add(chapterMemory);
            perDocumentCatalog.addAll(chapterCatalog);
        }

        List<Map<String, Object>> catalogRows = reconcileCrossDocument(perDocumentCatalog);
        return new ReconciliationResult(chapterMemoryRows, catalogRows);
    }

    private List<Map<String, Object>> reconcileCrossDocument(List<Map<String, Object>> perDocumentCatalog) {
        List<Map<String, Object>> globalCatalog = new ArrayList<>();
        for (Map<String, Object> row : perDocumentCatalog) {
            Map<String, Object> mergeTarget = findCrossDocumentMergeTarget(globalCatalog, row);
            if (mergeTarget == null) {
                globalCatalog.add(new LinkedHashMap<>(row));
                continue;
            }
            mergeTarget.put("canonical_name", chooseCanonicalName(
                    stringVal(mergeTarget.get("canonical_name")), stringVal(row.get("canonical_name"))));
            mergeTarget.put("canonical_name_en", chooseCanonicalName(
                    stringVal(mergeTarget.get("canonical_name_en")), stringVal(row.get("canonical_name_en"))));
            mergeTarget.put("description", preferLongerText(
                    mergeTarget.get("description"), row.get("description")));
            mergeTarget.put("aliases", unionStringLists(
                    mergeTarget.get("aliases"), row.get("aliases")));
            mergeTarget.put("page_numbers", unionIntegerLists(
                    mergeTarget.get("page_numbers"), row.get("page_numbers")));
            mergeTarget.put("local_batch_ids", unionLongLists(
                    mergeTarget.get("local_batch_ids"), row.get("local_batch_ids")));
            mergeTarget.put("evidence_excerpt", preferLongerText(
                    mergeTarget.get("evidence_excerpt"), row.get("evidence_excerpt")));
            mergeTarget.put("source_signature", buildCatalogSourceSignature(
                    intVal(mergeTarget.get("chapter_index")), stringVal(mergeTarget.get("canonical_name"))));
        }
        globalCatalog.sort(Comparator.comparing(row -> normalizeName(stringVal(row.get("canonical_name")))));
        return globalCatalog;
    }

    private Map<String, Object> findCrossDocumentMergeTarget(List<Map<String, Object>> catalog,
                                                              Map<String, Object> row) {
        String rowNormalized = normalizeName(stringVal(row.get("canonical_name")));
        String rowEnNormalized = normalizeName(stringVal(row.get("canonical_name_en")));
        String rowStem = stripNumberedPrefix(rowNormalized);
        for (Map<String, Object> existing : catalog) {
            String existingNormalized = normalizeName(stringVal(existing.get("canonical_name")));
            if (!rowNormalized.isBlank() && rowNormalized.equals(existingNormalized)) {
                return existing;
            }
            if (!rowNormalized.isBlank() && !existingNormalized.isBlank()
                    && (rowNormalized.contains(existingNormalized) || existingNormalized.contains(rowNormalized))) {
                return existing;
            }
            String existingStem = stripNumberedPrefix(existingNormalized);
            if (!rowStem.isBlank() && !existingStem.isBlank() && rowStem.equals(existingStem)) {
                return existing;
            }
            String existingEnNormalized = normalizeName(stringVal(existing.get("canonical_name_en")));
            if (!rowEnNormalized.isBlank() && !existingEnNormalized.isBlank()
                    && (rowEnNormalized.equals(existingEnNormalized)
                        || rowEnNormalized.contains(existingEnNormalized)
                        || existingEnNormalized.contains(rowEnNormalized))) {
                return existing;
            }
            for (String alias : parseStringList(existing.get("aliases"))) {
                String aliasNormalized = normalizeName(alias);
                if (!rowNormalized.isBlank() && rowNormalized.equals(aliasNormalized)) {
                    return existing;
                }
            }
        }
        return null;
    }

    private List<Map<String, Object>> reconcileSingleChapter(DocumentChapter chapter,
                                                             List<Map<String, Object>> batchRows) {
        List<Map<String, Object>> catalog = new ArrayList<>();
        for (Map<String, Object> batchRow : batchRows) {
            List<Map<String, Object>> rawKcs = parseObjectList(batchRow.get("raw_kcs"));
            for (Map<String, Object> rawKc : rawKcs) {
                Map<String, Object> existing = findMergeTarget(catalog, rawKc);
                if (existing == null) {
                    Map<String, Object> created = new LinkedHashMap<>();
                    created.put("document_id", chapter.documentId());
                    created.put("document_title", chapter.filename());
                    created.put("chapter_index", chapter.chapterIndex());
                    created.put("chapter_title", chapter.chapterTitle());
                    created.put("canonical_name", stringVal(rawKc.get("name")));
                    created.put("canonical_name_en", stringVal(rawKc.get("name_en")));
                    created.put("description", stringVal(rawKc.get("description")));
                    created.put("aliases", List.copyOf(new LinkedHashSet<>(parseStringList(rawKc.get("aliases")))));
                    created.put("page_numbers", List.copyOf(new LinkedHashSet<>(parseIntegerList(rawKc.get("pages")))));
                    created.put("local_batch_ids", List.of(longVal(batchRow.get("batch_id"))));
                    created.put("evidence_excerpt", stringVal(rawKc.get("evidence_excerpt")));
                    created.put("source_signature", buildCatalogSourceSignature(chapter.chapterIndex(), stringVal(rawKc.get("name"))));
                    catalog.add(created);
                    continue;
                }

                existing.put("canonical_name", chooseCanonicalName(stringVal(existing.get("canonical_name")), stringVal(rawKc.get("name"))));
                existing.put("canonical_name_en", chooseCanonicalName(stringVal(existing.get("canonical_name_en")), stringVal(rawKc.get("name_en"))));
                existing.put("description", preferLongerText(existing.get("description"), rawKc.get("description")));
                existing.put("aliases", unionStringLists(existing.get("aliases"), rawKc.get("aliases")));
                existing.put("page_numbers", unionIntegerLists(existing.get("page_numbers"), rawKc.get("pages")));
                existing.put("local_batch_ids", unionLongLists(existing.get("local_batch_ids"), List.of(longVal(batchRow.get("batch_id")))));
                existing.put("evidence_excerpt", preferLongerText(existing.get("evidence_excerpt"), rawKc.get("evidence_excerpt")));
                existing.put("source_signature", buildCatalogSourceSignature(chapter.chapterIndex(), stringVal(existing.get("canonical_name"))));
            }
        }
        catalog.sort(Comparator.comparing(row -> normalizeName(stringVal(row.get("canonical_name")))));
        return catalog;
    }

    private Map<String, Object> findMergeTarget(List<Map<String, Object>> catalog, Map<String, Object> rawKc) {
        String rawName = stringVal(rawKc.get("name"));
        String rawEnglish = stringVal(rawKc.get("name_en"));
        String rawNormalized = normalizeName(rawName);
        String rawEnglishNormalized = normalizeName(rawEnglish);
        String rawStem = stripNumberedPrefix(rawNormalized);
        for (Map<String, Object> existing : catalog) {
            String existingName = stringVal(existing.get("canonical_name"));
            String existingNameNormalized = normalizeName(existingName);
            if (!rawNormalized.isBlank() && rawNormalized.equals(existingNameNormalized)) {
                return existing;
            }
            if (!rawNormalized.isBlank()
                    && !existingNameNormalized.isBlank()
                    && (rawNormalized.contains(existingNameNormalized) || existingNameNormalized.contains(rawNormalized))) {
                return existing;
            }
            String existingStem = stripNumberedPrefix(existingNameNormalized);
            if (!rawStem.isBlank() && !existingStem.isBlank() && rawStem.equals(existingStem)) {
                return existing;
            }
            for (String alias : parseStringList(existing.get("aliases"))) {
                String aliasNormalized = normalizeName(alias);
                if (!rawNormalized.isBlank() && rawNormalized.equals(aliasNormalized)) {
                    return existing;
                }
                if (!rawEnglishNormalized.isBlank() && rawEnglishNormalized.equals(aliasNormalized)) {
                    return existing;
                }
                String aliasStem = stripNumberedPrefix(aliasNormalized);
                if (!rawStem.isBlank() && !aliasStem.isBlank() && rawStem.equals(aliasStem)) {
                    return existing;
                }
            }
            String existingEnNormalized = normalizeName(stringVal(existing.get("canonical_name_en")));
            if (!rawEnglishNormalized.isBlank() && !existingEnNormalized.isBlank()
                    && (rawEnglishNormalized.equals(existingEnNormalized)
                        || rawEnglishNormalized.contains(existingEnNormalized)
                        || existingEnNormalized.contains(rawEnglishNormalized))) {
                return existing;
            }
        }
        return null;
    }

    private String stripNumberedPrefix(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return "";
        }
        return normalized
                .replaceAll("^(第[一二三四五六七八九十百千\\d]+[种类个步条方])", "")
                .replaceAll("^(\\d+_)", "")
                .strip();
    }

    private Map<Integer, Long> insertChapters(Long taskId,
                                              Long languagePackId,
                                              List<DocumentChapter> documentChapters) {
        Map<Integer, Long> chapterIdByIndex = new LinkedHashMap<>();
        for (DocumentChapter chapter : documentChapters) {
            Long chapterId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO language_pack_chapter(language_pack_id, init_task_id, chapter_index, title,
                        description, page_range_start, page_range_end, create_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, now())
                    ON CONFLICT (language_pack_id, chapter_index) DO UPDATE
                        SET title = EXCLUDED.title, description = EXCLUDED.description,
                            page_range_start = EXCLUDED.page_range_start, page_range_end = EXCLUDED.page_range_end
                    RETURNING id
                    """,
                    Long.class,
                    languagePackId,
                    taskId,
                    chapter.chapterIndex(),
                    chapter.chapterTitle(),
                    "",
                    intVal(chapter.pages().getFirst().get("page_no")),
                    intVal(chapter.pages().getLast().get("page_no"))
            );
            chapterIdByIndex.put(chapter.chapterIndex(), chapterId);
        }
        return chapterIdByIndex;
    }

    private List<Map<String, Object>> insertCanonicalKcs(Long languagePackId,
                                                         Long taskId,
                                                         List<Map<String, Object>> catalogRows,
                                                         Map<Integer, Long> chapterIdByIndex,
                                                         List<DocumentChapter> documentChapters) {
        Map<Long, Map<Integer, Long>> pageIdByDocumentAndPageNo = new LinkedHashMap<>();
        for (DocumentChapter chapter : documentChapters) {
            Map<Integer, Long> mapping = new LinkedHashMap<>();
            for (Map<String, Object> page : chapter.pages()) {
                mapping.put(intVal(page.get("page_no")), longVal(page.get("id")));
            }
            pageIdByDocumentAndPageNo.put(chapter.documentId(), mapping);
        }

        List<Map<String, Object>> storedRows = new ArrayList<>();
        List<Object[]> pageMappingParams = new ArrayList<>();

        for (Map<String, Object> row : catalogRows) {
            Long chapterId = chapterIdByIndex.get(intVal(row.get("chapter_index")));
            String canonicalName = stringVal(row.get("canonical_name"));
            String normalizedName = normalizeName(canonicalName);
            Long kcId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO language_pack_kc(language_pack_id, init_task_id, chapter_id,
                        name, name_normalized, name_en, description, create_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, now())
                    ON CONFLICT (language_pack_id, name_normalized) DO UPDATE
                        SET name = EXCLUDED.name,
                            name_en = EXCLUDED.name_en,
                            description = EXCLUDED.description,
                            chapter_id = EXCLUDED.chapter_id
                    RETURNING id
                    """,
                    Long.class,
                    languagePackId,
                    taskId,
                    chapterId,
                    canonicalName,
                    normalizedName,
                    stringVal(row.get("canonical_name_en")),
                    stringVal(row.get("description"))
            );
            for (Integer pageNo : parseIntegerList(row.get("page_numbers"))) {
                Long pageId = pageIdByDocumentAndPageNo.getOrDefault(longVal(row.get("document_id")), Map.of()).get(pageNo);
                if (pageId != null) {
                    pageMappingParams.add(new Object[]{kcId, pageId});
                }
            }

            Map<String, Object> stored = new LinkedHashMap<>(row);
            stored.put("canonical_kc_id", kcId);
            storedRows.add(stored);
        }

        if (!pageMappingParams.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO language_pack_kc_page_mapping(kc_id, page_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    pageMappingParams
            );
        }

        return storedRows;
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

    private List<List<Map<String, Object>>> splitBatchPages(List<Map<String, Object>> pages) {
        int size = pages.size();
        if (size <= 1) {
            return List.of();
        }
        if (size == 2) {
            return List.of(
                    new ArrayList<>(pages.subList(0, 1)),
                    new ArrayList<>(pages.subList(1, 2))
            );
        }
        int overlap = Math.min(WINDOW_OVERLAP, size - 1);
        int leftSize = (size + 1) / 2 + overlap / 2;
        if (leftSize >= size) {
            leftSize = size - 1;
        }
        int rightStart = Math.max(1, leftSize - overlap);
        if (rightStart >= size) {
            rightStart = size - 1;
        }
        return List.of(
                new ArrayList<>(pages.subList(0, leftSize)),
                new ArrayList<>(pages.subList(rightStart, size))
        );
    }

    private boolean shouldSplitBatch(Exception exception, int batchSize) {
        if (batchSize <= 1) {
            return false;
        }
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("timed out") || lower.contains("timeout");
    }

    private String buildSystemPrompt() {
        return """
                You are a curriculum analyst for beginner programming courses aimed at non-CS majors.
                Given course pages from a single document slice, extract TEACHING-LEVEL knowledge components (KCs).

                A teaching-level KC is a concept that:
                - Can be the topic of a lecture section or textbook subsection
                - Is reusable: multiple exercises or examples can be attached to it
                - Is testable: you could write at least one programming problem about it
                - Represents a skill or concept a student must master, not a single fact or code snippet

                Return JSON with this exact schema:
                {
                  "kcs": [
                    {
                      "name": "KC name in original language",
                      "name_en": "KC name in English",
                      "description": "What the student should learn about this topic",
                      "pages": [1, 2, 3]
                    }
                  ]
                }

                Granularity rules (critical):
                - Each KC should correspond to a chapter section or a coherent teaching topic, NOT a single page detail.
                - Merge related sub-points into one KC. For example:
                  WRONG: "第一种函数调用", "第二种函数调用", "第三种函数调用" as three KCs.
                  RIGHT: "函数调用" as one KC covering all calling conventions.
                - Historical facts, single command names, individual API demos are NOT standalone KCs.
                  WRONG: "第一台电子计算机ENIAC", "pip工具常用子命令", "__name__变量" as independent KCs.
                  RIGHT: Absorb them into broader KCs like "计算机发展简史", "Python包管理", "模块与导入".
                - You are processing a SLICE of a larger document. This slice typically covers 15-32 pages.
                  Extract at most 2-4 KCs per slice. A full document (50-100 pages) should yield roughly 5-8 KCs total.
                - When in doubt, merge into a broader KC rather than creating a narrow one.
                - Prefer fewer, broader KCs over many narrow ones. Aim for the minimum set that covers the teaching content.

                Naming rules:
                - KC names must use the original language of the courseware.
                - Provide an English translation in name_en.
                - Prefer short, canonical topic names (e.g. "条件语句", "for循环", "列表操作").
                - Do not use exercise titles, example numbers, or page-specific headings as KC names.

                Other rules:
                - Every KC must list the real page numbers where it appears.
                - Do not invent KCs not taught in the provided pages.
                - If the pages only contain table of contents, diagrams, or administrative content, return {"kcs": []}.
                """;
    }

    private String buildUserPrompt(DocumentChapter chapter, List<Map<String, Object>> batchPages) {
        StringBuilder pageText = new StringBuilder();
        for (Map<String, Object> page : batchPages) {
            int pageNo = intVal(page.get("page_no"));
            pageText.append("\n--- Page ").append(pageNo)
                    .append(" [").append(chapter.filename()).append("] ---\n")
                    .append(stringVal(page.get("page_text"))).append("\n");
        }
        return "Document: " + chapter.filename()
                + "\nChapter: " + chapter.chapterTitle()
                + "\nExtract knowledge components from these course pages:\n\n"
                + pageText;
    }

    private String describeBatchContext(String filename, List<Map<String, Object>> pages) {
        int startPage = intVal(pages.getFirst().get("page_no"));
        int endPage = intVal(pages.getLast().get("page_no"));
        return filename + " pages " + startPage + "-" + endPage;
    }

    private String buildBatchInputHash(DocumentChapter chapter, List<Map<String, Object>> batchPages, int requestedWindowSize) {
        StringBuilder builder = new StringBuilder();
        builder.append(STAGE_NAME).append('|')
                .append(chapter.documentId()).append('|')
                .append(chapter.chapterIndex()).append('|')
                .append(requestedWindowSize).append('|');
        for (Map<String, Object> page : batchPages) {
            builder.append(intVal(page.get("page_no")))
                    .append(':')
                    .append(stringVal(page.get("text_hash")))
                    .append('|');
        }
        return builder.toString();
    }

    private String buildPageFingerprint(List<DocumentChapter> chapters) {
        StringBuilder builder = new StringBuilder();
        for (DocumentChapter chapter : chapters) {
            builder.append(chapter.documentId()).append(':').append(chapter.chapterIndex()).append(':');
            for (Map<String, Object> page : chapter.pages()) {
                builder.append(intVal(page.get("page_no")))
                        .append('-')
                        .append(stringVal(page.get("text_hash")))
                        .append('|');
            }
            builder.append(';');
        }
        return builder.toString();
    }

    private String buildBatchEvidenceExcerpt(List<Map<String, Object>> batchPages) {
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> page : batchPages) {
            if (builder.length() >= 240) {
                break;
            }
            String text = stringVal(page.get("page_text"));
            if (text.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(text, 0, Math.min(text.length(), 80));
        }
        return builder.toString();
    }

    private String buildEvidenceExcerptForPages(List<Map<String, Object>> batchPages, List<Integer> pageNumbers) {
        Set<Integer> allowed = new LinkedHashSet<>(pageNumbers);
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> page : batchPages) {
            if (!allowed.contains(intVal(page.get("page_no")))) {
                continue;
            }
            String text = stringVal(page.get("page_text"));
            if (text.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(text, 0, Math.min(text.length(), 120));
            if (builder.length() >= 240) {
                break;
            }
        }
        return builder.toString();
    }

    private int estimateSafeWindowSize(List<Map<String, Object>> chapterBatches) {
        int minWindow = ROOT_WINDOW_SIZE;
        for (Map<String, Object> batch : chapterBatches) {
            minWindow = Math.min(minWindow, intVal(batch.get("effective_window_size")));
        }
        return minWindow == ROOT_WINDOW_SIZE && chapterBatches.isEmpty() ? ROOT_WINDOW_SIZE : minWindow;
    }

    private String buildChapterSynopsis(List<Map<String, Object>> chapterCatalog) {
        List<String> names = chapterCatalog.stream()
                .map(row -> stringVal(row.get("canonical_name")))
                .filter(name -> !name.isBlank())
                .limit(8)
                .toList();
        if (names.isEmpty()) {
            return "";
        }
        return "本章重点：" + String.join("、", names);
    }

    private String buildCatalogSourceSignature(int chapterIndex, String canonicalName) {
        return "chapter:" + chapterIndex + "|kc:" + normalizeName(canonicalName);
    }

    private String chooseCanonicalName(String left, String right) {
        if (left == null || left.isBlank()) {
            return stringVal(right);
        }
        if (right == null || right.isBlank()) {
            return stringVal(left);
        }
        String normalizedLeft = normalizeName(left);
        String normalizedRight = normalizeName(right);
        if (normalizedRight.contains(normalizedLeft) && right.length() >= left.length()) {
            return right;
        }
        if (normalizedLeft.contains(normalizedRight) && left.length() >= right.length()) {
            return left;
        }
        return right.length() > left.length() ? right : left;
    }

    private String preferLongerText(Object left, Object right) {
        String leftText = stringVal(left);
        String rightText = stringVal(right);
        return rightText.length() > leftText.length() ? rightText : leftText;
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

    private List<Integer> parseIntegerList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        for (Object item : rawList) {
            if (item instanceof Number number) {
                values.add(number.intValue());
                continue;
            }
            if (item == null) {
                continue;
            }
            try {
                values.add(Integer.parseInt(String.valueOf(item).strip()));
            } catch (NumberFormatException ignored) {
            }
        }
        return List.copyOf(values);
    }

    private List<Long> parseLongList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        LinkedHashSet<Long> values = new LinkedHashSet<>();
        for (Object item : rawList) {
            Long parsed = longVal(item);
            if (parsed != null) {
                values.add(parsed);
            }
        }
        return List.copyOf(values);
    }

    private List<String> parseStringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Object item : rawList) {
            String text = stringVal(item);
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return List.copyOf(values);
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

    private List<String> unionStringLists(Object left, Object right) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(parseStringList(left));
        merged.addAll(parseStringList(right));
        return List.copyOf(merged);
    }

    private void clearExistingKnowledgeExtraction(Long languagePackId) {
        jdbcTemplate.update(
                """
                DELETE FROM language_pack_kc_page_mapping
                WHERE kc_id IN (SELECT id FROM language_pack_kc WHERE language_pack_id = ?)
                """,
                languagePackId
        );
        jdbcTemplate.update("DELETE FROM language_pack_kc WHERE language_pack_id = ?", languagePackId);
        jdbcTemplate.update("DELETE FROM language_pack_chapter WHERE language_pack_id = ?", languagePackId);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("json serialize failed", exception);
        }
    }

    private String stripExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
                .strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-]+", "_")
                .replaceAll("[^a-z0-9_\\u4e00-\\u9fff]", "");
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

    private int intVal(Object obj) {
        if (obj instanceof Number number) {
            return number.intValue();
        }
        if (obj == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(obj).strip());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private record DocumentChapter(Long documentId,
                                   String filename,
                                   String chapterTitle,
                                   int chapterIndex,
                                   List<Map<String, Object>> pages) {
    }

    private record ReconciliationResult(List<Map<String, Object>> chapterMemoryRows,
                                        List<Map<String, Object>> catalogRows) {
    }
}
