package com.alethicode.controller;

import com.alethicode.dto.request.IdeateAnalyzeRequest;
import com.alethicode.dto.request.IdeateInsertedRequest;
import com.alethicode.dto.request.PreflightCheckRequest;
import com.alethicode.dto.request.StrategyFeedbackRequest;
import com.alethicode.dto.request.TutorInferenceRequest;
import jakarta.validation.Valid;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.aitutor.AITutorAnalyticsDomainService;
import com.alethicode.service.aitutor.AITutorKnowledgeDomainService;
import com.alethicode.service.aitutor.AITutorSessionDomainService;
import com.alethicode.service.aitutor.AITutorWorkflowDomainService;
import com.alethicode.service.aitutor.profile.AITutorWelcomeService;
import com.alethicode.service.aitutor.profile.LearningTwinService;
import com.alethicode.service.aitutor.profile.StrategyFeedbackService;
import com.alethicode.service.aitutor.parsons.ParsonsCapabilityService;
import com.alethicode.service.aitutor.visualize.VisualizeCapabilityService;
import com.alethicode.service.aitutor.visualize.VisualizeIntent;
import com.alethicode.service.aitutor.visualize.VisualizeRequest;
import com.alethicode.service.aitutor.visualize.VisualizeResult;
import com.alethicode.service.aitutor.visualize.VisualizeValidationException;
import com.alethicode.util.AuthUserResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping
public class AITutorController {

    private static final Logger log = LoggerFactory.getLogger(AITutorController.class);

    private final AITutorSessionDomainService aiTutorSessionDomainService;
    private final AITutorAnalyticsDomainService aiTutorAnalyticsDomainService;
    private final AITutorKnowledgeDomainService aiTutorKnowledgeDomainService;
    private final AITutorWorkflowDomainService aiTutorWorkflowDomainService;
    private final LearningTwinService learningTwinService;
    private final AITutorWelcomeService aiTutorWelcomeService;
    private final StrategyFeedbackService strategyFeedbackService;
    private final VisualizeCapabilityService visualizeCapabilityService;
    private final ParsonsCapabilityService parsonsCapabilityService;

    public AITutorController(AITutorSessionDomainService aiTutorSessionDomainService,
                             AITutorAnalyticsDomainService aiTutorAnalyticsDomainService,
                             AITutorKnowledgeDomainService aiTutorKnowledgeDomainService,
                             AITutorWorkflowDomainService aiTutorWorkflowDomainService,
                             LearningTwinService learningTwinService,
                             AITutorWelcomeService aiTutorWelcomeService,
                             StrategyFeedbackService strategyFeedbackService,
                             VisualizeCapabilityService visualizeCapabilityService,
                             ParsonsCapabilityService parsonsCapabilityService) {
        this.aiTutorSessionDomainService = aiTutorSessionDomainService;
        this.aiTutorAnalyticsDomainService = aiTutorAnalyticsDomainService;
        this.aiTutorKnowledgeDomainService = aiTutorKnowledgeDomainService;
        this.aiTutorWorkflowDomainService = aiTutorWorkflowDomainService;
        this.learningTwinService = learningTwinService;
        this.aiTutorWelcomeService = aiTutorWelcomeService;
        this.strategyFeedbackService = strategyFeedbackService;
        this.visualizeCapabilityService = visualizeCapabilityService;
        this.parsonsCapabilityService = parsonsCapabilityService;
    }

    @PostMapping({
            "/api/ai/tutor/inference", "/api/ai/tutor/inference/"
    })
    public ApiResponse<Object> inference(@RequestBody TutorInferenceRequest request, Authentication authentication) {
        return aiTutorSessionDomainService.inference(request, authentication);
    }

    @GetMapping({"/api/ai/tutor/task", "/api/ai/tutor/task/"})
    public ApiResponse<Object> taskStatus(@RequestParam(name = "task_id", required = false) String taskId,
                                          Authentication authentication) {
        return aiTutorSessionDomainService.taskStatus(taskId, authentication);
    }

    @GetMapping({
            "/api/ai/tutor/session", "/api/ai/tutor/session/"
    })
    public ApiResponse<Object> session(@RequestParam(name = "session_id", required = false) String sessionId,
                                       Authentication authentication) {
        return aiTutorSessionDomainService.session(sessionId, authentication);
    }

    @DeleteMapping({
            "/api/ai/tutor/session", "/api/ai/tutor/session/"
    })
    public ApiResponse<Object> closeSession(@RequestParam(name = "session_id", required = false) String sessionId,
                                            Authentication authentication) {
        return aiTutorSessionDomainService.closeSession(sessionId, authentication);
    }

    @GetMapping({"/api/ai/skill/radar", "/api/ai/skill/radar/"})
    public ApiResponse<Object> skillRadar(@RequestParam Map<String, String> params, Authentication authentication) {
        return aiTutorAnalyticsDomainService.skillRadar(params, authentication);
    }

    @GetMapping({
            "/api/ai/skill/heatmap", "/api/ai/skill/heatmap/"
    })
    public ApiResponse<Object> skillHeatmap(@RequestParam Map<String, String> params, Authentication authentication) {
        return aiTutorAnalyticsDomainService.skillHeatmap(params, authentication);
    }

    @GetMapping({
            "/api/ai/skill/recommend", "/api/ai/skill/recommend/"
    })
    public ApiResponse<Object> recommendProblems(@RequestParam Map<String, String> params, Authentication authentication) {
        return aiTutorAnalyticsDomainService.recommendProblems(params, authentication);
    }

    @PostMapping({
            "/api/ai/tutor/error-attribution", "/api/ai/tutor/error-attribution/"
    })
    public ApiResponse<Object> errorAttribution(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorAnalyticsDomainService.errorAttribution(request, authentication);
    }

    @PostMapping({
            "/api/analytics/anti-patterns/analyze", "/api/analytics/anti-patterns/analyze/"
    })
    public ApiResponse<Object> antiPatterns(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorAnalyticsDomainService.antiPatternAnalyze(request, authentication);
    }

    @PostMapping({
            "/api/ai/tutor/eval-feedback", "/api/ai/tutor/eval-feedback/"
    })
    public ApiResponse<Object> evalFeedback(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorAnalyticsDomainService.evalFeedback(request, authentication);
    }

    @PostMapping({
            "/api/ai/tutor/safety-feedback", "/api/ai/tutor/safety-feedback/"
    })
    public ApiResponse<Object> safetyFeedback(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorAnalyticsDomainService.safetyFeedback(request, authentication);
    }

    @GetMapping({
            "/api/ai/tutor/notebook", "/api/ai/tutor/notebook/"
    })
    public ApiResponse<Object> notebookGet(@RequestParam Map<String, String> params, Authentication authentication) {
        return aiTutorKnowledgeDomainService.notebookList(params, authentication);
    }

    @PostMapping({
            "/api/ai/tutor/notebook", "/api/ai/tutor/notebook/"
    })
    public ApiResponse<Object> notebookCreate(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorKnowledgeDomainService.notebookCreate(request, authentication);
    }

    @PutMapping({
            "/api/ai/tutor/notebook", "/api/ai/tutor/notebook/"
    })
    public ApiResponse<Object> notebookUpdate(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorKnowledgeDomainService.notebookUpdate(request, authentication);
    }

    @DeleteMapping({
            "/api/ai/tutor/notebook", "/api/ai/tutor/notebook/"
    })
    public ApiResponse<Object> notebookDelete(@RequestParam(name = "id", required = false) String id,
                                              Authentication authentication) {
        return aiTutorKnowledgeDomainService.notebookDelete(id, authentication);
    }

    @GetMapping({
            "/api/ai/tutor/notebook/export", "/api/ai/tutor/notebook/export/"
    })
    public ApiResponse<Object> notebookExport(Authentication authentication) {
        return aiTutorKnowledgeDomainService.notebookExport(authentication);
    }

    @GetMapping({
            "/api/ai/tutor/notebook/class-frequency", "/api/ai/tutor/notebook/class-frequency/"
    })
    public ApiResponse<Object> notebookClassFrequency(Authentication authentication) {
        return aiTutorKnowledgeDomainService.notebookClassFrequency(authentication);
    }

    @PostMapping({"/api/ai/tutor/notebook/generate-reflection", "/api/ai/tutor/notebook/generate-reflection/"})
    public ApiResponse<Object> notebookGenerateReflection(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorKnowledgeDomainService.notebookGenerateReflection(request, authentication);
    }

    @GetMapping({"/api/ai/tutor/notebook/weekly-summary", "/api/ai/tutor/notebook/weekly-summary/"})
    public ApiResponse<Object> notebookWeeklySummary(Authentication authentication) {
        return aiTutorKnowledgeDomainService.notebookWeeklySummary(authentication);
    }

    @PostMapping({"/api/ai/tutor/supplement-plan", "/api/ai/tutor/supplement-plan/"})
    public ApiResponse<Object> supplementPlan(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorKnowledgeDomainService.supplementPlan(request, authentication);
    }

    @PostMapping({
            "/api/ai/ideate/analyze", "/api/ai/ideate/analyze/"
    })
    public ApiResponse<Object> ideateAnalyze(@Valid @RequestBody IdeateAnalyzeRequest request, Authentication authentication) {
        return ApiResponse.error("This endpoint has been migrated to POST /api/ai/tutor-workflow-sessions/{sessionId}/runs with event=IDEATING");
    }

    @PostMapping({
            "/api/ai/ideate/skeleton", "/api/ai/ideate/skeleton/"
    })
    public ApiResponse<Object> ideateSkeleton(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorWorkflowDomainService.ideateSkeleton(request, authentication);
    }

    @PostMapping({
            "/api/ai/ideate/inserted", "/api/ai/ideate/inserted/"
    })
    public ApiResponse<Object> ideateInserted(@Valid @RequestBody IdeateInsertedRequest request, Authentication authentication) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("problem_id", request.problemId());
        data.put("session_id", trimToEmpty(request.sessionId()));
        data.put("inserted", true);
        data.put("ts", System.currentTimeMillis());
        return aiTutorWorkflowDomainService.codeSnapshot(data, authentication);
    }

    @PostMapping({
            "/api/ai/frustration/analyze", "/api/ai/frustration/analyze/"
    })
    public ApiResponse<Object> frustrationAnalyze(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorAnalyticsDomainService.frustrationAnalyze(request, authentication);
    }

    @PostMapping({
            "/api/ai/frustration/event", "/api/ai/frustration/event/"
    })
    public ApiResponse<Object> frustrationEvent(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorAnalyticsDomainService.frustrationEvent(request, authentication);
    }

    @PostMapping({
            "/api/ai/frustration/alert", "/api/ai/frustration/alert/"
    })
    public ApiResponse<Object> frustrationAlert(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorAnalyticsDomainService.frustrationAlert(request, authentication);
    }

    @GetMapping({
            "/api/ai/misconceptions/mine", "/api/ai/misconceptions/mine/"
    })
    public ApiResponse<Object> misconceptionsMine(Authentication authentication) {
        return aiTutorKnowledgeDomainService.misconceptionsMine(authentication);
    }

    @GetMapping({"/api/ai/review/due", "/api/ai/review/due/"})
    public ApiResponse<Object> reviewDue(@RequestParam Map<String, String> params, Authentication authentication) {
        return aiTutorKnowledgeDomainService.reviewDue(params, authentication);
    }

    @PostMapping({
            "/api/ai/preflight/check", "/api/ai/preflight/check/"
    })
    public ApiResponse<Object> preflightCheck(@RequestBody PreflightCheckRequest request, Authentication authentication) {
        return aiTutorSessionDomainService.preflightCheck(request, authentication);
    }

    @PostMapping({
            "/api/ai/code-snapshot", "/api/ai/code-snapshot/"
    })
    public ApiResponse<Object> codeSnapshot(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorWorkflowDomainService.codeSnapshot(request, authentication);
    }

    @PostMapping({
            "/api/ai/learning-events/batch", "/api/ai/learning-events/batch/"
    })
    public ApiResponse<Object> learningEventsBatch(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorWorkflowDomainService.learningEventsBatch(request, authentication);
    }

    @GetMapping({
            "/api/ai/calibration/status", "/api/ai/calibration/status/"
    })
    public ApiResponse<Object> calibrationStatus(Authentication authentication) {
        return aiTutorWorkflowDomainService.calibrationStatus(authentication);
    }

    @PostMapping({
            "/api/ai/calibration/answer", "/api/ai/calibration/answer/"
    })
    public ApiResponse<Object> calibrationAnswer(@RequestBody Map<String, Object> request, Authentication authentication) {
        return aiTutorWorkflowDomainService.calibrationAnswer(request, authentication);
    }

    @PostMapping({
            "/api/ai/calibration/skip", "/api/ai/calibration/skip/"
    })
    public ApiResponse<Object> calibrationSkip(Authentication authentication) {
        return aiTutorWorkflowDomainService.calibrationSkip(authentication);
    }

    @GetMapping({
            "/api/ai/knowledge-graph", "/api/ai/knowledge-graph/"
    })
    public ApiResponse<Object> knowledgeGraph(@RequestParam Map<String, String> params, Authentication authentication) {
        return aiTutorKnowledgeDomainService.knowledgeGraph(params, authentication);
    }

    @GetMapping({
            "/api/ai/knowledge-graph/snapshot", "/api/ai/knowledge-graph/snapshot/"
    })
    public ApiResponse<Object> knowledgeGraphSnapshot(@RequestParam Map<String, String> params, Authentication authentication) {
        return aiTutorKnowledgeDomainService.knowledgeGraphSnapshot(params, authentication);
    }

    @GetMapping({
            "/api/ai/knowledge-graph/kc/{kc_id}/detail", "/api/ai/knowledge-graph/kc/{kc_id}/detail/"
    })
    public ApiResponse<Object> kcDetail(@PathVariable("kc_id") String kcId,
                                        @RequestParam Map<String, String> params,
                                        Authentication authentication) {
        return aiTutorKnowledgeDomainService.kcDetail(kcId, params, authentication);
    }

    @GetMapping({
            "/api/ai/submission-river/{problem_id}", "/api/ai/submission-river/{problem_id}/"
    })
    public ApiResponse<Object> submissionRiver(@PathVariable("problem_id") String problemId,
                                               @RequestParam Map<String, String> params,
                                               Authentication authentication) {
        return aiTutorAnalyticsDomainService.submissionRiver(problemId, params, authentication);
    }

    @GetMapping({"/api/ai/learning-twins/current", "/api/ai/learning-twins/current/"})
    public ApiResponse<Object> learningTwin(@RequestParam Map<String, String> params,
                                            Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            return ApiResponse.error("permission-denied", "请先登录");
        }
        Long languagePackId = parseLong(params.get("language_pack_id"));
        Long problemId = parseLong(params.get("problem_id"));
        if (languagePackId == null || problemId == null) {
            return ApiResponse.error("error", "language_pack_id 和 problem_id 必填");
        }
        Map<String, Object> twin = learningTwinService.getLearningTwin(userId, languagePackId, problemId);
        return ApiResponse.success(twin);
    }

    @GetMapping({"/api/ai/tutor/welcome", "/api/ai/tutor/welcome/"})
    public ApiResponse<Object> tutorWelcome(@RequestParam Map<String, String> params,
                                            Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            return ApiResponse.error("permission-denied", "请先登录");
        }
        Long problemId = parseLong(params.get("problem_id"));
        if (problemId == null) {
            return ApiResponse.error("error", "problem_id 必填");
        }
        Map<String, Object> welcome = aiTutorWelcomeService.getWelcome(userId, problemId);
        return ApiResponse.success(welcome);
    }

    @PostMapping({"/api/ai/tutor/visualize/inline", "/api/ai/tutor/visualize/inline/"})
    public ResponseEntity<ApiResponse<Object>> inlineVisualize(@RequestBody Map<String, Object> request,
                                                               Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("permission-denied", "请先登录"));
        }
        Long problemId = parseLong(request.get("problem_id"));
        if (problemId == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error("problem_id 必填"));
        }
        String intentRaw = trimToEmpty(request.get("intent"));
        VisualizeIntent intent = VisualizeIntent.fromKey(intentRaw)
                .orElse(null);
        if (intent == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error("intent 非法"));
        }
        String prompt = trimToEmpty(request.get("prompt"));
        if (prompt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error("prompt 必填"));
        }

        try {
            VisualizeResult result = visualizeCapabilityService.dispatch(new VisualizeRequest(
                    intent,
                    prompt,
                    castMap(request.get("context_hints")),
                    userId,
                    problemId,
                    trimToEmpty(request.get("session_id")),
                    trimToEmpty(request.get("source_role"))
            ));
            String cardId = "C-V-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("card_id", cardId);
            response.putAll(result.toCardPayload());
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException | VisualizeValidationException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("inline visualize failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("可视化服务暂不可用，请稍后重试"));
        }
    }

    @PostMapping({"/api/ai/tutor/parsons/dispatch", "/api/ai/tutor/parsons/dispatch/"})
    public ResponseEntity<ApiResponse<Object>> parsonsDispatch(@RequestBody Map<String, Object> request,
                                                              Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("permission-denied", "请先登录"));
        }
        Long problemId = parseLong(request.get("problem_id"));
        if (problemId == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error("problem_id 必填"));
        }
        try {
            ParsonsCapabilityService.DispatchResult result = parsonsCapabilityService.dispatch(
                    new ParsonsCapabilityService.DispatchRequest(
                            userId,
                            problemId,
                            trimOrNull(request.get("session_id")),
                            trimOrNull(request.get("source_card_id")),
                            trimOrNull(request.get("previous_session_id")),
                            trimOrNull(request.get("fsrs_origin")),
                            request.get("override_fading_level") instanceof Number n ? n.intValue() : null
                    ));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("parsons_session_id", result.parsonsSessionId());
            body.put("card_payload", result.cardPayload());
            return ResponseEntity.ok(ApiResponse.success(body));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping({"/api/ai/tutor/parsons/submit", "/api/ai/tutor/parsons/submit/"})
    public ResponseEntity<ApiResponse<Object>> parsonsSubmit(@RequestBody Map<String, Object> request,
                                                             Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("permission-denied", "请先登录"));
        }
        String sessionId = trimToEmpty(request.get("parsons_session_id"));
        if (sessionId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error("parsons_session_id 必填"));
        }
        Object rawOrder = request.get("ordered_block_ids");
        java.util.List<String> ordered = rawOrder instanceof java.util.List<?> list
                ? list.stream().map(String::valueOf).toList()
                : java.util.List.of();
        try {
            ParsonsCapabilityService.GradeResult result = parsonsCapabilityService.grade(
                    new ParsonsCapabilityService.GradeRequest(sessionId, ordered));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("passed", result.passed());
            body.put("judge_status", result.judgeStatus());
            body.put("attempts", result.attempts());
            body.put("walkthrough_required", result.walkthroughRequired());
            body.put("cascade_degrade", result.cascadeDegrade());
            body.put("cascade_failfast", result.cascadeFailfast());
            body.put("current_fading_level", result.currentFadingLevel());
            if (result.nextFadingLevel() != null) {
                body.put("next_fading_level", result.nextFadingLevel());
            }
            if (result.hint() != null) body.put("hint", result.hint());
            return ResponseEntity.ok(ApiResponse.success(body));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping({"/api/ai/tutor/parsons/walkthrough", "/api/ai/tutor/parsons/walkthrough/"})
    public ResponseEntity<ApiResponse<Object>> parsonsWalkthrough(@RequestBody Map<String, Object> request,
                                                                  Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("permission-denied", "请先登录"));
        }
        String sessionId = trimToEmpty(request.get("parsons_session_id"));
        if (sessionId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error("parsons_session_id 必填"));
        }
        String text = trimToEmpty(request.get("text"));
        try {
            ParsonsCapabilityService.WalkthroughResult result = parsonsCapabilityService.submitWalkthrough(
                    new ParsonsCapabilityService.WalkthroughRequest(sessionId, text));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("score", result.score());
            body.put("feedback", result.feedback());
            body.put("passed", result.passed());
            body.put("can_rewrite", result.canRewrite());
            if (result.breakthroughNotebookId() != null) {
                body.put("breakthrough_notebook_id", result.breakthroughNotebookId());
            }
            return ResponseEntity.ok(ApiResponse.success(body));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping({"/api/ai/tutor/parsons/{sessionId}", "/api/ai/tutor/parsons/{sessionId}/"})
    public ResponseEntity<ApiResponse<Object>> parsonsLoad(@PathVariable("sessionId") String sessionId,
                                                           Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("permission-denied", "请先登录"));
        }
        try {
            Map<String, Object> payload = parsonsCapabilityService.loadCard(sessionId);
            return ResponseEntity.ok(ApiResponse.success(payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping({"/api/ai/tutor/strategy-feedback", "/api/ai/tutor/strategy-feedback/"})
    public ApiResponse<Object> strategyFeedback(@RequestBody StrategyFeedbackRequest request,
                                                Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            return ApiResponse.error("permission-denied", "请先登录");
        }
        strategyFeedbackService.recordFeedback(userId, trimToEmpty(request.strategyType()), trimToEmpty(request.rating()));
        return ApiResponse.success(Map.of("saved", true));
    }

    private Long parseLong(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("parseLong: invalid numeric value '{}' discarded", value);
            return null;
        }
    }

    private String trimToEmpty(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private String trimOrNull(Object raw) {
        if (raw == null) return null;
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }
}
