package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.languagepack.LanguagePackQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class LanguagePackQueryController {

    private final LanguagePackQueryService languagePackQueryService;

    public LanguagePackQueryController(LanguagePackQueryService languagePackQueryService) {
        this.languagePackQueryService = languagePackQueryService;
    }

    @GetMapping({"/api/language-packs", "/api/language-packs/"})
    public ApiResponse<List<Map<String, Object>>> listPublishedPacks() {
        return ApiResponse.success(languagePackQueryService.listPublishedPacks());
    }

    @GetMapping({"/api/language-packs/visible", "/api/language-packs/visible/"})
    public ApiResponse<List<Map<String, Object>>> listVisiblePacks(Authentication authentication) {
        String username = authentication == null ? null : authentication.getName();
        return ApiResponse.success(languagePackQueryService.listVisiblePacks(username));
    }

    @GetMapping({"/api/language-packs/{id}", "/api/language-packs/{id}/"})
    public ApiResponse<Map<String, Object>> getPackDetail(@PathVariable Long id) {
        return ApiResponse.success(languagePackQueryService.getPackDetail(id));
    }

    @GetMapping({"/api/language-packs/{id}/documents", "/api/language-packs/{id}/documents/"})
    public ApiResponse<List<Map<String, Object>>> listPackDocuments(@PathVariable Long id) {
        return ApiResponse.success(languagePackQueryService.listPackDocuments(id));
    }

    @GetMapping({"/api/language-packs/{id}/chapters", "/api/language-packs/{id}/chapters/"})
    public ApiResponse<List<Map<String, Object>>> listPackChapters(@PathVariable Long id) {
        return ApiResponse.success(languagePackQueryService.listPackChapters(id));
    }

    @GetMapping({"/api/language-packs/{languagePackId}/documents/{documentId}/pages/{pageNo}",
                 "/api/language-packs/{languagePackId}/documents/{documentId}/pages/{pageNo}/"})
    public ApiResponse<Map<String, Object>> getPagePreview(
            @PathVariable Long languagePackId,
            @PathVariable Long documentId,
            @PathVariable Integer pageNo) {
        return ApiResponse.success(languagePackQueryService.getPagePreview(languagePackId, documentId, pageNo));
    }
}
