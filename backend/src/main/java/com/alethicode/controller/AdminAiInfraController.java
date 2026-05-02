package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.EnvSnapshotResponse;
import com.alethicode.dto.response.InfraSecretsResponse;
import com.alethicode.service.system.SystemOptionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminAiInfraController {

    private final SystemOptionService systemOptionService;

    public AdminAiInfraController(SystemOptionService systemOptionService) {
        this.systemOptionService = systemOptionService;
    }

    @GetMapping({"/api/admin/ai/infra/overview", "/api/admin/ai/infra/overview/"})
    public ApiResponse<Map<String, Object>> getInfraOverview(Authentication authentication) {
        EnvSnapshotResponse env = systemOptionService.getEnvSnapshot();
        InfraSecretsResponse secrets = systemOptionService.getInfraSecrets();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("temporal", Map.of(
                "enabled", env.temporalEnabled(),
                "target", env.temporalTarget(),
                "namespace", env.temporalNamespace(),
                "task_queue", env.temporalTaskQueue()
        ));
        payload.put("unleash", Map.of(
                "configured", secrets.unleashApiKeySet(),
                "api_url", secrets.unleashApiUrl(),
                "project", secrets.unleashProject()
        ));
        payload.put("nats", Map.of(
                "configured", secrets.natsUrlSet(),
                "url", secrets.natsUrl(),
                "transport", env.judgeDispatchTransport(),
                "stream_name", env.natsStreamName(),
                "subject", env.natsSubject()
        ));
        payload.put("langfuse", Map.of(
                "configured", env.langfuseEnabled(),
                "base_url", env.langfuseBaseUrl(),
                "environment", env.langfuseTracingEnvironment()
        ));
        payload.put("fsrs", Map.of(
                "enabled", env.fsrsEnabled(),
                "desired_retention", env.fsrsDesiredRetention()
        ));
        return ApiResponse.success(payload);
    }
}
