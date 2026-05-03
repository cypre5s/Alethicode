package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.twin.weekly.TwinWeeklyDigestService;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/twin/weekly")
public class TwinWeeklyController {

    private final TwinWeeklyDigestService digestService;

    public TwinWeeklyController(TwinWeeklyDigestService digestService) {
        this.digestService = digestService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getWeekly(Authentication authentication) {
        Long userId = requireUserId(authentication);
        return ApiResponse.success(digestService.getOrGenerateWeeklyDigest(userId));
    }

    @PostMapping("/reflection")
    public ApiResponse<Map<String, Object>> submitReflection(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text required");
        }
        digestService.submitReflection(userId, text.trim());
        return ApiResponse.success(Map.of("ok", true));
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}
