package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.exception.BusinessExceptions;
import com.alethicode.service.aitutor.AiTraceService;
import com.alethicode.service.aitutor.graph.TutorWorkflowProjectionService;
import com.alethicode.service.aitutor.observability.AgentObservabilityService;
import com.alethicode.service.aitutor.profile.StrategyFeedbackService;
import com.alethicode.service.aitutor.rlhf.PromptVariantSelector;
import com.alethicode.service.aitutor.rollout.RolloutPolicyService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminAiObservabilityController {

    private final AiTraceService aiTraceService;
    private final RolloutPolicyService rolloutPolicyService;
    private final AgentObservabilityService agentObservabilityService;
    private final PromptVariantSelector promptVariantSelector;
    private final TutorWorkflowProjectionService tutorWorkflowProjectionService;

    public AdminAiObservabilityController(AiTraceService aiTraceService,
                                           RolloutPolicyService rolloutPolicyService,
                                           AgentObservabilityService agentObservabilityService,
                                           PromptVariantSelector promptVariantSelector,
                                           TutorWorkflowProjectionService tutorWorkflowProjectionService) {
        this.aiTraceService = aiTraceService;
        this.rolloutPolicyService = rolloutPolicyService;
        this.agentObservabilityService = agentObservabilityService;
        this.promptVariantSelector = promptVariantSelector;
        this.tutorWorkflowProjectionService = tutorWorkflowProjectionService;
    }

    @GetMapping({"/api/admin/ai/traces", "/api/admin/ai/traces/"})
    public ApiResponse<List<Map<String, Object>>> getTraces(
            Authentication auth,
            @RequestParam("traceId") String traceId) {
        return ApiResponse.success(aiTraceService.getTraceDetails(traceId));
    }

    @GetMapping({"/api/admin/ai/quality-report", "/api/admin/ai/quality-report/"})
    public ApiResponse<Map<String, Object>> getQualityReport(
            Authentication auth,
            @RequestParam("languagePackId") Long languagePackId) {
        return ApiResponse.success(aiTraceService.getQualityReport(languagePackId));
    }

    @GetMapping({"/api/admin/ai/rollout-status", "/api/admin/ai/rollout-status/"})
    public ApiResponse<Map<String, Object>> getRolloutStatus(Authentication auth) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", rolloutPolicyService.isEnabled());
        return ApiResponse.success(status);
    }

    @GetMapping({"/api/admin/ai/agents/overview", "/api/admin/ai/agents/overview/"})
    public ApiResponse<Map<String, Object>> getAgentsOverview(
            Authentication auth,
            @RequestParam(name = "range", required = false, defaultValue = "7d") String range) {
        return ApiResponse.success(agentObservabilityService.getAgentsOverview(range));
    }

    @GetMapping({"/api/admin/ai/traces/{traceId}/timeline", "/api/admin/ai/traces/{traceId}/timeline/"})
    public ApiResponse<Map<String, Object>> getTraceTimeline(
            Authentication auth,
            @PathVariable("traceId") String traceId) {
        return ApiResponse.success(agentObservabilityService.getTraceTimeline(traceId));
    }

    @GetMapping({"/api/admin/ai/evaluations/dashboard", "/api/admin/ai/evaluations/dashboard/"})
    public ApiResponse<Map<String, Object>> getEvaluationsDashboard(
            Authentication auth,
            @RequestParam(name = "range", required = false, defaultValue = "7d") String range) {
        return ApiResponse.success(agentObservabilityService.getEvaluationsDashboard(range));
    }

    @GetMapping({"/api/admin/ai/behavior-analytics", "/api/admin/ai/behavior-analytics/"})
    public ApiResponse<Map<String, Object>> getBehaviorAnalytics(
            Authentication auth,
            @RequestParam(name = "range", required = false, defaultValue = "7d") String range) {
        return ApiResponse.success(agentObservabilityService.getBehaviorAnalytics(range));
    }

    @GetMapping({"/api/admin/ai/tutor-workflow-timeline", "/api/admin/ai/tutor-workflow-timeline/"})
    public ApiResponse<Object> getTutorWorkflowTimeline(
            Authentication auth,
            @RequestParam("session_id") String sessionId) {
        var session = tutorWorkflowProjectionService.getSession(sessionId);
        var events = tutorWorkflowProjectionService.getEvents(sessionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session", session.orElse(null));
        result.put("events", events);
        return ApiResponse.success(result);
    }

    @GetMapping({"/api/admin/ai/prompt-variants", "/api/admin/ai/prompt-variants/"})
    public ApiResponse<Map<String, Object>> listPromptVariants(
            Authentication auth,
            @RequestParam("agent_key") String agentKey) {
        String normalizedAgentKey = agentKey == null ? "" : agentKey.trim().toLowerCase(Locale.ROOT);
        if (!StrategyFeedbackService.isAllowedStrategy(normalizedAgentKey)) {
            throw BusinessExceptions.fromLegacy("error",
                    "agent_key 必须是: " + StrategyFeedbackService.allowedStrategiesDescription());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent_key", normalizedAgentKey);
        payload.put("variants", promptVariantSelector.listVariants(normalizedAgentKey));
        return ApiResponse.success(payload);
    }
}
