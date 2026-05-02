package com.alethicode.service.languagepack.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.exception.BadRequestException;
import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.languagepack.DocumentParsingService;
import com.alethicode.service.languagepack.LanguagePackInitExecutionService;
import com.alethicode.service.languagepack.LanguagePackInitService;
import com.alethicode.service.rag.RagIndexQueueService;
import com.alethicode.service.rag.RagServiceClient;
import com.alethicode.service.rag.dto.RagEntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@Transactional(rollbackFor = Exception.class)
public class DocumentParsingServiceImpl implements DocumentParsingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParsingServiceImpl.class);
    private static final int EXCERPT_MAX_LENGTH = 200;

    private final JdbcTemplate jdbcTemplate;
    private final LanguagePackInitService initService;
    private final LanguagePackInitExecutionService executionService;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties.LanguagePack config;
    private final RagIndexQueueService ragIndexQueue;
    private final RagServiceClient ragServiceClient;

    public DocumentParsingServiceImpl(JdbcTemplate jdbcTemplate,
                                      LanguagePackInitService initService,
                                      LanguagePackInitExecutionService executionService,
                                      ObjectMapper objectMapper,
                                      AlethicodeProperties properties,
                                      RagIndexQueueService ragIndexQueue,
                                      RagServiceClient ragServiceClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.initService = initService;
        this.executionService = executionService;
        this.objectMapper = objectMapper;
        this.config = properties.getLanguagePack();
        this.ragIndexQueue = ragIndexQueue;
        this.ragServiceClient = ragServiceClient;
    }

    @Override
    public void parseDocuments(Long taskId) {
        String currentStage = jdbcTemplate.queryForObject(
                "SELECT stage FROM language_pack_init_task WHERE id = ?",
                String.class, taskId
        );
        if (currentStage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Init task not found");
        }
        if (!"normalizing".equals(currentStage) && !"parsing".equals(currentStage)) {
            throw new BadRequestException("Cannot parse documents in stage: " + currentStage);
        }

        Long languagePackId = jdbcTemplate.queryForObject(
                "SELECT language_pack_id FROM language_pack_init_task WHERE id = ?",
                Long.class, taskId
        );

        List<Map<String, Object>> documents = jdbcTemplate.queryForList(
                """
                SELECT id, canonical_path, preview_pdf_path
                FROM language_pack_document
                WHERE init_task_id = ? AND status = 'normalized'
                ORDER BY sort_order, id
                """,
                taskId
        );

        if (documents.isEmpty()) {
            initService.failTask(taskId, "No normalized documents found for parsing");
            throw new BusinessException(ErrorCode.ERROR, "No normalized documents available for task " + taskId);
        }

        executionService.beginStep(taskId, "parsing", "开始解析文档，共 " + documents.size() + " 个文件", 0, documents.size());
        int totalPages = 0;
        int totalDocs = documents.size();
        try {
            for (int di = 0; di < totalDocs; di++) {
                Map<String, Object> doc = documents.get(di);
                Long docId = ((Number) doc.get("id")).longValue();
                String canonicalPath = (String) doc.get("canonical_path");
                String previewPdfPath = (String) doc.get("preview_pdf_path");

                Long existingPageCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM language_pack_page WHERE document_id = ?",
                        Long.class, docId
                );
                if (existingPageCount != null && existingPageCount > 0) {
                    totalPages += existingPageCount.intValue();
                    executionService.reportProgress(
                            taskId,
                            "parsing",
                            "解析文档 " + (di + 1) + "/" + totalDocs + "（复用已有页解析）",
                            di + 1,
                            totalDocs
                    );
                    continue;
                }

                executionService.reportProgress(taskId, "parsing", "解析文档 " + (di + 1) + "/" + totalDocs, di, totalDocs);

                try {
                    int pageCount = extractAndStorePages(taskId, docId, languagePackId, canonicalPath, previewPdfPath);
                    jdbcTemplate.update(
                            "UPDATE language_pack_document SET page_count = ?, update_time = now() WHERE id = ?",
                            pageCount, docId
                    );
                    totalPages += pageCount;
                    executionService.reportProgress(
                            taskId,
                            "parsing",
                            "解析文档 " + (di + 1) + "/" + totalDocs + "，累计 " + totalPages + " 页",
                            di + 1,
                            totalDocs
                    );
                } catch (Exception e) {
                    log.error("Page extraction failed for document {}: {}", docId, e.getMessage());
                    initService.failTask(taskId, "Page extraction failed for document " + docId + ": " + e.getMessage());
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Page extraction failed", e);
                }
            }

            jdbcTemplate.update(
                    "UPDATE language_pack SET page_count = ?, update_time = now() WHERE id = ?",
                    totalPages, languagePackId
            );
            if ("normalizing".equals(currentStage)) {
                initService.advanceStage(taskId, "parsing");
            }
            executionService.finishStep(taskId, "parsing", "文档解析完成，共 " + totalPages + " 页");

            try {
                ragServiceClient.wakeUpPipeline();
            } catch (Exception wakeEx) {
                log.warn("wake-up after document parsing failed (non-fatal): {}", wakeEx.getMessage());
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            initService.failTask(taskId, "Document parsing failed: " + exception.getMessage());
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Document parsing failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private int extractAndStorePages(Long taskId, Long docId, Long languagePackId,
                                     String canonicalPath, String previewPdfPath) throws Exception {
        String scriptPath = resolveScriptPath();
        ProcessBuilder pb = new ProcessBuilder(
                config.getPythonPath(),
                scriptPath,
                canonicalPath
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Page extraction script failed (exit " + exitCode + "): " + output);
        }

        JsonNode root = objectMapper.readTree(output);
        JsonNode pagesNode = root.get("pages");
        if (pagesNode == null || !pagesNode.isArray() || pagesNode.isEmpty()) {
            throw new RuntimeException("No pages extracted from document");
        }

        int pageCount = 0;
        for (JsonNode pageNode : pagesNode) {
            int pageNo = pageNode.get("page_no").asInt();
            String content = pageNode.has("content") ? pageNode.get("content").asText("") : "";
            String textHash = sha256(content);
            String excerpt = content.length() > EXCERPT_MAX_LENGTH
                    ? content.substring(0, EXCERPT_MAX_LENGTH) + "…"
                    : content;
            String pageTitle = extractPageTitle(content);
            // Phase 3 切流：删除 page_embedding（16 维伪 RAG）与 search_tsv 写入；
            // V77 已 DROP 这两列与 cjk_bigram_tokenize 函数。检索 100% 走 LightRAG
            // 路径（PageRetrievalServiceImpl → ragClient.queryCourseware），写入侧
            // 只需 ragIndexQueue.enqueueIndex 把 content 推到 outbox。
            Long pageId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO language_pack_page(document_id, language_pack_id, page_no, chunk_index,
                        page_title, page_text, text_hash, preview_asset_path, excerpt, create_time)
                    VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, now())
                    ON CONFLICT (document_id, page_no, chunk_index) DO UPDATE SET
                        language_pack_id = excluded.language_pack_id,
                        page_title = excluded.page_title,
                        page_text = excluded.page_text,
                        text_hash = excluded.text_hash,
                        preview_asset_path = excluded.preview_asset_path,
                        excerpt = excluded.excerpt
                    RETURNING id
                    """,
                    Long.class,
                    docId, languagePackId, pageNo,
                    pageTitle, content, textHash,
                    previewPdfPath, excerpt
            );
            pageCount++;

            if (pageId != null && content != null && !content.isBlank()) {
                Map<String, Object> indexMetadata = new java.util.LinkedHashMap<>();
                indexMetadata.put("language_pack_id", languagePackId);
                indexMetadata.put("document_id", docId);
                indexMetadata.put("page_no", pageNo);
                indexMetadata.put("page_title", pageTitle);
                indexMetadata.put("source_path", "language_pack/" + languagePackId + "/p" + pageNo);
                ragIndexQueue.enqueueIndex(
                        RagEntityType.COURSEWARE_PAGE,
                        String.valueOf(pageId),
                        content,
                        indexMetadata
                );
            }
        }
        return pageCount;
    }

    private String resolveScriptPath() {
        Path appScripts = Path.of("/app/scripts/extract_language_pack_pages.py");
        if (appScripts.toFile().exists()) {
            return appScripts.toString();
        }
        Path localScripts = Path.of("scripts/extract_language_pack_pages.py");
        if (localScripts.toFile().exists()) {
            return localScripts.toString();
        }
        return Path.of(System.getProperty("user.dir"), "scripts", "extract_language_pack_pages.py").toString();
    }

    private String extractPageTitle(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String firstLine = content.lines().findFirst().orElse("");
        if (firstLine.length() > 100) {
            return firstLine.substring(0, 100);
        }
        return firstLine;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

}
