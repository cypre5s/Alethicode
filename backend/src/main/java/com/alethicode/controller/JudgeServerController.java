package com.alethicode.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.request.JudgeServerHeartbeatRequest;
import com.alethicode.dto.request.UpdateJudgeServerRequest;
import com.alethicode.dto.request.monitor.JudgeHeartbeatV2Request;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.JudgeServerListResponse;
import com.alethicode.service.submission.JudgeServerService;
import com.alethicode.util.HashUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping
public class JudgeServerController {

    private final JudgeServerService judgeServerService;
    private final AlethicodeProperties properties;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public JudgeServerController(
            JudgeServerService judgeServerService,
            AlethicodeProperties properties,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.judgeServerService = judgeServerService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping({
            "/api/judge-server-heartbeat",
            "/api/judge-server-heartbeat/"
    })
    public ResponseEntity<ApiResponse<Object>> heartbeat(
            @RequestHeader(name = "X-JUDGE-SERVER-TOKEN", required = false) String judgeServerToken,
            @RequestBody JsonNode requestBody,
            HttpServletRequest httpServletRequest
    ) {
        String expected = HashUtils.sha256(properties.getJudgeServer().getToken());
        if (!expected.equals(judgeServerToken)) {
            return ResponseEntity.ok(ApiResponse.error("error", "Invalid token"));
        }

        JudgeServerHeartbeatRequest request = deserializeHeartbeatRequest(requestBody);
        judgeServerService.handleHeartbeat(request, httpServletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping({
            "/api/admin/judge-server",
            "/api/admin/judge-server/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<JudgeServerListResponse> listJudgeServers() {
        return ApiResponse.success(judgeServerService.getActiveJudgeServers());
    }

    @DeleteMapping({
            "/api/admin/judge-server",
            "/api/admin/judge-server/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<Void> deleteJudgeServer(@RequestParam(name = "hostname", required = false) String hostname) {
        judgeServerService.deleteJudgeServer(hostname);
        return ApiResponse.success(null);
    }

    @PutMapping({
            "/api/admin/judge-server",
            "/api/admin/judge-server/"
    })
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEACHER')")
    public ApiResponse<Void> updateJudgeServer(@Valid @RequestBody UpdateJudgeServerRequest request) {
        judgeServerService.updateJudgeServer(request.id(), request.isDisabled());
        return ApiResponse.success(null);
    }

    private JudgeServerHeartbeatRequest deserializeHeartbeatRequest(JsonNode requestBody) {
        if (requestBody.hasNonNull("nodeInfo") && requestBody.hasNonNull("hostMetrics")) {
            JudgeHeartbeatV2Request request = objectMapper.convertValue(requestBody, JudgeHeartbeatV2Request.class);
            validateRequest(request);
            return new JudgeServerHeartbeatRequest(
                    request.hostname(),
                    request.judgerVersion(),
                    request.nodeInfo().cpuCore(),
                    toPercent(request.hostMetrics().memoryUsageRatio()),
                    toPercent(request.hostMetrics().cpuUsageRatio()),
                    request.action(),
                    request.serviceUrl()
            );
        }

        JudgeServerHeartbeatRequest request = objectMapper.convertValue(requestBody, JudgeServerHeartbeatRequest.class);
        validateRequest(request);
        return request;
    }

    private <T> void validateRequest(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private Double toPercent(Double ratio) {
        return ratio == null ? null : ratio * 100.0;
    }
}
