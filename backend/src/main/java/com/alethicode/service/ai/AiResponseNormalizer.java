package com.alethicode.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AiResponseNormalizer {

    private static final Logger log = LoggerFactory.getLogger(AiResponseNormalizer.class);

    private static final Set<String> PROVIDER_METADATA_KEYS = Set.of(
            "id", "object", "created", "model", "usage", "system_fingerprint",
            "service_tier", "prompt_filter_results", "role", "type", "index",
            "finish_reason", "delta", "reasoning_content", "tool_calls"
    );
    private static final Set<String> PROVIDER_CONTENT_KEYS = Set.of(
            "choices", "output", "output_text", "text", "content", "message", "error"
    );
    private static final Set<String> WRAPPER_KEYS = Set.of("data", "payload", "result", "response");
    private static final Set<String> TEXT_CANDIDATE_KEYS = Set.of("content", "text", "output_text", "value", "message");

    private final ObjectMapper objectMapper;

    public AiResponseNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String normalizeJsonObjectContent(String content) {
        String raw = sanitizeJsonLikeText(unwrapJsonCodeFence(content));
        if (!parseJsonMap(raw).isEmpty()) {
            return raw;
        }
        String extracted = extractFirstJsonObject(raw);
        return extracted == null ? raw : sanitizeJsonLikeText(extracted);
    }

    public Map<String, Object> parseJsonResultFromLlmResponseBody(String responseBody) {
        Map<String, Object> respObj = parseJsonMap(responseBody);
        if (respObj.isEmpty()) {
            Map<String, Object> recovered = tryParseJsonObjectCandidate(responseBody);
            if (recovered == null) {
                throw new IllegalStateException("LLM response is not valid JSON object");
            }
            return resolveParsedJsonResult(recovered, responseBody, true);
        }
        return resolveParsedJsonResult(respObj, responseBody, false);
    }

    public String parseTextResultFromLlmResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("LLM response body is blank");
        }
        String normalizedBody = responseBody.trim();
        Map<String, Object> respObj = parseJsonMap(normalizedBody);
        if (respObj.isEmpty()) {
            return normalizedBody;
        }
        String providerError = extractProviderError(respObj);
        if (providerError != null) {
            throw new IllegalStateException("LLM response contains error payload: " + providerError);
        }
        String content = extractResponseContent(respObj);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("LLM response missing content");
        }
        return content.trim();
    }

    public String extractProviderError(Map<String, Object> respObj) {
        Object errorObj = respObj.get("error");
        if (errorObj == null) return null;
        if (errorObj instanceof Map<?, ?> map) {
            Object message = map.get("message");
            if (message != null && !String.valueOf(message).isBlank()) {
                return String.valueOf(message);
            }
            Object code = map.get("code");
            if (code != null && !String.valueOf(code).isBlank()) {
                return String.valueOf(code);
            }
            String serialized = toJson(errorObj);
            return serialized.isBlank() ? "unknown_error" : serialized;
        }
        String plain = String.valueOf(errorObj).trim();
        return plain.isEmpty() ? null : plain;
    }

    /**
     * Parses the given text into a {@code Map}.
     *
     * <p>Returns an empty, immutable map both when {@code raw} is blank and when parsing fails.
     * Callers that need to distinguish "model returned a literal {@code {}}" from "model output
     * is not JSON" must use {@link #tryParseJsonMap(String)} instead.
     */
    public Map<String, Object> parseJsonMap(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    /**
     * Strict JSON object parser. Returns {@link java.util.Optional#empty()} only when the input
     * text is not a JSON object; a legitimately empty JSON object is returned as an empty but
     * present {@code Map}.
     */
    public java.util.Optional<Map<String, Object>> tryParseJsonMap(String raw) {
        if (raw == null || raw.isBlank()) return java.util.Optional.empty();
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<>() {});
            return java.util.Optional.of(parsed);
        } catch (JsonProcessingException ignored) {
            return java.util.Optional.empty();
        }
    }

    // --- private helpers ---

    private String unwrapJsonCodeFence(String content) {
        String raw = (content == null ? "" : content).trim();
        if (raw.contains("<think>")) {
            int thinkEnd = raw.indexOf("</think>");
            if (thinkEnd >= 0) {
                raw = raw.substring(thinkEnd + "</think>".length()).trim();
            } else {
                int firstBrace = raw.indexOf('{');
                if (firstBrace >= 0) {
                    raw = raw.substring(firstBrace).trim();
                }
            }
        }
        if (raw.startsWith("```") && raw.endsWith("```")) {
            int firstBreak = raw.indexOf('\n');
            if (firstBreak > -1) {
                raw = raw.substring(firstBreak + 1, raw.length() - 3).trim();
            }
        }
        return raw;
    }

    private String sanitizeJsonLikeText(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        StringBuilder sanitized = new StringBuilder(raw.length() + 32);
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (!inString && current == '\ufeff') continue;
            if (escaped) {
                sanitized.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\') {
                sanitized.append(current);
                escaped = true;
                continue;
            }
            if (current == '"') {
                if (!inString) {
                    sanitized.append(current);
                    inString = true;
                } else if (isLikelyStringTerminator(raw, index)) {
                    sanitized.append(current);
                    inString = false;
                } else {
                    sanitized.append("\\\"");
                }
                continue;
            }
            if (inString) {
                appendEscapedStringCharacter(sanitized, current);
                continue;
            }
            sanitized.append(current);
        }
        return sanitized.toString().trim();
    }

    private boolean isLikelyStringTerminator(String raw, int quoteIndex) {
        char next = nextNonWhitespaceChar(raw, quoteIndex + 1);
        return next == ':' || next == ',' || next == '}' || next == ']' || next == '\0';
    }

    private char nextNonWhitespaceChar(String raw, int start) {
        for (int index = start; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (!Character.isWhitespace(current)) return current;
        }
        return '\0';
    }

    private void appendEscapedStringCharacter(StringBuilder sanitized, char current) {
        switch (current) {
            case '\n' -> sanitized.append("\\n");
            case '\r' -> sanitized.append("\\r");
            case '\t' -> sanitized.append("\\t");
            case '\b' -> sanitized.append("\\b");
            case '\f' -> sanitized.append("\\f");
            default -> {
                if (current < 0x20) {
                    sanitized.append("\\u");
                    String hex = Integer.toHexString(current);
                    sanitized.append("0000", 0, 4 - hex.length());
                    sanitized.append(hex);
                } else {
                    sanitized.append(current);
                }
            }
        }
    }

    private String extractFirstJsonObject(String raw) {
        if (raw == null || raw.isBlank()) return null;
        int start = raw.indexOf('{');
        if (start < 0) return null;
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = start; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (escaped) { escaped = false; continue; }
            if (current == '\\') { escaped = true; continue; }
            if (current == '"') {
                if (!inString) inString = true;
                else if (isLikelyStringTerminator(raw, index)) inString = false;
                continue;
            }
            if (inString) continue;
            if (current == '{') depth++;
            else if (current == '}') {
                depth--;
                if (depth == 0) return raw.substring(start, index + 1).trim();
            }
        }
        return null;
    }

    private Map<String, Object> resolveParsedJsonResult(Map<String, Object> respObj,
                                                        String responseBody,
                                                        boolean recoveredFromResponseBody) {
        String providerError = extractProviderError(respObj);
        if (providerError != null) {
            throw new IllegalStateException("LLM response contains error payload: " + providerError);
        }
        Map<String, Object> directPayload = tryExtractTopLevelBusinessPayload(respObj);
        if (directPayload != null) {
            logRecoveredPayload(recoveredFromResponseBody ? "direct_response_body" : "top_level_business_payload", responseBody);
            return directPayload;
        }
        Map<String, Object> wrappedPayload = tryExtractWrappedBusinessPayload(respObj);
        if (wrappedPayload != null) {
            logRecoveredPayload(recoveredFromResponseBody ? "normalized_wrapped_payload" : "wrapped_business_payload", responseBody);
            return wrappedPayload;
        }
        String content = extractResponseContent(respObj);
        if (content != null) {
            String jsonContent = normalizeJsonObjectContent(content);
            Map<String, Object> result = parseJsonMap(jsonContent);
            if (result.isEmpty()) {
                log.debug("LLM message.content is not valid JSON object (bytes={})",
                        content.length());
                throw new IllegalStateException(
                        "LLM message.content is not valid JSON object (bytes=" + content.length() + ")");
            }
            if (recoveredFromResponseBody) {
                logRecoveredPayload("normalized_response_body_content", responseBody);
            }
            return result;
        }
        Map<String, Object> nestedPayload = recoverNestedPayload(respObj, false);
        if (nestedPayload != null) {
            logRecoveredPayload(recoveredFromResponseBody ? "normalized_nested_content_scan" : "nested_content_scan", responseBody);
            return nestedPayload;
        }
        throw new IllegalStateException("LLM response missing choices");
    }

    private Map<String, Object> tryExtractTopLevelBusinessPayload(Map<String, Object> respObj) {
        if (isLikelyProviderEnvelope(respObj)) return null;
        return looksLikeBusinessPayload(respObj) ? respObj : null;
    }

    private Map<String, Object> tryExtractWrappedBusinessPayload(Map<String, Object> respObj) {
        for (String wrapperKey : WRAPPER_KEYS) {
            Map<String, Object> payload = recoverNestedPayload(respObj.get(wrapperKey), true);
            if (payload != null) return payload;
        }
        return null;
    }

    private Map<String, Object> recoverNestedPayload(Object node, boolean allowDirectMap) {
        if (node == null) return null;
        if (node instanceof String directText) {
            return tryResolveJsonObjectCandidate(directText);
        }
        if (node instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> payload = recoverNestedPayload(item, allowDirectMap);
                if (payload != null) return payload;
            }
            return null;
        }
        Map<String, Object> map = asMap(node);
        if (map.isEmpty()) return null;
        if (allowDirectMap && !isLikelyProviderEnvelope(map) && looksLikeBusinessPayload(map)) {
            return map;
        }
        for (String wrapperKey : WRAPPER_KEYS) {
            Map<String, Object> payload = recoverNestedPayload(map.get(wrapperKey), true);
            if (payload != null) return payload;
        }
        for (String textKey : TEXT_CANDIDATE_KEYS) {
            Map<String, Object> payload = recoverPayloadFromTextValue(map.get(textKey));
            if (payload != null) return payload;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (WRAPPER_KEYS.contains(key) || TEXT_CANDIDATE_KEYS.contains(key)) continue;
            Map<String, Object> payload = recoverNestedPayload(entry.getValue(), WRAPPER_KEYS.contains(key));
            if (payload != null) return payload;
        }
        return null;
    }

    private Map<String, Object> recoverPayloadFromTextValue(Object value) {
        String textCandidate = extractTextCandidate(value);
        if (textCandidate == null) return null;
        return tryResolveJsonObjectCandidate(textCandidate);
    }

    private String extractTextCandidate(Object value) {
        if (value == null) return null;
        if (value instanceof String directText) {
            return directText.isBlank() ? null : directText.trim();
        }
        if (value instanceof List<?> list) {
            List<String> fragments = new ArrayList<>();
            for (Object item : list) {
                String fragment = extractTextCandidate(item);
                if (fragment != null) fragments.add(fragment);
            }
            return fragments.isEmpty() ? null : String.join("\n", fragments);
        }
        Map<String, Object> map = asMap(value);
        if (map.isEmpty()) return null;
        for (String textKey : TEXT_CANDIDATE_KEYS) {
            String candidate = extractTextCandidate(map.get(textKey));
            if (candidate != null) return candidate;
        }
        return null;
    }

    private Map<String, Object> tryParseJsonObjectCandidate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String jsonContent = normalizeJsonObjectContent(raw.trim());
        Map<String, Object> parsed = parseJsonMap(jsonContent);
        return parsed.isEmpty() ? null : parsed;
    }

    private Map<String, Object> tryResolveJsonObjectCandidate(String raw) {
        Map<String, Object> parsed = tryParseJsonObjectCandidate(raw);
        if (parsed == null || extractProviderError(parsed) != null) return null;
        Map<String, Object> directPayload = tryExtractTopLevelBusinessPayload(parsed);
        if (directPayload != null) return directPayload;
        Map<String, Object> wrappedPayload = tryExtractWrappedBusinessPayload(parsed);
        if (wrappedPayload != null) return wrappedPayload;
        String content = extractResponseContent(parsed);
        if (content != null) {
            String jsonContent = normalizeJsonObjectContent(content);
            Map<String, Object> result = parseJsonMap(jsonContent);
            if (!result.isEmpty()) return result;
        }
        return recoverNestedPayload(parsed, false);
    }

    private boolean looksLikeBusinessPayload(Map<String, Object> payload) {
        if (payload.isEmpty()) return false;
        for (String key : payload.keySet()) {
            if (!PROVIDER_METADATA_KEYS.contains(key) && !PROVIDER_CONTENT_KEYS.contains(key) && !WRAPPER_KEYS.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLikelyProviderEnvelope(Map<String, Object> payload) {
        for (String key : payload.keySet()) {
            if (PROVIDER_CONTENT_KEYS.contains(key)) return true;
        }
        return false;
    }

    String extractResponseContent(Map<String, Object> respObj) {
        String fromChoices = extractContentFromChoices(respObj.get("choices"));
        if (fromChoices != null) return fromChoices;
        String outputText = extractTextCandidate(respObj.get("output_text"));
        if (outputText != null) return outputText;
        String messageContent = extractResponseApiOutput(respObj.get("output"));
        if (messageContent != null) return messageContent;
        String text = extractTextCandidate(respObj.get("text"));
        if (text != null) return text;
        return extractTextCandidate(respObj.get("content"));
    }

    private String extractContentFromChoices(Object choicesObj) {
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) return null;
        for (Object choiceObj : choices) {
            Map<String, Object> choice = asMap(choiceObj);
            if (choice.isEmpty()) continue;
            String fromMessage = extractMessageContent(choice.get("message"));
            if (fromMessage != null) return fromMessage;
            Object textObj = choice.get("text");
            if (textObj instanceof String t && !t.isBlank()) return t.trim();
            String fromDelta = extractMessageContent(choice.get("delta"));
            if (fromDelta != null) return fromDelta;
        }
        return null;
    }

    private String extractMessageContent(Object messageObj) {
        if (messageObj == null) return null;
        if (messageObj instanceof String direct) {
            return direct.isBlank() ? null : direct.trim();
        }
        Map<String, Object> message = asMap(messageObj);
        if (message.isEmpty()) return null;
        Object contentObj = message.get("content");
        if (contentObj instanceof String c) {
            return c.isBlank() ? null : c.trim();
        }
        if (contentObj instanceof List<?> contentList) {
            List<String> textParts = new ArrayList<>();
            for (Object item : contentList) {
                if (item instanceof String textPart) {
                    if (!textPart.isBlank()) textParts.add(textPart.trim());
                    continue;
                }
                Map<String, Object> contentMap = asMap(item);
                if (contentMap.isEmpty()) continue;
                String part = firstNonBlank(
                        stringVal(contentMap.get("text")),
                        stringVal(contentMap.get("content")),
                        stringVal(contentMap.get("output_text")),
                        stringVal(contentMap.get("value"))
                );
                if (part != null) textParts.add(part);
            }
            if (!textParts.isEmpty()) return String.join("\n", textParts);
        }
        return firstNonBlank(
                stringVal(message.get("text")),
                stringVal(message.get("output_text")),
                stringVal(message.get("value"))
        );
    }

    private String extractResponseApiOutput(Object outputObj) {
        if (!(outputObj instanceof List<?> outputList) || outputList.isEmpty()) return null;
        for (Object outputItem : outputList) {
            Map<String, Object> outputMap = asMap(outputItem);
            if (outputMap.isEmpty()) continue;
            String direct = firstNonBlank(
                    stringVal(outputMap.get("text")),
                    stringVal(outputMap.get("output_text"))
            );
            if (direct != null) return direct;
            String nested = extractMessageContent(outputMap);
            if (nested != null) return nested;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> data = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                data.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return data;
        }
        return new LinkedHashMap<>();
    }

    private String stringVal(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("json serialize failed", e);
        }
    }

    private void logRecoveredPayload(String recoveryMode, String source) {
        log.debug("Recovered LLM JSON payload via {} (bytes={})",
                recoveryMode, source == null ? 0 : source.length());
    }
}
