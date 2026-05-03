package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.twin.LearningTimelineResponse;
import com.alethicode.service.twin.timeline.LearningTimelineService;
import com.alethicode.util.AuthUserResolver;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/twin/timeline")
public class LearningTimelineController {

    private final LearningTimelineService timelineService;

    public LearningTimelineController(LearningTimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @GetMapping
    public ApiResponse<LearningTimelineResponse> getTimeline(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> kinds,
            @RequestParam(defaultValue = "200") int limit,
            Authentication authentication
    ) {
        Long userId = requireUserId(authentication);
        try {
            LearningTimelineResponse response = timelineService.query(userId, from, to, kinds, limit);
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}
