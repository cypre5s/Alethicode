package com.alethicode.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.dto.request.CreateLanguagePackInitTaskRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.LanguagePackInitTaskResponse;
import com.alethicode.exception.BadRequestException;
import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.languagepack.DocumentNormalizationService;
import com.alethicode.service.languagepack.LanguagePackDocumentQueryService;
import com.alethicode.service.languagepack.LanguagePackExportImportService;
import com.alethicode.service.languagepack.LanguagePackInitService;
import com.alethicode.service.languagepack.LanguagePackPipelineJobService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminLanguagePackController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminLanguagePackController.class);

    private final LanguagePackInitService languagePackInitService;
    private final DocumentNormalizationService documentNormalizationService;
    private final LanguagePackDocumentQueryService documentQueryService;
    private final LanguagePackExportImportService exportImportService;
    private final LanguagePackPipelineJobService languagePackPipelineJobService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final com.alethicode.service.rag.RagIndexQueueService ragIndexQueue;
    private final com.alethicode.service.languagepack.impl.KcPrerequisiteDetectorService kcPrerequisiteDetectorService;
    private final com.alethicode.service.rag.RagDiagnosticsService ragDiagnosticsService;
    private final com.alethicode.service.rag.RagRebuildService ragRebuildService;

    public AdminLanguagePackController(LanguagePackInitService languagePackInitService,
                                       DocumentNormalizationService documentNormalizationService,
                                       LanguagePackDocumentQueryService documentQueryService,
                                       LanguagePackExportImportService exportImportService,
                                       LanguagePackPipelineJobService languagePackPipelineJobService,
                                       ObjectMapper objectMapper,
                                       JdbcTemplate jdbcTemplate,
                                       com.alethicode.service.rag.RagIndexQueueService ragIndexQueue,
                                       com.alethicode.service.languagepack.impl.KcPrerequisiteDetectorService kcPrerequisiteDetectorService,
                                       com.alethicode.service.rag.RagDiagnosticsService ragDiagnosticsService,
                                       com.alethicode.service.rag.RagRebuildService ragRebuildService) {
        this.languagePackInitService = languagePackInitService;
        this.documentNormalizationService = documentNormalizationService;
        this.documentQueryService = documentQueryService;
        this.exportImportService = exportImportService;
        this.languagePackPipelineJobService = languagePackPipelineJobService;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.ragIndexQueue = ragIndexQueue;
        this.kcPrerequisiteDetectorService = kcPrerequisiteDetectorService;
        this.ragDiagnosticsService = ragDiagnosticsService;
        this.ragRebuildService = ragRebuildService;
    }

    @PostMapping({"/api/admin/language-packs/init-tasks", "/api/admin/language-packs/init-tasks/"})
    public ApiResponse<LanguagePackInitTaskResponse> createTask(
            @RequestParam("name") @NotBlank String name,
            @RequestParam("slug") @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]*[a-z0-9]$") String slug,
            @RequestParam("primary_language") @NotBlank String primaryLanguage,
            @RequestParam(value = "enable_objective_questions", required = false) Boolean enableObjectiveQuestions,
            @RequestParam("files") @NotEmpty List<MultipartFile> files,
            Authentication authentication) {
        Long creatorId = resolveUserId(authentication);
        CreateLanguagePackInitTaskRequest request = new CreateLanguagePackInitTaskRequest(
                name,
                slug,
                primaryLanguage,
                enableObjectiveQuestions
        );
        LanguagePackInitTaskResponse created = languagePackInitService.createTask(request, creatorId);
        documentNormalizationService.uploadAndNormalize(created.id(), files);
        return ApiResponse.success(languagePackInitService.getTask(created.id()));
    }

    @GetMapping({"/api/admin/language-packs/init-tasks/{taskId}", "/api/admin/language-packs/init-tasks/{taskId}/"})
    public ApiResponse<LanguagePackInitTaskResponse> getTask(@PathVariable Long taskId, Authentication authentication) {
        assertTaskLanguagePackAccessible(taskId, authentication);
        return ApiResponse.success(languagePackInitService.getTask(taskId));
    }

    @GetMapping({"/api/admin/language-packs/init-tasks", "/api/admin/language-packs/init-tasks/"})
    public ApiResponse<List<LanguagePackInitTaskResponse>> listTasks(Authentication authentication) {
        List<LanguagePackInitTaskResponse> allTasks = languagePackInitService.listTasks();
        if (!isTeacherRole(authentication)) {
            return ApiResponse.success(allTasks);
        }
        Long currentUserId = resolveUserId(authentication);
        Set<Long> adminUserIds = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT id FROM \"user\" WHERE admin_type = 'Admin'", Long.class));
        return ApiResponse.success(allTasks.stream()
                .filter(t -> {
                    Long creatorId = t.languagePack().creatorId();
                    return Objects.equals(creatorId, currentUserId)
                            || creatorId == null
                            || adminUserIds.contains(creatorId);
                })
                .toList());
    }

    @PostMapping({"/api/admin/language-packs/init-tasks/{taskId}/pipeline-jobs",
                  "/api/admin/language-packs/init-tasks/{taskId}/pipeline-jobs/"})
    public ApiResponse<com.alethicode.dto.response.LanguagePackPipelineJobResponse> startPipelineJob(
            @PathVariable Long taskId,
            Authentication authentication
    ) {
        assertTaskLanguagePackAccessible(taskId, authentication);
        return ApiResponse.success(languagePackPipelineJobService.startJob(taskId));
    }

    @GetMapping({"/api/admin/language-packs/init-tasks/{taskId}/pipeline-jobs/{jobId}",
                 "/api/admin/language-packs/init-tasks/{taskId}/pipeline-jobs/{jobId}/"})
    public ApiResponse<com.alethicode.dto.response.LanguagePackPipelineJobResponse> getPipelineJob(
            @PathVariable Long taskId,
            @PathVariable String jobId,
            Authentication authentication
    ) {
        assertTaskLanguagePackAccessible(taskId, authentication);
        return ApiResponse.success(languagePackPipelineJobService.getJob(taskId, jobId));
    }

    @PostMapping({"/api/admin/language-packs/init-tasks/{taskId}/pipeline-jobs/{jobId}/cancel",
                  "/api/admin/language-packs/init-tasks/{taskId}/pipeline-jobs/{jobId}/cancel/"})
    public ApiResponse<com.alethicode.dto.response.LanguagePackPipelineJobResponse> cancelPipelineJob(
            @PathVariable Long taskId,
            @PathVariable String jobId,
            Authentication authentication
    ) {
        assertTaskLanguagePackAccessible(taskId, authentication);
        return ApiResponse.success(languagePackPipelineJobService.cancelJob(taskId, jobId));
    }

    @PostMapping({"/api/admin/language-packs/init-tasks/{taskId}/pipeline-jobs/{jobId}/retry",
                  "/api/admin/language-packs/init-tasks/{taskId}/pipeline-jobs/{jobId}/retry/"})
    public ApiResponse<com.alethicode.dto.response.LanguagePackPipelineJobResponse> retryPipelineJob(
            @PathVariable Long taskId,
            @PathVariable String jobId,
            Authentication authentication
    ) {
        assertTaskLanguagePackAccessible(taskId, authentication);
        return ApiResponse.success(languagePackPipelineJobService.retryJob(taskId, jobId));
    }

    @DeleteMapping({"/api/admin/language-packs/init-tasks/{taskId}",
                    "/api/admin/language-packs/init-tasks/{taskId}/"})
    public ApiResponse<Map<String, Object>> deleteTask(@PathVariable Long taskId, Authentication authentication) {
        assertTaskLanguagePackAccessible(taskId, authentication);
        assertTaskCreator(taskId, authentication);
        Long languagePackId = jdbcTemplate.queryForObject(
                "SELECT language_pack_id FROM language_pack_init_task WHERE id = ?",
                Long.class, taskId
        );
        Map<String, Object> stats = exportImportService.deleteLanguagePack(languagePackId);
        return ApiResponse.success(stats);
    }

    @PostMapping({"/api/admin/language-packs/init-tasks/{taskId}/re-embed",
                  "/api/admin/language-packs/init-tasks/{taskId}/re-embed/"})
    public ApiResponse<Map<String, Object>> reEmbed(@PathVariable Long taskId, Authentication authentication) {
        // Phase 3 切流：原 16 维 page_embedding 重算逻辑改为「把该 language_pack 的所有
        // 页面重新入 outbox」，alethicode-rag 收到 INSERT 后会做 LightRAG content-hash
        // dedup —— 已索引的 page 立即返 dup-failed（不消耗 LLM），只有真变了的才会重抽
        // entity / relation。前端原 button 的语义不变（重新跑一遍 RAG 索引），但成本与
        // 行为彻底改变。
        assertTaskLanguagePackAccessible(taskId, authentication);
        Long languagePackId = jdbcTemplate.queryForObject(
                "SELECT language_pack_id FROM language_pack_init_task WHERE id = ?",
                Long.class, taskId
        );
        List<Map<String, Object>> pages = jdbcTemplate.queryForList(
                "SELECT id, document_id, page_title, page_text, page_no FROM language_pack_page WHERE language_pack_id = ? ORDER BY id",
                languagePackId
        );
        int enqueued = 0;
        for (Map<String, Object> page : pages) {
            String content = page.get("page_text") != null ? page.get("page_text").toString().trim() : "";
            if (content.isBlank()) {
                continue;
            }
            int pageNo = ((Number) page.get("page_no")).intValue();
            Object pageId = page.get("id");
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("language_pack_id", languagePackId);
            metadata.put("document_id", page.get("document_id"));
            metadata.put("page_no", pageNo);
            metadata.put("page_title", page.get("page_title"));
            metadata.put("source_path", "language_pack/" + languagePackId + "/p" + pageNo);
            try {
                ragIndexQueue.enqueueIndex(
                        com.alethicode.service.rag.dto.RagEntityType.COURSEWARE_PAGE,
                        String.valueOf(pageId),
                        content,
                        metadata
                );
                enqueued++;
            } catch (Exception e) {
                log.warn("Re-embed enqueue failed for page {}: {}", pageId, e.getMessage());
            }
        }
        return ApiResponse.success(Map.of("total", pages.size(), "enqueued", enqueued));
    }

    @GetMapping({"/api/admin/language-packs/init-tasks/{taskId}/documents",
                 "/api/admin/language-packs/init-tasks/{taskId}/documents/"})
    public ApiResponse<List<Map<String, Object>>> listDocuments(@PathVariable Long taskId, Authentication authentication) {
        assertTaskLanguagePackAccessible(taskId, authentication);
        return ApiResponse.success(documentQueryService.listDocuments(taskId));
    }

    @PatchMapping({"/api/admin/language-packs/init-tasks/{taskId}/documents/order",
                   "/api/admin/language-packs/init-tasks/{taskId}/documents/order/"})
    public ApiResponse<Void> reorderDocuments(@PathVariable Long taskId,
                                              @RequestBody Map<String, Object> body,
                                              Authentication authentication) {
        assertTaskLanguagePackAccessible(taskId, authentication);
        String stage = jdbcTemplate.queryForObject(
                "SELECT stage FROM language_pack_init_task WHERE id = ?",
                String.class, taskId
        );
        if (stage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Init task not found");
        }
        if (!List.of("created", "normalizing", "parsing").contains(stage)) {
            throw new BadRequestException("Cannot reorder documents in stage: " + stage);
        }
        @SuppressWarnings("unchecked")
        List<Number> documentIds = (List<Number>) body.get("document_ids");
        if (documentIds == null || documentIds.isEmpty()) {
            throw new BadRequestException("document_ids is required");
        }
        for (int i = 0; i < documentIds.size(); i++) {
            jdbcTemplate.update(
                    "UPDATE language_pack_document SET sort_order = ? WHERE id = ? AND init_task_id = ?",
                    i + 1,
                    documentIds.get(i).longValue(),
                    taskId
            );
        }
        return ApiResponse.success(null);
    }

    @GetMapping({"/api/admin/language-packs/documents/{documentId}/pages",
                 "/api/admin/language-packs/documents/{documentId}/pages/"})
    public ApiResponse<List<Map<String, Object>>> listPages(@PathVariable Long documentId, Authentication authentication) {
        Long languagePackId = resolveLanguagePackIdByDocument(documentId);
        assertLanguagePackAccessible(languagePackId, authentication);
        return ApiResponse.success(documentQueryService.listPages(documentId));
    }

    @GetMapping({"/api/admin/language-packs/{languagePackId}/documents/{documentId}/pages/{pageNo}",
                 "/api/admin/language-packs/{languagePackId}/documents/{documentId}/pages/{pageNo}/"})
    public ApiResponse<Map<String, Object>> getPage(
            @PathVariable Long languagePackId,
            @PathVariable Long documentId,
            @PathVariable Integer pageNo,
            Authentication authentication) {
        assertLanguagePackAccessible(languagePackId, authentication);
        return ApiResponse.success(documentQueryService.getPage(languagePackId, documentId, pageNo));
    }

    @GetMapping({"/api/admin/language-packs/init-tasks/{taskId}/kcs",
                 "/api/admin/language-packs/init-tasks/{taskId}/kcs/"})
    public ApiResponse<List<Map<String, Object>>> listKcs(@PathVariable Long taskId, Authentication authentication) {
        Long languagePackId = jdbcTemplate.queryForObject(
                "SELECT language_pack_id FROM language_pack_init_task WHERE id = ?",
                Long.class, taskId
        );
        assertLanguagePackAccessible(languagePackId, authentication);
        List<Map<String, Object>> kcs = jdbcTemplate.queryForList(
                """
                SELECT k.id, k.name, k.name_en, k.description,
                       c.title AS chapter_title, c.chapter_index
                FROM language_pack_kc k
                LEFT JOIN language_pack_chapter c ON c.id = k.chapter_id
                WHERE k.language_pack_id = ?
                ORDER BY c.chapter_index, k.id
                """,
                languagePackId
        );
        return ApiResponse.success(kcs);
    }

    @GetMapping({"/api/admin/language-packs/init-tasks/{taskId}/examples",
                 "/api/admin/language-packs/init-tasks/{taskId}/examples/"})
    public ApiResponse<List<Map<String, Object>>> listExamples(@PathVariable Long taskId, Authentication authentication) {
        Long languagePackId = jdbcTemplate.queryForObject(
                "SELECT language_pack_id FROM language_pack_init_task WHERE id = ?",
                Long.class, taskId
        );
        assertLanguagePackAccessible(languagePackId, authentication);
        List<Map<String, Object>> examples = jdbcTemplate.queryForList(
                """
                SELECT e.id, e.raw_text, e.normalized_body, e.evidence_excerpt,
                       e.page_range_start, e.page_range_end,
                       e.input_description, e.output_description,
                       e.unit_type, e.source_title, e.oj_convertible, e.oj_block_reason,
                       e.source_signature,
                       (SELECT count(*) FROM language_pack_example_kc_mapping m WHERE m.example_id = e.id) AS kc_count,
                       (SELECT string_agg(k.name, ', ' ORDER BY k.id)
                        FROM language_pack_example_kc_mapping m
                        JOIN language_pack_kc k ON k.id = m.kc_id
                        WHERE m.example_id = e.id) AS kc_names
                FROM language_pack_example e
                WHERE e.language_pack_id = ?
                ORDER BY e.id
                """,
                languagePackId
        );
        return ApiResponse.success(examples);
    }

    @GetMapping({"/api/admin/language-packs/init-tasks/{taskId}/candidates",
                 "/api/admin/language-packs/init-tasks/{taskId}/candidates/"})
    public ApiResponse<List<Map<String, Object>>> listCandidates(@PathVariable Long taskId, Authentication authentication) {
        assertTaskLanguagePackAccessible(taskId, authentication);
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
                """
                SELECT g.id, g.candidate_title, g.validation_status, g.validation_message,
                       g.teaching_explanation, g.common_mistakes_json, g.source_pages_json, g.related_kc_ids_json,
                       g.kc_id, k.name AS kc_name
                FROM language_pack_problem_generation_log g
                LEFT JOIN language_pack_kc k ON k.id = g.kc_id
                WHERE g.init_task_id = ?
                ORDER BY g.id
                """,
                taskId
        );
        return ApiResponse.success(candidates);
    }

    @GetMapping({"/api/admin/language-packs/init-tasks/{taskId}/stage-logs",
                 "/api/admin/language-packs/init-tasks/{taskId}/stage-logs/"})
    public ApiResponse<List<Map<String, Object>>> listStageLogs(@PathVariable Long taskId, Authentication authentication) {
        assertTaskLanguagePackAccessible(taskId, authentication);
        List<Map<String, Object>> logs = jdbcTemplate.queryForList(
                """
                SELECT id, from_stage, to_stage, message, create_time
                FROM language_pack_init_stage_log
                WHERE task_id = ?
                ORDER BY create_time
                """,
                taskId
        );
        return ApiResponse.success(logs);
    }

    @GetMapping({"/api/admin/language-packs/init-tasks/{taskId}/artifacts",
                 "/api/admin/language-packs/init-tasks/{taskId}/artifacts/"})
    public ApiResponse<List<Map<String, Object>>> listArtifacts(@PathVariable Long taskId, Authentication authentication) {
        assertTaskLanguagePackAccessible(taskId, authentication);
        List<Map<String, Object>> artifacts = jdbcTemplate.queryForList(
                """
                SELECT id, artifact_type, source_stage, content_hash,
                       content_json, content_markdown, create_time
                FROM language_pack_init_artifact
                WHERE task_id = ?
                ORDER BY id
                """,
                taskId
        );
        return ApiResponse.success(artifacts);
    }

    @GetMapping({"/api/admin/language-packs/init-tasks/{taskId}/agent-runs",
                 "/api/admin/language-packs/init-tasks/{taskId}/agent-runs/"})
    public ApiResponse<List<Map<String, Object>>> listAgentRuns(@PathVariable Long taskId, Authentication authentication) {
        assertTaskLanguagePackAccessible(taskId, authentication);
        List<Map<String, Object>> runs = jdbcTemplate.queryForList(
                """
                SELECT id, agent_name, source_stage, model_name, prompt_version,
                       input_artifact_hash, output_artifact_hash, status,
                       failure_reason, create_time, update_time
                FROM language_pack_init_agent_run
                WHERE task_id = ?
                ORDER BY id
                """,
                taskId
        );
        return ApiResponse.success(runs);
    }

    @GetMapping({"/api/admin/language-packs/init-tasks/{taskId}/export",
                 "/api/admin/language-packs/init-tasks/{taskId}/export/"})
    public ResponseEntity<byte[]> exportTask(@PathVariable Long taskId, Authentication authentication) {
        assertTaskLanguagePackAccessible(taskId, authentication);
        Map<String, Object> exported = exportImportService.exportTask(taskId);
        try {
            byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(exported);
            String slug = String.valueOf(((Map<?, ?>) exported.get("language_pack")).get("slug"));
            String filename = "language-pack-" + slug + ".json";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBytes);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to serialize export data");
        }
    }

    @PostMapping({"/api/admin/language-packs/init-tasks/import",
                  "/api/admin/language-packs/init-tasks/import/"})
    public ApiResponse<LanguagePackInitTaskResponse> importTask(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        if (file.isEmpty()) {
            throw new BadRequestException("Import file is empty");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BadRequestException("Import file exceeds maximum size of 10MB");
        }
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        if (contentType != null && !contentType.contains("json")
                && (filename == null || !filename.endsWith(".json"))) {
            throw new BadRequestException("Import file must be a JSON file");
        }

        Map<String, Object> payload;
        try {
            byte[] bytes = file.getBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);
            payload = objectMapper.readValue(content, new TypeReference<>() {});
        } catch (IOException e) {
            throw new BadRequestException("Invalid JSON file: unable to parse content");
        }

        Long creatorId = resolveUserId(authentication);
        Long taskId = exportImportService.importTask(payload, creatorId);
        return ApiResponse.success(languagePackInitService.getTask(taskId));
    }

    private void assertTaskLanguagePackAccessible(Long taskId, Authentication authentication) {
        Long languagePackId = jdbcTemplate.query(
                "SELECT language_pack_id FROM language_pack_init_task WHERE id = ?",
                rs -> rs.next() ? rs.getLong("language_pack_id") : null,
                taskId
        );
        if (languagePackId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Init task not found");
        }
        assertLanguagePackAccessible(languagePackId, authentication);
    }

    private Long resolveLanguagePackIdByDocument(Long documentId) {
        return jdbcTemplate.query(
                "SELECT language_pack_id FROM language_pack_document WHERE id = ?",
                rs -> rs.next() ? rs.getLong("language_pack_id") : null,
                documentId
        );
    }

    private void assertLanguagePackAccessible(Long languagePackId, Authentication authentication) {
    }

    private void assertTaskCreator(Long taskId, Authentication authentication) {
        Long creatorId = jdbcTemplate.query(
                "SELECT lp.creator_id FROM language_pack_init_task t JOIN language_pack lp ON lp.id = t.language_pack_id WHERE t.id = ?",
                rs -> rs.next() ? rs.getLong("creator_id") : null,
                taskId
        );
        Long currentUserId = resolveUserId(authentication);
        if (!Objects.equals(creatorId, currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己创建的课程内容包");
        }
    }

    private boolean isTeacherRole(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TEACHER".equals(a.getAuthority()));
    }

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return jdbcTemplate.query(
                "SELECT id FROM \"user\" WHERE lower(username) = ?",
                rs -> rs.next() ? rs.getLong("id") : null,
                authentication.getName().toLowerCase()
        );
    }

    @PostMapping({"/api/admin/language-packs/{languagePackId}/detect-prerequisites",
                  "/api/admin/language-packs/{languagePackId}/detect-prerequisites/"})
    public ApiResponse<Map<String, Object>> detectPrerequisites(
            Authentication auth, @PathVariable Long languagePackId) {
        return ApiResponse.success(kcPrerequisiteDetectorService.detectAndPersist(languagePackId));
    }

    @GetMapping({"/api/admin/language-packs/{languagePackId}/rag-status",
                 "/api/admin/language-packs/{languagePackId}/rag-status/"})
    public ApiResponse<Map<String, Object>> getRagStatus(@PathVariable Long languagePackId) {
        return ApiResponse.success(ragDiagnosticsService.getRagStatus(languagePackId));
    }

    @PostMapping({"/api/admin/language-packs/{languagePackId}/rag-rebuild",
                  "/api/admin/language-packs/{languagePackId}/rag-rebuild/"})
    public ApiResponse<com.alethicode.service.rag.RagRebuildService.RebuildResult> rebuildRag(
            @PathVariable Long languagePackId) {
        return ApiResponse.success(ragRebuildService.rebuildPack(languagePackId));
    }
}
