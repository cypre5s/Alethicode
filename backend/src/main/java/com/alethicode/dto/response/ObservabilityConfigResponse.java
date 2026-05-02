package com.alethicode.dto.response;

public record ObservabilityConfigResponse(
        String grafanaUrl,
        String source
) {
}
