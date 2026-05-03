package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.twin.health.LearningHealthAggregator;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/twin/health")
public class TwinHealthController {

    private final LearningHealthAggregator healthAggregator;

    public TwinHealthController(LearningHealthAggregator healthAggregator) {
        this.healthAggregator = healthAggregator;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getHealth(Authentication authentication) {
        Long userId = requireUserId(authentication);
        return ApiResponse.success(healthAggregator.aggregate(userId));
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}
