package com.alethicode.controller;

import com.alethicode.dto.request.OverrideProfileSummaryRequest;
import com.alethicode.dto.request.UpdateProfilePreferencesRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.StudentProfileView;
import com.alethicode.service.aitutor.profile.ProfileViewService;
import com.alethicode.util.AuthUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Student-side learner profile dashboard endpoints (P1 Persistent Memory layer).
 *
 * Design: docs/plans/2026-04-25-persistent-memory-layer-design.md  6.5
 */
@RestController
@RequestMapping("/api/ai/tutor/profile")
public class ProfileController {

    private final ProfileViewService profileViewService;

    public ProfileController(ProfileViewService profileViewService) {
        this.profileViewService = profileViewService;
    }

    @GetMapping("/me")
    public ApiResponse<StudentProfileView> getMyProfile(Authentication authentication) {
        Long userId = requireUserId(authentication);
        return ApiResponse.success(profileViewService.getMyProfile(userId));
    }

    @PatchMapping("/me/preferences")
    public ApiResponse<Map<String, Object>> updatePreferences(
            @Valid @RequestBody UpdateProfilePreferencesRequest request,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        profileViewService.updatePreferences(userId, request.personalizationEnabled());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("personalization_enabled", request.personalizationEnabled());
        return ApiResponse.success(body);
    }

    @PostMapping("/me/refresh")
    public ApiResponse<Map<String, Object>> refreshSummary(Authentication authentication) {
        Long userId = requireUserId(authentication);
        profileViewService.refreshSummary(userId);
        StudentProfileView view = profileViewService.getMyProfile(userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("version", view.narrativeSummary().version());
        body.put("summary_text", view.narrativeSummary().text());
        return ApiResponse.success(body);
    }

    @PostMapping("/me/summary/override")
    public ApiResponse<Map<String, Object>> overrideSummary(
            @Valid @RequestBody OverrideProfileSummaryRequest request,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        profileViewService.overrideSummary(userId, request.summaryText());
        StudentProfileView view = profileViewService.getMyProfile(userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("version", view.narrativeSummary().version());
        return ApiResponse.success(body);
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}
