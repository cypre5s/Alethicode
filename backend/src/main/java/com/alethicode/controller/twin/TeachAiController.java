package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.twin.teach.TeachAiSessionService;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/twin/teach-ai")
public class TeachAiController {

    private final TeachAiSessionService teachService;

    public TeachAiController(TeachAiSessionService teachService) {
        this.teachService = teachService;
    }

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> startSession(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        Number kcId = (Number) body.get("target_kc_id");
        if (kcId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "target_kc_id required");
        }
        Number problemId = (Number) body.get("problem_id");
        return ApiResponse.success(teachService.startSession(
                userId, kcId.longValue(),
                problemId != null ? problemId.longValue() : null));
    }

    @PostMapping("/{sessionId}/explain")
    public ApiResponse<Map<String, Object>> submitExplanation(
            @PathVariable Long sessionId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        String explanation = body.get("explanation");
        if (explanation == null || explanation.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "explanation required");
        }
        try {
            return ApiResponse.success(teachService.submitExplanation(userId, sessionId, explanation));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> listSessions(Authentication authentication) {
        Long userId = requireUserId(authentication);
        return ApiResponse.success(teachService.listSessions(userId));
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}
