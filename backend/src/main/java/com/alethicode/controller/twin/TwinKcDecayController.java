package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.twin.decay.TwinKcDecayService;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/twin/kc-decay")
public class TwinKcDecayController {

    private final TwinKcDecayService decayService;

    public TwinKcDecayController(TwinKcDecayService decayService) {
        this.decayService = decayService;
    }

    @GetMapping("/queue")
    public ApiResponse<Map<String, Object>> getDecayQueue(Authentication authentication) {
        Long userId = requireUserId(authentication);
        decayService.updateDecayStates(userId);
        return ApiResponse.success(decayService.getDecayQueue(userId));
    }

    @PostMapping("/{kcId}/review")
    public ApiResponse<Map<String, Object>> reviewKc(
            @PathVariable Long kcId,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        return ApiResponse.success(decayService.reviewKc(userId, kcId));
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}
