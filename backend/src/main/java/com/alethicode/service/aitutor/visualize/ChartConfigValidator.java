package com.alethicode.service.aitutor.visualize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validate Chart.js v4 configuration against a strict JSON Schema:
 *   - type must be one of: line / bar / radar
 *   - data.labels max 20
 *   - data.datasets length 1..3, each dataset.data max 20 items
 *   - reject options.plugins.tooltip.callbacks / onClick / onHover (function injection)
 */
@Component
public class ChartConfigValidator {

    private static final String SCHEMA_JSON = "{"
            + "\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
            + "\"type\":\"object\","
            + "\"required\":[\"type\",\"data\"],"
            + "\"properties\":{"
            +   "\"type\":{\"enum\":[\"line\",\"bar\",\"radar\"]},"
            +   "\"data\":{\"type\":\"object\",\"required\":[\"labels\",\"datasets\"],\"properties\":{"
            +     "\"labels\":{\"type\":\"array\",\"maxItems\":20},"
            +     "\"datasets\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":3,\"items\":{"
            +       "\"type\":\"object\",\"required\":[\"label\",\"data\"],\"properties\":{"
            +         "\"label\":{\"type\":\"string\",\"maxLength\":50},"
            +         "\"data\":{\"type\":\"array\",\"maxItems\":20}"
            +       "}"
            +     "}}"
            +   "}}"
            + "}}";

    private final ObjectMapper objectMapper;
    private JsonSchema schema;

    public ChartConfigValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initSchema() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try {
            schema = factory.getSchema(SCHEMA_JSON);
        } catch (Exception e) {
            throw new IllegalStateException("failed to load chart config schema", e);
        }
    }

    public void validate(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new VisualizeValidationException("chart payload is empty");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new VisualizeValidationException("chart payload is not valid JSON: " + e.getMessage());
        }
        Set<ValidationMessage> errors = schema.validate(root);
        if (!errors.isEmpty()) {
            ValidationMessage first = errors.iterator().next();
            throw new VisualizeValidationException("chart schema violation: " + first.getMessage());
        }
        rejectFunctionInjection(root);
    }

    private void rejectFunctionInjection(JsonNode root) {
        JsonNode options = root.get("options");
        if (options == null) return;
        JsonNode plugins = options.get("plugins");
        if (plugins != null) {
            JsonNode tooltip = plugins.get("tooltip");
            if (tooltip != null && tooltip.has("callbacks")) {
                throw new VisualizeValidationException(
                        "options.plugins.tooltip.callbacks is forbidden (function injection)");
            }
        }
        if (options.has("onClick") || options.has("onHover")) {
            throw new VisualizeValidationException(
                    "options.onClick / options.onHover are forbidden");
        }
    }
}
