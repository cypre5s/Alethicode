package com.alethicode.controller.twin;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.twin.metacog.MetacognitivePredictionService;
import com.alethicode.util.AuthUserResolver;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/twin/metacog")
public class MetacognitiveController {

    private final MetacognitivePredictionService predictionService;

    public MetacognitiveController(MetacognitivePredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @PostMapping("/predict")
    public ApiResponse<Map<String, Object>> submitPrediction(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long userId = requireUserId(authentication);
        Number problemId = (Number) body.get("problem_id");
        if (problemId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "problem_id required");
        }
        String predictedOutput = (String) body.get("predicted_output");
        if (predictedOutput == null || predictedOutput.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "predicted_output required");
        }
        String predictedReason = body.get("predicted_reason") != null ? body.get("predicted_reason").toString() : null;
        String codeSnapshot = body.get("code_snapshot") != null ? body.get("code_snapshot").toString() : null;
        String sessionId = body.get("session_id") != null ? body.get("session_id").toString() : null;

        long eventId = predictionService.recordPrediction(
                userId, problemId.longValue(), predictedOutput, predictedReason, codeSnapshot, sessionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event_id", eventId);
        return ApiResponse.success(result);
    }

    @GetMapping("/map")
    public ApiResponse<Map<String, Object>> getMetacognitiveMap(Authentication authentication) {
        Long userId = requireUserId(authentication);
        return ApiResponse.success(predictionService.getMetacognitiveMap(userId));
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = AuthUserResolver.currentUserIdOrNull(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userId;
    }
}
