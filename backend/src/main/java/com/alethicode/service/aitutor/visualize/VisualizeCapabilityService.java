package com.alethicode.service.aitutor.visualize;

import com.alethicode.service.ai.AiModelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class VisualizeCapabilityService {

    private static final Set<String> ALLOWED_FORMATS = Set.of("mermaid", "chart", "svg");

    private final AiModelGateway aiModelGateway;
    private final VisualizePromptCatalog promptCatalog;
    private final MermaidValidator mermaidValidator;
    private final ChartConfigValidator chartConfigValidator;
    private final SvgSanitizer svgSanitizer;
    private final ObjectMapper objectMapper;

    public VisualizeCapabilityService(AiModelGateway aiModelGateway,
                                      VisualizePromptCatalog promptCatalog,
                                      MermaidValidator mermaidValidator,
                                      ChartConfigValidator chartConfigValidator,
                                      SvgSanitizer svgSanitizer,
                                      ObjectMapper objectMapper) {
        this.aiModelGateway = aiModelGateway;
        this.promptCatalog = promptCatalog;
        this.mermaidValidator = mermaidValidator;
        this.chartConfigValidator = chartConfigValidator;
        this.svgSanitizer = svgSanitizer;
        this.objectMapper = objectMapper;
    }

    public VisualizeResult dispatch(VisualizeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("visualize request is required");
        }
        if (request.intent() == null) {
            throw new IllegalArgumentException("intent is required");
        }
        String prompt = trimToEmpty(request.prompt());
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("prompt is required");
        }

        String systemPrompt = promptCatalog.promptFor(request.intent());
        String userPrompt = buildUserPrompt(request, prompt);
        Map<String, Object> raw = aiModelGateway.callForJson(systemPrompt, userPrompt);

        String format = normalizeFormat(raw == null ? null : raw.get("format"), request.intent());
        String payload = normalizePayload(raw == null ? null : raw.get("payload"), format);
        String altText = normalizeAltText(raw, request.intent(), prompt);
        String normalizedSourceRole = normalizeSourceRole(request.sourceRole());

        String normalizedPayload = switch (format) {
            case "mermaid" -> {
                mermaidValidator.validate(payload);
                yield payload;
            }
            case "chart" -> {
                chartConfigValidator.validate(payload);
                yield payload;
            }
            case "svg" -> svgSanitizer.sanitize(payload);
            default -> throw new VisualizeValidationException("unknown format: " + format);
        };

        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("intent", request.intent().key());
        debug.put("format", format);
        debug.put("problem_id", request.problemId());
        debug.put("session_id", request.sessionId());

        return new VisualizeResult(
                request.intent(),
                format,
                normalizedPayload,
                altText,
                normalizedSourceRole,
                debug
        );
    }

    private String buildUserPrompt(VisualizeRequest request, String normalizedPrompt) {
        Map<String, Object> context = request.contextHints() == null ? Map.of() : request.contextHints();
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            contextJson = "{}";
        }
        return """
                【visualize_intent】
                %s

                【教学画图指令】
                %s

                【上下文提示（JSON）】
                %s

                【来源】
                source_role=%s
                user_id=%s
                problem_id=%s
                session_id=%s
                """.formatted(
                request.intent().key(),
                normalizedPrompt,
                contextJson,
                normalizeSourceRole(request.sourceRole()),
                request.userId() == null ? "" : request.userId(),
                request.problemId() == null ? "" : request.problemId(),
                trimToEmpty(request.sessionId())
        );
    }

    private String normalizeFormat(Object rawFormat, VisualizeIntent intent) {
        String normalized = rawFormat == null
                ? intent.defaultFormat()
                : rawFormat.toString().trim().toLowerCase();
        if (normalized.isBlank()) {
            normalized = intent.defaultFormat();
        }
        if (!ALLOWED_FORMATS.contains(normalized)) {
            throw new VisualizeValidationException("unknown format: " + normalized);
        }
        return normalized;
    }

    private String normalizePayload(Object rawPayload, String format) {
        if (rawPayload == null) {
            throw new VisualizeValidationException("payload is required");
        }
        if (rawPayload instanceof String payloadText) {
            String cleaned = stripCodeFence(payloadText.trim());
            if (cleaned.isBlank()) {
                throw new VisualizeValidationException("payload is empty");
            }
            return cleaned;
        }
        if ("chart".equals(format)) {
            try {
                return objectMapper.writeValueAsString(rawPayload);
            } catch (Exception e) {
                throw new VisualizeValidationException("chart payload cannot be serialized to JSON");
            }
        }
        throw new VisualizeValidationException(format + " payload must be string");
    }

    private String normalizeAltText(Map<String, Object> raw, VisualizeIntent intent, String prompt) {
        if (raw != null) {
            Object value = raw.get("alt_text");
            if (value == null) {
                value = raw.get("altText");
            }
            if (value != null && !value.toString().trim().isBlank()) {
                return value.toString().trim();
            }
        }
        return intent.label() + "：" + abbreviate(prompt, 80);
    }

    private String normalizeSourceRole(String raw) {
        String sourceRole = trimToEmpty(raw);
        return sourceRole.isBlank() ? "AI" : sourceRole;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String abbreviate(String value, int maxLength) {
        String normalized = trimToEmpty(value);
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, maxLength) + "...";
    }

    private String stripCodeFence(String payload) {
        if (!payload.startsWith("```")) {
            return payload;
        }
        String[] lines = payload.split("\\r?\\n");
        if (lines.length <= 1) {
            return payload;
        }
        int start = 0;
        int end = lines.length;
        if (lines[0].trim().startsWith("```")) {
            start = 1;
        }
        if (lines[end - 1].trim().equals("```")) {
            end -= 1;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString().trim();
    }
}
