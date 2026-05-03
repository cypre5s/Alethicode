package com.alethicode.controller.internal;

import com.alethicode.config.InternalServiceKeyMatcher;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.aitutor.InternalAITutorToolService;
import com.alethicode.service.aitutor.profile.ContextSignals;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import java.util.Map;

@RestController
@RequestMapping("/internal/ai-tutor")
public class InternalAITutorToolController {

    private final InternalAITutorToolService service;
    private final InternalServiceKeyMatcher internalServiceKeyMatcher;

    public InternalAITutorToolController(
            InternalAITutorToolService service,
            InternalServiceKeyMatcher internalServiceKeyMatcher
    ) {
        this.service = service;
        this.internalServiceKeyMatcher = internalServiceKeyMatcher;
    }

    private void validateServiceKey(String key) {
        if (!internalServiceKeyMatcher.isConfigured()) {
            throw new org.springframework.security.access.AccessDeniedException("Internal service key not configured");
        }
        if (key == null || key.isBlank()) {
            // Some HTTP clients send the header with an empty value — do not NPE on key.getBytes.
            throw new org.springframework.security.access.AccessDeniedException("Missing X-Internal-Service-Key header");
        }
        if (!internalServiceKeyMatcher.matches(key)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid internal service key");
        }
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(org.springframework.security.access.AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(
            com.alethicode.service.aitutor.visualize.VisualizeValidationException.class)
    public ResponseEntity<Map<String, String>> handleVisualizeValidation(
            com.alethicode.service.aitutor.visualize.VisualizeValidationException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(
            com.alethicode.service.aitutor.impl.InternalAITutorToolServiceImpl.ProblemNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(
            com.alethicode.service.aitutor.impl.InternalAITutorToolServiceImpl.ProblemNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException e) {
        // Internal API: the caller is tutor_graph, not an end user. Still redact the
        // exception message so logs are the single source of truth and an accidental
        // leak (e.g. key compromise) doesn't surface stack traces on the wire.
        org.slf4j.LoggerFactory.getLogger(InternalAITutorToolController.class)
                .error("internal tool runtime error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "internal error"));
    }

    @GetMapping("/problems/{problemId}/workflow-context")
    public ResponseEntity<Object> getWorkflowContext(
            @PathVariable Long problemId,
            @RequestParam("user_id") Long userId,
            @RequestParam("session_id") String sessionId,
            @RequestParam("language") String language,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        return ResponseEntity.ok(service.getWorkflowContext(problemId, userId, sessionId, language));
    }

    @GetMapping("/submissions/{submissionId}/diagnosis-evidence")
    public ResponseEntity<Object> getDiagnosisEvidence(
            @PathVariable String submissionId,
            @RequestParam("user_id") Long userId,
            @RequestParam("problem_id") Long problemId,
            @RequestParam("session_id") String sessionId,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        return ResponseEntity.ok(service.getDiagnosisEvidence(submissionId, userId, problemId, sessionId));
    }

    @PostMapping("/learners/{userId}/state-with-context")
    public ResponseEntity<Object> getLearnerStateWithContext(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        Long problemId = readLong(body, "problem_id");
        String sessionId = readString(body, "session_id");
        String language = readString(body, "language");
        ContextSignals signals = readContextSignals(body.get("context_signals"));
        return ResponseEntity.ok(service.getLearnerState(userId, problemId, sessionId, language, signals));
    }

    @SuppressWarnings("unchecked")
    private ContextSignals readContextSignals(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        Object kcsRaw = map.get("current_kcs");
        List<String> kcs = kcsRaw instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        String errorContext = map.get("current_error_context") == null
                ? "" : map.get("current_error_context").toString();
        String problemStatement = map.get("current_problem_statement") == null
                ? "" : map.get("current_problem_statement").toString();
        return new ContextSignals(kcs, errorContext, problemStatement);
    }

    private Long readLong(Map<String, Object> body, String name) {
        Object value = body.get(name);
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number");
        }
    }

    private String readString(Map<String, Object> body, String name) {
        Object value = body.get(name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.toString();
    }

    @GetMapping("/problems/{problemId}/courseware-hits")
    public ResponseEntity<Object> getCoursewareHits(
            @PathVariable Long problemId,
            @RequestParam("user_id") Long userId,
            @RequestParam("session_id") String sessionId,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        return ResponseEntity.ok(service.getCoursewareHits(problemId, userId, sessionId));
    }

    @GetMapping("/learners/{userId}/similar-errors")
    public ResponseEntity<Object> getSimilarErrors(
            @PathVariable Long userId,
            @RequestParam("problem_id") Long problemId,
            @RequestParam("session_id") String sessionId,
            @RequestParam("language") String language,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        return ResponseEntity.ok(service.getSimilarErrors(userId, problemId, sessionId, language));
    }

    @PostMapping("/transfer-problems")
    public ResponseEntity<Object> createTransferProblem(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createTransferProblem(request));
    }

    @PostMapping("/workflow-events")
    public ResponseEntity<Object> postWorkflowEvent(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        return ResponseEntity.ok(service.recordWorkflowEvent(request));
    }

    @PostMapping("/visualize/dispatch")
    public ResponseEntity<Object> dispatchVisualize(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        return ResponseEntity.ok(service.dispatchVisualize(request));
    }

    @PostMapping("/parsons/dispatch")
    public ResponseEntity<Object> dispatchParsons(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        return ResponseEntity.ok(service.dispatchParsons(request));
    }

    @PostMapping("/parsons/grade")
    public ResponseEntity<Object> gradeParsons(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        return ResponseEntity.ok(service.gradeParsons(request));
    }

    @GetMapping("/sessions/{sessionId}/last-cards")
    public ResponseEntity<Object> getLastCards(
            @PathVariable String sessionId,
            @RequestParam(name = "limit", required = false, defaultValue = "5") int limit,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        return ResponseEntity.ok(service.getLastCards(sessionId, limit));
    }

    @PostMapping("/sessions/{sessionId}/references/resolve")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Object> resolveReferences(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-Internal-Service-Key") String key
    ) {
        validateServiceKey(key);
        Object raw = request == null ? null : request.get("references");
        List<String> references = raw instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        // current_query 用作 @courseware:<lpId> 的 RAG 检索 query；缺失时不解析 courseware
        // 引用（保留 backwards compat：tutor-graph 老调用者只传 references 仍能拿到 cards）。
        String currentQuery = request == null ? null : asTrimmedString(request.get("current_query"));
        return ResponseEntity.ok(service.resolveReferences(sessionId, references, currentQuery));
    }

    private static String asTrimmedString(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }
}
