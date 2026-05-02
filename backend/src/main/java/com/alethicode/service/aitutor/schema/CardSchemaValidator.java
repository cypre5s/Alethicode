package com.alethicode.service.aitutor.schema;

import com.alethicode.service.aitutor.contract.CardType;

import java.util.List;
import java.util.Map;

public class CardSchemaValidator {

    private final CardSchemaRegistry registry;

    public CardSchemaValidator(CardSchemaRegistry registry) {
        this.registry = registry;
    }

    public void validateOrThrow(CardType cardType, Map<String, Object> payload) {
        for (String field : registry.requiredFields(cardType)) {
            Object value = payload.get(field);
            if (value == null) {
                throw new IllegalStateException("schema violation for " + cardType.messageType() + ": " + field + " is required");
            }
            if (value instanceof String stringValue
                    && stringValue.trim().isEmpty()
                    && !allowBlankString(cardType, field, payload)) {
                throw new IllegalStateException("schema violation for " + cardType.messageType() + ": " + field + " is required");
            }
            if (value instanceof List<?> listValue && listValue.isEmpty() && !registry.allowEmptyList(cardType, field)) {
                throw new IllegalStateException("schema violation for " + cardType.messageType() + ": " + field + " is required");
            }
            if (value instanceof Map<?, ?> mapValue && mapValue.isEmpty() && !"blanks".equals(field)) {
                throw new IllegalStateException("schema violation for " + cardType.messageType() + ": " + field + " is required");
            }
        }
    }

    private boolean allowBlankString(CardType cardType, String field, Map<String, Object> payload) {
        if (cardType == CardType.IDEATE_ANALYSIS && "logic_gap_hint".equals(field)) {
            return !parseBoolean(payload.get("has_logic_gap"));
        }
        if (cardType == CardType.EXECUTION_TRACE_EXPLAINER && "failure_reason".equals(field)) {
            return "ready".equalsIgnoreCase(String.valueOf(payload.getOrDefault("status", "")));
        }
        if (cardType == CardType.EXECUTION_TRACE_EXPLAINER && "input_sample".equals(field)) {
            return "failed".equalsIgnoreCase(String.valueOf(payload.getOrDefault("status", "")));
        }
        return false;
    }

    private boolean parseBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            return "true".equalsIgnoreCase(s.trim());
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        return false;
    }
}
