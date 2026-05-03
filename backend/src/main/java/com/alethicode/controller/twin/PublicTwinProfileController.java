package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.twin.profile.PublicTwinProfileService;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/twin")
public class PublicTwinProfileController {

    private final PublicTwinProfileService profileService;

    public PublicTwinProfileController(PublicTwinProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/public/{handle}")
    public ApiResponse<Map<String, Object>> getPublicProfile(@PathVariable String handle) {
        Map<String, Object> profile = profileService.getPublicProfile(handle);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "profile-not-found-or-private");
        }
        return ApiResponse.success(profile);
    }

    @PatchMapping("/profile/privacy")
    public ApiResponse<Map<String, Object>> updatePrivacy(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        profileService.updatePrivacy(userId, body);
        return ApiResponse.success(Map.of("ok", true));
    }
}
