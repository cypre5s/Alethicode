package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.twin.replay.CodeReplayService;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/twin/replay")
public class CodeReplayController {

    private final CodeReplayService replayService;

    public CodeReplayController(CodeReplayService replayService) {
        this.replayService = replayService;
    }

    @GetMapping("/events")
    public ApiResponse<Map<String, Object>> getReplayData(
            @RequestParam("problem_id") Long problemId,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        return ApiResponse.success(replayService.getReplayData(userId, problemId));
    }

    @GetMapping("/problems")
    public ApiResponse<List<Map<String, Object>>> listReplayableProblems(
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        return ApiResponse.success(replayService.listReplayableProblems(userId));
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}
