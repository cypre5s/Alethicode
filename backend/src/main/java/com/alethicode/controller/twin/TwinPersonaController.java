package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.aitutor.profile.LearnerNarrativeSummaryService;
import com.alethicode.service.aitutor.profile.LearnerNarrativeSummaryService.NarrativeSummary;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/twin/persona")
public class TwinPersonaController {

    private final LearnerNarrativeSummaryService summaryService;
    private final JdbcTemplate jdbcTemplate;

    public TwinPersonaController(LearnerNarrativeSummaryService summaryService,
                                  JdbcTemplate jdbcTemplate) {
        this.summaryService = summaryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getPersona(Authentication authentication) {
        Long userId = requireUserId(authentication);
        NarrativeSummary summary = summaryService.loadOrGenerate(userId);
        return ApiResponse.success(toPersonaMap(summary));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> overridePersona(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        String text = body.get("summary_text");
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text-empty");
        }
        if (text.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text-too-long");
        }
        summaryService.overrideSummary(userId, text);
        NarrativeSummary updated = summaryService.loadOrGenerate(userId);
        return ApiResponse.success(toPersonaMap(updated));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refreshPersona(Authentication authentication) {
        Long userId = requireUserId(authentication);
        summaryService.refreshIfStale(userId);
        NarrativeSummary refreshed = summaryService.loadOrGenerate(userId);
        return ApiResponse.success(toPersonaMap(refreshed));
    }

    @PostMapping("/feedback")
    public ApiResponse<Map<String, Object>> submitFeedback(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        Boolean isAccurate = (Boolean) body.get("is_accurate");
        if (isAccurate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "is_accurate required");
        }
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        if (reason != null && reason.length() > 500) {
            reason = reason.substring(0, 500);
        }

        NarrativeSummary current = summaryService.loadOrGenerate(userId);
        jdbcTemplate.update(
                "INSERT INTO ai_learner_narrative_feedback (user_id, summary_version, is_accurate, reason) VALUES (?, ?, ?, ?)",
                userId, current.version(), isAccurate, reason
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        return ApiResponse.success(result);
    }

    private Map<String, Object> toPersonaMap(NarrativeSummary s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("summary_text", s.summaryText());
        map.put("summary_version", s.version());
        map.put("learning_style_key", s.learningStyleKey());
        map.put("is_user_overridden", s.userOverridden());
        map.put("user_disabled", s.userDisabled());
        map.put("updated_at", s.updatedAt() != null ? s.updatedAt().toString() : null);
        return map;
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}
