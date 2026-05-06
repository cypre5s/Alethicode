package com.alethicode.controller;

import com.alethicode.dto.request.CreateLanguagePackQaSessionRequest;
import com.alethicode.dto.request.LanguagePackQaFeedbackRequest;
import com.alethicode.dto.request.LanguagePackQaMessageRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.languagepack.LanguagePackQaService;
import com.alethicode.service.languagepack.VideoJobService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@Validated
public class LanguagePackQaController {

    private final LanguagePackQaService languagePackQaService;
    private final VideoJobService videoJobService;

    public LanguagePackQaController(LanguagePackQaService languagePackQaService,
                                     VideoJobService videoJobService) {
        this.languagePackQaService = languagePackQaService;
        this.videoJobService = videoJobService;
    }

    @GetMapping({"/api/language-pack-qa/packs", "/api/language-pack-qa/packs/"})
    public ApiResponse<List<Map<String, Object>>> listQaPacks(Authentication authentication) {
        return ApiResponse.success(languagePackQaService.listQaPacks(username(authentication)));
    }

    @PostMapping({"/api/language-pack-qa/sessions", "/api/language-pack-qa/sessions/"})
    public ApiResponse<Map<String, Object>> createSession(
            Authentication authentication,
            @Valid @RequestBody CreateLanguagePackQaSessionRequest request
    ) {
        return ApiResponse.success(languagePackQaService.createSession(username(authentication), request.languagePackId()));
    }

    @GetMapping({"/api/language-pack-qa/sessions", "/api/language-pack-qa/sessions/"})
    public ApiResponse<List<Map<String, Object>>> listSessions(
            Authentication authentication,
            @RequestParam(value = "language_pack_id", required = false) Long languagePackId
    ) {
        return ApiResponse.success(languagePackQaService.listSessions(username(authentication), languagePackId));
    }

    @DeleteMapping({"/api/language-pack-qa/sessions/{sessionId}", "/api/language-pack-qa/sessions/{sessionId}/"})
    public ApiResponse<Object> deleteSession(
            Authentication authentication,
            @PathVariable Long sessionId
    ) {
        languagePackQaService.deleteSession(username(authentication), sessionId);
        return ApiResponse.success(null);
    }

    @PatchMapping({"/api/language-pack-qa/sessions/{sessionId}/starred", "/api/language-pack-qa/sessions/{sessionId}/starred/"})
    public ApiResponse<Map<String, Object>> toggleSessionStarred(
            Authentication authentication,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.success(languagePackQaService.toggleSessionStarred(username(authentication), sessionId));
    }

    @GetMapping({"/api/language-pack-qa/sessions/{sessionId}/usage", "/api/language-pack-qa/sessions/{sessionId}/usage/"})
    public ApiResponse<Map<String, Object>> getSessionUsage(
            Authentication authentication,
            @PathVariable Long sessionId
    ) {
        // Phase 1 chat composer plan 1.7：课件问答 ContextUsageBar 拉 token / 上下文用量。
        // 鉴权由 service 层 requireOwnedSessionId 统一做，越权抛 BadRequestException → 422。
        return ApiResponse.success(languagePackQaService.getSessionUsage(username(authentication), sessionId).toMap());
    }

    @GetMapping({"/api/language-pack-qa/sessions/{sessionId}/messages", "/api/language-pack-qa/sessions/{sessionId}/messages/"})
    public ApiResponse<List<Map<String, Object>>> listMessages(
            Authentication authentication,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.success(languagePackQaService.listMessages(username(authentication), sessionId));
    }

    @PostMapping({"/api/language-pack-qa/sessions/{sessionId}/messages", "/api/language-pack-qa/sessions/{sessionId}/messages/"})
    public ApiResponse<Map<String, Object>> sendMessage(
            Authentication authentication,
            @PathVariable Long sessionId,
            @Valid @RequestBody LanguagePackQaMessageRequest request,
            @RequestParam(value = "async", required = false, defaultValue = "false") Boolean async
    ) {
        if (Boolean.TRUE.equals(async)) {
            return ApiResponse.success(languagePackQaService.sendMessageAsync(username(authentication), sessionId, request.content()));
        }
        return ApiResponse.success(languagePackQaService.sendMessage(username(authentication), sessionId, request.content()));
    }

    /** 压缩课件问答会话历史，用 LLM 摘要替换旧消息。 */
    @PostMapping({"/api/language-pack-qa/sessions/{sessionId}/compact", "/api/language-pack-qa/sessions/{sessionId}/compact/"})
    public ApiResponse<Map<String, Object>> compactSession(
            Authentication authentication,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.success(languagePackQaService.compactSession(username(authentication), sessionId));
    }

    /** 分叉课件问答会话，复制源 session 消息到新 session。 */
    @PostMapping({"/api/language-pack-qa/sessions/{sessionId}/fork", "/api/language-pack-qa/sessions/{sessionId}/fork/"})
    public ApiResponse<Map<String, Object>> forkSession(
            Authentication authentication,
            @PathVariable Long sessionId,
            @RequestBody Map<String, Object> request
    ) {
        Long fromMessageId = request.get("fromMessageId") instanceof Number n ? n.longValue() : null;
        return ApiResponse.success(languagePackQaService.forkSession(username(authentication), sessionId, fromMessageId));
    }

    @PostMapping({"/api/language-pack-qa/messages/{messageId}/feedback", "/api/language-pack-qa/messages/{messageId}/feedback/"})
    public ApiResponse<Object> submitFeedback(
            Authentication authentication,
            @PathVariable Long messageId,
            @Valid @RequestBody LanguagePackQaFeedbackRequest request
    ) {
        languagePackQaService.submitFeedback(username(authentication), messageId, request.feedbackLabel(), request.comment());
        return ApiResponse.success(null);
    }

    @GetMapping({"/api/language-pack-qa/packs/{languagePackId}/documents/{documentId}/pages/{pageNo}",
                 "/api/language-pack-qa/packs/{languagePackId}/documents/{documentId}/pages/{pageNo}/"})
    public ApiResponse<Map<String, Object>> getCitationPage(
            Authentication authentication,
            @PathVariable Long languagePackId,
            @PathVariable Long documentId,
            @PathVariable Integer pageNo
    ) {
        return ApiResponse.success(languagePackQaService.getCitationPage(username(authentication), languagePackId, documentId, pageNo));
    }

    @GetMapping({"/api/language-pack-qa/packs/{languagePackId}/documents/{documentId}/preview",
                 "/api/language-pack-qa/packs/{languagePackId}/documents/{documentId}/preview/"})
    public ResponseEntity<Resource> previewDocument(
            Authentication authentication,
            @PathVariable Long languagePackId,
            @PathVariable Long documentId
    ) throws MalformedURLException {
        Path previewPath = languagePackQaService.getPreviewDocumentPath(username(authentication), languagePackId, documentId);
        Resource resource = new UrlResource(previewPath.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(previewPath.getFileName().toString()).build().toString())
                .body(resource);
    }

    @PostMapping({"/api/language-pack-qa/messages/{messageId}/video-jobs",
                  "/api/language-pack-qa/messages/{messageId}/video-jobs/"})
    public ApiResponse<Map<String, Object>> createVideoJob(
            Authentication authentication,
            @PathVariable Long messageId
    ) {
        requireAdmin(authentication);
        return ApiResponse.success(videoJobService.createOrReuse(username(authentication), messageId));
    }

    @GetMapping({"/api/language-pack-qa/video-jobs/{jobId}",
                 "/api/language-pack-qa/video-jobs/{jobId}/"})
    public ApiResponse<Map<String, Object>> getVideoJob(
            Authentication authentication,
            @PathVariable Long jobId
    ) {
        requireAdmin(authentication);
        return ApiResponse.success(videoJobService.getJob(username(authentication), jobId));
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new com.alethicode.exception.BadRequestException("请先登录");
        }
    }

    private String username(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
